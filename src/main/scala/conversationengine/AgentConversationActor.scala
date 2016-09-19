package conversationengine

import akka.actor.{Actor, ActorLogging}
import apis.ciscospark.SparkWebhookResponseData
import com.google.inject.Inject
import conversationengine.ConversationActor.TextLike
import modules.akkaguice.NamedActor
import services.{FacebookService, SparkService}

import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Created by markmo on 10/09/2016.
  */
class AgentConversationActor @Inject()(facebookService: FacebookService, sparkService: SparkService)
  extends Actor with ActorLogging {

  import AgentConversationActor._

  override def receive = {

    case SparkMessageEvent(sender, data) =>
      for (message <- sparkService.getMessage(data.id)) yield {
        if (message.personEmail == "m4rkmo@gmail.com") {
          facebookService.sendTextMessage(sender, message.text.get.substring(7))
        }
      }

    case SparkWrappedEvent(roomId, personId, message) =>
      sparkService.postMessage(Some(roomId), None, None, "user: " + message.text, None)
  }

}

object AgentConversationActor extends NamedActor {

  override final val name = "AgentConversationActor"

  case class SparkMessageEvent(sender: String, data: SparkWebhookResponseData)

  case class SparkRoomLeftEvent(sender: String)

  case class SparkWrappedEvent(roomId: String, personId: String, message: TextLike)

}
