import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.PathMatchers.Segment
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import com.typesafe.config.Config
import facebookmessengerapi._
import spray.json._

/**
  * Created by markmo on 17/07/2016.
  */
trait Service extends JsonSupport {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  val http = Http()

  def config: Config
  val logger: LoggingAdapter

  def catalogService = new CatalogService(config)

  def token = System.getenv("FB_PAGE_ACCESS_TOKEN")

  def sendGenericMessage(sender: String): Unit = {
    logger.info("sending generic message to sender: " + sender)
    val elements = catalogService.getElements
    val payload =
      GenericMessagePayload(
        Recipient(sender),
        GenericMessage(
          Attachment(
            attachmentType = "template",
            payload = AttachmentPayload(templateType = "generic", elements = elements)
          )
        )
      )
    logger.info("sending payload:\n" + payload.toJson.prettyPrint)
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
    logger.info("sending text message: [" + text + "] to sender: " + sender)
    val messageData = JsObject("text" -> JsString(text))
    val payload = JsObject(
      "recipient" -> JsObject("id" -> JsString(sender)),
      "message" -> messageData
    )
    logger.info("sending payload:\n" + payload.prettyPrint)
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
          logger.info("received body:\n" + response.toJson.prettyPrint)
          val messagingEvents = response.entry.head.messaging
          for (event <- messagingEvents) {
            val sender = event.sender.id
            if (event.message.isDefined) {
              logger.info("event.message is defined")
              val text = event.message.get.text
              logger.info("text: [" + text + "]")
              if (text == "/buy") {
                sendGenericMessage(sender)
              } else {
                sendTextMessage(sender, "echo: " + text)
              }
            } else if (event.postback.isDefined) {
              logger.info("event.postback is defined")
              sendTextMessage(sender, event.postback.get.payload)
            }
          }
          complete(StatusCodes.OK)
        }
      }
    } ~
    pathPrefix("img") {
      path(Segment) { filename =>
        getFromResource(s"images/$filename")
      }
    } ~
    path("") {
      complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>Welcome to Tombot</h1>"))
    }

}
