package services

import java.net.URLEncoder

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, RequestEntity}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import apis.wva.{WvaJsonSupport, WvaMessageResponse, WvaStartChatResponse}
import com.google.inject.Inject
import com.typesafe.config.Config
import spray.json._

import scala.concurrent.Future

/**
  * Created by markmo on 17/12/2016.
  */
class WatsonVirtualAgentService @Inject()(config: Config,
                                          logger: LoggingAdapter,
                                          implicit val system: ActorSystem,
                                          implicit val fm: Materializer)
  extends WvaJsonSupport {

  import system.dispatcher

  val clientId = System.getenv("WVA_CLIENT_ID")
  val clientSecret = System.getenv("WVA_CLIENT_SECRET")
  val botId = System.getenv("WVA_BOT_ID")
  val baseURL = config.getString("services.ibm.watson.wva.url")

  def start(): Future[Either[String, WvaStartChatResponse]] = {
    logger.debug("start Watson Virtual Agent chat")
    //logger.debug("bot ID {}", botId)
    //logger.debug("client ID {}", clientId)
    //logger.debug("client Secret {}", clientSecret)
    val payload = JsObject(
      "userID" -> JsNull
    )
    for {
      request <- Marshal(payload).to[RequestEntity]
      response <- Http().singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = s"$baseURL/bots/$botId/dialogs",
        headers = List(
          RawHeader("X-IBM-Client-Id", clientId),
          RawHeader("X-IBM-Client-Secret", clientSecret),
          RawHeader("X-Request-ID", uuid)
        ),
        entity = request))
      entity <- Unmarshal(response.entity).to[String]
    } yield {
      val json = entity.parseJson
      logger.debug(json.prettyPrint)
      try {
        json.convertTo[Either[String, WvaStartChatResponse]]
      } catch {
        case e: Exception =>
          logger.error(e, e.getMessage)
          throw new RuntimeException(e.getMessage, e)
      }
    }
  }

  def send(chatId: String, message: String): Future[Either[String, WvaMessageResponse]] = {
    logger.debug("send message to Watson Virtual Agent")
    val payload = JsObject(
      "message" -> JsString(message),
      "userID" -> JsNull
    )
    val encodedMessage = URLEncoder.encode(message, "UTF-8")
    for {
      request <- Marshal(payload).to[RequestEntity]
      response <- Http().singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = s"$baseURL/bots/$botId/dialogs/$chatId/messages?message=$encodedMessage",
        headers = List(
          RawHeader("X-IBM-Client-Id", clientId),
          RawHeader("X-IBM-Client-Secret", clientSecret),
          RawHeader("X-Request-ID", uuid)
        ),
        entity = request))
      entity <- Unmarshal(response.entity).to[String]
    } yield {
      val json = entity.parseJson
      logger.debug(json.prettyPrint)
      try {
        json.convertTo[Either[String, WvaMessageResponse]]
      } catch {
        case e: Exception =>
          logger.error(e, e.getMessage)
          throw new RuntimeException(e.getMessage, e)
      }
    }
  }


  def uuid = java.util.UUID.randomUUID.toString

}
