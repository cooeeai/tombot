package apis.skype

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, JsObject}

/**
  * Created by markmo on 10/08/2016.
  */
case class SkypeSender(id: String, name: String)

case class SkypeRecipient(id: String, name: String)

case class SkypeConversation(id: String, name: Option[String], isGroup: Option[Boolean])

case class SkypeAttachment(contentType: String, contentUrl: Option[String], thumbnailUrl: Option[String], filename: Option[String])

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

case class MicrosoftToken(tokenType: String, expires: Int, extExpires: Int, accessToken: String)

case class SkypeButton(buttonType: String, title: String, value: String)

case class SigninAttachmentContent(text: String, buttons: List[SkypeButton])

case class SigninAttachment(contentType: String, content: SigninAttachmentContent)

case class SigninCard(cardType: String, attachments: List[SigninAttachment])

trait SkypeJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val skypeSenderJsonFormat = jsonFormat2(SkypeSender)
  implicit val skypeRecipientJsonFormat = jsonFormat2(SkypeRecipient)
  implicit val skypeConversationJsonFormat = jsonFormat3(SkypeConversation)
  implicit val skypeAttachmentJsonFormat = jsonFormat4(SkypeAttachment)
  implicit val skypeUserMessageJsonFormat = jsonFormat(SkypeUserMessage, "id", "type", "timestamp", "text", "channelId", "serviceUrl", "conversation", "from", "recipient", "attachments", "entities")
  implicit val skypeBotMessageJsonFormat = jsonFormat(SkypeBotMessage, "type", "text", "attachments")
  implicit val microsoftTokenJsonFormat = jsonFormat(MicrosoftToken, "token_type", "expires_in", "ext_expires_in", "access_token")
  implicit val skypeButtonJsonFormat = jsonFormat(SkypeButton, "type", "title", "value")
  implicit val signinAttachmentContentJsonFormat = jsonFormat2(SigninAttachmentContent)
  implicit val signinAttachmentJsonFormat = jsonFormat2(SigninAttachment)
  implicit val signinCardJsonFormat = jsonFormat(SigninCard, "type", "attachments")
}