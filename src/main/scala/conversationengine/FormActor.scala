package conversationengine

import akka.actor.{Actor, ActorLogging}
import com.google.inject.Inject
import conversationengine.ConciergeActor.EndFillForm
import conversationengine.ConversationActor.TextLike
import memory.Slot
import modules.akkaguice.NamedActor
import services.FacebookService

/**
  * Created by markmo on 14/09/2016.
  */
class FormActor @Inject()(facebookService: FacebookService) extends Actor with ActorLogging {

  import FormActor._

  var currentSlotKey: Option[String] = None

  var slot =
    Slot.create("purchase")
      .fillSlot("city", "Melbourne").get
      .fillSlot("state", "VIC").get
      .fillSlot("postcode", "3000").get
      .fillSlot("country", "Australia").get
      .fillSlot("cardholderName", "Mark Moloney").get
      .fillSlot("cardNumber", "1234").get
      .fillSlot("securityCode", "1234").get
      .fillSlot("expiryMonth", "01").get
      .fillSlot("expiryYear", "19").get

  log.debug("slot:\n" + slot.toString)

  override def receive = {

    case NextQuestion(sender) =>
      log.debug("received NextQuestion event")
      nextQuestion(sender)

    case ev: TextLike =>
      log.debug("received TextLike event")
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

  case class NextQuestion(sender: String)

}
