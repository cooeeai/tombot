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

/**
  *
  * @param url   String URL to the image. Must be HTTPS.
  * @param alt   String Accessible description of the image
  * @param value String Action assigned to the image
  */
case class SkypeImage(url: String, alt: Option[String], value: Option[String])

/**
  * buttonType String Required field. One of openURL (opens the given URL), imBack (posts a message in the chat to the bot that sent the card), call (skype or phone number), showImage (for images only, displays the image), signin (sign in card only).
  * title String Text description that appears on the button
  * tap String Value depending on the type of action. For openURL is a URL, for signin is the URL to the authentication flow, for imBack is a user defined string, for call can be “skype:skypeid” or “tel:telephone”, for showImage not required.
  */
sealed trait SkypeButton {
  val buttonType: String
  val title: String
  val tap: String
}

case class SkypeSigninButton(title: String, tap: String) extends SkypeButton {
  override val buttonType = "signin"
}

case class SkypeLinkButton(title: String, tap: String) extends SkypeButton {
  override val buttonType = "openUrl"
}

// Sending context with actions
// It can be useful to send context back to your bot (e.g. a request ID) without showing this information to the user in a message. To do this you can append hidden XML to the visible string shown to the user, which is only seen by your bot.
// Visible message &lt;context hiddenId='10'/&gt;

case class SkypePostbackButton(title: String, tap: String) extends SkypeButton {
  override val buttonType = "imBack"
}

case class SkypeCallButton(title: String, tap: String) extends SkypeButton {
  override val buttonType = "call"
}

case class SkypeShowImageButton(title: String, tap: String) extends SkypeButton {
  override val buttonType = "showImage"
}

case class SkypeSigninAttachmentContent(text: String, buttons: List[SkypeSigninButton])

case class SkypeImageCardAttachmentContent(title: Option[String],
                                           subtitle: Option[String],
                                           text: Option[String],
                                           images: Option[List[SkypeImage]],
                                           buttons: List[SkypeButton])

case class SkypeFact(key: String, value: String)

case class SkypeReceiptItem(title: Option[String],
                            subtitle: Option[String],
                            image: Option[SkypeImage],
                            price: Option[String],
                            quantity: Option[String])

case class SkypeReceiptAttachmentContent(title: Option[String],
                                         facts: Option[List[SkypeFact]],
                                         items: Option[List[SkypeReceiptItem]],
                                         total: Option[String],
                                         tax: Option[String],
                                         vat: Option[String],
                                         buttons: Option[List[SkypeButton]])

sealed trait SkypeAttachment {
  val contentType: String
}

case class SkypeSigninAttachment(content: SkypeSigninAttachmentContent) extends SkypeAttachment {
  override val contentType = "application/vnd.microsoft.card.signin"
}

case class SkypeHeroAttachment(content: SkypeImageCardAttachmentContent) extends SkypeAttachment {
  override val contentType = "application/vnd.microsoft.card.hero"
}

case class SkypeThumbnailAttachment(content: SkypeImageCardAttachmentContent) extends SkypeAttachment {
  override val contentType = "application/vnd.microsoft.card.thumbnail"
}

case class SkypeReceiptAttachment(content: SkypeReceiptAttachmentContent) extends SkypeAttachment {
  override val contentType = "application/vnd.microsoft.card.receipt"
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

case class SkypeHeroCard(attachments: List[SkypeHeroAttachment])

case class SkypeThumbnailCard(attachments: List[SkypeThumbnailAttachment])

case class SkypeReceiptCard(attachments: List[SkypeReceiptAttachment])

case class MicrosoftToken(tokenType: String, expires: Int, extExpires: Int, accessToken: String)

trait SkypeJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val skypeSenderJsonFormat = jsonFormat2(SkypeSender)
  implicit val skypeRecipientJsonFormat = jsonFormat2(SkypeRecipient)
  implicit val skypeConversationJsonFormat = jsonFormat3(SkypeConversation)
  implicit val skypeImageJsonFormat = jsonFormat3(SkypeImage)

  implicit object skypeButtonJsonFormat extends RootJsonFormat[SkypeButton] {

    def write(b: SkypeButton) =
      JsObject(
        "type" -> JsString(b.buttonType),
        "title" -> JsString(b.title),
        "tap" -> JsString(b.tap)
      )

    def read(value: JsValue) =
      value.extract[String]('type) match {
        case "signin" =>
          SkypeSigninButton(value.extract[String]('title), value.extract[String]('tap))
        case "openUrl" =>
          SkypeLinkButton(value.extract[String]('title), value.extract[String]('tap))
        case "imBack" =>
          SkypePostbackButton(value.extract[String]('title), value.extract[String]('tap))
        case "call" =>
          SkypeCallButton(value.extract[String]('title), value.extract[String]('tap))
        case "showImage" =>
          SkypeShowImageButton(value.extract[String]('title), value.extract[String]('tap))
        case _ => throw DeserializationException("SkypeButton expected")
      }

  }

  implicit object skypeSigninButtonJsonFormat extends RootJsonFormat[SkypeSigninButton] {

    def write(b: SkypeSigninButton) =
      JsObject(
        "type" -> JsString(b.buttonType),
        "title" -> JsString(b.title),
        "tap" -> JsString(b.tap)
      )

    def read(value: JsValue) =
      SkypeSigninButton(value.extract[String]('title), value.extract[String]('tap))

  }

  implicit object skypeLinkButtonJsonFormat extends RootJsonFormat[SkypeLinkButton] {

    def write(b: SkypeLinkButton) =
      JsObject(
        "type" -> JsString(b.buttonType),
        "title" -> JsString(b.title),
        "tap" -> JsString(b.tap)
      )

    def read(value: JsValue) = SkypeLinkButton(value.extract[String]('title), value.extract[String]('tap))

  }

  implicit object skypePostbackButtonJsonFormat extends RootJsonFormat[SkypePostbackButton] {

    def write(b: SkypePostbackButton) =
      JsObject(
        "type" -> JsString(b.buttonType),
        "title" -> JsString(b.title),
        "tap" -> JsString(b.tap)
      )

    def read(value: JsValue) = SkypePostbackButton(value.extract[String]('title), value.extract[String]('tap))

  }

  implicit object skypeCallButtonJsonFormat extends RootJsonFormat[SkypeCallButton] {

    def write(b: SkypeCallButton) =
      JsObject(
        "type" -> JsString(b.buttonType),
        "title" -> JsString(b.title),
        "tap" -> JsString(b.tap)
      )

    def read(value: JsValue) = SkypeCallButton(value.extract[String]('title), value.extract[String]('tap))

  }

  implicit object skypeShowImageButtonJsonFormat extends RootJsonFormat[SkypeShowImageButton] {

    def write(b: SkypeShowImageButton) =
      JsObject(
        "type" -> JsString(b.buttonType),
        "title" -> JsString(b.title),
        "tap" -> JsString(b.tap)
      )

    def read(value: JsValue) = SkypeShowImageButton(value.extract[String]('title), value.extract[String]('tap))

  }

  implicit val skypeFactJsonFormat = jsonFormat2(SkypeFact)
  implicit val skypeReceiptItemJsonFormat = jsonFormat5(SkypeReceiptItem)
  implicit val skypeReceiptAttachmentContentJsonFormat = jsonFormat7(SkypeReceiptAttachmentContent)
  implicit val skypeSigninAttachmentContentJsonFormat = jsonFormat2(SkypeSigninAttachmentContent)
  implicit val skypeImageCardAttachmentContentJsonFormat = jsonFormat5(SkypeImageCardAttachmentContent)

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
      case t: SkypeThumbnailAttachment => JsObject(
        "contentType" -> JsString(t.contentType),
        "content" -> t.content.toJson
      )
      case r: SkypeReceiptAttachment => JsObject(
        "contentType" -> JsString(r.contentType),
        "content" -> r.content.toJson
      )
    }

    def read(value: JsValue) =
      value.extract[String]('contentType) match {
        case "application/vnd.microsoft.card.signin" =>
          SkypeSigninAttachment(value.extract[SkypeSigninAttachmentContent]('content))
        case "application/vnd.microsoft.card.hero" =>
          SkypeHeroAttachment(value.extract[SkypeImageCardAttachmentContent]('content))
        case "application/vnd.microsoft.card.thumbnail" =>
          SkypeThumbnailAttachment(value.extract[SkypeImageCardAttachmentContent]('content))
        case "application/vnd.microsoft.card.receipt" =>
          SkypeReceiptAttachment(value.extract[SkypeReceiptAttachmentContent]('content))
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
      SkypeHeroAttachment(value.extract[SkypeImageCardAttachmentContent]('content))

  }

  implicit object skypeThumbnailAttachmentJsonFormat extends RootJsonFormat[SkypeThumbnailAttachment] {

    def write(a: SkypeThumbnailAttachment) =
      JsObject(
        "contentType" -> JsString(a.contentType),
        "content" -> a.content.toJson
      )

    def read(value: JsValue) =
      SkypeThumbnailAttachment(value.extract[SkypeImageCardAttachmentContent]('content))

  }

  implicit object skypeReceiptAttachmentJsonFormat extends RootJsonFormat[SkypeReceiptAttachment] {

    def write(a: SkypeReceiptAttachment) =
      JsObject(
        "contentType" -> JsString(a.contentType),
        "content" -> a.content.toJson
      )

    def read(value: JsValue) =
      SkypeReceiptAttachment(value.extract[SkypeReceiptAttachmentContent]('content))

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

  implicit object skypeCarouselJsonFormat extends RootJsonFormat[SkypeCarousel] {

    def write(l: SkypeCarousel) =
      JsObject(
        "attachmentLayout" -> JsString(l.attachmentLayout),
        "attachments" -> l.attachments.toJson
      )

    def read(value: JsValue) =
      SkypeCarousel(value.extract[List[SkypeAttachment]]('attachments))

  }

  implicit object skypeListJsonFormat extends RootJsonFormat[SkypeList] {

    def write(l: SkypeList) =
      JsObject(
        "attachmentLayout" -> JsString(l.attachmentLayout),
        "attachments" -> l.attachments.toJson
      )

    def read(value: JsValue) =
      SkypeList(value.extract[List[SkypeAttachment]]('attachments))

  }

  implicit val skypeUserMessageJsonFormat = jsonFormat(SkypeUserMessage, "id", "type", "timestamp", "text", "channelId", "serviceUrl", "conversation", "from", "recipient", "attachments", "entities")
  implicit val skypeBotMessageJsonFormat = jsonFormat(SkypeBotMessage, "type", "text", "attachments")
  implicit val skypeSigninCardJsonFormat = jsonFormat(SkypeSigninCard, "type", "attachments")
  implicit val skypeHeroCardJsonFormat = jsonFormat1(SkypeHeroCard)
  implicit val skypeThumbnailCardJsonFormat = jsonFormat1(SkypeThumbnailCard)
  implicit val skypeReceiptCardJsonFormat = jsonFormat1(SkypeReceiptCard)
  implicit val microsoftTokenJsonFormat = jsonFormat(MicrosoftToken, "token_type", "expires_in", "ext_expires_in", "access_token")

}

object Builder {

  class ThumbnailCardBuilder(attachmentBuilder: ThumbnailAttachmentBuilder) {

    def withTitle(value: String) = new ThumbnailCardBuilder(attachmentBuilder withTitle value)

    def withSubtitle(value: String) = new ThumbnailCardBuilder(attachmentBuilder withSubtitle value)

    def withText(value: String) = new ThumbnailCardBuilder(attachmentBuilder withText value)

    def addImage(image: SkypeImage) = new ThumbnailCardBuilder(attachmentBuilder addImage image)

    def addImage(url: String) = new ThumbnailCardBuilder(attachmentBuilder addImage url)

    def withImages(value: List[SkypeImage]) = new ThumbnailCardBuilder(attachmentBuilder withImages value)

    def addButton(button: SkypeButton) = new ThumbnailCardBuilder(attachmentBuilder addButton button)

    def withButtons(value: List[SkypeButton]) = new ThumbnailCardBuilder(attachmentBuilder withButtons value)

    def build() = SkypeThumbnailCard(List(attachmentBuilder build()))

  }

  def thumbnailCard = new ThumbnailCardBuilder(thumbnailAttachment)

  class HeroCardBuilder(attachmentBuilder: HeroAttachmentBuilder) {

    def withTitle(value: String) = new HeroCardBuilder(attachmentBuilder withTitle value)

    def withSubtitle(value: String) = new HeroCardBuilder(attachmentBuilder withSubtitle value)

    def withText(value: String) = new HeroCardBuilder(attachmentBuilder withText value)

    def addImage(image: SkypeImage) = new HeroCardBuilder(attachmentBuilder addImage image)

    def addImage(url: String) = new HeroCardBuilder(attachmentBuilder addImage url)

    def withImages(value: List[SkypeImage]) = new HeroCardBuilder(attachmentBuilder withImages value)

    def addButton(button: SkypeButton) = new HeroCardBuilder(attachmentBuilder addButton button)

    def withButtons(value: List[SkypeButton]) = new HeroCardBuilder(attachmentBuilder withButtons value)

    def build() = SkypeHeroCard(List(attachmentBuilder build()))

  }

  def heroCard = new HeroCardBuilder(heroAttachment)

  class CarouselHeroCardBuilder(attachments: List[SkypeHeroAttachment]) {

    def addAttachment(attachment: SkypeHeroAttachment) = new CarouselHeroCardBuilder(attachments :+ attachment)

    def withAttachments(value: List[SkypeHeroAttachment]) = new CarouselHeroCardBuilder(value)

    def build() = SkypeCarousel(attachments = attachments)

  }

  def carouselHeroCard = new CarouselHeroCardBuilder(Nil)

  class ThumbnailAttachmentBuilder(contentBuilder: CardAttachmentContentBuilder) {

    def withTitle(value: String) = new ThumbnailAttachmentBuilder(contentBuilder withTitle value)

    def withSubtitle(value: String) = new ThumbnailAttachmentBuilder(contentBuilder withSubtitle value)

    def withText(value: String) = new ThumbnailAttachmentBuilder(contentBuilder withText value)

    def addImage(image: SkypeImage) = new ThumbnailAttachmentBuilder(contentBuilder addImage image)

    def addImage(url: String) = new ThumbnailAttachmentBuilder(contentBuilder addImage url)

    def withImages(value: List[SkypeImage]) = new ThumbnailAttachmentBuilder(contentBuilder withImages value)

    def addButton(button: SkypeButton) = new ThumbnailAttachmentBuilder(contentBuilder addButton button)

    def withButtons(value: List[SkypeButton]) = new ThumbnailAttachmentBuilder(contentBuilder withButtons value)

    def build() = SkypeThumbnailAttachment(contentBuilder build())

  }

  def thumbnailAttachment = new ThumbnailAttachmentBuilder(cardAttachmentContent)

  class HeroAttachmentBuilder(contentBuilder: CardAttachmentContentBuilder) {

    def withTitle(value: String) = new HeroAttachmentBuilder(contentBuilder withTitle value)

    def withSubtitle(value: String) = new HeroAttachmentBuilder(contentBuilder withSubtitle value)

    def withText(value: String) = new HeroAttachmentBuilder(contentBuilder withText value)

    def addImage(image: SkypeImage) = new HeroAttachmentBuilder(contentBuilder addImage image)

    def addImage(url: String) = new HeroAttachmentBuilder(contentBuilder addImage url)

    def withImages(value: List[SkypeImage]) = new HeroAttachmentBuilder(contentBuilder withImages value)

    def addButton(button: SkypeButton) = new HeroAttachmentBuilder(contentBuilder addButton button)

    def withButtons(value: List[SkypeButton]) = new HeroAttachmentBuilder(contentBuilder withButtons value)

    def build() = SkypeHeroAttachment(contentBuilder build())

  }

  def heroAttachment = new HeroAttachmentBuilder(cardAttachmentContent)

  class CardAttachmentContentBuilder(title: Option[String], subtitle: Option[String], text: Option[String], images: List[SkypeImage], buttons: List[SkypeButton]) {

    def withTitle(value: String) = new CardAttachmentContentBuilder(Some(value), subtitle, text, images, buttons)

    def withSubtitle(value: String) = new CardAttachmentContentBuilder(title, Some(value), text, images, buttons)

    def withText(value: String) = new CardAttachmentContentBuilder(title, subtitle, Some(value), images, buttons)

    def addImage(image: SkypeImage) = new CardAttachmentContentBuilder(title, subtitle, text, images :+ image, buttons)

    def addImage(url: String) = new CardAttachmentContentBuilder(title, subtitle, text, images :+ SkypeImage(url, None, None), buttons)

    def withImages(value: List[SkypeImage]) = new CardAttachmentContentBuilder(title, subtitle, text, value, buttons)

    def addButton(button: SkypeButton) = new CardAttachmentContentBuilder(title, subtitle, text, images, buttons :+ button)

    def withButtons(value: List[SkypeButton]) = new CardAttachmentContentBuilder(title, subtitle, text, images, value)

    def build() =
      SkypeImageCardAttachmentContent(
        title = title,
        subtitle = subtitle,
        text = text,
        images = if (images.nonEmpty) Some(images) else None,
        buttons = buttons
      )

  }

  def cardAttachmentContent = new CardAttachmentContentBuilder(None, None, None, Nil, Nil)

  class ReceiptCardBuilder(title: Option[String], facts: List[SkypeFact], items: List[SkypeReceiptItem], total: Option[String], tax: Option[String], vat: Option[String], buttons: List[SkypeButton]) {

    def withTitle(value: String) = new ReceiptCardBuilder(Some(value), facts, items, total, tax, vat, buttons)

    def addFact(fact: SkypeFact) = new ReceiptCardBuilder(title, facts :+ fact, items, total, tax, vat, buttons)

    def addFact(key: String, value: String) = new ReceiptCardBuilder(title, facts :+ SkypeFact(key, value), items, total, tax, vat, buttons)

    def addItem(item: SkypeReceiptItem) = new ReceiptCardBuilder(title, facts, items :+ item, total, tax, vat, buttons)

    def withTotal(value: String) = new ReceiptCardBuilder(title, facts, items, Some(value), tax, vat, buttons)

    def withTax(value: String) = new ReceiptCardBuilder(title, facts, items, total, Some(value), vat, buttons)

    def withVAT(value: String) = new ReceiptCardBuilder(title, facts, items, total, tax, Some(value), buttons)

    def addButton(button: SkypeButton) = new ReceiptCardBuilder(title, facts, items, total, tax, vat, buttons :+ button)

    def addLinkButton(buttonTitle: String, tap: String) = new ReceiptCardBuilder(title, facts, items, total, tax, vat, buttons :+ SkypeLinkButton(buttonTitle, tap))

    def build() =
      SkypeReceiptCard(
        attachments = List(
          SkypeReceiptAttachment(
            SkypeReceiptAttachmentContent(
              title = title,
              facts = if (facts.nonEmpty) Some(facts) else None,
              items = if (items.nonEmpty) Some(items) else None,
              total = total,
              tax = tax,
              vat = vat,
              buttons = if (buttons.nonEmpty) Some(buttons) else None
            )
          )
        )
      )

  }

  def receiptCard = new ReceiptCardBuilder(None, Nil, Nil, None, None, None, Nil)

  class ReceiptItemBuilder(title: Option[String], subtitle: Option[String], image: Option[SkypeImage], price: Option[String], quantity: Option[String]) {

    def withTitle(value: String) = new ReceiptItemBuilder(Some(value), subtitle, image, price, quantity)

    def withSubtitle(value: String) = new ReceiptItemBuilder(title, Some(value), image, price, quantity)

    def withImage(value: SkypeImage) = new ReceiptItemBuilder(title, subtitle, Some(value), price, quantity)

    def withImageURL(url: String) = new ReceiptItemBuilder(title, subtitle, Some(SkypeImage(url, None, None)), price, quantity)

    def withPrice(value: String) = new ReceiptItemBuilder(title, subtitle, image, Some(value), quantity)

    def withQuantity(value: String) = new ReceiptItemBuilder(title, subtitle, image, price, Some(value))

    def build() =
      SkypeReceiptItem(
        title = title,
        subtitle = subtitle,
        image = image,
        price = price,
        quantity = quantity
      )

  }

  def receiptItem = new ReceiptItemBuilder(None, None, None, None, None)

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
              tap = s"${api.get}/skypeauthorize?sender=${sender.get}"
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