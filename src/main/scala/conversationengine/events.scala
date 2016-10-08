package conversationengine

import controllers.Platforms
import memory.Slot
import services.User

/**
  * Created by markmo on 4/10/2016.
  */
object events {

  case class Exchange(userSaid: Option[String], botSaid: String)

  trait TextLike {
    val sender: String
    val text: String
  }

  case class Greet(platform: Platforms.Value, sender: String, user: User, text: String) extends TextLike

  case class Qualify(platform: Platforms.Value, sender: String, productType: Option[String], text: String) extends TextLike

  case class Buy(platform: Platforms.Value, sender: String, productType: String, text: String)

  case class Respond(platform: Platforms.Value, sender: String, text: String) extends TextLike

  case class Welcome(platform: Platforms.Value, sender: String)

  case class Analyze(platform: Platforms.Value, sender: String, text: String) extends TextLike

  case class BillEnquiry(platform: Platforms.Value, sender: String, text: String) extends TextLike

  case class PostAuth(sender: String)

  case class Fallback(sender: String, history: List[Exchange])

  case class FillForm(sender: String, goal: String)

  case class EndFillForm(sender: String, slot: Slot)

  case class NextQuestion(sender: String)

  object Activate

  object Deactivate

  object Reset

}
