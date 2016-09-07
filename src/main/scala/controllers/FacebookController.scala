package controllers

import akka.event.LoggingAdapter
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.PathMatchers.Segment
import akkahttptwirl.TwirlSupport._
import apis.facebookmessenger._
import apis.witapi.WitJsonSupport
import com.google.inject.Inject
import com.typesafe.config.Config
import conversationengine.ConversationActor._
import services._
import spray.json._
import spray.json.lenses.JsonLenses._

import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Created by markmo on 17/07/2016.
  */
class FacebookController @Inject()(config: Config,
                                   logger: LoggingAdapter,
                                   conversationService: ConversationService,
                                   intentService: IntentService,
                                   facebookService: FacebookService,
                                   userService: UserService,
                                   rulesService: RulesService,
                                   alchemyService: AlchemyService)
  extends FacebookJsonSupport with WitJsonSupport {

  import StatusCodes._
  import conversationService._

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

    facebookService.sendTextMessage(sender, "Authentication successful")
  }

  def receivedMessage(data: JsObject, event: JsValue, user: User): Unit = {
    val message = event.extract[JsObject]('message)
    val isEcho = message.extract[Boolean](optionalField("is_echo")).getOrElse(false)
    val quickReply = message.extract[JsObject](optionalField("quick_reply")).getOrElse(JsObject())
    val payload = quickReply.extract[String](optionalField("payload")).getOrElse("none")
    val messageText = message.extract[String](optionalField("text"))
    val messageAttachments = message.extract[JsArray](optionalField("attachments"))
    if (isEcho) {
      logger.info("received echo message")
    } else if (payload != "none") {
      logger.info("received quick reply")
      val response = data.convertTo[FacebookResponse]
      val messagingEvents = response.entry.head.messaging
      val event = messagingEvents.head
      val text = event.message.get.text
      if (text != "No") {
        val sender = userService.getUserIdOrElse(event.sender.id)
        converse(sender, Respond("facebook", sender, "Visa 1234"))
      }
    } else if (messageAttachments.isDefined) {
      logger.info("received attachments")
    } else if (messageText.isDefined) {
      val response = data.convertTo[FacebookResponse]
      val messagingEvents = response.entry.head.messaging
      for (event <- messagingEvents) {
        val sender = userService.getUserIdOrElse(event.sender.id)
        if (event.message.isDefined) {
          logger.info("event.message is defined")
          val text = event.message.get.text
          logger.debug("text: [" + text + "]")
          if (text.startsWith("/alchemy")) {
            facebookService.sendTextMessage(sender, "Keywords:\n" + formatKeywords(alchemyService.getKeywords(text.substring(8).trim)))
          } else {
            if (rulesService.isQuestion(text)) {
              rulesService.getContent(text) match {
                case Some(content) =>
                  logger.debug("found content")
                  facebookService.sendTextMessage(sender, content)
                case None =>
                  parseIntent(sender, text, user)
              }
            } else {
              parseIntent(sender, text, user)
            }
          }
        }
      }
    }
  }

  private def formatKeywords(keywords: Map[String, Double]) = {
    keywords map {
      case (keyword, relevance) => f"$keyword ($relevance%2.2f)"
    } mkString "\n"
  }

  private def parseIntent(sender: String, text: String, user: User) = {
    intentService.getIntent(text) map { meaning =>
      logger.debug("received meaning:\n" + meaning.toJson.prettyPrint)
      // TODO
      // how can we bypass this when not needed
      val intent = meaning.getIntent
      intent match {
        case Some("buy") => converse(sender, Qualify("facebook", sender, meaning.getEntityValue("product_type")))
        case Some("greet") => converse(sender, Greet("facebook", sender, user))
        case Some("analyze") => converse(sender, Analyze("facebook", sender, text))
        case Some("bill-enquiry") => converse(sender, BillEnquiry("facebook", sender))
        //case _ => converse(sender, Analyze("facebook", sender, text))
        case _ => converse(sender, Respond("facebook", sender, text))
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

    converse(sender, Welcome("facebook", sender))
    facebookService.sendTextMessage(sender, "Welcome, login successful")
    converse(sender, PostAuth(sender))

    logger.info(
      s"""
         |Received account linking event for user $sender with status $status
         |and auth code $authCode
       """.stripMargin
    )
  }

  def processEvent(data: JsObject, event: JsValue, sender: String, user: User) = {
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
      converse(sender, Buy("facebook", sender, "iphone 6s plus"))
    } else if (f.contains("read")) {
      logger.info("received message read event")
    } else if (f.contains("account_linking")) {
      logger.info("received account linking event")
      receivedAccountLink(event.convertTo[FacebookAccountLinkingEvent])
    } else {
      logger.error("webhook received unknown messaging event:\n" + event.prettyPrint)
    }
  }

  def setupWelcomeGreeting(): Unit = facebookService.setupWelcomeGreeting()

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
                        facebookService.getUserProfile(sender) map { resp =>
                          val json = resp.parseJson
                          logger.info("found profile:\n" + json.prettyPrint)
                          val profile = json.convertTo[FacebookUserProfile]
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
              facebookService.getSenderId(sender) map { psid =>
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
