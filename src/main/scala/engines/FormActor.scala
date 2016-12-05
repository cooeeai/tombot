package engines

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.contrib.pattern.ReceivePipeline
import com.google.inject.Inject
import com.google.inject.assistedinject.Assisted
import engines.interceptors.LoggingInterceptor
import memory._
import models.{Location, Address}
import models.events._
import modules.akkaguice.NamedActor
import services.{SlotContainer, SlotService}
import xml.Utility.escape

import scala.collection.mutable

/**
  * Created by markmo on 14/09/2016.
  */
class FormActor @Inject()(slotService: SlotService,
                          form: Form,
                          @Assisted("defaultProvider") val defaultProvider: ActorRef)
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

  def receive = withProvider(defaultProvider)

  def withProvider(provider: ActorRef): Receive = {

    case Reset =>
      currentKey = None
      lastKey = None
      confirming = false
      slot = originalSlot

    case NextQuestion(sender) =>
      provider ! TextMessage(sender,
        "I need some details from you to complete this action.\n" +
          "While answering the following questions, these commands\n" +
          "are available to you:\n\n" + commands
      )
      nextQuestion(provider, sender, None)

    case TextResponse(_, sender, text) =>
      log.debug("form received text [{}]", text)
      if (text == "help" || text == "?") {
        provider ! TextMessage(sender,
          s"You are providing details required to complete a ${originalSlot.key}.\n" +
            "Possible responses:\n" + commands
        )
      } else if (text == "back") {
        if (lastKey.isDefined) {
          history += Exchange(Some(text), "going back")
          val key = lastKey.get
          log.debug("last key [{}]", key)
          slot = originalSlot.findSlot(key) match {
            case Some(s) => slot.updateSlot(key, s)
            case None => slot
          }
        } else {
          provider ! TextMessage(sender, "There is no previous question")
        }
        nextQuestion(provider, sender, None)
      } else if (text == "reset") {
        history += Exchange(Some(text), "resetting form")
        self ! Reset
        nextQuestion(provider, sender, None)
      } else if (text == "status") {
        val total = originalSlot.numberQuestions
        val remaining = slot.numberQuestions
        val progress = math.ceil((total - remaining).toDouble * 100 / total).toInt
        multiMessage(provider, sender,
          printAnswers + "\n\n" +
            s"Progress: $progress%\n" +
            s"Questions remaining: $remaining"
        )
      } else if (text == "quit") {
        confirmingQuit = true
        provider ! QuickReply(sender,
          "Warning! You will be unable to complete a " + originalSlot.key +
            ".\nThis action will end your request. Do you want to proceed?"
        )
      } else if (confirmingQuit) {
        confirmingQuit = false
        if (text.toLowerCase == "yes") {
          context.parent ! Reset
        } else {
          nextQuestion(provider, sender, Some(text))
        }
      } else if (currentKey.isDefined) {
        val key = currentKey.get
        val (maybeError, s) = updateSlot(key, text)
        if (maybeError.isDefined) {
          provider ! TextMessage(sender, maybeError.get.message)
        } else {
          lastKey = currentKey
          slot = s
          nextQuestion(provider, sender, Some(text))
        }
      } else {
        nextQuestion(provider, sender, Some(text))
      }

    case SetProvider(_, _, ref, _, _, _) =>
      context become withProvider(ref)

  }

  def updateSlot(key: String, value: String): (Option[SlotError], Slot) =
    if (confirming) {
      confirming = false
      if (value.toLowerCase == "no") {
        log.debug("emptying slot [{}]", key)
        (None, slot.emptySlot(key))
      } else {
        log.debug("confirming contents of slot [{}]", key)
        (None, slot.confirmSlot(key))
      }
    } else {
      log.debug("filling slot [{}] with [{}]", key, value)
      slotService.fillSlot(slot, key, value)
    }

  def nextQuestion(provider: ActorRef, sender: String, text: Option[String]) =
    slot.nextQuestion match {

      case Some(Question(key, question, false, _)) =>
        currentKey = Some(key)
        history += Exchange(text, question)
        provider ! TextMessage(sender, escape(question))

      case Some(Question(key, question, true, _)) =>
        currentKey = Some(key)
        history += Exchange(text, question)
        confirming = true
        provider ! QuickReply(sender, escape(question))
        if (key == "address") {
          val addressSlot = slot.findSlot(key).get
          val address = Address(
            street1 = addressSlot.getString("street1"),
            street2 = "",
            city = addressSlot.getString("city"),
            postcode = addressSlot.getString("postcode"),
            state = addressSlot.getString("state"),
            country = addressSlot.getString("country"),
            location = Location(
              latitude = addressSlot.getValue[Double]("latitude").getOrElse(37.8136),
              longitude = addressSlot.getValue[Double]("longitude").getOrElse(144.9631)
            )
          )
          provider ! AddressCard(sender, address)
        }

      case None =>
        log.debug("No next question")
        log.debug("slot:\n{}", slot.toString)
        // TODO
        // GetHistory
        context.parent ! EndFillForm(sender, slot)

    }

  def printAnswers: String =
    history zip history.tail flatMap { h =>
      h._2.request match {
        case None => None
        case Some(a) if Set("back", "quit", "reset", "status", "help") contains a => None
        case Some(a) =>
          Some("? " + h._1.response + "\n" + "> " + a)
      }
    } mkString "\n"

  def multiMessage(provider: ActorRef, sender: String, message: String): Unit =
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
        provider ! TextMessage(sender, lines mkString "\n")
    }

}

object FormActor extends NamedActor {

  override final val name = "FormActor"

  trait Factory {
    def apply(@Assisted("defaultProvider") defaultProvider: ActorRef): Actor
  }

}
