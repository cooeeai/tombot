package services

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, RequestEntity}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import apis.apiai.{AaJsonSupport, AaResponse, Builder}
import com.google.inject.Inject
import com.typesafe.config.Config
import spray.json._

import scala.concurrent.Future

/**
  * Created by markmo on 29/11/2016.
  */
class ApiAiService @Inject()(config: Config,
                             logger: LoggingAdapter,
                             implicit val system: ActorSystem,
                             implicit val fm: Materializer)
  extends AaJsonSupport {

  import Builder._
  import system.dispatcher

  val accessToken = System.getenv("APIAI_ACCESS_TOKEN")
  val api = config.getString("services.google.apiai.url")

  def getIntent(text: String): Future[AaResponse] = {
    logger.info("ApiAiService getting intent of [{}]", text)
    val payload = apiAiRequest withQuery text build()
    logger.debug("payload:\n{}", payload.toJson.prettyPrint)
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    for {
      request <- Marshal(payload).to[RequestEntity]
      response <- Http().singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = api,
        headers = List(authorization),
        entity = request))
      entity <- Unmarshal(response.entity).to[String]
    } yield {
      val json = entity.parseJson
      logger.debug("API.ai:\n{}", json.prettyPrint)
      try {
        json.convertTo[AaResponse]
      } catch {
        case e: Throwable =>
          logger.error(e, e.getMessage)
          throw new RuntimeException(e.getMessage, e)
      }
    }
  }

}
