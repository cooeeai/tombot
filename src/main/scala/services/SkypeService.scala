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
class SkypeService @Inject()(config: Config,
                             logger: LoggingAdapter,
                             implicit val system: ActorSystem,
                             implicit val fm: Materializer)
  extends MessagingProvider with SkypeJsonSupport {

  import system.dispatcher

  val http = Http()

  val api = config.getString("api.host")

  val skypeApi = config.getString("services.microsoft.skype.url")
  val skypeAuthURL = config.getString("services.microsoft.skype.auth-url")

  val clientId = System.getenv("MICROSOFT_CLIENT_ID")

  val secret = System.getenv("MICROSOFT_API_SECRET")

  var token: Option[MicrosoftToken] = None

  val postbackURL = ""

  def sendTextMessage(conversationId: String, text: String): Future[SendResponse] = {
    logger.info("sending Skype message [{}] to conversation [{}]", text, conversationId)
    import Builder._
    val authorization = Authorization(OAuth2BearerToken(token.get.accessToken))

    val payload = messageElement withText text build()

    for {
      request <- Marshal(payload).to[RequestEntity]
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = s"$skypeApi/v3/conversations/$conversationId/activities",
        headers = List(authorization),
        entity = request))
    } yield SendResponse("")
  }

  def sendLoginCard(sender: String, conversationId: String): Future[SendResponse] = {
    logger.info("sending Skype signin request to sender [{}] using conversationId [{}]", sender, conversationId)
    import Builder._
    val authorization = Authorization(OAuth2BearerToken(token.get.accessToken))

    val payload = (
      loginCard
        usingApi api
        forSender sender
        withText "You need to authorize me"
        withButtonTitle "Connect"
        build()
      )

    logger.debug("sending payload:\n{}", payload.toJson.prettyPrint)

    for {
      request <- Marshal(payload).to[RequestEntity]
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = s"$skypeApi/v3/conversations/$conversationId/activities",
        headers = List(authorization),
        entity = request))
    } yield SendResponse("")
  }

  def sendHeroCard(conversationId: String, items: List[Item]): Future[SendResponse] = {
    logger.info("sending Skype hero card")
    import Builder._
    val authorization = Authorization(OAuth2BearerToken(token.get.accessToken))
    val attachments = itemsToSkypeHeroAttachments(items)

    val payload = (
      carouselHeroCard
        withAttachments attachments
        build()
      )

    for {
      request <- Marshal(payload).to[RequestEntity]
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = s"$skypeApi/v3/conversations/$conversationId/activities",
        headers = List(authorization),
        entity = request))
    } yield SendResponse("")
  }

  def sendReceiptCard(conversationId: String, slot: Slot): Future[SendResponse] = {
    logger.info("sending Skype receipt card")
    import Builder._
    val authorization = Authorization(OAuth2BearerToken(token.get.accessToken))
    val receiptId = "order" + Math.floor(Math.random() * 1000)

    val payload = (
      receiptCard
        withTitle receiptId
        build()
      )

    for {
      request <- Marshal(payload).to[RequestEntity]
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = s"$skypeApi/v3/conversations/$conversationId/activities",
        headers = List(authorization),
        entity = request))
    } yield SendResponse("")
  }

  def sendQuickReply(conversationId: String, text: String): Future[SendResponse] = {
    logger.info("sending Skype quick reply using conversationId [{}]", conversationId)
    import Builder._
    val authorization = Authorization(OAuth2BearerToken(token.get.accessToken))

    val payload = (
      heroCard
        withTitle text
        addButton SkypePostbackButton("Yes", "yes")
        addButton SkypePostbackButton("No", "no")
        build()
      )

    logger.debug("sending payload:\n{}", payload.toJson.prettyPrint)

    for {
      request <- Marshal(payload).to[RequestEntity]
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = s"$skypeApi/v3/conversations/$conversationId/activities",
        headers = List(authorization),
        entity = request))
    } yield SendResponse("")
  }

  def getUserProfile(sender: String): Future[UserProfile] = ???

  def getMicrosoftToken: Future[MicrosoftToken] = {
    logger.info("getting MS token")

    val data = FormData(Map(
      "client_id" -> clientId,
      "client_secret" -> secret,
      "grant_type" -> "client_credentials",
      "scope" -> "https://graph.microsoft.com/.default"
    )).toEntity

    for {
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = skypeAuthURL,
        entity = data))
      entity <- Unmarshal(response.entity).to[MicrosoftToken]
    } yield entity
  }

  private def itemsToSkypeHeroAttachments(items: List[Item]): List[SkypeHeroAttachment] = {
    import Builder._
    items map { item =>
      val buttons = item.actions map {
        case ItemLinkAction(title, url) => SkypeLinkButton(title, url)
        case ItemPostbackAction(title, payload) => SkypePostbackButton(title, payload.toString)
      }
      (
        heroAttachment
          withTitle item.title
          withSubtitle item.subtitle
          addImage item.imageURL
          withButtons buttons
          build()
        )
    }
  }

}

object SkypeService extends NamedService {

  override final val name = "SkypeService"

}