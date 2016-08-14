package services

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.PathMatchers.Segment
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import akkahttptwirl.TwirlSupport._
import com.google.inject.Inject
import com.typesafe.config.Config
import apis.facebookmessenger._
import conversationengine.ConversationActor._
import spray.json._
import spray.json.lenses.JsonLenses._

import scala.concurrent._

/**
  * Created by markmo on 17/07/2016.
  */
class FacebookService @Inject()(config: Config,
                                logger: LoggingAdapter,
                                intentService: IntentService,
                                conversationService: ConversationService,
                                userService: UserService,
                                implicit val system: ActorSystem,
                                implicit val fm: Materializer)
  extends FacebookJsonSupport {

  import StatusCodes._
  import conversationService._
  import intentService._
  import system.dispatcher

  val http = Http()

  val api = config.getString("api.host")

  val accessToken = System.getenv("FB_PAGE_ACCESS_TOKEN")

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
        uri = s"https://graph.facebook.com/v2.6/me/messages?access_token=$accessToken",
        entity = request))
      entity <- Unmarshal(response.entity).to[String]
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

  def getSenderId(accountLinkingToken: String): Future[UserPsid] = {
    logger.info("getting sender id")
    for {
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.GET,
        uri = s"https://graph.facebook.com/v2.6/me?access_token=$accessToken&fields=recipient&account_linking_token=$accountLinkingToken"))
      entity <- Unmarshal(response.entity).to[UserPsid]
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
    val authCode = event.accountLinking.authorizationCode.get

    converse(sender, Welcome(sender))

    logger.info(
      s"""
         |Received account linking event for user $sender with status $status
         |and auth code $authCode
       """.stripMargin
    )
  }

  def receivedMessage(data: JsObject, event: JsValue, user: User): Unit = {
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
        val sender = userService.getUserIdOrElse(event.sender.id)
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
              case Some("buy") => converse(sender, Qualify(sender, meaning.getEntityValue("product_type")))
              case Some("greet") => converse(sender, Greet(sender, user))
              case _ => converse(sender, Respond(sender, text))
            }
          }
        }
      }
    }
  }

  def processEvent(data: JsObject, event: JsValue, sender: String, user: User) = {
    val f = event.asJsObject.fields
    if (f.contains("optin")) {
      logger.info("received authentication event")
      receivedAuthentication(event.convertTo[AuthenticationEvent])
    } else if (f.contains("message")) {
      logger.info("received message:\n" + event.prettyPrint)
      receivedMessage(data, event, user)
    } else if (f.contains("delivery")) {
      logger.info("received delivery confirmation")
      receivedDeliveryConfirmation(event.convertTo[MessageDeliveredEvent])
    } else if (f.contains("postback")) {
      logger.info("received postback")
      //sendTextMessage(sender, event.postback.get.payload)
      converse(sender, Buy(sender, "iphone 6s plus"))
    } else if (f.contains("read")) {
      logger.info("received message read event")
    } else if (f.contains("account_linking")) {
      logger.info("received account linking event")
      receivedAccountLink(event.convertTo[AccountLinkingEvent])
    } else {
      logger.error("webhook received unknown messaging event:\n" + event.prettyPrint)
    }
  }

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
                      if (userService.hasUser(sender)) {
                        val user = userService.getUser(sender).get
                        processEvent(data, event, sender, user)
                      } else {
                        getUserProfile(sender) map { resp =>
                          val json = resp.parseJson
                          logger.info("found profile:\n" + json.prettyPrint)
                          val profile = json.convertTo[UserProfile]
                          val user = User(sender, profile)
                          userService.setUser(sender, user)
                          processEvent(data, event, sender, user)
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
              getSenderId(sender) map { psid =>
                userService.setUser(psid.recipient, user)
              }
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
    path("") {
      logger.info("default path")
      complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>Welcome to Tombot</h1>"))
    }

}
