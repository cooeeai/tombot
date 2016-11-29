package services

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import apis.facebookmessenger._
import com.google.inject.{Inject, Singleton}
import com.typesafe.config.Config
import memory.Slot
import models.{Item, ItemLinkAction, ItemPostbackAction, UserProfile}
import spray.json._

import scala.concurrent.Future

/**
  * Created by markmo on 14/08/2016.
  */
@Singleton
class FacebookService @Inject()(config: Config,
                                logger: LoggingAdapter,
                                paymentService: PaymentService,
                                implicit val system: ActorSystem,
                                implicit val fm: Materializer)
  extends MessagingProvider with FacebookJsonSupport {

  import system.dispatcher

  val api = config.getString("api.host")

  val accessToken = System.getenv("FB_PAGE_ACCESS_TOKEN")

  val http = Http()

  def sendTextMessage(sender: String, text: String): Future[SendResponse] = {
    logger.info("sending text message: [{}] to sender [{}]", text, sender)
    import Builder._

    val payload = messageElement forSender sender withText text build()

    logger.debug("sending payload:\n{}", payload.toJson.prettyPrint)
    for {
      request <- Marshal(payload).to[RequestEntity]
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = s"https://graph.facebook.com/v2.8/me/messages?access_token=$accessToken",
        headers = List(headers.Accept(MediaTypes.`application/json`)),
        entity = request))
      entity <- Unmarshal(response.entity).to[FacebookAttachmentReuseResponse]
    } yield SendResponse(entity.messageId)
  }

  def sendLoginCard(sender: String, conversationId: String): Future[SendResponse] = {
    logger.info("sending login message to sender: {}", sender)
    import Builder._

    val payload = (
      genericTemplate
        forSender sender
        withTitle "Welcome to Telstra"
        withSubtitle "Please login so I can access that information for you"
        withImageURL s"$api/img/telstra_logo_128.png"
        addButton FacebookLoginButton(s"$api/authorize")
        build()
      )

    logger.debug("sending payload:\n{}", payload.toJson.prettyPrint)
    for {
      request <- Marshal(payload).to[RequestEntity]
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = s"https://graph.facebook.com/v2.8/me/messages?access_token=$accessToken",
        headers = List(headers.Accept(MediaTypes.`application/json`)),
        entity = request))
      entity <- Unmarshal(response.entity).to[FacebookAttachmentReuseResponse]
    } yield SendResponse(entity.messageId)
  }

  def sendHeroCard(sender: String, items: List[Item]): Future[SendResponse] = {
    logger.info("sending generic message to sender [{}]", sender)
    import Builder._
    val elements = itemsToFacebookElements(items)
    val payload = (
      genericTemplate
        forSender sender
        withElements elements
        build()
      )
    logger.debug("sending payload:\n{}", payload.toJson.prettyPrint)
    for {
      request <- Marshal(payload).to[RequestEntity]
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = s"https://graph.facebook.com/v2.8/me/messages?access_token=$accessToken",
        headers = List(headers.Accept(MediaTypes.`application/json`)),
        entity = request))
      entity <- Unmarshal(response.entity).to[FacebookAttachmentReuseResponse]
    } yield SendResponse(entity.messageId)
  }

  def sendReceiptCard(sender: String, slot: Slot): Future[SendResponse] = {
    logger.info("sending receipt message to sender [{}]", sender)
    import Builder._
    val elements = paymentService.getElements
    val receiptId = "order" + Math.floor(Math.random() * 1000)
    val address = FacebookAddress(
      street1 = slot.getString("street1"),
      street2 = "",
      city = slot.getString("city"),
      postcode = slot.getString("postcode"),
      state = slot.getString("state"),
      country = slot.getString("country")
    )
    val payload = (
      receiptCard
        forSender sender
        withReceiptName slot.getString("cardholderName")
        withOrderNumber receiptId
        withCurrency "AUD"
        withPaymentMethod "Visa 1234"
        withTimestamp 1428444852L
        withElements elements
        withAddress address
        withSummary(subtotal = "1047.00", shippingCost = "25.00", totalTax = "104.70", totalCost = "942.30")
        addAdjustment(name = "Coupon DAY1", amount = "-100.00")
        build()
      )
    logger.debug("sending payload:\n{}", payload.toJson.prettyPrint)
    for {
      request <- Marshal(payload).to[RequestEntity]
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = s"https://graph.facebook.com/v2.8/me/messages?access_token=$accessToken",
        headers = List(headers.Accept(MediaTypes.`application/json`)),
        entity = request))
      entity <- Unmarshal(response.entity).to[FacebookAttachmentReuseResponse]
    } yield SendResponse(entity.messageId)
  }

  def sendQuickReply(sender: String, text: String): Future[SendResponse] = {
    logger.info("sending quick reply to sender [{}]", sender)
    import Builder._
    val payload = (
      quickReply
        forSender sender
        withText text
        build()
      )
    logger.debug("sending payload:\n{}", payload.toJson.prettyPrint)
    for {
      request <- Marshal(payload).to[RequestEntity]
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = s"https://graph.facebook.com/v2.8/me/messages?access_token=$accessToken",
        headers = List(headers.Accept(MediaTypes.`application/json`)),
        entity = request))
      entity <- Unmarshal(response.entity).to[FacebookAttachmentReuseResponse]
    } yield SendResponse(entity.messageId)
  }

  def getUserProfile(userId: String): Future[UserProfile] = {
    logger.info("getting user profile for id[{}]", userId)
    for {
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.GET,
        uri = s"https://graph.facebook.com/v2.8/$userId?fields=first_name,last_name,profile_pic,locale,timezone,gender&access_token=$accessToken",
        headers = List(headers.Accept(MediaTypes.`application/json`))))
      entity <- Unmarshal(response.entity).to[FacebookUserProfile]
    } yield UserProfile(
      entity.firstName, entity.lastName, entity.picture,
      entity.locale, entity.timezone, entity.gender)
  }

  // example error message
  //{"error":{"message":"(#10301) Account linking token expired","type":"OAuthException","code":10301,"fbtrace_id":"C4HHSJ9+b9G"}}
  def getSenderId(accountLinkingToken: String): Future[FacebookUserPSID] = {
    logger.info("getting sender id")
    for {
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.GET,
        uri = s"https://graph.facebook.com/v2.8/me?access_token=$accessToken&fields=recipient&account_linking_token=$accountLinkingToken",
        headers = List(headers.Accept(MediaTypes.`application/json`))))
      entity <- Unmarshal(response.entity).to[FacebookUserPSID]
    } yield entity
  }

  def setupWelcomeGreeting(): Unit = {
    logger.info("Setting up welcome greeting")
    val payload = JsObject(
      "setting_type" -> JsString("greeting"),
      "greeting" -> JsObject(
        "text" -> JsString("Hi, my name is Tom")
      )
    )
    logger.info("sending payload:\n{}", payload.toJson.prettyPrint)
    for {
      request <- Marshal(payload).to[RequestEntity]
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = s"https://graph.facebook.com/v2.8/me/thread_settings?access_token=$accessToken",
        entity = request))
    } yield ()
  }

  private def itemsToFacebookElements(items: List[Item]): List[FacebookElement] =
    items map { item =>
      FacebookElement(
        title = item.title,
        subtitle = item.subtitle,
        itemURL = item.itemURL,
        imageURL = item.imageURL,
        buttons = item.actions map {
          case ItemLinkAction(title, url) => FacebookLinkButton(title, url)
          case ItemPostbackAction(title, payload) => FacebookPostbackButton(title, payload)
        }
      )
    }

}

object FacebookService extends NamedService {

  override final val name = "Facebook"

}