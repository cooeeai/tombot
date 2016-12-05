package services

import akka.actor.{Actor, ActorLogging, ActorRef}
import apis.liveengage.{LpChatJsonSupport, LpErrorResponse}
import com.google.inject.Inject
import com.typesafe.config.Config
import example.BuyConversationActor.Buy
import models.events._
import modules.akkaguice.NamedActor
import spray.json._
import utils.{General, Implicits}

import scala.concurrent.duration._
import scala.language.implicitConversions
import scala.util.{Failure, Success}

/**
  * Created by markmo on 27/11/2016.
  */
class LiveEngageChatActor @Inject()(config: Config,
                                    leService: LiveEngageService,
                                    conversationService: ConversationService)
  extends Actor with ActorLogging with LpChatJsonSupport with General {

  import Implicits._
  import LiveEngageChatActor._
  import context.dispatcher
  import conversationService._
  import models.Platform._

  log.debug("initializing LiveEngageChatActor")
  for {
    loginResponse <- leService.login()
    accessToken = loginResponse.bearer
    agentSessionUrl <- leService.createAgentSessionUrl(accessToken)
  } yield context become awaitingChat(accessToken, agentSessionUrl)

  // a negative consequence of polling is that a user message may be
  // reprocessed before the last process completed
  val tick = context.system.scheduler.schedule(1 seconds, 5 seconds, self, "tick")

  var conversationActor: Option[ActorRef] = None

  var expecting: Option[Expectation] = None

  // needed for Implicits.eitherToRichEither
  implicit def errorHandler(e: LpErrorResponse): Unit =
    log.debug(e.toJson.prettyPrint)

  def receive = uninitialized

  def uninitialized: Receive = {
    case _ =>
      log.debug("still uninitialized")
  }

  def awaitingChat(accessToken: String, agentSessionUrl: String): Receive = {

    case "tick" =>
      log.debug("tick awaitingChat")
      for {
        dataEither <- leService.getRingCount(agentSessionUrl, accessToken)
        data <- dataEither.rightFuture if data.incomingRequests.ringingCount.toInt > 0
        chatUrlEither <- leService.takeChat(agentSessionUrl, accessToken)
        chatUrl <- chatUrlEither.rightFuture
        conversationEither <- leService.getChatConversation(chatUrl, accessToken)
        conversation <- conversationEither.rightFuture
      } yield {
        val eventsUrl = conversation.getEventsUrl
        val nextUrl = conversation.getNextUrl
        val transferUrl = conversation.getTransferUrl
        val agentTypingUrl = conversation.getAgentTypingUrl
        log.debug("chatUrl: {}", chatUrl)
        log.debug("eventsUrl: {}", eventsUrl)
        log.debug("nextUrl: {}", nextUrl)
        log.debug("transferUrl: {}", transferUrl)
        log.debug("agentTypingUrl: {}", agentTypingUrl)
        context become inChat(accessToken, agentSessionUrl, nextUrl, eventsUrl, transferUrl, agentTypingUrl)
        conversation.getLastVisitorEvent match {
          case Some(ev) =>
            // TODO
            // validate that `visitorId` is best identifier for a user session
            val sender = conversation.chat.info.visitorId.toString
            val text = jsonValueToString(ev.text)
            log.debug("sender [{}]", sender)
            log.debug("text [{}]", text)
            val message = TextResponse(LiveEngageChat, sender, text)
            if (conversationActor.isEmpty) {
              getConversationActor(sender) onComplete {
                case Success(ref) =>
                  conversationActor = Some(ref)
                  ref ! SetProvider(LiveEngageChat, None, self, message, sender)
                case Failure(e) =>
                  // TODO
                  // send 'not understood' message and fallback
                  log.error(e, e.getMessage)
              }
            } else {
              converse(sender, message)
            }
          case None =>
            log.debug("no new events")
        }
      }

  }

  def inChat(accessToken: String, agentSessionUrl: String, chatUrl: String, eventsUrl: String, transferUrl: String, agentTypingUrl: String): Receive = {

    case "tick" =>
      log.debug("tick inChat")
      for {
        conversationEither <- leService.continueConversation(chatUrl, accessToken)
        conversation <- conversationEither.rightFuture
      } yield {
        val nextUrl = conversation.getNextUrl
        log.debug("nextUrl: {}", nextUrl)
        conversation.getLastVisitorEvent match {
          case Some(ev) =>
            // TODO
            // validate that `visitorId` is best identifier for a user session
            val sender = conversation.chat.info.visitorId.toString
            val text = jsonValueToString(ev.text)
            log.debug("sender [{}]", sender)
            log.debug("text [{}]", text)
            if (conversationActor.isEmpty) {
              getConversationActor(sender) onComplete {
                case Success(ref) =>
                  conversationActor = Some(ref)
                  val message = TextResponse(LiveEngageChat, sender, text)
                  ref ! SetProvider(LiveEngageChat, None, self, message, sender)
                case Failure(e) =>
                  // TODO
                  // send 'not understood' message and fallback
                  log.error(e, e.getMessage)
              }
            } else {
              expecting match {
                case Some(Expectation(selection, next)) =>
                  if (text.isInt) {
                    next(selection(text.toInt - 1))
                  } else {
                    val message = TextResponse(LiveEngageChat, sender, text)
                    converse(sender, message)
                  }
                  expecting = None
                case None =>
                  val message = TextResponse(LiveEngageChat, sender, text)
                  converse(sender, message)
              }
            }
          case None =>
            log.debug("no new events")
        }
        context become inChat(accessToken, agentSessionUrl, nextUrl, eventsUrl, transferUrl, agentTypingUrl)
      }

    case TextMessage(sender, text) =>
      log.debug("sending plain message [{}] to [{}]", text, sender)
      val message = clean(text).toHTML
      log.debug(message)
      leService.sendTextMessage(eventsUrl, accessToken, message, "html")

    case QuickReply(sender, text) =>
      log.debug("sending Quick Reply [{}] to [{}]", text, sender)
      val selection = List("Yes", "No")
      val nextEvent = (text: String) =>
        converse(sender, TextResponse(LiveEngageChat, sender, text))
      expecting = Some(Expectation(selection, nextEvent))
      val message = (clean(text) + "\n\nEnter 1 for Yes, 2 for No").toHTML
      leService.sendTextMessage(eventsUrl, accessToken, message, "html")

    case HeroCard(sender, items) =>
      log.debug("sending Hero Card to [{}]", sender)
      val selection = items.map(_.title)
      val nextEvent = (text: String) =>
        converse(sender, Buy(LiveEngageChat, sender, text))
      expecting = Some(Expectation(selection, nextEvent))
      val message = items.zipWithIndex map {
        case (it, i) => (i + 1) + ". " + it.title
      } mkString "\n"
      leService.sendTextMessage(eventsUrl, accessToken, message.toHTML, "html")

    case TransferToAgent =>
      log.debug("transferring to agent")
      for {
        sessionInfoEither <- leService.getSessionInfo(agentSessionUrl, accessToken)
        sessionInfo <- sessionInfoEither.rightFuture
        availableAgentsUrl = sessionInfo.getAvailableAgentsUrl
        availableAgentsEither <- leService.getAvailableAgents(availableAgentsUrl, accessToken)
        availableAgents <- availableAgentsEither.rightFuture
      } yield {
        availableAgents.getNextBestAgent match {
          case Some(agent) =>
            val name = agent.nickname
            log.info("Agent {} is available", name)
            leService.setAgentTyping(agentTypingUrl, accessToken, isTyping = true)
            val message = "Transferring you to " + name
            val transferMessage = s"Hi $name, can you assist please"
            context.system.scheduler.scheduleOnce(3 seconds) {
              leService.setAgentTyping(agentTypingUrl, accessToken, isTyping = false)
              leService.sendTextMessage(eventsUrl, accessToken, message)
              leService.transferToAgent(transferUrl, accessToken, agent.id, transferMessage)
            }
          case None =>
            log.info("No agents available")
            val message = "I'm sorry, all our agents are busy. Please hold.."
            leService.sendTextMessage(eventsUrl, accessToken, message)
            context.system.scheduler.scheduleOnce(30 seconds) {
              self ! TransferToAgent
            }
        }
      }
  }

  private def clean(str: String) =
    str
      .replaceAll("&lt;", "(")
      .replaceAll("&gt;", ")")

}

object LiveEngageChatActor extends NamedActor {

  override final val name = "LiveEngageChatActor"

  case class LpException(message: String) extends Exception(message)

  case class Expectation(selection: List[String], next: String => Unit)

}
