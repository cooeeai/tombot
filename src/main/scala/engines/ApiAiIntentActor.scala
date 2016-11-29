package engines

import akka.actor.{Actor, ActorLogging}
import com.google.inject.Inject
import models.events.{IntentVote, Say, TextResponse}
import services.ApiAiService

/**
  * Created by markmo on 29/11/2016.
  */
class ApiAiIntentActor @Inject()(service: ApiAiService)
  extends Actor with ActorLogging {

  import context.dispatcher

  def receive = {

    case ev@TextResponse(_, from, text) =>
      log.debug("ApiAiIntentActor received TextResponse")

      // avoid closing over mutable state
      val currentSender = sender()

      service.getIntent(text) map { response =>
        if (response.result.action == "input.unknown") {
          log.debug("unknown intent")
          currentSender ! IntentVote(0.0, ev)
        } else {
          val message = response.result.fulfillment.speech
          currentSender ! IntentVote(response.result.score, Say(from, text, message))
        }
      }

  }

}
