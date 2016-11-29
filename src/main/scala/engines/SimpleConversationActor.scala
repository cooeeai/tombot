package engines

import akka.actor.{Actor, ActorRef}
import akka.contrib.pattern.ReceivePipeline
import engines.interceptors.LoggingInterceptor
import models.events.{Reset, SetProvider}

/**
  * Created by markmo on 20/11/2016.
  */
trait SimpleConversationActor
  extends Actor
    with ReceivePipeline
    with LoggingInterceptor {

  val defaultProvider: ActorRef
  val historyActor: ActorRef

  def receive = defaultWithProvider(defaultProvider)

  def defaultWithProvider(provider: ActorRef): Receive =
    defaultReceive orElse withProvider(provider)

  def withProvider(provider: ActorRef): Receive

  val defaultReceive: Receive = {
    case SetProvider(_, _, ref, _, _, _) =>
      context become defaultWithProvider(ref)

    case Reset => // nothing to reset
  }

}
