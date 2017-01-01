package engines

import akka.actor.{Actor, ActorLogging}
import com.google.inject.Inject
import models.ConversationEngine._
import models.events._
import modules.akkaguice.NamedActor
import services.AlchemyService
import utils.RegexUtils

/**
  * Created by markmo on 21/11/2016.
  */
class CommandIntentActor @Inject()(alchemyService: AlchemyService) extends Actor with ActorLogging {

  import CommandIntentActor._
  import RegexUtils._

  def receive = {

    case ev@TextResponse(_, from, text, _) =>
      log.debug("CommandIntentActor received TextResponse")

      if (AlchemyCommand matches text) {
        log.debug("use alchemy service to show keywords")
        val keywords = alchemyService.getKeywords(text.substring(8).trim)
        val message = "Keywords:\n" + formatKeywords(keywords)
        sender() ! IntentVote(1.0, Say(from, text, message))

      } else if (SwitchEngineCommand matches text) {
        log.debug("switch conversation engine")
        if (text contains "watson") {
          log.debug("to Watson")
          sender() ! IntentVote(1.0, SetEngine(from, Watson))
        } else {
          log.debug("to Cooee")
          sender() ! IntentVote(1.0, SetEngine(from, Cooee))
        }

      } else if (HistoryCommand matches text) {
        log.debug("showing history")
        sender() ! IntentVote(1.0, ShowHistory(from))

      } else if (LoginCommand matches text) {
        log.debug("sending login card")
        sender() ! IntentVote(1.0, LoginCard(from))

      } else if (ResetCommand matches text) {
        log.debug("resetting")
        sender() ! IntentVote(1.0, Reset)

      } else {
        sender() ! IntentVote(0, ev)
      }

  }

  def formatKeywords(keywords: Map[String, Double]) =
    keywords map {
      case (keyword, relevance) => f"$keyword ($relevance%2.2f)"
    } mkString "\n"

}

object CommandIntentActor extends NamedActor {

  override final val name = "CommandIntentActor"

  val AlchemyCommand = command("alchemy")
  val SwitchEngineCommand = command("engine")
  val HistoryCommand = command("history")
  val LoginCommand = command("login")
  val ResetCommand = command("reset")

  private def command(name: String) = s"""^[/:]$name.*""".r

}
