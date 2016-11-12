package services

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import apis.telegram.{TelegramJsonSupport, TelegramResult}
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

  def setWebhook(): Future[TelegramResult[Boolean]] = {
    val filename = "telegram.pem"
    val in = getClass.getResourceAsStream(filename)
    val bytes = Stream.continually(in.read()) takeWhile (-1 !=) map (_.toByte) toArray
    val formData = Multipart.FormData(
      Multipart.FormData.BodyPart("certificate",
        HttpEntity(MediaTypes.`application/octet-stream`, bytes),
        Map("filename" -> filename)),
      Multipart.FormData.BodyPart("url", HttpEntity(s"$api/telegram-webhook"))
    )
    for {
      request <- Marshal(formData).to[RequestEntity]
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = s"https://api.telegram.org/bot$accessToken/setWebhook",
        entity = request))
      entity <- Unmarshal(response.entity).to[TelegramResult[Boolean]]
    } yield entity
  }
}
