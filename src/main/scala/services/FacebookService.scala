package services

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, RequestEntity}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import apis.facebookmessenger._
import com.google.inject.Inject
import com.typesafe.config.Config
import spray.json._

import scala.concurrent.Future

/**
  * Created by markmo on 14/08/2016.
  */
class FacebookService @Inject()(config: Config,
                                logger: LoggingAdapter,
                                catalogService: CatalogService,
                                paymentService: PaymentService,
                                implicit val system: ActorSystem,
                                implicit val fm: Materializer)
  extends MessagingProvider with FacebookJsonSupport {

  import system.dispatcher

  val http = Http()

  val api = config.getString("api.host")

  val accessToken = System.getenv("FB_PAGE_ACCESS_TOKEN")

  def sendTextMessage(sender: String, text: String): Unit = {
    logger.info(s"sending text message: [$text] to sender [$sender]")
    import Builder._

    val payload = messageElement forSender sender withText text build()

    logger.debug("sending payload:\n" + payload.toJson.prettyPrint)
    for {
      request <- Marshal(payload).to[RequestEntity]
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = s"https://graph.facebook.com/v2.6/me/messages?access_token=$accessToken",
        entity = request))
    } yield ()
  }

  def sendLoginCard(sender: String, conversationId: String): Unit = {
    logger.info("sending login message to sender: " + sender)
    import Builder._

    val payload = (
      genericTemplate
        forSender sender
        withTitle "Welcome to T-Corp"
        withSubtitle "Please login so I can serve you better"
        withImageURL s"$api/img/bot.png"
        addButton FacebookLoginButton(s"$api/authorize")
        build()
      )

    logger.debug("sending payload:\n" + payload.toJson.prettyPrint)
    for {
      request <- Marshal(payload).to[RequestEntity]
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = s"https://graph.facebook.com/v2.6/me/messages?access_token=$accessToken",
        entity = request))
    } yield ()
  }

  def sendHeroCard(sender: String): Unit = {
    logger.info("sending generic message to sender: " + sender)
    import Builder._
    val elements = catalogService.getElements
    val payload = (
      genericTemplate
        forSender sender
        withElements elements
        build()
      )
    logger.debug("sending payload:\n" + payload.toJson.prettyPrint)
    for {
      request <- Marshal(payload).to[RequestEntity]
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = s"https://graph.facebook.com/v2.6/me/messages?access_token=$accessToken",
        entity = request))
    } yield ()
  }

  def sendReceiptCard(sender: String, address: FacebookAddress): Unit = {
    logger.info("sending receipt message to sender: " + sender)
    import Builder._
    val elements = paymentService.getElements
    val receiptId = "order" + Math.floor(Math.random() * 1000)
    val payload = (
      receiptCard
        forSender sender
        withReceiptName "Peter Chang"
        withOrderNumber receiptId
        withCurrency "AUD"
        withPaymentMethod "Visa 1234"
        withTimestamp 1428444852L
        withElements elements
        withAddress address
        withSummary (subtotal = "1047.00", shippingCost = "25.00", totalTax = "104.70", totalCost = "942.30")
        addAdjustment (name = "Coupon DAY1", amount = "-100.00")
        build()
      )
    logger.debug("sending payload:\n" + payload.toJson.prettyPrint)
    for {
      request <- Marshal(payload).to[RequestEntity]
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = s"https://graph.facebook.com/v2.6/me/messages?access_token=$accessToken",
        entity = request))
    } yield ()
  }

  // don't know why I can't unmarshal to UserProfile or JsValue
  def getUserProfile(userId: String): Future[String] = {
    logger.info("getting user profile for id: " + userId)
    for {
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.GET,
        uri = s"https://graph.facebook.com/v2.6/$userId?fields=first_name,last_name,profile_pic,locale,timezone,gender&access_token=$accessToken"))
      entity <- Unmarshal(response.entity).to[String]
    } yield entity
  }

  def getSenderId(accountLinkingToken: String): Future[FacebookUserPSID] = {
    logger.info("getting sender id")
    for {
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.GET,
        uri = s"https://graph.facebook.com/v2.6/me?access_token=$accessToken&fields=recipient&account_linking_token=$accountLinkingToken"))
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
    //    val payload = JsObject(
    //      "setting_type" -> JsString("call_to_actions"),
    //      "thread_state" -> JsString("new_thread"),
    //      "call_to_actions" -> JsArray(
    //        JsObject(
    //          "payload" -> JsString("Hi, my name is Tom")
    //          )
    //        )
    //      )
    logger.info("sending payload:\n" + payload.toJson.prettyPrint)
    for {
      request <- Marshal(payload).to[RequestEntity]
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = s"https://graph.facebook.com/v2.6/me/thread_settings?access_token=$accessToken",
        entity = request))
    } yield ()
  }

}
