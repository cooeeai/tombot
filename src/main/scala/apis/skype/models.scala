package apis.skype

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._
import spray.json.lenses.JsonLenses._

/**
  * Created by markmo on 10/08/2016.
  */
case class SkypeSender(id: String, name: String)

case class SkypeRecipient(id: String, name: String)

case class SkypeConversation(id: String, name: Option[String], isGroup: Option[Boolean])

case class SkypeImage(url: String)

sealed trait SkypeButton {
  val buttonType: String
  val title: String
  val value: String
}

case class SkypeSigninButton(title: String, value: String) extends SkypeButton {
  override val buttonType = "signin"
}

case class SkypeLinkButton(title: String, value: String) extends SkypeButton {
  override val buttonType = "openUrl"
}

case class SkypeSigninAttachmentContent(text: String, buttons: List[SkypeSigninButton])

case class SkypeHeroAttachmentContent(title: Option[String],
                                      subtitle: Option[String],
                                      images: Option[List[SkypeImage]],
                                      buttons: List[SkypeButton])

sealed trait SkypeAttachment {
  val contentType: String
}

case class SkypeSigninAttachment(content: SkypeSigninAttachmentContent) extends SkypeAttachment {
  override val contentType = "application/vnd.microsoft.card.signin"
}

case class SkypeHeroAttachment(content: SkypeHeroAttachmentContent) extends SkypeAttachment {
  override val contentType = "application/vnd.microsoft.card.hero"
}

case class SkypeUserMessage(id: String,
                            messageType: String,
                            timestamp: String,
                            text: String,
                            channelId: String,
                            serviceUrl: String,
                            conversation: SkypeConversation,
                            from: SkypeSender,
                            recipient: SkypeRecipient,
                            attachments: Option[List[SkypeAttachment]],
                            entities: Option[List[JsObject]])

case class SkypeBotMessage(messageType: String, text: String, attachments: Option[List[SkypeAttachment]])

sealed trait SkypeAttachmentLayout {
  val attachmentLayout: String
  val attachments: List[SkypeAttachment]
}

case class SkypeCarousel(attachments: List[SkypeAttachment]) extends SkypeAttachmentLayout {
  override val attachmentLayout = "carousel"
}

case class SkypeList(attachments: List[SkypeAttachment]) extends SkypeAttachmentLayout {
  override val attachmentLayout = "list"
}

case class SkypeSigninCard(cardType: String, attachments: List[SkypeSigninAttachment])

case class MicrosoftToken(tokenType: String, expires: Int, extExpires: Int, accessToken: String)

trait SkypeJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val skypeSenderJsonFormat = jsonFormat2(SkypeSender)
  implicit val skypeRecipientJsonFormat = jsonFormat2(SkypeRecipient)
  implicit val skypeConversationJsonFormat = jsonFormat3(SkypeConversation)
  implicit val skypeImageJsonFormat = jsonFormat1(SkypeImage)

  implicit object skypeButtonJsonFormat extends RootJsonFormat[SkypeButton] {

    def write(b: SkypeButton) =
      JsObject(
        "type" -> JsString(b.buttonType),
        "title" -> JsString(b.title),
        "value" -> JsString(b.value)
      )

    def read(value: JsValue) =
      value.extract[String]('type) match {
        case "signin" =>
          SkypeSigninButton(value.extract[String]('title), value.extract[String]('value))
        case "openUrl" =>
          SkypeLinkButton(value.extract[String]('title), value.extract[String]('value))
        case _ => throw DeserializationException("SkypeButton expected")
      }

  }

  implicit object skypeSigninButtonJsonFormat extends RootJsonFormat[SkypeSigninButton] {

    def write(b: SkypeSigninButton) =
      JsObject(
        "type" -> JsString(b.buttonType),
        "title" -> JsString(b.title),
        "value" -> JsString(b.value)
      )

    def read(value: JsValue) =
      SkypeSigninButton(value.extract[String]('title), value.extract[String]('value))

  }

  implicit object skypeLinkButtonJsonFormat extends RootJsonFormat[SkypeLinkButton] {

    def write(b: SkypeLinkButton) =
      JsObject(
        "type" -> JsString(b.buttonType),
        "title" -> JsString(b.title),
        "value" -> JsString(b.value)
      )

    def read(value: JsValue) = SkypeLinkButton(value.extract[String]('title), value.extract[String]('value))

  }

  implicit val skypeSigninAttachmentContentJsonFormat = jsonFormat2(SkypeSigninAttachmentContent)
  implicit val skypeHeroAttachmentContentJsonFormat = jsonFormat4(SkypeHeroAttachmentContent)

  implicit object skypeAttachmentJsonFormat extends RootJsonFormat[SkypeAttachment] {

    def write(a: SkypeAttachment) = a match {
      case s: SkypeSigninAttachment => JsObject(
        "contentType" -> JsString(s.contentType),
        "content" -> s.content.toJson
      )
      case h: SkypeHeroAttachment => JsObject(
        "contentType" -> JsString(h.contentType),
        "content" -> h.content.toJson
      )
    }

    def read(value: JsValue) =
      value.extract[String]('contentType) match {
        case "application/vnd.microsoft.card.signin" =>
          SkypeSigninAttachment(value.extract[SkypeSigninAttachmentContent]('content))
        case "application/vnd.microsoft.card.hero" =>
          SkypeHeroAttachment(value.extract[SkypeHeroAttachmentContent]('content))
        case _ => throw DeserializationException("SkypeAttachment expected")
      }

  }

  implicit object skypeSigninAttachmentJsonFormat extends RootJsonFormat[SkypeSigninAttachment] {

    def write(a: SkypeSigninAttachment) =
      JsObject(
        "contentType" -> JsString(a.contentType),
        "content" -> a.content.toJson
      )

    def read(value: JsValue) =
      SkypeSigninAttachment(value.extract[SkypeSigninAttachmentContent]('content))

  }

  implicit object skypeHeroAttachmentJsonFormat extends RootJsonFormat[SkypeHeroAttachment] {

    def write(a: SkypeHeroAttachment) =
      JsObject(
        "contentType" -> JsString(a.contentType),
        "content" -> a.content.toJson
      )

    def read(value: JsValue) =
      SkypeHeroAttachment(value.extract[SkypeHeroAttachmentContent]('content))

  }

  implicit object skypeAttachmentLayoutJsonFormat extends RootJsonFormat[SkypeAttachmentLayout] {

    def write(l: SkypeAttachmentLayout) =
      JsObject(
        "attachmentLayout" -> JsString(l.attachmentLayout),
        "attachments" -> l.attachments.toJson
      )

    def read(value: JsValue) =
      value.extract[String]('attachmentLayout) match {
        case "carousel" =>
          SkypeCarousel(value.extract[List[SkypeAttachment]]('attachments))
        case "list" =>
          SkypeList(value.extract[List[SkypeAttachment]]('attachments))
        case _ => throw DeserializationException("SkypeAttachmentLayout expected")
      }

  }

  implicit val skypeUserMessageJsonFormat = jsonFormat(SkypeUserMessage, "id", "type", "timestamp", "text", "channelId", "serviceUrl", "conversation", "from", "recipient", "attachments", "entities")
  implicit val skypeBotMessageJsonFormat = jsonFormat(SkypeBotMessage, "type", "text", "attachments")
  implicit val skypeSigninCardJsonFormat = jsonFormat(SkypeSigninCard, "type", "attachments")
  implicit val microsoftTokenJsonFormat = jsonFormat(MicrosoftToken, "token_type", "expires_in", "ext_expires_in", "access_token")

}

object Builder {

  class SigninCardBuilder(api: Option[String], sender: Option[String], text: Option[String], buttonTitle: Option[String]) {

    def usingApi(value: String) = new SigninCardBuilder(Some(value), sender, text, buttonTitle)

    def forSender(value: String) = new SigninCardBuilder(api, Some(value), text, buttonTitle)

    def withText(value: String) = new SigninCardBuilder(api, sender, Some(value), buttonTitle)

    def withButtonTitle(value: String) = new SigninCardBuilder(api, sender, text, Some(value))

    def build() =
      SkypeSigninCard(
        cardType = "message/card.signin",
        attachments = SkypeSigninAttachment(
          SkypeSigninAttachmentContent(
            text = text.get,
            buttons = SkypeSigninButton(
              title = buttonTitle.get,
              value = s"${api.get}/skypeauthorize?sender=${sender.get}"
            ) :: Nil
          )
        ) :: Nil
      )

  }

  def loginCard() = new SigninCardBuilder(None, None, None, None)

  class MessageBuilder(text: Option[String]) {

    def withText(value: String) = new MessageBuilder(Some(value))

    def build() =
      SkypeBotMessage(
        messageType = "message/text",
        text = text.get,
        attachments = None
      )
  }

  def messageElement = new MessageBuilder(None)

}