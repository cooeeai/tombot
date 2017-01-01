package services

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials}
import akka.http.scaladsl.model.{FormData, HttpMethods, HttpRequest, StatusCodes}
import akka.stream.Materializer
import com.google.inject.Inject
import com.typesafe.config.Config
import memory.Slot
import models.{UserProfile, Item}

import scala.concurrent.Future
import scala.concurrent.duration._

/**
  * Created by markmo on 20/12/2016.
  */
class EmailService @Inject()(config: Config,
                             logger: LoggingAdapter,
                             implicit val system: ActorSystem,
                             implicit val fm: Materializer)
  extends MessagingProvider {

  import system.dispatcher

  val baseURL = config.getString("services.mailgun.url")

  val domain = System.getenv("MAILGUN_DOMAIN")
  val apiKey = System.getenv("MAILGUN_API_KEY")

  val http = Http()

  def sendTextMessage(sender: String, text: String): Future[SendResponse] = {
    logger.info("EmailService sending text message [{}]", text)
    val authorization = Authorization(BasicHttpCredentials("api", apiKey))
    val data = FormData(Map(
      "from" -> s"Tombot <$domain>",
      "to" -> sender,
      "subject" -> "Hello",
      "text" -> text
    )).toEntity
    for {
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = s"$baseURL/$domain/messages",
        headers = List(authorization),
        entity = data))
    } yield {
      response.status match {
        case StatusCodes.Found =>
          for {
            body <- response.entity.toStrict(5 seconds).map(_.data.decodeString("UTF-8"))
          } yield {
            logger.debug(body)
          }
        case _ =>
          logger.error("returned status {}", response.status.value)
      }
      SendResponse(response.status.value)
    }

  }

  def sendLoginCard(sender: String, conversationId: String = ""): Future[SendResponse] = ???

  def sendHeroCard(sender: String, items: List[Item]): Future[SendResponse] = ???

  def sendReceiptCard(sender: String, slot: Slot): Future[SendResponse] = ???

  def sendQuickReply(sender: String, text: String): Future[SendResponse] = ???

  def getUserProfile(sender: String): Future[UserProfile] = ???

}
