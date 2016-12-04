package models

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, JsValue}

/**
  * Created by markmo on 7/08/2016.
  */
case class LoginForm(username: String, password: String, redirectURI: String, successURI: String)

trait LoginFormJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val loginFormJsonFormat = jsonFormat(LoginForm, "username", "password", "redirect-uri", "redirect-success-uri")
}

object ItemActionType extends Enumeration {
  val Link, Postback = Value
}

sealed trait ItemAction {
  def actionType: ItemActionType.Value
}

case class ItemLinkAction(title: String, url: String) extends ItemAction {
  override val actionType = ItemActionType.Link
}

case class ItemPostbackAction(title: String, payload: JsValue) extends ItemAction {
  override val actionType = ItemActionType.Postback
}

case class Item(title: String, subtitle: String, itemURL: String, imageURL: String, actions: List[ItemAction])

case class UserProfile(firstName: String, lastName: String, picture: String, locale: String, timezone: Int, gender: String)

case class Location(latitude: Double, longitude: Double)

case class Address(street1: String, street2: String, city: String, postcode: String, state: String, country: String, location: Location) {
  override def toString = street1 + ", " + street2 + ", " + city + ", " + state + " " + postcode
}

case class AddressFlat(street1: String, street2: String, city: String, postcode: String, state: String, country: String, latitude: Double, longitude: Double) {
  override def toString = street1 + ", " + street2 + ", " + city + ", " + state + " " + postcode
}

case class AddressResponse(address: Address, text: String)

trait AddressJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val locationJsonFormat = jsonFormat2(Location)
  implicit val addressJsonFormat = jsonFormat7(Address)
  implicit val addressFlatJsonFormat = jsonFormat8(AddressFlat)
  implicit val addressResponseJsonFormat = jsonFormat2(AddressResponse)
}

object Platform extends Enumeration {

  type Platform = Value

  val Facebook, Telegram, LiveEngageChat, Skype, SMS, Web = Value

}

object IntentType extends Enumeration {

  type IntentType = Value

  val GreetIntent, BuyIntent, AnalyzeIntent = Value

}

object ConversationEngine extends Enumeration {

  type ConversationEngine = Value

  val Cooee, Watson = Value

}
