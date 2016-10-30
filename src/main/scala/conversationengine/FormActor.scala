package conversationengine

import akka.actor.{Actor, ActorLogging}
import akka.contrib.pattern.ReceivePipeline
import com.google.inject.Inject
import conversationengine.events._
import memory._
import modules.akkaguice.NamedActor
import services.{FacebookService, SlotContainer, SlotService}

import scala.collection.mutable

/**
  * Created by markmo on 14/09/2016.
  */
class FormActor @Inject()(facebookService: FacebookService,
                          slotService: SlotService,
                          form: Form)
  extends Actor
    with ActorLogging
    with ReceivePipeline
    with LoggingInterceptor {

  val maxMessageLength = 300

  var currentKey: Option[String] = None

  var lastKey: Option[String] = None

  var confirming = false

  var confirmingQuit = false

  val originalSlot = SlotContainer(slotService, form.data("purchase"))
    .fillSlot("phone", "0395551535")
    .fillSlot("cardholderName", "Mark Moloney")
    .fillSlot("cardNumber", "**** **** 1234")
    .fillSlot("securityCode", "1234")
    .fillSlot("expiryMonth", "01")
    .fillSlot("expiryYear", "19")
    .slot

  var slot = originalSlot

  val history = mutable.ListBuffer[Exchange]()

  final val commands =
    "back - go back to the previous question\n" +
      "quit - quit the form without completing it\n" +
      "reset - start over again\n" +
      "status - show progress\n" +
      "help|? - show this message"

  //log.debug("slot:\n" + slot.toString)

  override def receive = {

    case Reset =>
      currentKey = None
      lastKey = None
      confirming = false
      slot = originalSlot

    case NextQuestion(sender) =>
      facebookService.sendTextMessage(sender,
        "I need some details from you to complete this action.\n" +
          "While answering the following questions, these commands\n" +
          "are available to you:\n\n" + commands
      )
      nextQuestion(sender, None)

    case ev: TextLike =>
      val sender = ev.sender
      val text = ev.text
      if (text == "help" || text == "?") {
        facebookService.sendTextMessage(sender,
          s"You are providing details required to complete a ${originalSlot.key}.\n" +
            "Possible responses:\n" + commands
        )
      } else if (text == "back") {
        if (lastKey.isDefined) {
          history += Exchange(Some(text), "going back")
          val key = lastKey.get
          log.debug(s"last key [$key]")
          slot = originalSlot.findSlot(key) match {
            case Some(s) => slot.updateSlot(key, s)
            case None => slot
          }
        } else {
          facebookService.sendTextMessage(sender, "There is no previous question")
        }
        nextQuestion(sender, None)
      } else if (text == "reset") {
        history += Exchange(Some(text), "resetting form")
        self ! Reset
        nextQuestion(sender, None)
      } else if (text == "status") {
        val total = originalSlot.numberQuestions
        val remaining = slot.numberQuestions
        val progress = math.ceil((total - remaining).toDouble * 100 / total).toInt
        multiMessage(sender,
          printAnswers + "\n\n" +
            s"Progress: $progress%\n" +
            s"Questions remaining: $remaining"
        )
      } else if (text == "quit") {
        confirmingQuit = true
        facebookService.sendQuickReply(sender,
          "Warning! You will be unable to complete a " + originalSlot.key +
            ".\nThis action will end your request. Do you want to proceed?"
        )
      } else if (confirmingQuit) {
        confirmingQuit = false
        if (text.toLowerCase == "yes") {
          context.parent ! Reset
        } else {
          nextQuestion(sender, Some(text))
        }
      } else if (currentKey.isDefined) {
        val key = currentKey.get
        val (maybeError, s) = updateSlot(key, text)
        if (maybeError.isDefined) {
          facebookService.sendTextMessage(sender, maybeError.get.message)
        } else {
          lastKey = currentKey
          slot = s
          nextQuestion(sender, Some(text))
        }
      } else {
        nextQuestion(sender, Some(text))
      }

  }

  def updateSlot(key: String, value: String): (Option[SlotError], Slot) =
    if (confirming) {
      confirming = false
      if (value.toLowerCase == "no") {
        log.debug(s"emptying slot [$key]")
        (None, slot.emptySlot(key))
      } else {
        log.debug(s"confirming contents of slot [$key]")
        (None, slot.confirmSlot(key))
      }
    } else {
      log.debug(s"filling slot [$key] with [$value]")
      slotService.fillSlot(slot, key, value)
    }

  def nextQuestion(sender: String, text: Option[String]) =
    slot.nextQuestion match {

      case Some(Question(key, question, false)) =>
        currentKey = Some(key)
        history += Exchange(text, question)
        facebookService.sendTextMessage(sender, question)

      case Some(Question(key, question, true)) =>
        currentKey = Some(key)
        history += Exchange(text, question)
        confirming = true
        facebookService.sendQuickReply(sender, question)

      case None =>
        log.debug("No next question")
        log.debug("slot:\n" + slot.toString)
        context.parent ! EndFillForm(sender, slot, history.toList.reverse)

    }

  def printAnswers: String =
    history zip history.tail flatMap { h =>
      h._2.userSaid match {
        case None => None
        case Some(a) if Set("back", "quit", "reset", "status", "help") contains a => None
        case Some(a) =>
          Some("? " + h._1.botSaid + "\n" + "> " + a)
      }
    } mkString "\n"

  def multiMessage(sender: String, message: String): Unit =
    message.split("\n").foldLeft(0, 0, List[(Int, String)]()) {
      case ((group, len, lines), line) =>
        val len1 = len + line.length
        if (len1 < maxMessageLength) {
          (group, len1, (group, line) :: lines)
        } else {
          (group + 1, line.length, (group + 1, line) :: lines)
        }
    }._3 groupBy (_._1) foreach {
      case (_, ls) =>
        val lines = ls.map(_._2).reverse
        facebookService.sendTextMessage(sender, lines mkString "\n")
    }

}

object FormActor extends NamedActor {

  override final val name = "FormActor"

}
