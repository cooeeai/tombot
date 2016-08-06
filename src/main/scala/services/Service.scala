package services

import java.net.URLEncoder

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.PathMatchers.Segment
import akka.http.scaladsl.server.{MalformedRequestContentRejection, RejectionHandler}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akkahttptwirl.TwirlSupport._
import akka.stream.ActorMaterializer
import akkaguice.{AkkaModule, GuiceAkkaExtension}
import com.google.inject.Guice
import com.typesafe.config.Config
import config.ConfigModule
import facebookmessenger._
import fsm.ConversationActor.{Buy, Greet, Qualify, Respond}
import fsm.{ConversationActor, ConversationModule}
import net.codingwell.scalaguice.InjectorExtensions._
import spray.json._
import spray.json.lenses.JsonLenses._
import witapi.{Meaning, WitJsonSupport}

import scala.concurrent.Future

/**
  * Created by markmo on 17/07/2016.
  */
trait Service extends FbJsonSupport with WitJsonSupport {

  val injector = Guice.createInjector(
    new ConfigModule(),
    new AkkaModule(),
    new ConversationModule()
  )

  implicit val system = injector.instance[ActorSystem]
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  val http = Http()

  def config: Config

  val logger: LoggingAdapter

  def catalogService = new CatalogService(config)

  def paymentService = new PaymentService(config)

  def fbApiToken = System.getenv("FB_PAGE_ACCESS_TOKEN")

  def witApiToken = System.getenv("WIT_AI_API_TOKEN")

  val fsm = system.actorOf(GuiceAkkaExtension(system).props(ConversationActor.name))

  def sendTextMessage(sender: String, text: String): Unit = {
    logger.info("sending text message: [" + text + "] to sender: " + sender)
    val messageData = JsObject("text" -> JsString(text))
    val payload = JsObject(
      "recipient" -> JsObject("id" -> JsString(sender)),
      "message" -> messageData
    )
    logger.debug("sending payload:\n" + payload.toJson.prettyPrint)
    for {
      request <- Marshal(payload).to[RequestEntity]
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = s"https://graph.facebook.com/v2.6/me/messages?access_token=$fbApiToken",
        entity = request))
      entity <- Unmarshal(response.entity).to[String]
    } yield ()
  }

  def getUserProfile(userId: String): Future[UserProfile] = {
    logger.info("getting user profile")
    for {
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.GET,
        uri = s"https://graph.facebook.com/v2.6/me/$userId?fields=first_name,last_name,profile_pic,locale,timezone,gender&access_token=$fbApiToken"))
      entity <- Unmarshal(response.entity).to[UserProfile]
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
        uri = s"https://graph.facebook.com/v2.6/me/thread_settings?access_token=$fbApiToken",
        entity = request))
      entity <- Unmarshal(response.entity).to[String]
    } yield ()
  }

  def receivedAuthentication(event: AuthenticationEvent): Unit = {
    val sender = event.sender.id
    val recipient = event.recipient.id
    val timeOfAuth = event.timestamp

    // The 'ref' field is set in the 'Send to Messenger' plugin, in the 'data-ref'
    // This can be set to an arbitrary value to associate the authentication
    // callback with the 'Send to Messenger' click event. This can be used to
    // link accounts.
    val passThroughParam = event.optin.ref

    logger.info(
      s"""
         |Received authentication event for user $sender and page $recipient
         |with pass-through param '$passThroughParam' at time $timeOfAuth
       """.stripMargin)

    sendTextMessage(sender, "Authentication successful")
  }

  def receivedDeliveryConfirmation(event: MessageDeliveredEvent): Unit = {
    val sender = event.sender.id
    val recipient = event.recipient.id
    val delivery = event.delivery
    val messageIds = delivery.mids
    val watermark = delivery.watermark
    val sequenceNumber = delivery.seq

    messageIds.foreach { messageId =>
      logger.info("Received delivery confirmation for message ID: " + messageId)
    }
    logger.info(s"All messages before $watermark were delivered")
  }

  def receivedAccountLink(event: AccountLinkingEvent): Unit = {
    val sender = event.sender.id
    val recipient = event.recipient.id
    val status = event.accountLinking.status
    val authCode = event.accountLinking.authorizationCode

    logger.info(
      s"""
         |Received account linking event for user $sender with status $status
         |and auth code $authCode
       """.stripMargin
    )
  }

  def receivedMessage(data: JsObject, event: JsValue): Unit = {
    val message = event.extract[JsObject]('message)
    val isEcho = message.extract[Boolean](optionalField("is_echo")).getOrElse(false)
    val quickReply = message.extract[Boolean](optionalField("quick_reply")).getOrElse(false)
    val messageText = message.extract[String](optionalField("text"))
    val messageAttachments = message.extract[JsArray](optionalField("attachments"))
    if (isEcho) {
      logger.info("received echo message")
    } else if (quickReply) {
      logger.info("received quick reply")
    } else if (messageAttachments.isDefined) {
      logger.info("received attachments")
    } else if (messageText.isDefined) {
      val response = data.convertTo[Response]
      val messagingEvents = response.entry.head.messaging
      for (event <- messagingEvents) {
        val sender = event.sender.id
        if (event.message.isDefined) {
          logger.info("event.message is defined")
          val text = event.message.get.text
          logger.debug("text: [" + text + "]")
          getIntent(text) map { meaning =>
            logger.debug("received meaning:\n" + meaning.toJson.prettyPrint)
            // TODO
            // how can we bypass this when not needed
            val intent = meaning.getIntent
            intent match {
              case Some("buy") => fsm ! Qualify(sender, meaning.getEntityValue("product_type"))
              case Some("greet") => fsm ! Greet(sender)
              case _ => fsm ! Respond(sender, text)
            }
          }
        }
      }
    }
  }

  def getIntent(text: String): Future[Meaning] = {
    logger.info("getting intent of [" + text + "]")
    val url = config.getString("wit.api.url")
    val version = config.getString("wit.api.version")
    val authorization = Authorization(OAuth2BearerToken(witApiToken))
    for {
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.GET,
        uri = s"$url/message?v=$version&q=${URLEncoder.encode(text, "UTF-8")}",
        headers = List(authorization)))
      entity <- Unmarshal(response.entity).to[Meaning]
    } yield entity
  }

  import StatusCodes._

  implicit def myRejectionHandler =
    RejectionHandler.newBuilder().handle {
      case MalformedRequestContentRejection(message, e) =>
        logger.error(message)
        extractRequest { request =>
          logger.error(request._4.toString)
          complete(BadRequest)
        }
      case e =>
        logger.error(e.toString)
        complete(BadRequest)
    }
      .result()

  val routes =
    path("webhook") {
      get {
        parameters("hub.verify_token", "hub.challenge") { (token, challenge) =>
          if (token == "dingdong") {
            complete(challenge)
          } else {
            complete("Error, invalid token")
          }
        }
      } ~
      post {
        logger.info("webhook posted")
        entity(as[JsObject]) { data =>
          logger.debug("received body:\n" + data.prettyPrint)
          val fields = data.fields
          fields("object") match {
            case JsString("page") =>
              // there may be multiple events if batched
              fields("entry") match {
                case JsArray(entry) => entry foreach { messagingEvent =>
                  logger.debug("messagingEvent:\n" + messagingEvent.prettyPrint)
                  messagingEvent.asJsObject.fields("messaging") match {
                    // Iterate over each messaging event
                    case JsArray(messaging) => messaging foreach { event =>
                      val f = event.asJsObject.fields
                      if (f.contains("optin")) {
                        logger.info("received authentication event")
                        receivedAuthentication(event.convertTo[AuthenticationEvent])
                      } else if (f.contains("message")) {
                        logger.info("received message:\n" + event.prettyPrint)
                        receivedMessage(data, event)
                      } else if (f.contains("delivery")) {
                        logger.info("received delivery confirmation")
                        receivedDeliveryConfirmation(event.convertTo[MessageDeliveredEvent])
                      } else if (f.contains("postback")) {
                        logger.info("received postback")
                        //sendTextMessage(sender, event.postback.get.payload)
                        val sender = event.extract[String]('sender / 'id)
                        fsm ! Buy(sender, "iphone 6s plus")
                      } else if (f.contains("read")) {
                        logger.info("received message read event")
                      } else if (f.contains("account_linking")) {
                        logger.info("received account link")
                        receivedAccountLink(event.convertTo[AccountLinkingEvent])
                      } else {
                        logger.error("webhook received unknown messaging event:\n" + event.prettyPrint)
                      }
                    }
                    case _ => logger.error("invalid content")
                  }
                }
                case _ => logger.error("invalid content")
              }
            case _ => logger.error("invalid content")
          }
          // a 200 status code must be sent back within 20 seconds,
          // otherwise the request will timeout on the Facebook end
          complete(StatusCodes.OK)
        }
      }
    } ~
    path("authorize") {
      get {
        parameters("account_linking_token", "redirect_uri") { (token, redirectURI) =>
          // Authorization Code, per user, passed to the Account Linking callback
          val authCode = "1234567890"
          val successURI = s"$redirectURI&authorization_code=$authCode"
          val api = config.getString("api.host")
          complete {
            html.login.render(s"$api/authenticate", redirectURI, successURI)
          }
        }
      }
    } ~
    pathPrefix("img") {
      path(Segment) { filename =>
        getFromResource(s"images/$filename")
      }
    } ~
    path("") {
      logger.info("default path")
      complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>Welcome to Tombot</h1>"))
    }

}
