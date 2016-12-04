package services

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, RequestEntity}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import apis.jira.{Builder, JIRABug, JIRACreatedIssueResponse, JIRAJsonSupport}
import com.google.inject.Inject
import com.typesafe.config.Config

import scala.concurrent.Future

/**
  * Created by markmo on 15/10/2016.
  */
class JIRAService @Inject()(config: Config,
                            implicit val system: ActorSystem,
                            implicit val fm: Materializer
                           )
  extends JIRAJsonSupport {

  import system.dispatcher

  val http = Http()

  val baseURL = config.getString("services.atlassian.jira.url")

  val accessToken = System.getenv("JIRA_ACCESS_TOKEN")

  def createIssue(projectKey: String, summary: String): Future[JIRACreatedIssueResponse] = {
    import Builder._
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    val uri = baseURL + "rest/api/2/issue/"

    val payload = (
      issue
        forProject projectKey
        withSummary summary
        withIssueType JIRABug
        build()
      )

    for {
      request <- Marshal(payload).to[RequestEntity]
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = uri,
        headers = List(authorization),
        entity = request))
      entity <- Unmarshal(response.entity).to[JIRACreatedIssueResponse]
    } yield entity
  }

}
