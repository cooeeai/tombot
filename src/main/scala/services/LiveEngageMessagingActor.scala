package services

import akka.actor.{Actor, ActorLogging}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.{Message, TextMessage, WebSocketRequest}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.{Done, NotUsed}
import apis.liveengage.LpService
import com.google.inject.Inject
import com.typesafe.config.Config
import modules.akkaguice.NamedActor

import scala.concurrent.Future

/**
  * Created by markmo on 26/11/2016.
  */
class LiveEngageMessagingActor @Inject()(config: Config, leService: LiveEngageService)
  extends Actor with ActorLogging {

  implicit val system = context.system
  implicit val materializer = ActorMaterializer()

  import context.dispatcher

  val brandId = System.getenv("LP_BRAND_ID")
  val username = System.getenv("LP_USERNAME")
  val password = System.getenv("LP_PASSWORD")
  val domain = config.getString("liveengage.prod-domain")

  val http = Http()

  val printSink: Sink[Message, Future[Done]] =
    Sink foreach {
      case message: TextMessage.Strict =>
        log.debug("message: {}", message.text)
      case _ =>
        log.debug("!!!")
    }

  val helloSource: Source[Message, NotUsed] =
    Source.single(TextMessage("hello world!"))

  val flow: Flow[Message, Message, NotUsed] =
    Flow.fromSinkAndSource(printSink, helloSource)

  for {
    services <- leService.getServices(domain, Set("adminArea", "liveEngage", "asyncMessagingEnt"))
    brandServices = getBrandServices(services)
    adminURL = brandServices("adminArea")
    liveEngageURL = brandServices("liveEngage")
    messagingURL = brandServices("asyncMessagingEnt")
    otk <- leService.getOTK(adminURL)
    token <- leService.getMessagingToken(liveEngageURL, otk)
  } yield {
    log.debug("messagingURL: {}", messagingURL)
    //log.debug("token [{}]", token)

    val (upgradeResponse, closed) =
      http.singleWebSocketRequest(WebSocketRequest(s"$messagingURL/ws_api/account/$brandId/messaging/brand/$token?v=2"), flow)

    val connected = upgradeResponse map { upgrade =>
      if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
        Done
      } else {
        throw new RuntimeException(s"Connection failed: ${upgrade.response.status}")
      }
    }
  }

  def receive = {
    case _ =>
  }

  def getBrandServices(services: List[LpService]): Map[String, String] = services map {
    case LpService(_, baseURI, "adminArea") =>
      ("adminArea", s"https://$baseURI/hc/s-$brandId/web/m-LP/mlogin/home.jsp")
    case LpService(_, baseURI, "liveEngage") =>
      ("liveEngage", s"https://$baseURI/le/account/$brandId/session")
    case LpService(_, baseURI, "asyncMessagingEnt") =>
      ("asyncMessagingEnt", s"wss://$baseURI")
  } toMap

}

object LiveEngageMessagingActor extends NamedActor {
  override final val name = "LiveEngageMessagingActor"
}
