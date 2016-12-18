package engines

import akka.actor.{Actor, ActorRef}
import akka.contrib.pattern.ReceivePipeline
import apis.wva.WvaJsonSupport
import com.google.inject.Inject
import com.google.inject.assistedinject.Assisted
import engines.interceptors.LoggingInterceptor
import models.events._
import modules.akkaguice.NamedActor
import services.WatsonVirtualAgentService
import spray.json._
import utils.Implicits

import scala.language.implicitConversions

/**
  * Created by markmo on 18/12/2016.
  */
class WvaConversationActor @Inject()(wvaService: WatsonVirtualAgentService,
                                     @Assisted("defaultProvider") val defaultProvider: ActorRef,
                                     @Assisted("historyActor") val historyActor: ActorRef)
  extends Actor
    with ReceivePipeline
    with LoggingInterceptor
    with WvaJsonSupport {

  import Implicits._
  import context.dispatcher

  // needed for Implicits.eitherToRichStringErrorEither
  implicit def errorHandler(e: String): Unit =
    log.debug(e.parseJson.prettyPrint)

  var provider: ActorRef = defaultProvider

  def receive = defaultReceive orElse uninitialized

  def uninitialized: Receive = {
    case ev@TextResponse(platform, sender, text) =>
      log.debug("provider {}", provider.path.name)
      if (provider.path.name != "synchronous-request-actor") {
        log.debug("chat starting from a non-WVA client")
        self ! InitiateChat(platform, sender)
      }

    case _ =>
      log.debug("still uninitialized")
  }

  def chatting(botId: String, chatId: String): Receive = defaultReceive orElse {
    case ev@TextResponse(_, sender, text) =>
      for {
        responseEither <- wvaService.send(chatId, text)
        response <- responseEither.rightFuture
      } yield {
        log.debug("provider {}", provider.path.name)
        if (provider.path.name == "synchronous-request-actor" ||
          response.layoutName == "show-locations") {

          log.debug("sending raw format from WVA")
          provider ! CustomMessage(sender, response.toJson)
        } else {
          log.debug("sending text message")
          response.message.text foreach { line =>
            log.debug(line)
            provider ! TextMessage(sender, line)
          }
        }
      }
  }

  val defaultReceive: Receive = {
    case InitiateChat(_, sender) =>
      log.debug("initializing WvaConversationActor")
      for {
        responseEither <- wvaService.start()
        response <- responseEither.rightFuture
      } yield {
        val botId = response.botId
        val chatId = response.dialogId
        context become chatting(botId, chatId)
        log.debug("provider {}", provider.path.name)
        if (provider.path.name == "synchronous-request-actor") {
          provider ! CustomMessage(sender, response.toJson)
        } else {
          response.message.text foreach { line =>
            log.debug(line)
            provider ! TextMessage(sender, line)
          }
        }
      }

    case SetProvider(_, _, ref, _, _, _) =>
      provider = ref

    case Reset => // nothing to reset
  }
}

object WvaConversationActor extends NamedActor {

  override final val name = "WvaConversationActor"

  trait Factory {
    def apply(@Assisted("defaultProvider") defaultProvider: ActorRef,
              @Assisted("historyActor") historyActor: ActorRef): Actor
  }

}