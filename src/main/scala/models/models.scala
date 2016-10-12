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
  val actionType: ItemActionType.Value
}

case class ItemLinkAction(title: String, url: String) extends ItemAction {
  override val actionType = ItemActionType.Link
}

case class ItemPostbackAction(title: String, payload: JsValue) extends ItemAction {
  override val actionType = ItemActionType.Postback
}

case class Item(title: String, subtitle: String, itemURL: String, imageURL: String, actions: List[ItemAction])
