package services

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.stream.Materializer
import apis.telegram.TelegramJsonSupport
import com.google.inject.Inject
import com.typesafe.config.Config
import spray.client.pipelining._
import spray.http.StatusCodes.{ClientError, Success}
import spray.http._
import spray.httpx.unmarshalling._
import spray.json.{DefaultJsonProtocol, JsonFormat}

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
  import TelegramResultJsonSupport._

  val api = config.getString("api.host")

  val accessToken = System.getenv("TELEGRAM_ACCESS_TOKEN")

  val http = Http()

  def setWebhook(): Future[Either[TelegramFailResult, Boolean]] = {
    val pipeline = sendReceive ~> failureAwareUnmarshal[TelegramFailResult, TelegramResult[Boolean]]
    val filename = "telegram.pem"
    val in = getClass.getResourceAsStream(filename)
    val bytes = Stream.continually(in.read()) takeWhile (-1 !=) map (_.toByte) toArray
    val formData = Seq(
      buildParameterBodyPart("url", s"$api/telegram-webhook"),
      buildFileBodyPart(filename, "certificate", bytes)
    )
    pipeline(Post(
      s"https://api.telegram.org/bot$accessToken/setWebhook",
      MultipartFormData(formData)
    )) map {
      case Right(TelegramResult(true, true)) => Right(true)
      case Left(failResult) => Left(failResult)
    }
  }

  case class MarshallingException(message: String) extends Exception

  private def buildFileBodyPart(filename: String, key: String, bytes: Array[Byte]) = {
    val data = HttpData(bytes)
    val httpEntity = HttpEntity(MediaTypes.`multipart/form-data`, data).asInstanceOf[HttpEntity.NonEmpty]
    BodyPart(FormFile(filename, httpEntity), key)
  }

  private def buildParameterBodyPart(key: String, value: String) =
    BodyPart(value, Seq(HttpHeaders.`Content-Disposition`("form-data", Map("name" -> key))))

  private def failureAwareUnmarshal[E: FromResponseUnmarshaller, R: FromResponseUnmarshaller]: HttpResponse => Either[E, R] = { response =>
    response.status match {
      case Success(_) => response.as[R] match {
        case Right(value) => Right(value)
        case Left(err) => throw new MarshallingException(err.toString)
        case err => throw new MarshallingException(err.toString)
      }
      case ClientError(_) => response.as[E] match {
        case Right(value) => Left(value)
        case Left(err) => throw new MarshallingException(err.toString)
        case err => throw new MarshallingException(err.toString)
      }
      case err => throw new MarshallingException(err.toString)
    }
  }
}

case class TelegramFailResult(status: Boolean, code: Int, description: String)

case class TelegramResult[T](status: Boolean, result: T)

object TelegramResultJsonSupport extends DefaultJsonProtocol with spray.httpx.SprayJsonSupport {

  implicit val telegramFailResultJsonFormat = jsonFormat(TelegramFailResult, "ok", "error_code", "description")

  implicit def telegramResultJsonFormat[T: JsonFormat] = jsonFormat(TelegramResult.apply[T], "ok", "result")

}
