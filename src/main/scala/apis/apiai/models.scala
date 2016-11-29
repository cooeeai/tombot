package apis.apiai

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._
import spray.json.lenses.JsonLenses._

/**
  * Created by markmo on 29/11/2016.
  */

case class AaContext(name: String, parameters: Map[String, String], lifespan: Int)

trait AaMessage

case class AaTextMessage(messageType: Int, speech: String) extends AaMessage

case class AaImageMessage(messageType: Int, imageUrl: String) extends AaMessage

case class AaButton(text: String, postback: String) extends AaMessage

case class AaCardMessage(messageType: Int, title: String, subtitle: String, buttons: List[AaButton]) extends AaMessage

case class AaQuickReply(messageType: Int, title: String, replies: List[String]) extends AaMessage

case class AaCustomMessage(messageType: Int, payload: JsObject) extends AaMessage

case class AaFulfillment(speech: String, messages: Option[List[AaMessage]])

case class AaMetadata(intentId: Option[String], webhookUsed: Option[String], intentName: Option[String])

case class AaStatus(code: Int, errorType: String, errorId: Option[String], errorDetails: Option[String])

case class AaResult(source: String,
                    resolvedQuery: String,
                    action: String,
                    actionIncomplete: Option[Boolean],
                    parameters: Map[String, String],
                    contexts: Option[List[AaContext]],
                    fulfillment: AaFulfillment,
                    score: Double,
                    metadata: Option[AaMetadata])

case class AaResponse(id: String, timestamp: String, result: AaResult, status: AaStatus, sessionId: String)

case class AaLocation(latitude: Double, longitude: Double)

case class AaOriginalRequest(source: String, data: JsObject)

case class AaRequest(query: List[String],
                     v: String,
                     confidence: Option[Double],
                     sessionId: String,
                     lang: String,
                     contexts: Option[List[AaContext]],
                     resetContexts: Option[Boolean],
                     entities: Option[List[String]],
                     timezone: Option[String],
                     location: Option[AaLocation],
                     originalRequest: Option[AaOriginalRequest])

trait AaJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val aaContextJsonFormat = jsonFormat3(AaContext)
  implicit val aaButtonJsonFormat = jsonFormat2(AaButton)

  implicit object aaMessageJsonFormat extends RootJsonFormat[AaMessage] {

    def write(m: AaMessage) = m match {
      case t: AaTextMessage => JsObject(
        "type" -> JsNumber(t.messageType),
        "speech" -> JsString(t.speech)
      )
      case i: AaImageMessage => JsObject(
        "type" -> JsNumber(i.messageType),
        "imageUrl" -> JsString(i.imageUrl)
      )
      case c: AaCardMessage => JsObject(
        "type" -> JsNumber(c.messageType),
        "title" -> JsString(c.title),
        "subtitle" -> JsString(c.subtitle),
        "buttons" -> JsArray(c.buttons.map(_.toJson))
      )
      case q: AaQuickReply => JsObject(
        "type" -> JsNumber(q.messageType),
        "title" -> JsString(q.title),
        "replies" -> JsArray(q.replies.map(JsString(_)))
      )
      case u: AaCustomMessage => JsObject(
        "type" -> JsNumber(u.messageType),
        "payload" -> u.payload
      )
    }

    def read(value: JsValue) =
      value.extract[Int]('type) match {
        case 0 => AaTextMessage(0, value.extract[String]('speech))
        case 1 => AaCardMessage(1,
          value.extract[String]('title),
          value.extract[String]('subtitle),
          value.extract[List[AaButton]]('buttons)
        )
        case 2 => AaQuickReply(2,
          value.extract[String]('title),
          value.extract[List[String]]('replies)
        )
        case 3 => AaImageMessage(3, value.extract[String]('imageUrl))
        case 4 => AaCustomMessage(4, value.extract[JsObject]('payload))
        case _ => throw DeserializationException("AaMessage expected")
      }

  }

  implicit val aaFulfillmentJsonFormat = jsonFormat2(AaFulfillment)
  implicit val aaMetadataJsonFormat = jsonFormat3(AaMetadata)
  implicit val aaStatusJsonFormat = jsonFormat4(AaStatus)
  implicit val aaResultJsonFormat = jsonFormat9(AaResult)
  implicit val aaResponseJsonFormat = jsonFormat5(AaResponse)
  implicit val aaLocationJsonFormat = jsonFormat2(AaLocation)
  implicit val aaOriginalRequestJsonFormat = jsonFormat2(AaOriginalRequest)
  implicit val aaRequestJsonFormat = jsonFormat11(AaRequest)

}

object Builder {

  class RequestBuilder(query: Option[String]) {

    def withQuery(value: String) = new RequestBuilder(Some(value))

    def build() = AaRequest(
      query = List(query.get),
      v = "20150910",
      confidence = None,
      sessionId = "2d62c50f-5771-4522-bef6-67911b5f5ae6",
      lang = "en",
      contexts = None,
      resetContexts = None,
      entities = None,
      timezone = Some("Australia/Melbourne"),
      location = Some(AaLocation(-37.8134861, 144.9915857)),
      originalRequest = None
    )

  }

  def apiAiRequest = new RequestBuilder(None)

}