package controllers

import akka.actor.{Actor, ActorLogging}
import akka.contrib.pattern.ReceivePipeline
import com.google.inject.Inject
import engines.interceptors.LoggingInterceptor
import models.Platform
import models.events._
import services.ConversationService
import spray.json.JsValue

import scala.collection.mutable
import scala.concurrent.Promise

/**
  * Created by markmo on 18/12/2016.
  */
class SynchronousRequestActor @Inject()(conversationService: ConversationService) //, bus: LookupBusImpl)
  extends Actor with ActorLogging
    with ReceivePipeline
    with LoggingInterceptor {

  import context.dispatcher
  import conversationService._

  val promises: mutable.Map[String, Promise[JsValue]] = mutable.Map()

  //  val key = "response:" + self.path.name

  //  def preStart() = {
  //    log.debug("key {}", key)
  //    bus subscribe(self, key)
  //  }

  //  def postStop() = {
  //    bus unsubscribe key
  //  }

  def receive = {
    case ev@InitiateChat(_, from) =>
      val p = Promise[JsValue]()
      promises(from) = p
      getConversationActor(from) map { ref =>
        log.debug("sending SetProvider event to {}", ref.path)
        // TODO
        // may need to send a different event to avoid an infinite loop
        // as this actor also serves as provider
        ref ! SetProvider(Platform.WVA, None, self, ev, from, handleEventImmediately = true)
      }
      sender() ! p.future

    case ev@TextResponse(_, from, _, _) =>
      val p = Promise[JsValue]()
      promises(from) = p
      converse(from, ev)
      sender() ! p.future

    case CustomMessage(from, message) =>
      if (promises.contains(from)) {
        log.debug("found promise")
        promises(from).success(message)
      }
  }

}
