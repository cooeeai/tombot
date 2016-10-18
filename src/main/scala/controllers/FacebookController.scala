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
import conversationengine.events._
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
                                   alchemyService: AlchemyService)
  extends FacebookJsonSupport with WitJsonSupport {

  import Platform._
  import StatusCodes._
  import conversationService._
  import conversationengine.ConversationEngine._

  implicit val timeout = 30 second

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
      s"""
         |Received authentication event for user $sender and page $recipient
         |with pass-through param '$passThroughParam' at time $timeOfAuth
       """.stripMargin)

    provider.sendTextMessage(sender, "Authentication successful")
  }

  def receivedMessage(data: JsObject, event: JsValue, user: User): Unit = {
    val message = event.extract[JsObject]('message)
    val isEcho = message.extract[Boolean](optionalField("is_echo")).getOrElse(false)
    val quickReply = message.extract[JsObject](optionalField("quick_reply")).getOrElse(JsObject())
    val payload = quickReply.extract[String](optionalField("payload"))
    val messageText = message.extract[String](optionalField("text"))
    val messageAttachments = message.extract[JsArray](optionalField("attachments"))

    if (isEcho) {
      logger.info("received echo message")

    } else if (payload.isDefined) {
      logger.info("received quick reply")
      val response = data.convertTo[FacebookResponse]
      val messagingEvents = response.entry.head.messaging
      val event = messagingEvents.head
      val text = event.message.get.text
      val sender = event.sender.id
      converse(sender, Confirm(Facebook, sender, text))

    } else if (messageAttachments.isDefined) {
      logger.info("received attachments")
      // TODO

    } else if (messageText.isDefined) {
      val response = data.convertTo[FacebookResponse]
      val messagingEvents = response.entry.head.messaging
      for (event <- messagingEvents) {
        val sender = event.sender.id
        if (event.message.isDefined) {
          logger.info("event.message is defined")

          val text = event.message.get.text
          logger.debug("text: [" + text + "]")
          if (text startsWith "/reset") {
            converse(sender, Reset)
          } else if (text startsWith "/engine") {
            logger.debug("switching conversation engine")
            if (text contains "watson") {
              logger.debug("choosing Watson")
              converse(sender, SwitchConversationEngine(sender, Watson))
            } else {
              logger.debug("choosing Cooee")
              converse(sender, SwitchConversationEngine(sender, Cooee))
            }
          } else {
            converse(sender, Respond(Facebook, sender, text))
          }
        }
      }
    }
  }

  def receivedDeliveryConfirmation(event: FacebookMessageDeliveredEvent): Unit = {
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

  def processEvent(data: JsObject, event: JsValue, sender: String, user: User, text: String) = {
    val f = event.asJsObject.fields

    if (f.contains("optin")) {
      logger.info("received authentication event")
      receivedAuthentication(event.convertTo[FacebookAuthenticationEvent])

    } else if (f.contains("message")) {
      logger.info("received message:\n" + event.prettyPrint)
      receivedMessage(data, event, user)

    } else if (f.contains("delivery")) {
      logger.info("received delivery confirmation")
      receivedDeliveryConfirmation(event.convertTo[FacebookMessageDeliveredEvent])

    } else if (f.contains("postback")) {
      logger.info("received postback")
      //facebookService.sendTextMessage(sender, event.postback.get.payload)
      // TODO
      converse(sender, Buy(Facebook, sender, "iphone 6s plus", text))

    } else if (f.contains("read")) {
      logger.info("received message read event")

    } else if (f.contains("account_linking")) {
      logger.info("received account linking event")
      receivedAccountLink(event.convertTo[FacebookAccountLinkingEvent])

    } else {
      logger.error("webhook received unknown messaging event:\n" + event.prettyPrint)
    }
  }

  def setupWelcomeGreeting(): Unit = provider.asInstanceOf[FacebookService].setupWelcomeGreeting()

  val routes =
    path("webhook") {
      get {
        parameters("hub.verify_token", "hub.challenge") { (verifyToken, challenge) =>
          if (verifyToken == "dingdong") {
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
                      val sender = event.extract[String]('sender / 'id)
                      val text = event.extract[JsObject](optionalField("message")) match {
                        case Some(message) =>
                          val messageText = message.extract[String](optionalField("text"))
                          messageText.getOrElse("")
                        case None => ""
                      }
                      if (userService.hasUser(sender)) {
                        val user = userService.getUser(sender).get
                        processEvent(data, event, sender, user, text)
                      } else {
                        provider.getUserProfile(sender) map { resp =>
                          val json = resp.parseJson
                          logger.info("found profile:\n" + json.prettyPrint)
                          val profile = json.convertTo[FacebookUserProfile]
                          val user = User(sender, profile)
                          logger.debug(s"setting user with id [$sender]")
                          userService.setUser(sender, user)
                          processEvent(data, event, sender, user, text)
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
        parameters("redirect_uri", "account_linking_token") { (redirectURI, accountLinkingToken) =>
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
        //formFields('username, 'password, 'redirectURI, 'successURI) { (username, password, redirectURI, successURI) =>
        entity(as[FormData]) { form =>
          val f = form.fields.toMap
          // the following will throw an error if any field is missing
          val username = f("username")
          val password = f("password")
          val sender = f("sender") // account_linking_token
        val redirectURI = f("redirectURI")
          val successURI = f("successURI")
          userService.authenticate(username, password) match {
            case Some(user) =>
              logger.debug("login successful")
              val f = provider.asInstanceOf[FacebookService].getSenderId(sender) map { psid =>
                logger.debug("psid:\n" + psid.toJson.prettyPrint)
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
