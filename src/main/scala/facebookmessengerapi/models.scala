package facebookmessengerapi

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
  val title: String
}

case class LinkButton(override val title: String, url: String) extends Button {
  override val buttonType = "web_url"
}

case class PostbackButton(override val title: String, payload: JsValue) extends Button {
  override val buttonType = "postback"
}

case class Element(title: String, subtitle: String, itemURL: String, imageURL: String, buttons: List[Button])

case class AttachmentPayload(templateType: String, elements: List[Element])

case class Attachment(attachmentType: String, payload: AttachmentPayload)

case class GenericMessage(attachment: Attachment)

case class GenericMessagePayload(recipient: Recipient, message: GenericMessage)

trait JsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
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
  implicit val genericMessagePayloadJsonFormat = jsonFormat2(GenericMessagePayload)

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
