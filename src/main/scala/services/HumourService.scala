package services

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpMethods, HttpRequest}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import apis.jokes.{Joke, JokesJsonSupport}
import com.google.inject.Inject
import com.typesafe.config.Config

import scala.concurrent.Future

/**
  * Created by markmo on 20/08/2016.
  */
class HumourService @Inject()(config: Config,
                              implicit val system: ActorSystem,
                              implicit val fm: Materializer)
  extends JokesJsonSupport {

  import system.dispatcher

  val url = config.getString("tambal.api.url")

  def getJoke: Future[String] = {
    for {
      response <- Http().singleRequest(HttpRequest(
        method = HttpMethods.GET,
        uri = url
      ))
      entity <- Unmarshal(response.entity).to[Joke]
    } yield entity.joke
  }

}
