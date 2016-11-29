package services

import akka.actor.{Actor, ActorLogging, ActorRef}
import com.google.inject.Inject
import com.typesafe.config.Config
import models.events.{SetProvider, TextMessage, TextResponse}
import modules.akkaguice.NamedActor

import scala.concurrent.duration._
import scala.util.{Failure, Success}

/**
  * Created by markmo on 27/11/2016.
  */
class LiveEngageChatActor @Inject()(config: Config,
                                    leService: LiveEngageService,
                                    conversationService: ConversationService)
  extends Actor with ActorLogging {

  import context.dispatcher
  import conversationService._
  import models.Platform._

  log.debug("initializing LiveEngageChatActor")
  for {
    loginResponse <- leService.login()
    accessToken = loginResponse.bearer
    agentSessionURL <- leService.createAgentSessionURL(accessToken)
  } yield context become awaitingChat(accessToken, agentSessionURL)

  // a negative consequence of polling is that a user message may be
  // reprocessed before the last process completed
  val tick = context.system.scheduler.schedule(1 seconds, 5 seconds, self, "tick")

  var conversationActor: Option[ActorRef] = None

  def receive = uninitialized

  def uninitialized: Receive = {
    case _ =>
      log.debug("still uninitialized")
  }

  def awaitingChat(accessToken: String, agentSessionURL: String): Receive = {

    case "tick" =>
      log.debug("tick awaitingChat")
      for {
        data <- leService.getRingCount(agentSessionURL, accessToken) if data.incomingRequests.ringingCount.toInt > 0
        chatURL <- leService.takeChat(agentSessionURL, accessToken)
        conversation <- leService.getChatConversation(chatURL, accessToken)
      } yield {
        val eventsURL = conversation.getEventsURL
        val nextURL = conversation.getNextURL
        log.debug("chatURL: {}", chatURL)
        log.debug("eventsURL: {}", eventsURL)
        log.debug("nextURL: {}", nextURL)
        context become inChat(accessToken, nextURL, eventsURL)
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

  def inChat(accessToken: String, chatURL: String, eventsURL: String): Receive = {

    case "tick" =>
      log.debug("tick inChat")
      for {
        conversation <- leService.continueConversation(chatURL, accessToken)
      } yield {
        val nextURL = conversation.getNextURL
        log.debug("nextURL: {}", nextURL)
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
        context become inChat(accessToken, nextURL, eventsURL)
      }

    case TextMessage(sender, text) =>
      log.debug("sending [{}] to [{}]", text, sender)
      leService.sendTextMessage(eventsURL, accessToken, text)

  }

}

object LiveEngageChatActor extends NamedActor {
  override final val name = "LiveEngageChatActor"
}
