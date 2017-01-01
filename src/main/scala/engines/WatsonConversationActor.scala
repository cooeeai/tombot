package engines

import java.lang.{Boolean => JBoolean}
import java.util.{Map => JMap}

import akka.actor.{Actor, ActorRef}
import com.google.inject.Inject
import com.google.inject.assistedinject.Assisted
import models.events.{TextResponse, Unhandled}
import modules.akkaguice.NamedActor
import services.WatsonConversationService
import utils.General

import scala.collection.JavaConversions._
import scala.collection.mutable

/**
  * Created by markmo on 11/09/2016.
  */
class WatsonConversationActor @Inject()(watsonConversationService: WatsonConversationService,
                                        @Assisted("defaultProvider") val defaultProvider: ActorRef,
                                        @Assisted("historyActor") val historyActor: ActorRef)
  extends SimpleConversationActor with General {

  val contextMap = mutable.Map[String, JMap[String, AnyRef]]()

  override def withProvider(provider: ActorRef): Receive = {
    case ev@TextResponse(_, sender, text, _) =>
      val response = watsonConversationService.converse(text, contextMap.get(sender))

      log.debug("intents: {}",
        response.getIntents
          .map(intent => s"${intent.getIntent} (${intent.getConfidence})")
          .mkString(", "))

      log.debug("entities: {}",
        response.getEntities
          .map(entity => s"${entity.getEntity} (${entity.getValue})")
          .mkString(", "))

      val conversationCtx = response.getContext
      val message = response.getText.mkString("\n")
      if (conversationCtx.getOrDefault("nomatch", JBoolean.FALSE).asInstanceOf[JBoolean]) {
        log.debug("nomatch")
        conversationCtx.remove("nomatch")
        context.parent ! Unhandled(ev)
      } else {
        say(provider, historyActor, sender, text, message)
      }
      contextMap(sender) = conversationCtx
  }

}

object WatsonConversationActor extends NamedActor {

  override final val name = "WatsonConversationActor"

  trait Factory {
    def apply(@Assisted("defaultProvider") defaultProvider: ActorRef,
              @Assisted("historyActor") historyActor: ActorRef): Actor
  }

}