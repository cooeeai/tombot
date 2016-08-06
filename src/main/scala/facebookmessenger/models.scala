package facebookmessenger

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._

/**
  * Created by markmo on 17/07/2016.
  */
case class Sender(id: String)

case class Recipient(id: String)

case class Message(mid: String, seq: Int, text: String)

case class Postback(payload: String)

case class Messaging(sender: Sender,
                     recipient: Recipient,
                     timestamp: Long,
                     message: Option[Message],
                     postback: Option[Postback])

case class Entry(id: String, time: Long, messaging: List[Messaging])

case class Response(obj: String, entry: List[Entry])

sealed trait Button {
  val buttonType: String
}

case class LinkButton(title: String, url: String) extends Button {
  override val buttonType = "web_url"
}

case class PostbackButton(title: String, payload: JsValue) extends Button {
  override val buttonType = "postback"
}

case class LoginButton(url: String) extends Button {
  override val buttonType = "account_link"
}

case class Element(title: String, subtitle: String, itemURL: String, imageURL: String, buttons: List[Button])

case class AttachmentPayload(templateType: String, elements: List[Element])

case class Attachment(attachmentType: String, payload: AttachmentPayload)

case class GenericMessage(attachment: Attachment)

case class GenericMessageTemplate(recipient: Recipient, message: GenericMessage)

case class Address(street1: String, street2: String, city: String, postcode: String, state: String, country: String)

case class Summary(subtotal: BigDecimal, shippingCost: BigDecimal, totalTax: BigDecimal, totalCost: BigDecimal)

case class Adjustment(name: String, amount: BigDecimal)

case class ReceiptElement(title: String, subtitle: String, quantity: Int, price: BigDecimal, currency: String, imageURL: String)

case class ReceiptPayload(templateType: String,
                          recipientName: String,
                          orderNumber: String,
                          currency: String,
                          paymentMethod: String,
                          orderURL: String,
                          timestamp: Long,
                          elements: List[ReceiptElement],
                          address: Address,
                          summary: Summary,
                          adjustments: List[Adjustment])

case class ReceiptAttachment(attachmentType: String, payload: ReceiptPayload)

case class ReceiptMessage(attachment: ReceiptAttachment)

case class ReceiptMessageTemplate(recipient: Recipient, message: ReceiptMessage)

case class UserProfile(firstName: String, lastName: String, picture: String, locale: String, timezone: String, gender: String)

case class Optin(ref: String)

case class AuthenticationEvent(sender: Sender, recipient: Recipient, timestamp: Long, optin: Optin)

case class Delivery(mids: List[String], watermark: Long, seq: Int)

case class MessageDeliveredEvent(sender: Sender, recipient: Recipient, delivery: Delivery)

case class AccountLinking(status: String, authorizationCode: Option[String])

case class AccountLinkingEvent(sender: Sender, recipient: Recipient, timestamp: Long, accountLinking: AccountLinking)

trait FbJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val senderJsonFormat = jsonFormat1(Sender)
  implicit val recipientJsonFormat = jsonFormat1(Recipient)
  implicit val messageJsonFormat = jsonFormat3(Message)
  implicit val postbackJsonFormat = jsonFormat1(Postback)
  implicit val messagingJsonFormat = jsonFormat5(Messaging)
  implicit val entryJsonFormat = jsonFormat3(Entry)
  implicit val responseJsonFormat = jsonFormat(Response, "object", "entry")
  implicit val elementJsonFormat = jsonFormat(Element, "title", "subtitle", "item_url", "image_url", "buttons")
  implicit val payloadJsonFormat = jsonFormat(AttachmentPayload, "template_type", "elements")
  implicit val attachmentJsonFormat = jsonFormat(Attachment, "type", "payload")
  implicit val genericMessageJsonFormat = jsonFormat1(GenericMessage)
  implicit val genericMessageTemplateJsonFormat = jsonFormat2(GenericMessageTemplate)
  implicit val addressJsonFormat = jsonFormat(Address, "street_1", "street_2", "city", "postal_code", "state", "country")
  implicit val summaryJsonFormat = jsonFormat(Summary, "subtotal", "shipping_cost", "total_tax", "total_cost")
  implicit val adjustmentJsonFormat = jsonFormat2(Adjustment)
  implicit val receiptElementJsonFormat = jsonFormat(ReceiptElement, "title", "subtitle", "quantity", "price", "currency", "image_url")
  implicit val receiptPayloadJsonFormat = jsonFormat(ReceiptPayload, "template_type", "recipient_name", "order_number", "currency", "payment_method", "order_url", "timestamp", "elements", "address", "summary", "adjustments")
  implicit val receiptAttachmentJsonFormat = jsonFormat(ReceiptAttachment, "type", "payload")
  implicit val receiptMessageJsonFormat = jsonFormat1(ReceiptMessage)
  implicit val receiptMessageTemplateJsonFormat = jsonFormat2(ReceiptMessageTemplate)
  implicit val userProfileJsonFormat = jsonFormat(UserProfile, "first_name", "last_name", "profile_pic", "locale", "timezone", "gender")
  implicit val optinJsonFormat = jsonFormat1(Optin)
  implicit val authenticationEventJsonFormat = jsonFormat4(AuthenticationEvent)
  implicit val deliveryJsonFormat = jsonFormat3(Delivery)
  implicit val messageDeliveredEventJsonFormat = jsonFormat3(MessageDeliveredEvent)
  implicit val accountLinkingJsonFormat = jsonFormat(AccountLinking, "status", "authorization_code")
  implicit val accountLinkingEventJsonFormat = jsonFormat(AccountLinkingEvent, "sender", "recipient", "timestamp", "account_linking")

  implicit object buttonJsonFormat extends RootJsonFormat[Button] {

    def write(b: Button) = b match {
      case l: LinkButton => JsObject(
        "type" -> JsString(l.buttonType),
        "title" -> JsString(l.title),
        "url" -> JsString(l.url)
      )
      case p: PostbackButton => JsObject(
        "type" -> JsString(p.buttonType),
        "title" -> JsString(p.title),
        "payload" -> p.payload
      )
    }

    def read(value: JsValue) = {
      value.asJsObject.getFields("type") match {
        case Seq(JsString(buttonType)) if buttonType == "web_url" =>
          value.asJsObject.getFields("title", "url") match {
            case Seq(JsString(title), JsString(url)) => LinkButton(title.toString, url.toString)
            case _ => throw DeserializationException("LinkButton expected")
          }
        case Seq(JsString(buttonType)) if buttonType == "postback" =>
          value.asJsObject.getFields("title", "payload") match {
            case Seq(JsString(title), payload: JsValue) => PostbackButton(title.toString, payload)
            case _ => throw DeserializationException("PostbackButton expected")
          }
        case _ => throw DeserializationException("Button expected")
      }
    }
  }

}
