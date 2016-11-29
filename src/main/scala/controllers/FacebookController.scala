package controllers

import akka.event.LoggingAdapter
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.PathMatchers.Segment
import akkahttptwirl.TwirlSupport._
import apis.facebookmessenger._
import apis.witapi.WitJsonSupport
import com.google.inject.Inject
import com.google.inject.name.Named
import com.typesafe.config.Config
import engines.{LookupBusImpl, MsgEnvelope}
import models.Platform
import models.events.{QuickReplyResponse, TextResponse, Welcome}
import services._
import spray.json._
import spray.json.lenses.JsonLenses._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.concurrent.duration._

/**
  * Created by markmo on 17/07/2016.
  */
class FacebookController @Inject()(config: Config,
                                   logger: LoggingAdapter,
                                   conversationService: Conversation,
                                   @Named(FacebookService.name) provider: MessagingProvider,
                                   userService: UserService,
                                   rulesService: RulesService,
                                   alchemyService: AlchemyService,
                                   bus: LookupBusImpl)
  extends FacebookJsonSupport with WitJsonSupport {

  import Platform._
  import StatusCodes._
  import conversationService._

  implicit val timeout = 30 second

  val verifyToken = System.getenv("FB_VERIFY_TOKEN")

  def receivedAuthentication(event: FacebookAuthenticationEvent): Unit = {
    val sender = event.sender.id
    val recipient = event.recipient.id
    val timeOfAuth = event.timestamp

    // The 'ref' field is set in the 'Send to Messenger' plugin, in the 'data-ref'
    // This can be set to an arbitrary value to associate the authentication
    // callback with the 'Send to Messenger' click event. This can be used to
    // link accounts.
    val passThroughParam = event.optin.ref

    logger.info(
      """
         |Received authentication event for user {} and page {}
         |with pass-through param '{}' at time {}
       """.stripMargin, sender, recipient, passThroughParam, timeOfAuth)

    provider.sendTextMessage(sender, "Authentication successful")
  }

  def receivedMessage(event: JsValue): Unit = {
    val message = event.extract[JsObject]('message)
    val isEcho = message.extract[Boolean](optionalField("is_echo")).getOrElse(false)
    val payload = message.extract[String](optionalField("quick_reply") / optionalField("payload"))
    val messageText = message.extract[String](optionalField("text"))
    val messageAttachments = message.extract[JsArray](optionalField("attachments"))

    if (isEcho) {
      logger.info("received echo message")

    } else if (payload.isDefined) {
      logger.info("received quick reply")
      val ev = event.convertTo[FacebookMessaging]
      val sender = ev.sender.id
      val text = ev.message.get.text
      converse(sender, QuickReplyResponse(Facebook, sender, text))

    } else if (messageAttachments.isDefined) {
      logger.info("received attachments")
      // TODO

    } else if (messageText.isDefined) {
      val ev = event.convertTo[FacebookMessaging]
      val sender = ev.sender.id
      if (ev.message.isDefined) {
        logger.info("event.message is defined")
        val text = ev.message.get.text
        logger.debug("text [{}]", text)
        converse(sender, TextResponse(Facebook, sender, text))
      }
    }
  }

  def receivedPostback(event: JsValue): Unit = {

  }

  def receivedDeliveryConfirmation(event: FacebookMessageDeliveredEvent): Unit = {
    val sender = event.sender.id
    val recipient = event.recipient.id
    val delivery = event.delivery
    val messageIds = delivery.mids
    val watermark = delivery.watermark
    val sequenceNumber = delivery.seq

    messageIds.getOrElse(Nil).foreach { messageId =>
      logger.debug("Received delivery confirmation for message ID: {}", messageId)
    }
    logger.debug("All messages before {} were delivered", watermark)

    bus publish MsgEnvelope(s"delivered:$sender", event)
  }

  def receivedReadConfirmation(event: FacebookMessageReadEvent): Unit = {
    val sender = event.sender.id
    val recipient = event.recipient.id
    val read = event.read
    val watermark = read.watermark
    val sequenceNumber = read.seq

    logger.debug("All messages before {} were read", watermark)

    bus publish MsgEnvelope(s"delivered:$sender", event)
  }

  def receivedAccountLink(event: FacebookAccountLinkingEvent): Unit = {
    val sender = event.sender.id
    val recipient = event.recipient.id
    val status = event.accountLinking.status
    val authCode = event.accountLinking.authorizationCode.get

    converse(sender, Welcome(Facebook, sender))

    // TODO
    // implement a queue using message echoes to advance the queue
    // facebook doesn't guarantee message order - https://developers.facebook.com/bugs/565416400306038
    //facebookService.sendTextMessage(sender, "Welcome, login successful")

    logger.info(
      s"""
         |Received account linking event for user $sender with status $status
         |and auth code $authCode
       """.stripMargin
    )
  }

  def processEvent(event: JsValue) = {
    logger.debug("processing event")
    val f = event.asJsObject.fields

    if (f contains "optin") {
      logger.info("received authentication event")
      receivedAuthentication(event.convertTo[FacebookAuthenticationEvent])

    } else if (f contains "message") {
      logger.info("received message:\n{}", event.prettyPrint)
      receivedMessage(event)

    } else if (f contains "delivery") {
      logger.info("received delivery confirmation")
      receivedDeliveryConfirmation(event.convertTo[FacebookMessageDeliveredEvent])

    } else if (f contains "postback") {
      logger.info("received postback")
      receivedPostback(event)
      //facebookService.sendTextMessage(sender, event.postback.get.payload)
      // TODO
      //      converse(sender, Buy(Facebook, sender, "iphone 6s plus", text))

    } else if (f contains "read") {
      logger.info("received message read event")
      receivedReadConfirmation(event.convertTo[FacebookMessageReadEvent])

    } else if (f contains "account_linking") {
      logger.info("received account linking event")
      receivedAccountLink(event.convertTo[FacebookAccountLinkingEvent])

    } else {
      logger.error("webhook received unknown messaging event:\n{}", event.prettyPrint)
    }
  }

  def setupWelcomeGreeting(): Unit = provider.asInstanceOf[FacebookService].setupWelcomeGreeting()

  val routes =
    path("webhook") {
      get {
        parameters("hub.verify_token", "hub.challenge") {
          case (token, challenge) =>
            if (token == verifyToken) {
              complete(challenge)
            } else {
              complete("Error, invalid token")
            }
        }
      } ~
      post {
        logger.info("Facebook webhook posted")
        entity(as[JsObject]) { data =>
          logger.debug("received body:\n{}", data.prettyPrint)
          val fields = data.fields
          fields("object") match {
            case JsString("page") =>
              // there may be multiple events if batched
              fields("entry") match {
                case JsArray(entry) => entry foreach { messagingEvent =>
                  logger.debug("messagingEvent:\n{}", messagingEvent.prettyPrint)
                  messagingEvent.asJsObject.fields("messaging") match {
                    // iterate over each messaging event
                    case JsArray(messaging) => messaging foreach { event =>
                      val sender = event.extract[String]('sender / 'id)
                      logger.debug("sender [{}]", sender)
                      userService.getUser(sender) match {
                        case Some(_) =>
                          logger.debug("found user")
                          processEvent(event)
                        case None =>
                          logger.debug("lookup user profile")
                          provider.getUserProfile(sender) map { profile =>
                            val user = User(sender, profile)
                            logger.debug("setting user with id [{}]", sender)
                            userService.setUser(sender, user)
                            processEvent(event)
                          }
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
          complete(OK)
        }
      }
    } ~
    path("authorize") {
      get {
        logger.info("authorize get request")
        parameters('redirect_uri, 'account_linking_token) {
          case (redirectURI, accountLinkingToken) =>
            logger.info("received account linking callback")
            // Authorization Code, per user, passed to the Account Linking callback
            val authCode = "1234567890"
            val successURI = s"$redirectURI&authorization_code=$authCode"
            val api = config.getString("api.host")
            complete {
              html.login.render(s"$api/authenticate", accountLinkingToken, redirectURI, successURI)
            }
        }
      }
    } ~
    path("authenticate") {
      post {
        logger.info("authentication request posted")
        // requests that don’t have issues are using HttpEntity.Strict with application/x-www-form-urlencoded
        // see https://github.com/akka/akka/issues/18591
        //formFields('username, 'password, 'accountLinkingToken, 'redirectURI, 'successURI) { (username, password, accountLinkingToken, redirectURI, successURI) =>
        entity(as[FormData]) { form =>
          val f = form.fields.toMap
          // the following will throw an error if any field is missing
          val username = f("username")
          val password = f("password")
          val accountLinkingToken = f("accountLinkingToken")
          val redirectURI = f("redirectURI")
          val successURI = f("successURI")
          userService.authenticate(username, password) match {
            case Some(user) =>
              logger.debug("login successful")
              val f = provider.asInstanceOf[FacebookService].getSenderId(accountLinkingToken) map { psid =>
                logger.debug("psid:\n{}", psid.toJson.prettyPrint)
                userService.setUser(psid.recipient, user)
              }
              Await.result(f, timeout)
              redirect(successURI, Found)
            case None =>
              logger.debug("login failed")
              redirect(redirectURI, Found)
          }
        }
      }
    } ~
    pathPrefix("img") {
      path(Segment) { filename =>
        getFromResource(s"images/$filename")
      }
    } ~
    pathPrefix("vid") {
      path(Segment) { filename =>
        getFromResource(s"videos/$filename")
      }
    } ~
    path("") {
      logger.info("default path")
      complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>Welcome to Tombot</h1>"))
    }

}
