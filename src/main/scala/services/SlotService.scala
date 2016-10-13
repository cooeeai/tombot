package services

import java.net.URLEncoder
import javax.script.ScriptEngineManager

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpMethods, HttpRequest}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import com.google.inject.Inject
import jdk.nashorn.api.scripting.{JSObject => NashornJSObject}
import memory.{Slot, SlotError}
import spray.json._

import scala.collection.JavaConversions._
import scala.concurrent._
import scala.concurrent.duration._
import scala.reflect._

/**
  * Created by markmo on 9/10/2016.
  */
class SlotService @Inject()(logger: LoggingAdapter,
                            implicit val system: ActorSystem,
                            implicit val fm: Materializer) {

  import DefaultJsonProtocol._
  import system.dispatcher

  val engine = new ScriptEngineManager(null).getEngineByName("nashorn")

  val invalidMessageDefault = "Invalid format. Please try again."

  val timeout = 30 second

  implicit object AnyJsonFormat extends JsonFormat[Any] {

    def write(x: Any) = x match {
      case n: Int => JsNumber(n)
      case s: String => JsString(s)
      case b: Boolean if b => JsTrue
      case b: Boolean if !b => JsFalse
    }

    def read(value: JsValue) = value match {
      case JsNumber(n) => n.intValue()
      case JsString(s) => s
      case JsTrue => true
      case JsFalse => false
    }

  }

  def fillSlot(slot: Slot, key: String, value: Any): (Option[SlotError], Slot) = {
    val Slot(slotKey, question, children, slotValue, validateFn, invalidMessage, parseApi, parseExpr, parseFn, confirm, confirmed, caption) = slot
    if (slotKey == key) {
      if (validateFn.isDefined && !validateFn.get(value.toString)) {
        val message = invalidMessage.getOrElse(invalidMessageDefault)
        (Some(SlotError(key, message)), slot)
      } else {
        if (children.isDefined) {
          if (parseApi.isDefined) {
            if (parseExpr.isDefined) {
              // TODO
              // don't block
              val dict = Await.result(callApi[Map[String, Any]](parseApi.get, value), timeout)
              val jsObject = mapToNashornJSObject(dict)
              val fn = engine.eval(parseExpr.get).asInstanceOf[NashornJSObject]
              val result = fn.call(null, jsObject).asInstanceOf[NashornJSObject]
              val params = jsObjectToMap(result)
              logger.debug("params:\n" + params)
              fillChildSlots(slot, params, value)
            } else {
              val params = Await.result(callApi[Map[String, Any]](parseApi.get, value), timeout)
              fillChildSlots(slot, params, value)
            }
          } else if (parseExpr.isDefined) {
            val fn = engine.eval(parseExpr.get).asInstanceOf[NashornJSObject]
            val params = fn.call(null, value.toString).asInstanceOf[NashornJSObject]
            fillChildSlots(slot, jsObjectToMap(params), value)
          } else if (parseFn.isDefined) {
            val params = parseFn.get(value.toString)
            fillChildSlots(slot, params, value)
          } else {
            (None, Slot(key, question, children, Some(value), validateFn, invalidMessage, parseApi, parseExpr, parseFn, confirm, confirmed, caption))
          }
        } else {
          (None, Slot(key, question, children, Some(value), validateFn, invalidMessage, parseApi, parseExpr, parseFn, confirm, confirmed, caption))
        }
      }
    } else if (children.isDefined) {
      val slots = children.get.map(child => fillSlot(child, key, value))
      val error = slots.map(_._1).find(_.isDefined).flatten
      if (error.isDefined) {
        (error, slot)
      } else {
        (None, Slot(slotKey, question, Some(slots.map(_._2)), slotValue, validateFn, invalidMessage, parseApi, parseExpr, parseFn, confirm, confirmed, caption))
      }
    } else {
      (None, slot)
    }
  }

  private def mapToNashornJSObject(params: Map[String, Any]): NashornJSObject = {
    val constructor = engine.eval("Object").asInstanceOf[NashornJSObject]
    val jsObject = constructor.newObject().asInstanceOf[NashornJSObject]
    params foreach {
      case (key, value) => jsObject.setMember(key, value)
    }
    jsObject
  }

  private def jsObjectToMap(jsObject: NashornJSObject): Map[String, Any] =
    jsObject.keySet().map(key => (key, jsObject.getMember(key))).toMap

  private def callApi[T: ClassTag](uriTemplate: String, value: Any)(implicit reader: JsonReader[T]): Future[T] = {
    val uri = uriTemplate.format(URLEncoder.encode(value.toString, "UTF-8"))
    logger.debug(s"calling [$uri], returning [${classTag[T].runtimeClass}]")
    for {
      response <- Http().singleRequest(HttpRequest(
        method = HttpMethods.GET,
        uri = uri))
      entity <- Unmarshal(response.entity).to[String]
    } yield {
      logger.debug("response:\n" + entity)
      entity.parseJson.convertTo[T]
    }
  }

  private def fillChildSlots(slot: Slot, params: Map[String, Any], value: Any): (Option[SlotError], Slot) = {
    val Slot(key, question, children, _, validateFn, invalidMessage, parseApi, parseExpr, parseFn, confirm, confirmed, caption) = slot
    val xs = children.get map { child =>
      val k = child.key
      if (params.contains(k)) {
        fillSlot(child, k, params(k))
      } else {
        (None, child)
      }
    }
    val error = xs.map(_._1).find(_.isDefined).flatten
    if (error.isDefined) {
      (error, slot)
    } else {
      (None, Slot(key, question, Some(xs.map(_._2)), Some(value), validateFn, invalidMessage, parseApi, parseExpr, parseFn, confirm, confirmed, caption))
    }
  }

}

case class SlotContainer(service: SlotService, slot: Slot, errors: List[SlotError] = Nil) {

  def fillSlot(key: String, value: Any): SlotContainer =
    service.fillSlot(slot, key, value) match {
      case (Some(e), s) => SlotContainer(service, s, e :: errors)
      case (None, s) => SlotContainer(service, s, errors)
    }

}
