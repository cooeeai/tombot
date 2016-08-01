package services

import java.net.URLEncoder

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpMethods, HttpRequest}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import com.google.inject.Inject
import com.typesafe.config.Config
import googlemaps.{MapsJsonSupport, MapsResponse}

import scala.concurrent.Future

/**
  * Created by markmo on 30/07/2016.
  */
class AddressService @Inject()(config: Config, implicit val system: ActorSystem)
  extends MapsJsonSupport {

  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  val url = config.getString("maps.api.url")
  val token = System.getenv("GOOGLE_MAPS_API_TOKEN")

  def getAddress(text: String): Future[MapsResponse] = {
    // TODO
    // ACCESS_DENIED if token sent. works without token.
    for {
      response <- Http().singleRequest(HttpRequest(
        method = HttpMethods.GET,
        //uri = s"$url?address=${URLEncoder.encode(text, "UTF-8")}&key=$token"))
        uri = s"$url?address=${URLEncoder.encode(text, "UTF-8")}"))
      entity <- Unmarshal(response.entity).to[MapsResponse]
    } yield entity
  }

}
