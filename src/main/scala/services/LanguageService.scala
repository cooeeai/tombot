package services

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, RequestEntity}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import apis.googlenlp.{GoogleDocumentSentiment, GoogleEntities, GoogleJsonSupport}
import com.google.inject.Inject
import com.typesafe.config.Config

import scala.concurrent.Future

/**
  * Created by markmo on 17/08/2016.
  */
class LanguageService @Inject()(config: Config,
                                logger: LoggingAdapter,
                                implicit val system: ActorSystem)
  extends GoogleJsonSupport {

  import system.dispatcher

  implicit val materializer = ActorMaterializer()

  val url = config.getString("services.google.language.url")

  val accessToken = System.getenv("GOOGLE_NLP_API_TOKEN")

  def getEntities(content: String): Future[GoogleEntities] = {
    logger.debug("extracting entities from content [{}]", content)
    import apis.googlenlp.Builder._
    val payload = entitiesRequest withContent content build()
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    for {
      request <- Marshal(payload).to[RequestEntity]
      response <- Http().singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = s"$url/v1beta1/documents:analyzeEntities",
        headers = List(authorization),
        entity = request))
      entity <- Unmarshal(response.entity).to[GoogleEntities]
    } yield entity
  }

  def getSentiment(content: String): Future[GoogleDocumentSentiment] = {
    logger.debug("extracting sentiment from content [{}]", content)
    import apis.googlenlp.Builder._
    val payload = sentimentRequest withContent content build()
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    for {
      request <- Marshal(payload).to[RequestEntity]
      response <- Http().singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = s"$url/v1beta1/documents:analyzeSentiment",
        headers = List(authorization),
        entity = request))
      entity <- Unmarshal(response.entity).to[GoogleDocumentSentiment]
    } yield entity
  }

}
