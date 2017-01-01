package controllers

import akka.event.LoggingAdapter
import akka.http.scaladsl.model.Multipart.BodyPart
import akka.http.scaladsl.model.{Multipart, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.stream.Materializer
import apis.mailgun.MailgunMessageReceivedResponse
import com.google.inject.Inject
import models.Platform
import models.events.TextResponse
import services.Conversation
import spray.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

/**
  * Created by markmo on 20/12/2016.
  */
class EmailController @Inject()(logger: LoggingAdapter,
                                conversationService: Conversation,
                                implicit val fm: Materializer) {

  import Platform._
  import StatusCodes._
  import conversationService._

//  val props = List(
//    "event", "recipient", "domain", "message-headers", "Message-Id",
//    "timestamp", "token", "signature", "body-plain")
  val props = List(
    "recipient", "body-plain", "subject", "timestamp", "Sender", "signature",
    "Received", "stripped-signature", "References", "token", "Content-Type",
    "from", "To", "Message-Id", "Date", "stripped-html", "attachment-count",
    "stripped-text", "In-Reply-To", "body-html")

  val routes =
    path("email") {
      post {
        logger.info("email webhook called")
        entity(as[Multipart.FormData]) { formData =>
          complete {
            formData.parts.mapAsync[(String, Any)](1) {
              case part: BodyPart => part.toStrict(2 seconds).map(strict => part.name -> strict.entity.data.utf8String)
            }.runFold(Map.empty[String, Any])((map, tuple) => map + tuple).map { params =>
              val headers = params("message-headers").toString.parseJson.asInstanceOf[JsArray].elements.map {
                case JsArray(Vector(JsString(k), JsString(v))) => k -> List(v)
                case JsArray(Vector(JsString(k), a: JsArray)) => k -> a.elements.map(_.compactPrint).toList
              }
              val customVariables = params.filterKeys(k => !props.contains(k))
              val response = MailgunMessageReceivedResponse(
                params("recipient").toString,
                headers.toMap,
                params("body-plain").toString,
                params("subject").toString,
                params("timestamp").toString.toLong,
                params("Sender").toString,
                params("signature").toString,
                params("Received").toString,
                params("stripped-signature").toString,
                params("References").toString,
                params("token").toString,
                params("Content-Type").toString,
                params("from").toString,
                params("To").toString,
                params("Message-Id").toString,
                params("Date").toString,
                params("stripped-html").toString,
                params("attachment-count").toString.toInt,
                params("stripped-text").toString,
                params("In-Reply-To").toString,
                params("body-html").toString,
                customVariables
              )
              val sender = response.sender
              val text = response.subject
              converse(sender, TextResponse(Email, sender, text))
              OK
            }
          }
          // received as urlencoded string
//          val params = Uri.Query(reply).toMap
//          val headers = params("message-headers").parseJson.asInstanceOf[JsArray].elements.map {
//            case JsArray(Vector(JsString(k), JsString(v))) => k -> List(v)
//            case JsArray(Vector(JsString(k), a: JsArray)) => k -> a.elements.map(_.compactPrint).toList
//          }
//          val customVariables = params.filterKeys(k => !props.contains(k))
//          val response = MailgunMessageDeliveredResponse(
//            params("event"),
//            params("recipient"),
//            params("domain"),
//            headers.toMap,
//            params("Message-Id"),
//            customVariables,
//            params("timestamp").toLong,
//            params("token"),
//            params("signature"),
//            params("body-plain")
//          )
//          val sender = parseEmail(response.headers("From").head)
//          val text = response.headers("Subject").head
//          converse(sender, TextResponse(Email, sender, text))
//          complete(OK)
        }
      }
    }

//  private def parseEmail(text: String) = {
//    val re = """[^<]*<([^>]+)>""".r
//    text match {
//      case re(email) => email
//      case _ => text
//    }
//  }

}
