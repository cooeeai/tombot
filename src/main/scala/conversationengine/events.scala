package conversationengine

import apis.ciscospark.SparkWebhookResponseData
import controllers.Platform
import memory.Slot
import services.User

/**
  * Created by markmo on 4/10/2016.
  */
object events {

  case class Exchange(userSaid: Option[String], botSaid: String)

  trait TextLike {
    val platform: Platform.Value
    val sender: String
    val text: String
  }

  case class Greet(platform: Platform.Value, sender: String, user: User, text: String) extends TextLike

  case class Qualify(platform: Platform.Value, sender: String, productType: Option[String], text: String) extends TextLike

  case class Buy(platform: Platform.Value, sender: String, productType: String, text: String)

  case class Respond(platform: Platform.Value, sender: String, text: String) extends TextLike

  case class Confirm(platform: Platform.Value, sender: String, text: String) extends TextLike

  case class Welcome(platform: Platform.Value, sender: String)

  case class Analyze(platform: Platform.Value, sender: String, text: String) extends TextLike

  case class BillEnquiry(platform: Platform.Value, sender: String, text: String) extends TextLike

  case class Fallback(sender: String, history: List[Exchange])

  case class FillForm(sender: String, goal: String)

  case class EndFillForm(sender: String, slot: Slot)

  case class NextQuestion(sender: String)

  case class SparkMessageEvent(sender: String, data: SparkWebhookResponseData)

  case class SparkRoomLeftEvent(sender: String)

  case class SparkWrappedEvent(roomId: String, personId: String, message: TextLike)

  object Activate

  object Deactivate

  object Reset

}
