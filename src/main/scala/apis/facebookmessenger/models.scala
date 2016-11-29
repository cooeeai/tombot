package apis.facebookmessenger

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._
import spray.json.lenses.JsonLenses._

/**
  * Created by markmo on 17/07/2016.
  */
case class FacebookSender(id: String)

case class FacebookRecipient(id: String)

case class FacebookMessage(mid: String, seq: Int, text: String)

// attachment_id is only returned when the is_reusable flag is set to true
// on messages sent with a multimedia attachment
case class FacebookAttachmentReuseResponse(recipientId: String, messageId: String, attachmentId: Option[String])

case class FacebookPostback(payload: String)

case class FacebookMessaging(sender: FacebookSender,
                             recipient: FacebookRecipient,
                             timestamp: Long,
                             message: Option[FacebookMessage],
                             postback: Option[FacebookPostback])

case class FacebookEntry(id: String, time: Long, messaging: List[FacebookMessaging])

case class FacebookResponse(obj: String, entry: List[FacebookEntry])

sealed trait FacebookButton {
  val buttonType: String
}

case class FacebookLinkButton(title: String, url: String) extends FacebookButton {
  override val buttonType = "web_url"
}

case class FacebookPostbackButton(title: String, payload: JsValue) extends FacebookButton {
  override val buttonType = "postback"
}

case class FacebookLoginButton(url: String) extends FacebookButton {
  override val buttonType = "account_link"
}

case class FacebookElement(title: String, subtitle: String, itemURL: String, imageURL: String, buttons: List[FacebookButton])

case class FacebookReceiptElement(title: String, subtitle: String, quantity: Int, price: BigDecimal, currency: String, imageURL: String)

case class FacebookAddress(street1: String, street2: String, city: String, postcode: String, state: String, country: String) {
  override def toString = street1 + ", " + street2 + ", " + city + ", " + state + " " + postcode
}

case class FacebookSummary(subtotal: BigDecimal, shippingCost: BigDecimal, totalTax: BigDecimal, totalCost: BigDecimal)

case class FacebookAdjustment(name: String, amount: BigDecimal)

sealed trait FacebookTemplate {
  val templateType: String
}

case class FacebookGenericTemplate(elements: List[FacebookElement]) extends FacebookTemplate {
  override val templateType = "generic"
}

case class FacebookReceiptTemplate(recipientName: String,
                                   orderNumber: String,
                                   currency: String,
                                   paymentMethod: String,
                                   orderURL: String,
                                   timestamp: Long,
                                   elements: List[FacebookReceiptElement],
                                   address: FacebookAddress,
                                   summary: FacebookSummary,
                                   adjustments: List[FacebookAdjustment]) extends FacebookTemplate {
  override val templateType = "receipt"
}

case class FacebookAttachment(attachmentType: String, payload: FacebookTemplate)

case class FacebookGenericMessage(attachment: FacebookAttachment)

case class FacebookGenericMessageTemplate(recipient: FacebookRecipient, message: FacebookGenericMessage)

case class FacebookUserProfile(firstName: String, lastName: String, picture: String, locale: String, timezone: Int, gender: String)

case class FacebookOptIn(ref: String)

case class FacebookAuthenticationEvent(sender: FacebookSender, recipient: FacebookRecipient, timestamp: Long, optin: FacebookOptIn)

case class FacebookDelivery(mids: Option[List[String]], watermark: Long, seq: Int)

case class FacebookMessageDeliveredEvent(sender: FacebookSender, recipient: FacebookRecipient, delivery: FacebookDelivery)

case class FacebookRead(watermark: Long, seq: Int)

case class FacebookMessageReadEvent(sender: FacebookSender, recipient: FacebookRecipient, read: FacebookRead)

case class FacebookAccountLinking(status: String, authorizationCode: Option[String])

case class FacebookAccountLinkingEvent(sender: FacebookSender, recipient: FacebookRecipient, timestamp: Long, accountLinking: FacebookAccountLinking)

case class FacebookUserPSID(id: String, recipient: String)

case class FacebookQuickReply(contentType: String, title: String, payload: String)

case class FacebookQuickReplyMessage(text: String, quickReplies: List[FacebookQuickReply])

case class FacebookQuickReplyTemplate(recipient: FacebookRecipient, message: FacebookQuickReplyMessage)

trait FacebookJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {

  implicit val facebookSenderJsonFormat = jsonFormat1(FacebookSender)
  implicit val facebookRecipientJsonFormat = jsonFormat1(FacebookRecipient)
  implicit val facebookMessageJsonFormat = jsonFormat3(FacebookMessage)
  implicit val facebookResponseJsonFormat = jsonFormat(FacebookAttachmentReuseResponse, "recipient_id", "message_id", "attachment_id")
  implicit val facebookPostbackJsonFormat = jsonFormat1(FacebookPostback)
  implicit val facebookMessagingJsonFormat = jsonFormat5(FacebookMessaging)
  implicit val facebookEntryJsonFormat = jsonFormat3(FacebookEntry)
  implicit val facebookQuickReplyResponseJsonFormat = jsonFormat(FacebookResponse, "object", "entry")

  implicit object facebookButtonJsonFormat extends RootJsonFormat[FacebookButton] {

    def write(b: FacebookButton) = b match {
      case l: FacebookLinkButton => JsObject(
        "type" -> JsString(l.buttonType),
        "title" -> JsString(l.title),
        "url" -> JsString(l.url)
      )
      case p: FacebookPostbackButton => JsObject(
        "type" -> JsString(p.buttonType),
        "title" -> JsString(p.title),
        "payload" -> p.payload
      )
      case b: FacebookLoginButton => JsObject(
        "type" -> JsString(b.buttonType),
        "url" -> JsString(b.url)
      )
    }

    def read(value: JsValue) =
      value.extract[String]('type) match {
        case "web_url" =>
          FacebookLinkButton(value.extract[String]('title), value.extract[String]('url))
        case "postback" =>
          FacebookPostbackButton(value.extract[String]('title), value.extract[JsValue]('payload))
        case "account_link" =>
          FacebookLoginButton(value.extract[String]('url))
        case _ => throw DeserializationException("Button expected")
      }
  }

  implicit val facebookElementJsonFormat = jsonFormat(FacebookElement, "title", "subtitle", "item_url", "image_url", "buttons")
  implicit val facebookReceiptElementJsonFormat = jsonFormat(FacebookReceiptElement, "title", "subtitle", "quantity", "price", "currency", "image_url")
  implicit val facebookAddressJsonFormat = jsonFormat(FacebookAddress, "street_1", "street_2", "city", "postal_code", "state", "country")
  implicit val facebookSummaryJsonFormat = jsonFormat(FacebookSummary, "subtotal", "shipping_cost", "total_tax", "total_cost")
  implicit val facebookAdjustmentJsonFormat = jsonFormat2(FacebookAdjustment)
  implicit val facebookUserProfileJsonFormat = jsonFormat(FacebookUserProfile, "first_name", "last_name", "profile_pic", "locale", "timezone", "gender")
  implicit val facebookOptInJsonFormat = jsonFormat1(FacebookOptIn)
  implicit val facebookAuthenticationEventJsonFormat = jsonFormat4(FacebookAuthenticationEvent)
  implicit val facebookDeliveryJsonFormat = jsonFormat3(FacebookDelivery)
  implicit val facebookMessageDeliveredEventJsonFormat = jsonFormat3(FacebookMessageDeliveredEvent)
  implicit val facebookReadJsonFormat = jsonFormat2(FacebookRead)
  implicit val facebookMessageReadEventJsonFormat = jsonFormat3(FacebookMessageReadEvent)
  implicit val facebookAccountLinkingJsonFormat = jsonFormat(FacebookAccountLinking, "status", "authorization_code")
  implicit val facebookAccountLinkingEventJsonFormat = jsonFormat(FacebookAccountLinkingEvent, "sender", "recipient", "timestamp", "account_linking")
  implicit val facebookUserPSIDJsonFormat = jsonFormat2(FacebookUserPSID)

  implicit object facebookTemplateJsonFormat extends RootJsonFormat[FacebookTemplate] {

    def write(t: FacebookTemplate) = t match {
      case g: FacebookGenericTemplate => JsObject(
        "template_type" -> JsString(g.templateType),
        "elements" -> g.elements.toJson
      )
      case r: FacebookReceiptTemplate => JsObject(
        "template_type" -> JsString(r.templateType),
        "recipient_name" -> JsString(r.recipientName),
        "order_number" -> JsString(r.orderNumber),
        "currency" -> JsString(r.currency),
        "payment_method" -> JsString(r.paymentMethod),
        "order_url" -> JsString(r.orderURL),
        "timestamp" -> JsNumber(r.timestamp),
        "elements" -> r.elements.toJson,
        "address" -> r.address.toJson,
        "summary" -> r.summary.toJson,
        "adjustments" -> r.adjustments.toJson
      )
    }

    def read(value: JsValue) =
      value.extract[String]('template_type) match {
        case "generic" =>
          FacebookGenericTemplate(value.extract[List[FacebookElement]]('elements))
        case "receipt" =>
          FacebookReceiptTemplate(
            value.extract[String]('recipient_name),
            value.extract[String]('order_number),
            value.extract[String]('currency),
            value.extract[String]('payment_method),
            value.extract[String]('order_url),
            value.extract[Long]('timestamp),
            value.extract[List[FacebookReceiptElement]]('elements),
            value.extract[FacebookAddress]('address),
            value.extract[FacebookSummary]('summary),
            value.extract[List[FacebookAdjustment]]('adjustments)
          )
        case _ => throw DeserializationException("FacebookTemplate expected")
      }

  }

  implicit val facebookAttachmentJsonFormat = jsonFormat(FacebookAttachment, "type", "payload")
  implicit val facebookGenericMessageJsonFormat = jsonFormat1(FacebookGenericMessage)
  implicit val facebookGenericMessageTemplateJsonFormat = jsonFormat2(FacebookGenericMessageTemplate)
  implicit val facebookQuickReplyJsonFormat = jsonFormat(FacebookQuickReply, "content_type", "title", "payload")
  implicit val facebookQuickReplyMessageJsonFormat = jsonFormat(FacebookQuickReplyMessage, "text", "quick_replies")
  implicit val facebookQuickReplyTemplateJsonFormat = jsonFormat2(FacebookQuickReplyTemplate)

}

object Builder {

  class GenericMessageTemplateBuilder(sender: Option[String],
                                      title: Option[String],
                                      subtitle: Option[String],
                                      itemURL: Option[String],
                                      imageURL: Option[String],
                                      buttons: List[FacebookButton],
                                      elements: List[FacebookElement]) {

    def forSender(value: String) = new GenericMessageTemplateBuilder(Some(value), title, subtitle, itemURL, imageURL, buttons, elements)

    def withTitle(value: String) = new GenericMessageTemplateBuilder(sender, Some(value), subtitle, itemURL, imageURL, buttons, elements)

    def withSubtitle(value: String) = new GenericMessageTemplateBuilder(sender, title, Some(value), itemURL, imageURL, buttons, elements)

    def withItemURL(value: String) = new GenericMessageTemplateBuilder(sender, title, subtitle, Some(value), imageURL, buttons, elements)

    def withImageURL(value: String) = new GenericMessageTemplateBuilder(sender, title, subtitle, itemURL, Some(value), buttons, elements)

    def addButton(button: FacebookButton) = new GenericMessageTemplateBuilder(sender, title, subtitle, itemURL, imageURL, buttons :+ button, elements)

    def withElements(value: List[FacebookElement]) = new GenericMessageTemplateBuilder(sender, title, subtitle, itemURL, imageURL, buttons, value)

    def build() =
      if (elements.isEmpty) {
        FacebookGenericMessageTemplate(
          FacebookRecipient(sender.get),
          FacebookGenericMessage(
            FacebookAttachment(
              attachmentType = "template",
              payload = FacebookGenericTemplate(
                elements = FacebookElement(
                  title = title.get,
                  subtitle = subtitle.getOrElse(""),
                  itemURL = itemURL.getOrElse(""),
                  imageURL = imageURL.getOrElse(""),
                  buttons = buttons
                ) :: Nil
              )
            )
          )
        )
      } else {
        FacebookGenericMessageTemplate(
          FacebookRecipient(sender.get),
          FacebookGenericMessage(
            FacebookAttachment(
              attachmentType = "template",
              payload = FacebookGenericTemplate(elements)
            )
          )
        )
      }
  }

  def genericTemplate = new GenericMessageTemplateBuilder(None, None, None, None, None, Nil, Nil)

  class ReceiptCardBuilder(sender: Option[String],
                           recipientName: Option[String],
                           orderNumber: Option[String],
                           currency: Option[String],
                           paymentMethod: Option[String],
                           orderURL: Option[String],
                           timestamp: Option[Long],
                           elements: List[FacebookReceiptElement],
                           address: Option[FacebookAddress],
                           summary: Option[FacebookSummary],
                           adjustments: List[FacebookAdjustment]) {

    def forSender(value: String) = new ReceiptCardBuilder(Some(value), recipientName, orderNumber, currency, paymentMethod, orderURL, timestamp, elements, address, summary, adjustments)

    def withReceiptName(value: String) = new ReceiptCardBuilder(sender, Some(value), orderNumber, currency, paymentMethod, orderURL, timestamp, elements, address, summary, adjustments)

    def withOrderNumber(value: String) = new ReceiptCardBuilder(sender, recipientName, Some(value), currency, paymentMethod, orderURL, timestamp, elements, address, summary, adjustments)

    def withCurrency(value: String) = new ReceiptCardBuilder(sender, recipientName, orderNumber, Some(value), paymentMethod, orderURL, timestamp, elements, address, summary, adjustments)

    def withPaymentMethod(value: String) = new ReceiptCardBuilder(sender, recipientName, orderNumber, currency, Some(value), orderURL, timestamp, elements, address, summary, adjustments)

    def withOrderURL(value: String) = new ReceiptCardBuilder(sender, recipientName, orderNumber, currency, paymentMethod, Some(value), timestamp, elements, address, summary, adjustments)

    def withTimestamp(value: Long) = new ReceiptCardBuilder(sender, recipientName, orderNumber, currency, paymentMethod, orderURL, Some(value), elements, address, summary, adjustments)

    def withElements(value: List[FacebookReceiptElement]) = new ReceiptCardBuilder(sender, recipientName, orderNumber, currency, paymentMethod, orderURL, timestamp, value, address, summary, adjustments)

    def withAddress(value: FacebookAddress) = new ReceiptCardBuilder(sender, recipientName, orderNumber, currency, paymentMethod, orderURL, timestamp, elements, Some(value), summary, adjustments)

    def withSummary(value: FacebookSummary) = new ReceiptCardBuilder(sender, recipientName, orderNumber, currency, paymentMethod, orderURL, timestamp, elements, address, Some(value), adjustments)

    def withSummary(subtotal: String, shippingCost: String, totalTax: String, totalCost: String) = {
      val s = summaryElement withSubtotal "1047.00" withShippingCost "25.00" withTotalTax "104.70" withTotalCost "942.30" build()
      new ReceiptCardBuilder(sender, recipientName, orderNumber, currency, paymentMethod, orderURL, timestamp, elements, address, Some(s), adjustments)
    }

    def addAdjustment(adjustment: FacebookAdjustment) = new ReceiptCardBuilder(sender, recipientName, orderNumber, currency, paymentMethod, orderURL, timestamp, elements, address, summary, adjustments :+ adjustment)

    def addAdjustment(name: String, amount: String) = {
      val a = FacebookAdjustment(name, BigDecimal(amount))
      new ReceiptCardBuilder(sender, recipientName, orderNumber, currency, paymentMethod, orderURL, timestamp, elements, address, summary, adjustments :+ a)
    }

    def build() =
      FacebookGenericMessageTemplate(
        FacebookRecipient(sender.get),
        FacebookGenericMessage(
          FacebookAttachment(
            attachmentType = "template",
            payload = FacebookReceiptTemplate(
              recipientName = recipientName.getOrElse(""),
              orderNumber = orderNumber.getOrElse(""),
              currency = currency.getOrElse(""),
              paymentMethod = paymentMethod.getOrElse(""),
              orderURL = orderURL.getOrElse(""),
              timestamp = timestamp.getOrElse(0L),
              elements = elements,
              address = address.get,
              summary = summary.get,
              adjustments = adjustments
            )
          )
        )
      )
  }

  def receiptCard = new ReceiptCardBuilder(None, None, None, None, None, None, None, Nil, None, None, Nil)

  class SummaryBuilder(subtotal: Option[String],
                       shippingCost: Option[String],
                       totalTax: Option[String],
                       totalCost: Option[String]) {

    def withSubtotal(value: String) = new SummaryBuilder(Some(value), shippingCost, totalTax, totalCost)

    def withShippingCost(value: String) = new SummaryBuilder(subtotal, Some(value), totalTax, totalCost)

    def withTotalTax(value: String) = new SummaryBuilder(subtotal, shippingCost, Some(value), totalCost)

    def withTotalCost(value: String) = new SummaryBuilder(subtotal, shippingCost, totalTax, Some(value))

    def build() =
      FacebookSummary(
        subtotal = BigDecimal(subtotal.getOrElse("0")),
        shippingCost = BigDecimal(shippingCost.getOrElse("0")),
        totalTax = BigDecimal(totalTax.getOrElse("0")),
        totalCost = BigDecimal(totalCost.getOrElse("0"))
      )
  }

  def summaryElement = new SummaryBuilder(None, None, None, None)

  class MessageBuilder(sender: Option[String], text: Option[String]) {

    def forSender(value: String) = new MessageBuilder(Some(value), text)

    def withText(value: String) = new MessageBuilder(sender, Some(value))

    def build() =
      JsObject(
        "recipient" -> JsObject("id" -> JsString(sender.get)),
        "message" -> JsObject("text" -> JsString(text.get))
      )

  }

  def messageElement = new MessageBuilder(None, None)

  class QuickReplyBuilder(sender: Option[String], text: Option[String]) {

    def forSender(value: String) = new QuickReplyBuilder(Some(value), text)

    def withText(value: String) = new QuickReplyBuilder(sender, Some(value))

    def build() =
      FacebookQuickReplyTemplate(FacebookRecipient(sender.get), FacebookQuickReplyMessage(
        text = text.get,
        quickReplies = FacebookQuickReply(
          contentType = "text",
          title = "Yes",
          payload = ""
        ) :: FacebookQuickReply(
          contentType = "text",
          title = "No",
          payload = ""
        ) :: Nil
      ))

  }

  def quickReply = new QuickReplyBuilder(None, None)

}