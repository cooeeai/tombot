package services

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{FormData, HttpMethods, HttpRequest, RequestEntity}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import apis.skype._
import com.google.inject.Inject
import com.typesafe.config.Config
import memory.Slot

import scala.concurrent.Future

/**
  * Created by markmo on 14/08/2016.
  */
class SkypeService @Inject()(config: Config,
                             logger: LoggingAdapter,
                             implicit val system: ActorSystem,
                             implicit val fm: Materializer)
  extends MessagingProvider with SkypeJsonSupport {

  import system.dispatcher

  val http = Http()

  val api = config.getString("api.host")

  val clientId = System.getenv("MICROSOFT_CLIENT_ID")

  val secret = System.getenv("MICROSOFT_API_SECRET")

  var token: Option[MicrosoftToken] = None

  def sendTextMessage(conversationId: String, text: String): Unit = {
    logger.info(s"sending Skype message [$text] to conversation [$conversationId]")
    import Builder._
    val url = config.getString("microsoft.api.url")
    //logger.debug("token:\n" + token.toJson.prettyPrint)
    val authorization = Authorization(OAuth2BearerToken(token.get.accessToken))

    val payload = messageElement withText text build()

    for {
      request <- Marshal(payload).to[RequestEntity]
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = s"$url/v3/conversations/$conversationId/activities",
        headers = List(authorization),
        entity = request))
    } yield ()
  }

  def sendLoginCard(sender: String, conversationId: String): Unit = {
    logger.info("sending Skype signin request")
    import Builder._
    val url = config.getString("microsoft.api.url")
    val authorization = Authorization(OAuth2BearerToken(token.get.accessToken))

    val payload = (
      loginCard
        usingApi api
        forSender sender
        withText "You need to authorize me"
        withButtonTitle "Connect"
        build()
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

  def sendHeroCard(sender: String): Unit = ???

  def sendReceiptCard(sender: String, slot: Slot): Unit = ???

  def sendQuickReply(sender: String, text: String): Unit = ???

  def getUserProfile(sender: String): Future[String] = ???

  def getMicrosoftToken: Future[MicrosoftToken] = {
    logger.info("getting MS token")
    val url = config.getString("microsoft.api.auth_url")
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

}

object SkypeService extends NamedService {

  override final val name = "SkypeService"

}