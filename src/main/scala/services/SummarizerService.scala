package services

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, RequestEntity, Uri}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import apis.intellexer.{IlxJsonSupport, IlxSummary}
import com.google.inject.Inject
import com.typesafe.config.Config

import scala.concurrent.Future

/**
  * Created by markmo on 20/12/2016.
  */
class SummarizerService @Inject()(config: Config,
                                  logger: LoggingAdapter,
                                  implicit val system: ActorSystem,
                                  implicit val fm: Materializer)
  extends IlxJsonSupport {

  import system.dispatcher

  val http = Http()

  val baseURL = config.getString("services.intellexer.url")

  val apiKey = System.getenv("INTELLEXER_API_KEY")

  def summarizeText(text: String): Future[IlxSummary] = {
    logger.info("summarizing text")
    val uri = Uri(s"$baseURL/summarizeText").withQuery(Query(
      "apiKey" -> apiKey,
      "summaryRestriction" -> "1",
      "returnedTopicsCount" -> "2",
      "loadConceptsTree" -> "true",
      "loadNamedEntityTree" -> "true",
      "structure" -> "general",
      "wrapConcepts" -> "true"
    ))
    for {
      request <- Marshal(text).to[RequestEntity]
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = uri,
        headers = List(RawHeader("cache-control", "no-cache")),
        entity = request))
      entity <- Unmarshal(response.entity).to[IlxSummary]
    } yield entity
  }

}
