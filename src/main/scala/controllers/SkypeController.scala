package controllers

import javax.inject.Singleton

import akka.event.LoggingAdapter
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akkahttptwirl.TwirlSupport._
import apis.skype._
import com.google.inject.Inject
import com.typesafe.config.Config
import conversationengine.events._
import services.{Conversation, SkypeService, UserService}
import spray.json._

import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Created by markmo on 13/08/2016.
  */
@Singleton
class SkypeController @Inject()(config: Config,
                                logger: LoggingAdapter,
                                conversationService: Conversation,
                                skypeService: SkypeService,
                                userService: UserService)
  extends SkypeJsonSupport {

  import Platform._
  import StatusCodes._
  import conversationService._

  var token: Option[MicrosoftToken] = None

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
              logger.debug("token: " + token)
              token match {
                case Some(_) =>
                  converse(conversationId, Respond(Skype, conversationId, userMessage.text))
                case None =>
                  skypeService.getMicrosoftToken map { tk =>
                    logger.debug("tk: " + tk)
                    token = Some(tk)
                    skypeService.token = token
                    skypeService.sendLoginCard(sender, conversationId)
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
          val redirectURI = sender
          val successURI = ""
          val api = config.getString("api.host")
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
