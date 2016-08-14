package services

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import akkahttptwirl.TwirlSupport._
import com.google.inject.Inject
import com.typesafe.config.Config
import apis.skype._
import spray.json._

import scala.concurrent.Future

/**
  * Created by markmo on 13/08/2016.
  */
class SkypeService @Inject()(config: Config,
                             logger: LoggingAdapter,
                             intentService: IntentService,
                             userService: UserService,
                             implicit val system: ActorSystem,
                             implicit val fm: Materializer)
  extends SkypeJsonSupport {

  import StatusCodes._
  import system.dispatcher

  val http = Http()

  val api = config.getString("api.host")

  var token: Option[MicrosoftToken] = None

  def getMicrosoftToken: Future[MicrosoftToken] = {
    logger.info("getting MS token")
    val url = config.getString("microsoft.api.auth_url")
    val clientId = config.getString("microsoft.api.client_id")
    val secret = config.getString("microsoft.api.secret")
    val data = FormData(Map(
      "client_id" -> clientId,
      "client_secret" -> secret,
      "grant_type" -> "client_credentials",
      "scope" -> "https://graph.microsoft.com/.default"
    )).toEntity
    for {
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = url,
        entity = data))
      entity <- Unmarshal(response.entity).to[MicrosoftToken]
    } yield entity
  }

  def sendSkypeMessage(conversationId: String, text: String): Unit = {
    logger.info(s"sending Skype message [$text] to conversation [$conversationId]")
    val url = config.getString("microsoft.api.url")
    //logger.debug("token:\n" + token.toJson.prettyPrint)
    val authorization = Authorization(OAuth2BearerToken(token.get.accessToken))
    val payload = SkypeBotMessage(
      messageType = "message/text",
      text = text,
      attachments = None
    )
    for {
      request <- Marshal(payload).to[RequestEntity]
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = s"$url/v3/conversations/$conversationId/activities",
        headers = List(authorization),
        entity = request))
    } yield ()
  }

  def sendSkypeSigninCard(conversationId: String, sender: String): Unit = {
    logger.info("sending Skype signin request")
    val url = config.getString("microsoft.api.url")
    val authorization = Authorization(OAuth2BearerToken(token.get.accessToken))
    val payload = SigninCard(
      cardType = "message/card.signin",
      attachments = SigninAttachment(
        contentType = "application/vnd.microsoft.card.signin",
        content = SigninAttachmentContent(
          text = "You need to authorize me",
          buttons = SkypeButton(
            buttonType = "signin",
            title = "Connect",
            value = s"$api/skypeauthorize?sender=$sender"
          ) :: Nil
        )
      ) :: Nil
    )
    for {
      request <- Marshal(payload).to[RequestEntity]
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = s"$url/v3/conversations/$conversationId/activities",
        headers = List(authorization),
        entity = request))
    } yield ()
  }

  val routes =
    path("skypewebhook") {
      post {
        logger.info("skypewebhook called")
        entity(as[JsObject]) { data =>
          logger.debug("received body:\n" + data.prettyPrint)
          val fields = data.fields
          fields("type") match {
            case JsString("message") =>
              val userMessage = data.convertTo[SkypeUserMessage]
              val conversationId = userMessage.conversation.id
              val sender = userMessage.from.id
              token match {
                case Some(_) =>
                  sendSkypeSigninCard(conversationId, sender)
                case None =>
                  getMicrosoftToken map { tk =>
                    token = Some(tk)
                    sendSkypeSigninCard(conversationId, sender)
                  }
              }
            case _ => logger.error("invalid content")
          }
          complete(OK)
        }
      }
    } ~
    path("skypeauthorize") {
      get {
        parameters("sender") { sender =>
          logger.info("skypeauthorize get request")
          val api = config.getString("api.host")
          val redirectURI = sender
          val successURI = ""
          complete {
            html.login.render(s"$api/skypeauthenticate", sender, redirectURI, successURI)
          }
        }
      }
    } ~
    path("skypeauthenticate") {
      post {
        logger.info("skypeauthenticate request posted")
        entity(as[FormData]) { form =>
          val f = form.fields.toMap
          // the following will throw an error if any field is missing
          val username = f("username")
          val password = f("password")
          val sender = f("sender")
          userService.authenticate(username, password) match {
            case Some(user) =>
              logger.debug("login successful")
              userService.setUser(sender, user)
              complete(HttpEntity(ContentTypes.`text/html(UTF-8)`,
                "<h1>Login successful</h1><p>Click Close to continue.</p>"))
            case None =>
              logger.debug("login failed")
              complete(HttpEntity(ContentTypes.`text/html(UTF-8)`,
                "<h1>Login failed</h1><p>Click Close to continue.</p>"))
          }
        }
      }
    }

}
