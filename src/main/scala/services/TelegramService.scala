package services

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import apis.telegram._
import com.google.inject.Inject
import com.typesafe.config.Config

import scala.concurrent.Future

/**
  * Created by markmo on 10/11/2016.
  */
class TelegramService @Inject()(config: Config,
                                logger: LoggingAdapter,
                                implicit val system: ActorSystem,
                                implicit val fm: Materializer)
  extends TelegramJsonSupport {

  import system.dispatcher

  val api = config.getString("api.host")

  val accessToken = System.getenv("TELEGRAM_ACCESS_TOKEN")

  val http = Http()

  val baseURL = "https://api.telegram.org"

  def setWebhook(): Future[TelegramResult[Boolean]] = {
    logger.debug("setting telegram webhook")
//    val filename = "/telegram.pem"
//    val in = getClass.getResourceAsStream(filename)
//    val bytes = Stream.continually(in.read()) takeWhile (-1 !=) map (_.toByte) toArray
    val formData = Multipart.FormData(
//      Multipart.FormData.BodyPart("certificate",
//        HttpEntity(MediaTypes.`application/octet-stream`, bytes),
//        Map("filename" -> filename)),
      Multipart.FormData.BodyPart("url", HttpEntity(s"$api/telegram-webhook"))
    )
    //logger.debug("formData:\n" + formData)
    for {
      request <- Marshal(formData).to[RequestEntity]
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = s"$baseURL/bot$accessToken/setWebhook",
        entity = request))
      entity <- Unmarshal(response.entity).to[TelegramResult[Boolean]]
    } yield entity
  }

  def getWebhookInfo: Future[TelegramWebhookInfo] = {
    logger.debug("getting webhook info")
    for {
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.GET,
        uri = s"$baseURL/bot$accessToken/getWebhookInfo"))
      entity <- Unmarshal(response.entity).to[TelegramWebhookInfo]
    } yield entity
  }

  def sendMessage(chatId: Int, text: String): Future[TelegramMessage] = {
    val payload = TelegramSendMessage(chatId, text, None, None, None, None, None)
    for {
      request <- Marshal(payload).to[RequestEntity]
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = s"$baseURL/bot$accessToken/sendMessage",
        entity = request))
      entity <- Unmarshal(response.entity).to[TelegramMessage]
    } yield entity
  }
}
