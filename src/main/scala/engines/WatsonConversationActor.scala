package engines

import java.lang.{Boolean => JBoolean}
import java.util.{Map => JMap}

import akka.actor.{Actor, ActorLogging}
import akka.contrib.pattern.ReceivePipeline
import com.google.inject.Inject
import com.typesafe.config.Config
import engines.interceptors.LoggingInterceptor
import models.events.{Exchange, Fallback, TextResponse}
import modules.akkaguice.NamedActor
import services.{FacebookService, WatsonConversationService}

import scala.collection.JavaConversions._
import scala.collection.mutable

/**
  * Created by markmo on 11/09/2016.
  */
class WatsonConversationActor @Inject()(config: Config,
                                        facebookService: FacebookService,
                                        watsonConversationService: WatsonConversationService)
  extends Actor
    with ActorLogging
    with ReceivePipeline
    with LoggingInterceptor {

  val maxFailCount = config.getInt("settings.max-fail-count")

  val contextMap = mutable.Map[String, JMap[String, AnyRef]]()

  var failCount = 0

  val history = mutable.ListBuffer[Exchange]()

  def receive = {
    case ev: TextResponse =>
      val sender = ev.sender
      val response = watsonConversationService.converse(ev.text, contextMap.get(sender))

      log.debug("intents: {}",
        response.getIntents
          .map(intent => s"${intent.getIntent} (${intent.getConfidence})")
          .mkString(", "))

      log.debug("entities: {}",
        response.getEntities
          .map(entity => s"${entity.getEntity} (${entity.getValue})")
          .mkString(", "))

      val conversationCtx = response.getContext
      val text = response.getText.mkString("\n")
      history append Exchange(Some(ev.text), text)
      if (conversationCtx.getOrDefault("nomatch", JBoolean.FALSE).asInstanceOf[JBoolean]) {
        log.debug("nomatch")
        failCount += 1
        conversationCtx.remove("nomatch")
      }
      if (failCount > maxFailCount) {
        context.parent ! Fallback(sender, history.toList)
        failCount = 0
        history.clear()
      } else {
        facebookService.sendTextMessage(sender, text)
      }
      contextMap(sender) = conversationCtx
  }

}

object WatsonConversationActor extends NamedActor {

  override final val name = "WatsonConversationActor"

}