import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, RequestEntity, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import spray.json._

import scala.util.Properties

/**
  * Created by markmo on 16/07/2016.
  */
case class Sender(id: String)

case class Recipient(id: String)

case class Message(mid: String, seq: Int, text: String)

case class Postback(payload: String)

case class Messaging(sender: Sender,
                     recipient: Recipient,
                     timestamp: Long,
                     message: Option[Message],
                     postback: Option[Postback])

case class Entry(id: String, time: Long, messaging: List[Messaging])

case class Response(obj: String, entry: List[Entry])

trait JsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val senderJsonFormat = jsonFormat1(Sender)
  implicit val recipientJsonFormat = jsonFormat1(Recipient)
  implicit val messageJsonFormat = jsonFormat3(Message)
  implicit val postbackJsonFormat = jsonFormat1(Postback)
  implicit val messagingJsonFormat = jsonFormat5(Messaging)
  implicit val entryJsonFormat = jsonFormat3(Entry)
  implicit val responseJsonFormat = jsonFormat(Response, "object", "entry")
}

trait Service extends JsonSupport {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  val http = Http()

  val logger: LoggingAdapter

  val token = "EAAW4wYExjKYBAJvdLNWqZAKm7HG3ZCi5S3dfO7zsw6gGuwZCLMJiRqfyOAZCuUQsahlZAnIymyntWLo7YnSq87yAG4j6yoF2ce1RqSFZBhcKOZBlR7Isg1rZApKIZBzHhTEfvI2s3Ec5ohI24yCBZCj95iQ4H6tmWsTtCdt4lUGrakxwZDZD"

  def sendGenericMessage(sender: String): Unit = {
    val messageData = JsObject(
      "attachment" -> JsObject(
        "type" -> JsString("template"),
        "payload" -> JsObject(
          "template_type" -> JsString("generic"),
          "elements" -> JsArray(
            JsObject(
              "title" -> JsString("iPhone 6s 64GB Space Grey"),
              "subtitle" -> JsString("4.7 inch (diagonal) Retina HD display"),
              "image_url" -> JsString("https://cryptic-caverns-85624.herokuapp.com/img/iphone-6s-front-spacegrey-400.jpg"),
              "buttons" -> JsArray(
                JsObject(
                  "type" -> JsString("web_url"),
                  "url" -> JsString("https://www.telstra.com.au/mobile-phones/mobiles-on-a-plan/iphone-6s"),
                  "title" -> JsString("Details")
                ),
                JsObject(
                  "type" -> JsString("postback"),
                  "title" -> JsString("Buy"),
                  "payload" -> JsString("Order for iPhone 6s 64GB Space Grey")
                )
              )
            ),
            JsObject(
              "title" -> JsString("iPhone 6s Plus 64GB Silver"),
              "subtitle" -> JsString("5.5 inch (diagonal) Retina HD display"),
              "image_url" -> JsString("https://cryptic-caverns-85624.herokuapp.com/img/iphone-6s-plus-silver-400.jpg"),
              "buttons" -> JsArray(
                JsObject(
                  "type" -> JsString("postback"),
                  "title" -> JsString("Buy"),
                  "payload" -> JsString("Order for iPhone 6s Plus 64GB Silver")
                )
              )
            )
          )
        )
      )
    )
    val payload = JsObject(
      "recipient" -> JsObject("id" -> JsString(sender)),
      "message" -> messageData
    )
    for {
      request <- Marshal(payload).to[RequestEntity]
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = s"https://graph.facebook.com/v2.6/me/messages?access_token=$token",
        entity = request))
      entity <- Unmarshal(response.entity).to[String]
    } yield ()
  }

  def sendTextMessage(sender: String, text: String): Unit = {
    val messageData = JsObject("text" -> JsString(text))
    val payload = JsObject(
      "recipient" -> JsObject("id" -> JsString(sender)),
      "message" -> messageData
    )
    for {
      request <- Marshal(payload).to[RequestEntity]
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = s"https://graph.facebook.com/v2.6/me/messages?access_token=$token",
        entity = request))
      entity <- Unmarshal(response.entity).to[String]
    } yield ()
  }

  val routes =
    path("img" / RemainingPath) { filename =>
      getFromResource(s"images/$filename")
    } ~
    path("webhook") {
      get {
        parameters("hub.verify_token", "hub.challenge") { (token, challenge) =>
          if (token == "dingdong") {
            complete(challenge)
          } else {
            complete("Error, invalid token")
          }
        }
      } ~
      post {
        entity(as[Response]) { response =>
          val messagingEvents = response.entry.head.messaging
          for (event <- messagingEvents) {
            val sender = event.sender.id
            if (event.message.isDefined) {
              val text = event.message.get.text
              if (text == "/buy") {
                sendGenericMessage(sender)
              } else {
                sendTextMessage(sender, "echo: " + text)
              }
            } else if (event.postback.isDefined) {
              sendTextMessage(sender, event.postback.get.payload)
            }
          }
          complete(StatusCodes.OK)
        }
      }
    }

}

object Main extends App with Service {

  override val logger = Logging(system, getClass)

  val port = Properties.envOrElse("PORT", "8080").toInt

  val bindingFuture = http.bindAndHandle(routes, "0.0.0.0", port)

//  println("Server online at http://localhost:8080/\nPress RETURN to stop...")
//  StdIn.readLine() // let it run until user presses return
//  bindingFuture
//    .flatMap(_.unbind()) // trigger unbinding from the port
//    .onComplete(_ => system.terminate()) // and shutdown when done
}
