package engines

import akka.actor.{Actor, ActorLogging}
import com.google.inject.Inject
import models.events.{IntentVote, Say, TextResponse}
import services.KnowledgeService

/**
  * Created by markmo on 30/11/2016.
  */
class WolframAlphaIntentActor @Inject()(service: KnowledgeService) extends Actor with ActorLogging {

  def receive = {

    case ev@TextResponse(_, from, text) =>
      log.debug("WolframAlphaIntentActor received TextResponse")

      service.getFacts(text) match {
        case Some(reply) =>
          sender() ! IntentVote(1.0, Say(from, text, reply))
        case None =>
          sender() ! IntentVote(0.0, ev)
      }

  }
}