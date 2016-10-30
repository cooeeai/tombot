package services

import akka.actor.{Actor, ActorLogging}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, RequestEntity}
import akka.stream.ActorMaterializer
import apis.facebookmessenger.{Builder, FacebookJsonSupport}
import conversationengine.events.Respond
import modules.akkaguice.NamedActor

import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Created by markmo on 27/10/2016.
  */
class FacebookMessageQueue extends Actor with ActorLogging with FacebookJsonSupport {

  val accessToken = System.getenv("FB_PAGE_ACCESS_TOKEN")

  implicit val system = context.system
  implicit val materializer = ActorMaterializer()

  override def receive = {
    case Respond(_, sender, text) =>
      log.info(s"sending text message: [$text] to sender [$sender]")
      import Builder._

      val payload = messageElement forSender sender withText text build()

      log.debug("sending payload:\n" + payload.toJson.prettyPrint)
      for {
        request <- Marshal(payload).to[RequestEntity]
        response <- Http().singleRequest(HttpRequest(
          method = HttpMethods.POST,
          uri = s"https://graph.facebook.com/v2.8/me/messages?access_token=$accessToken",
          entity = request))
      } yield ()
  }

}

object FacebookMessageQueue extends NamedActor {

  override final val name = "FacebookMessageQueue"

}
