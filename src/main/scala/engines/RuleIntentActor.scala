package engines

import akka.actor.{Actor, ActorLogging}
import com.google.inject.Inject
import models.events.{IntentVote, Say, TextResponse}
import modules.akkaguice.NamedActor
import services.RulesService

/**
  * Created by markmo on 21/11/2016.
  */
class RuleIntentActor @Inject()(rulesService: RulesService) extends Actor with ActorLogging {

  import rulesService._

  def receive = {

    case ev@TextResponse(_, from, text) =>
      log.debug("RuleIntentActor received TextResponse")
      if (isQuestion(text)) {
        log.debug("text is a question")
        getContent(text) match {
          case Some(content) =>
            log.debug("found content in response to question [{}]", content)
            sender() ! IntentVote(1.0, Say(from, text, content))
          case None =>
            log.debug("no content")
            sender() ! IntentVote(0, ev)
        }
      } else {
        sender() ! IntentVote(0, ev)
      }
  }

}

object RuleIntentActor extends NamedActor {

  override final val name = "RuleIntentActor"

}