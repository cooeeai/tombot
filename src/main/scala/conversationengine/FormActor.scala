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

  var currentKey: Option[String] = None

  var confirming = false

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

  //log.debug("slot:\n" + slot.toString)

  override def receive = {

    case Reset =>
      currentKey = None
      confirming = false
      slot = originalSlot

    case NextQuestion(sender) =>
      nextQuestion(sender, None)

    case ev: TextLike =>
      val sender = ev.sender
      val text = ev.text
      if (currentKey.isDefined) {
        val key = currentKey.get
        val (maybeError, s) = updateSlot(key, text)
        if (maybeError.isDefined) {
          facebookService.sendTextMessage(sender, maybeError.get.message)
        } else {
          slot = s
          nextQuestion(sender, Some(text))
        }
      } else {
        nextQuestion(sender, Some(text))
      }

  }

  private def updateSlot(key: String, value: String): (Option[SlotError], Slot) =
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

  private def nextQuestion(sender: String, text: Option[String]) =
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

}

object FormActor extends NamedActor {

  override final val name = "FormActor"

}
