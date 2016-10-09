package conversationengine

import akka.actor.{Actor, ActorLogging}
import com.google.inject.Inject
import conversationengine.events._
import memory._
import modules.akkaguice.NamedActor
import services.{FacebookService, SlotContainer, SlotService}

/**
  * Created by markmo on 14/09/2016.
  */
class FormActor @Inject()(facebookService: FacebookService,
                          slotService: SlotService,
                          form: Form)
  extends Actor with ActorLogging {

  import FormActor._

  var currentKey: Option[String] = None

  var confirming = false

  val originalSlot = SlotContainer(slotService, form.data("purchase"))
//    .fillSlot("name", "Mark Moloney")
    .fillSlot("phone", "0395551535")
    .fillSlot("cardholderName", "Mark Moloney")
    .fillSlot("cardNumber", "**** **** 1234")
    .fillSlot("securityCode", "1234")
    .fillSlot("expiryMonth", "01")
    .fillSlot("expiryYear", "19")
    .slot

  var slot = originalSlot

  log.debug("slot:\n" + slot.toString)

  override def receive = {

    case Reset =>
      currentKey = None
      confirming = false
      slot = originalSlot

    case NextQuestion(sender) =>
      log.debug(s"$name received NextQuestion event")
      nextQuestion(sender)

    case ev: TextLike =>
      log.debug(s"$name received TextLike event")
      val sender = ev.sender
      val text = ev.text
      if (currentKey.isDefined) {
        val key = currentKey.get
        val (maybeError, s) = updateSlot(key, text)
        if (maybeError.isDefined) {
          facebookService.sendTextMessage(sender, maybeError.get.message)
        } else {
          slot = s
          nextQuestion(sender)
        }
      } else {
        nextQuestion(sender)
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

  private def nextQuestion(sender: String) =
    slot.nextQuestion match {

      case Some(Question(key, question, false)) =>
        currentKey = Some(key)
        facebookService.sendTextMessage(sender, question)

      case Some(Question(key, question, true)) =>
        currentKey = Some(key)
        confirming = true
        facebookService.sendQuickReply(sender, question)

      case None =>
        log.debug("No next question")
        log.debug("slot:\n" + slot.toString)
        context.parent ! EndFillForm(sender, slot)

    }

}

object FormActor extends NamedActor {

  override final val name = "FormActor"

}
