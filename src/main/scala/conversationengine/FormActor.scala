package conversationengine

import akka.actor.{Actor, ActorLogging}
import com.google.inject.Inject
import conversationengine.events._
import memory.Form
import modules.akkaguice.NamedActor
import services.FacebookService

/**
  * Created by markmo on 14/09/2016.
  */
class FormActor @Inject()(facebookService: FacebookService, form: Form) extends Actor with ActorLogging {

  import FormActor._

  var currentSlotKey: Option[String] = None

  var slot = form.data
    .fillSlotNext("city", "Melbourne")
    .fillSlotNext("state", "VIC")
    .fillSlotNext("postcode", "3000")
    .fillSlotNext("country", "Australia")
    .fillSlotNext("cardholderName", "Mark Moloney")
    .fillSlotNext("cardNumber", "1234")
    .fillSlotNext("securityCode", "1234")
    .fillSlotNext("expiryMonth", "01")
    .fillSlotNext("expiryYear", "19")

  //log.debug("slot:\n" + slot.toString)

  override def receive = {

    case NextQuestion(sender) =>
      log.debug(s"$name received NextQuestion event")
      nextQuestion(sender)

    case ev: TextLike =>
      log.debug(s"$name received TextLike event")
      val sender = ev.sender
      val text = ev.text
      if (currentSlotKey.isDefined) {
        val key = currentSlotKey.get
        log.debug(s"filling slot [$key] with [$text]")
        slot = slot.fillSlot(key, text).get
      }
      nextQuestion(sender)

  }

  private def nextQuestion(sender: String) =
    slot.nextQuestion match {

      case Some((key, question)) =>
        currentSlotKey = Some(key)
        facebookService.sendTextMessage(sender, question)

      case None =>
        log.debug("No next question")
        context.parent ! EndFillForm(sender)

    }

}

object FormActor extends NamedActor {

  override final val name = "FormActor"

}
