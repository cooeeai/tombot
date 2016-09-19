package services

import java.util
import java.util.{Map => JMap}

import akka.event.LoggingAdapter
import clojure.java.api.Clojure
import clojure.lang._
import com.google.inject.Inject
import com.ibm.watson.developer_cloud.conversation.v1.model._
import com.ibm.watson.developer_cloud.conversation.v1.{ConversationService => WatsonConversationSvc}
import com.typesafe.config.Config
import humanize.Humanize._
import humanize.time.TimeMillis
import org.joda.time.{DateTime, DateTimeZone}
import spray.json._
import utils.ClojureInterop._

import scala.collection.JavaConversions._

/**
  * Created by markmo on 11/09/2016.
  */
class WatsonConversationService @Inject()(config: Config, logger: LoggingAdapter) {

  val username = System.getenv("WATSON_USERNAME")
  val password = System.getenv("WATSON_PASSWORD")
  val workspaceId = System.getenv("WATSON_WORKSPACE_ID")

  val require = Clojure.`var`("clojure.core", "require")
  require.invoke(Symbol.intern("duckling.core"))
  val load = Clojure.`var`("duckling.core", "load!")
  load.invoke()
  val parse = Clojure.`var`("duckling.core", "parse")

  val tz = DateTimeZone.forID(config.getString("timezone"))

  def converse(text: String, context: Option[JMap[String, AnyRef]]): MessageResponse = {
    logger.info("call watson conversation api")
    val ctx = if (context.isDefined) context.get else new util.HashMap[String, AnyRef]()
    val parsed = parse.invoke(Symbol.intern("en$core"), text, Clojure.read("[:time]")).asInstanceOf[LazySeq]
    for (x <- parsed.toArray) yield {
      logger.debug("parsed time:\n" + prettyPrintScalaDataStructureAsClojure(x))
      val parsedMap = clojureToScala(x).asInstanceOf[Map[Any, Any]]
      val timeBody = parsedMap(CKeyword(null, "body")).asInstanceOf[String]
      val timeValue = parsedMap(CKeyword(null, "value")).asInstanceOf[Map[Any, Any]]
      val timeType = timeValue(CKeyword(null, "type")).asInstanceOf[String]
      logger.debug("time type: " + timeType)

      timeType match {

        case "value" =>
          val value = timeValue(CKeyword(null, "value")).toString
          val grain = timeValue(CKeyword(null, "grain")).toString.substring(1)
          val date = DateTime.parse(value).withZone(tz).toDate
          val precision = getTimePrecision(grain)
          val timeCtx = new util.HashMap[String, String]()
          timeCtx.put("body", timeBody)
          timeCtx.put("type", timeType)
          timeCtx.put("value", value)
          timeCtx.put("grain", grain)
          timeCtx.put("natural", naturalTime(date, precision))
          ctx.put("time", timeCtx)

        case "interval" =>
          val from = timeValue(CKeyword(null, "from")).asInstanceOf[Map[Any, Any]]
          val fromValue = from(CKeyword(null, "value")).toString
          val fromGrain = from(CKeyword(null, "grain")).toString.substring(1)
          val fromDate = DateTime.parse(fromValue).withZone(tz).toDate
          //val fromPrecision = getTimePrecision(fromGrain)

          val to = timeValue(CKeyword(null, "to")).asInstanceOf[Map[Any, Any]]
          val toValue = to(CKeyword(null, "value")).toString
          val toGrain = to(CKeyword(null, "grain")).toString.substring(1)
          val toDate = DateTime.parse(toValue).withZone(tz).toDate
          //val toPrecision = getTimePrecision(toGrain)

          val timeCtx = new util.HashMap[String, String]()
          timeCtx.put("body", timeBody)
          timeCtx.put("type", timeType)
          timeCtx.put("fromValue", fromValue)
          timeCtx.put("fromGrain", fromGrain)
          timeCtx.put("fromNatural", naturalDay(fromDate))
          timeCtx.put("toValue", toValue)
          timeCtx.put("toGrain", toGrain)
          timeCtx.put("toNatural", naturalDay(toDate))
          ctx.put("time", timeCtx)
      }
    }
    val service = new WatsonConversationSvc("2016-07-11")
    service.setUsernameAndPassword(username, password)
    val partialMessage = new MessageRequest.Builder().inputText(text)
    logger.debug("context: " + javaMapToJson(ctx).prettyPrint)
    partialMessage.context(ctx)
    val message = partialMessage.build()
    service.message(workspaceId, message).execute()
  }

  def getTimePrecision(grain: String): TimeMillis = grain match {
    case "minute" => TimeMillis.MINUTE
    case "hour" => TimeMillis.HOUR
    case "day" => TimeMillis.DAY
    case "week" => TimeMillis.WEEK
    case "month" => TimeMillis.MONTH
  }

  def javaMapToJson(map: JMap[String, AnyRef]): JsValue =
    JsObject(map.toMap.mapValues {
      case v: java.lang.Boolean => JsBoolean(v)
      case v: Integer => JsNumber(v)
      case v: String => JsString(v)
      case v: JMap[_, _] => javaMapToJson(v.asInstanceOf[JMap[String, AnyRef]])
      case v: AnyRef => JsString(v.toString)
    })

  def mapToJson(map: Map[String, Any]): JsValue =
    JsObject(map.toMap.mapValues {
      case v: java.lang.Boolean => JsBoolean(v)
      case v: Integer => JsNumber(v)
      case v: String => JsString(v)
      case v: JMap[_, _] => mapToJson(v.asInstanceOf[Map[String, Any]])
      case v: Any => JsString(v.toString)
    })
}