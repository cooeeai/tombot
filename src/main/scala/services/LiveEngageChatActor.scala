package services

import akka.actor.{Actor, ActorLogging, ActorRef}
import apis.liveengage.{LpChatJsonSupport, LpErrorResponse}
import com.google.inject.Inject
import com.typesafe.config.Config
import models.events.{SetProvider, TextMessage, TextResponse}
import modules.akkaguice.NamedActor
import spray.json._
import utils.General

import scala.concurrent.Future
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
        log.debug("chatUrl: {}", chatUrl)
        log.debug("eventsUrl: {}", eventsUrl)
        log.debug("nextUrl: {}", nextUrl)
        context become inChat(accessToken, nextUrl, eventsUrl)
        conversation.getLastVisitorEvent match {
          case Some(ev) =>
            // TODO
            // validate that `visitorId` is best identifier for a user session
            val sender = conversation.chat.info.visitorId.toString
            val text = ev.text
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

  def inChat(accessToken: String, chatUrl: String, eventsUrl: String): Receive = {

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
            val text = ev.text
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
        context become inChat(accessToken, nextUrl, eventsUrl)
      }

    case TextMessage(sender, text) =>
      log.debug("sending [{}] to [{}]", text, sender)
      leService.sendTextMessage(eventsUrl, accessToken, text)

  }

  class RichEither[T](either: Either[LpErrorResponse, T]) {

    def rightFuture = either.fold(handleError, Future.successful)

    def handleError(e: LpErrorResponse) = {
      log.error(e.toJson.prettyPrint)
      Future.failed(LpException(e.error.message))
    }

  }

  implicit def eitherToRichEither[T](either: Either[LpErrorResponse, T]): RichEither[T] =
    new RichEither[T](either)

}

object LiveEngageChatActor extends NamedActor {

  override final val name = "LiveEngageChatActor"

  case class LpException(message: String) extends Exception(message)

}
