package engines

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.contrib.pattern.ReceivePipeline
import apis.ciscospark.SparkWebhookResponseData
import com.google.inject.Inject
import com.google.inject.assistedinject.Assisted
import engines.interceptors.LoggingInterceptor
import models.events.{SetProvider, TextMessage, TextResponse}
import modules.akkaguice.NamedActor
import services.SparkSendQueue.{TextMessage => SparkSendMessage}
import services.SparkService

/**
  * Created by markmo on 10/09/2016.
  */
class AgentConversationActor @Inject()(sparkService: SparkService,
                                       @Assisted("defaultUserProvider") defaultUserProvider: ActorRef,
                                       @Assisted("defaultAgentProvider") defaultAgentProvider: ActorRef,
                                       @Assisted("historyActor") historyActor: ActorRef)
  extends Actor
    with ActorLogging
    with ReceivePipeline
    with LoggingInterceptor {

  import AgentConversationActor._

  // import execution context for service calls
  import context.dispatcher

  def receive = withProvider(defaultUserProvider, defaultAgentProvider)

  def withProvider(userProvider: ActorRef, agentProvider: ActorRef): Receive = {

    case SparkMessageEvent(sender, data) =>
      for (message <- sparkService.getMessage(data.id)) yield {
        if (message.personEmail == "m4rkmo@gmail.com") {
          userProvider ! TextMessage(sender, message.text.get.substring(7))
        }
      }

    case SparkWrappedEvent(roomId, personId, message) =>
      agentProvider ! SparkSendMessage(Some(roomId), None, None, "user: " + message.text, None)

    case SetProvider(_, _, ref, _, _, _) =>
      context become withProvider(ref, agentProvider)

  }

}

object AgentConversationActor extends NamedActor {

  override final val name = "AgentConversationActor"

  trait Factory {
    def apply(@Assisted("defaultUserProvider") defaultUserProvider: ActorRef,
              @Assisted("defaultAgentProvider") defaultAgentProvider: ActorRef,
              @Assisted("historyActor") historyActor: ActorRef): Actor
  }

  case class SparkMessageEvent(sender: String, data: SparkWebhookResponseData)

  case class SparkRoomLeftEvent(sender: String)

  case class SparkWrappedEvent(roomId: String, personId: String, message: TextResponse)

}
