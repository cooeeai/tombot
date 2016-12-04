package services

import java.net.URLEncoder

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{HttpMethods, HttpRequest}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import apis.witapi.{Meaning, WitJsonSupport}
import com.google.inject.Inject
import com.typesafe.config.Config

import scala.concurrent.Future

/**
  * Created by markmo on 13/08/2016.
  */
class IntentService @Inject()(config: Config,
                              logger: LoggingAdapter,
                              implicit val system: ActorSystem,
                              implicit val fm: Materializer)
  extends WitJsonSupport {

  import system.dispatcher

  val accessToken = System.getenv("WIT_AI_API_TOKEN")
  val url = config.getString("services.facebook.wit.url")
  val version = config.getString("services.facebook.wit.version")

  def getIntent(text: String): Future[Meaning] = {
    logger.info("IntentService getting intent of [{}]", text)
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    for {
      response <- Http().singleRequest(HttpRequest(
        method = HttpMethods.GET,
        uri = s"$url/message?v=$version&q=${URLEncoder.encode(text, "UTF-8")}",
        headers = List(authorization)))
      entity <- Unmarshal(response.entity).to[Meaning]
    } yield entity
  }

}
