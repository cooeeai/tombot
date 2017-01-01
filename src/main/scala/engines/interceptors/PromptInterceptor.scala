package engines.interceptors

import akka.actor.{ActorLogging, ActorRef}
import akka.contrib.pattern.ReceivePipeline
import akka.contrib.pattern.ReceivePipeline.{HandledCompletely, Inner}
import clojure.java.api.Clojure
import clojure.lang.{LazySeq, Symbol}
import com.typesafe.config.Config
import humanize.Humanize._
import humanize.time.TimeMillis
import models.events.{TextMessage, TextResponse}
import org.joda.time.{DateTime, DateTimeZone}
import utils.ClojureInterop._

import scala.collection.mutable
import scala.math.{ceil, min}

/**
  * Created by markmo on 31/12/2016.
  */
trait PromptInterceptor extends ActorLogging {
  this: ReceivePipeline =>

  var config: Option[Config] = None

  var currentPrompt: Option[String] = None

  var selection: Option[List[String]] = None

  var provider: Option[ActorRef] = None

  val require = Clojure.`var`("clojure.core", "require")
  require.invoke(Symbol.intern("duckling.core"))
  val load = Clojure.`var`("duckling.core", "load!")
  load.invoke()
  val parse = Clojure.`var`("duckling.core", "parse")

  def tz = DateTimeZone.forID(config.get.getString("settings.timezone"))

  final val yesSynonyms = config.get.getStringList("prompts.boolean.yes-synonyms")

  final val noSynonyms = config.get.getStringList("prompts.boolean.no-synonyms")

  val invalidResponseMessage = config.get.getString("invalid-response-message")

  pipelineInner {
    case ev@TextResponse(_, sender, text, _) =>
      if (currentPrompt.isEmpty) {

        Inner(ev)

      } else {

        currentPrompt match {

          case Some("time") =>
            // examples:
            // “today”
            // “Monday, Feb 18”
            // “the 1st of march”
            // “last week”
            // “a quarter to noon”
            // “11:45am”
            // “three months ago”
            // “next 3 weeks”
            // “thanksgiving”
            // “Mother’s Day”
            // “from 9:30 - 11:00 on Thursday
            // “the day before labor day 2020”
            val ctx = mutable.Map[String, Any]()
            val parsed = parse.invoke(Symbol.intern("en$core"), text, Clojure.read("[:time]")).asInstanceOf[LazySeq]
            for (x <- parsed.toArray) yield {
              log.debug("parsed time:\n{}", prettyPrintScalaDataStructureAsClojure(x))
              val parsedMap = clojureToScala(x).asInstanceOf[Map[Any, Any]]
              val timeBody = parsedMap(CKeyword(null, "body")).asInstanceOf[String]
              val timeValue = parsedMap(CKeyword(null, "value")).asInstanceOf[Map[Any, Any]]
              val timeType = timeValue(CKeyword(null, "type")).asInstanceOf[String]
              log.debug("time type: {}", timeType)

              timeType match {

                case "value" =>
                  val value = timeValue(CKeyword(null, "value")).toString
                  val grain = timeValue(CKeyword(null, "grain")).toString.substring(1)
                  val date = DateTime.parse(value).withZone(tz).toDate
                  val precision = getTimePrecision(grain)
                  val timeCtx = mutable.Map[String, String]()
                  timeCtx.put("body", timeBody)
                  timeCtx.put("type", timeType)
                  timeCtx.put("value", value)
                  timeCtx.put("grain", grain)
                  timeCtx.put("natural", naturalTime(date, precision))
                  ctx.put("time", timeCtx.toMap)

                case "interval" =>
                  val from = timeValue(CKeyword(null, "from")).asInstanceOf[Map[Any, Any]]
                  val fromValue = from(CKeyword(null, "value")).toString
                  val fromGrain = from(CKeyword(null, "grain")).toString.substring(1)
                  val fromDate = DateTime.parse(fromValue).withZone(tz).toDate

                  val to = timeValue(CKeyword(null, "to")).asInstanceOf[Map[Any, Any]]
                  val toValue = to(CKeyword(null, "value")).toString
                  val toGrain = to(CKeyword(null, "grain")).toString.substring(1)
                  val toDate = DateTime.parse(toValue).withZone(tz).toDate

                  val timeCtx = mutable.Map[String, String]()
                  timeCtx.put("body", timeBody)
                  timeCtx.put("type", timeType)
                  timeCtx.put("fromValue", fromValue)
                  timeCtx.put("fromGrain", fromGrain)
                  timeCtx.put("fromNatural", naturalDay(fromDate))
                  timeCtx.put("toValue", toValue)
                  timeCtx.put("toGrain", toGrain)
                  timeCtx.put("toNatural", naturalDay(toDate))
                  ctx.put("time", timeCtx.toMap)
              }
            }
            if (ctx.isEmpty) {
              // reply back with invalid response error
              provider.get ! TextMessage(sender, invalidResponseMessage)
              HandledCompletely
            } else {
              ctx.put("prompt", "time")
              currentPrompt = None
              Inner(ev.copy(context = Some(ctx.toMap)))
            }

          case Some("temperature") =>
            // examples:
            // “70°F”
            // “72° Fahrenheit”
            // “thirty two celsius”
            // “65 degrees”
            val ctx = mutable.Map[String, Any]()
            val parsed = parse.invoke(Symbol.intern("en$core"), text, Clojure.read("[:temperature]")).asInstanceOf[LazySeq]
            for (x <- parsed.toArray) yield {
              log.debug("parsed temperature:\n{}", prettyPrintScalaDataStructureAsClojure(x))
              val parsedMap = clojureToScala(x).asInstanceOf[Map[Any, Any]]
              val value = parsedMap(CKeyword(null, "value")).asInstanceOf[Double]
              val unit = parsedMap(CKeyword(null, "unit")).toString
              ctx.put("value", value)
              ctx.put("unit", unit)
            }
            if (ctx.isEmpty) {
              // reply back with invalid response error
              provider.get ! TextMessage(sender, invalidResponseMessage)
              HandledCompletely
            } else {
              ctx.put("prompt", "temperature")
              currentPrompt = None
              Inner(ev.copy(context = Some(ctx.toMap)))
            }

          case Some("number") =>
            // examples:
            // “eighteen”
            // “0.77”
            // “100K”
            // “33”
            val ctx = mutable.Map[String, Any]()
            /*
            try {
              val d = text.toDouble
              if (d % 1 == 0) {
                // d is an Int
                ctx.put("type", "Int")
                ctx.put("value", d.toInt)
              } else {
                ctx.put("type", "Double")
                ctx.put("value", d)
              }
              Inner(ev.copy(context = Some(ctx.toMap)))
            } catch {
              case e: NumberFormatException =>
              // reply back with invalid response error
            }*/
            val parsed = parse.invoke(Symbol.intern("en$core"), text, Clojure.read("[:number]")).asInstanceOf[LazySeq]
            for (x <- parsed.toArray) yield {
              log.debug("parsed number:\n{}", prettyPrintScalaDataStructureAsClojure(x))
              val parsedMap = clojureToScala(x).asInstanceOf[Map[Any, Any]]
              val isInt = parsedMap(CKeyword(null, "integer")).asInstanceOf[Boolean]
              if (isInt) {
                val value = parsedMap(CKeyword(null, "value")).asInstanceOf[Int]
                ctx.put("type", "Int")
                ctx.put("value", value)
              } else {
                val value = parsedMap(CKeyword(null, "value")).asInstanceOf[Double]
                ctx.put("type", "Double")
                ctx.put("value", value)
              }
            }
            if (ctx.isEmpty) {
              // reply back with invalid response error
              provider.get ! TextMessage(sender, invalidResponseMessage)
              HandledCompletely
            } else {
              ctx.put("prompt", "number")
              currentPrompt = None
              Inner(ev.copy(context = Some(ctx.toMap)))
            }

          case Some("ordinal") =>
            // examples:
            // “4th”
            // “first”
            // “seventh”
            val ctx = mutable.Map[String, Any]()
            val parsed = parse.invoke(Symbol.intern("en$core"), text, Clojure.read("[:ordinal]")).asInstanceOf[LazySeq]
            for (x <- parsed.toArray) yield {
              log.debug("parsed ordinal:\n{}", prettyPrintScalaDataStructureAsClojure(x))
              val parsedMap = clojureToScala(x).asInstanceOf[Map[Any, Any]]
              val value = parsedMap(CKeyword(null, "value")).asInstanceOf[Double]
              ctx.put("value", value)
            }
            if (ctx.isEmpty) {
              // reply back with invalid response error
              provider.get ! TextMessage(sender, invalidResponseMessage)
              HandledCompletely
            } else {
              ctx.put("prompt", "ordinal")
              currentPrompt = None
              Inner(ev.copy(context = Some(ctx.toMap)))
            }

          case Some("distance") =>
            // examples:
            // “8miles”
            // “3 feet”
            // “2 inches”
            // “3’’“
            // “4km”
            // “12cm”
            val ctx = mutable.Map[String, Any]()
            val parsed = parse.invoke(Symbol.intern("en$core"), text, Clojure.read("[:distance]")).asInstanceOf[LazySeq]
            for (x <- parsed.toArray) yield {
              log.debug("parsed distance:\n{}", prettyPrintScalaDataStructureAsClojure(x))
              val parsedMap = clojureToScala(x).asInstanceOf[Map[Any, Any]]
              val value = parsedMap(CKeyword(null, "value")).asInstanceOf[Double]
              val unit = parsedMap(CKeyword(null, "unit")).toString
              val normalized = parsedMap(CKeyword(null, "normalized")).asInstanceOf[Map[Any, Any]]
              val normalizedValue = normalized(CKeyword(null, "value")).asInstanceOf[Double]
              val normalizedUnit = normalized(CKeyword(null, "unit")).toString
              ctx.put("value", value)
              ctx.put("unit", unit)
              ctx.put("normalizedValue", normalizedValue)
              ctx.put("normalizedUnit", normalizedUnit)
            }
            if (ctx.isEmpty) {
              // reply back with invalid response error
              provider.get ! TextMessage(sender, invalidResponseMessage)
              HandledCompletely
            } else {
              ctx.put("prompt", "distance")
              Inner(ev.copy(context = Some(ctx.toMap)))
            }

          case Some("volume") =>
            // examples:
            // “250ml”
            // “2liters”
            // “1 gallon”
            val ctx = mutable.Map[String, Any]()
            val parsed = parse.invoke(Symbol.intern("en$core"), text, Clojure.read("[:volume]")).asInstanceOf[LazySeq]
            for (x <- parsed.toArray) yield {
              log.debug("parsed volume:\n{}", prettyPrintScalaDataStructureAsClojure(x))
              val parsedMap = clojureToScala(x).asInstanceOf[Map[Any, Any]]
              val value = parsedMap(CKeyword(null, "value")).asInstanceOf[Double]
              val unit = parsedMap(CKeyword(null, "unit")).toString
              val normalized = parsedMap(CKeyword(null, "normalized")).asInstanceOf[Map[Any, Any]]
              val normalizedValue = normalized(CKeyword(null, "value")).asInstanceOf[Double]
              val normalizedUnit = normalized(CKeyword(null, "unit")).toString
              ctx.put("value", value)
              ctx.put("unit", unit)
              ctx.put("normalizedValue", normalizedValue)
              ctx.put("normalizedUnit", normalizedUnit)
            }
            if (ctx.isEmpty) {
              // reply back with invalid response error
              provider.get ! TextMessage(sender, invalidResponseMessage)
              HandledCompletely
            } else {
              ctx.put("prompt", "volume")
              currentPrompt = None
              Inner(ev.copy(context = Some(ctx.toMap)))
            }

          case Some("money") =>
            // examples:
            // “ten dollars”
            // “4 bucks”
            // “$20”
            val ctx = mutable.Map[String, Any]()
            val parsed = parse.invoke(Symbol.intern("en$core"), text, Clojure.read("[:amount-of-money]")).asInstanceOf[LazySeq]
            for (x <- parsed.toArray) yield {
              log.debug("parsed amount-of-money:\n{}", prettyPrintScalaDataStructureAsClojure(x))
              val parsedMap = clojureToScala(x).asInstanceOf[Map[Any, Any]]
              val value = parsedMap(CKeyword(null, "value")).asInstanceOf[BigDecimal]
              val unit = parsedMap(CKeyword(null, "unit")).toString
              ctx.put("value", value)
              ctx.put("unit", unit)
            }
            if (ctx.isEmpty) {
              // reply back with invalid response error
              provider.get ! TextMessage(sender, invalidResponseMessage)
              HandledCompletely
            } else {
              ctx.put("prompt", "money")
              currentPrompt = None
              Inner(ev.copy(context = Some(ctx.toMap)))
            }

          case Some("duration") =>
            // examples:
            // “2 hours”
            // “4 days”
            // ”3 minutes”
            val ctx = mutable.Map[String, Any]()
            val parsed = parse.invoke(Symbol.intern("en$core"), text, Clojure.read("[:duration]")).asInstanceOf[LazySeq]
            for (x <- parsed.toArray) yield {
              log.debug("parsed duration:\n{}", prettyPrintScalaDataStructureAsClojure(x))
              val parsedMap = clojureToScala(x).asInstanceOf[Map[Any, Any]]
              val value = parsedMap(CKeyword(null, "value")).asInstanceOf[Double]
              val unit = parsedMap(CKeyword(null, "unit")).toString
              val normalized = parsedMap(CKeyword(null, "normalized")).asInstanceOf[Map[Any, Any]]
              val normalizedValue = normalized(CKeyword(null, "value")).asInstanceOf[Double]
              val normalizedUnit = normalized(CKeyword(null, "unit")).toString
              ctx.put("value", value)
              ctx.put("unit", unit)
              ctx.put("normalizedValue", normalizedValue)
              ctx.put("normalizedUnit", normalizedUnit)
            }
            if (ctx.isEmpty) {
              // reply back with invalid response error
              provider.get ! TextMessage(sender, invalidResponseMessage)
              HandledCompletely
            } else {
              ctx.put("prompt", "duration")
              currentPrompt = None
              Inner(ev.copy(context = Some(ctx.toMap)))
            }

          case Some("email") =>
            // examples:
            // “mark@mydomain.com”
            val ctx = mutable.Map[String, Any]()
            val parsed = parse.invoke(Symbol.intern("en$core"), text, Clojure.read("[:email]")).asInstanceOf[LazySeq]
            for (x <- parsed.toArray) yield {
              log.debug("parsed email:\n{}", prettyPrintScalaDataStructureAsClojure(x))
              val parsedMap = clojureToScala(x).asInstanceOf[Map[Any, Any]]
              val value = parsedMap(CKeyword(null, "value")).toString
              ctx.put("value", value)
            }
            if (ctx.isEmpty) {
              // reply back with invalid response error
              provider.get ! TextMessage(sender, invalidResponseMessage)
              HandledCompletely
            } else {
              ctx.put("prompt", "email")
              currentPrompt = None
              Inner(ev.copy(context = Some(ctx.toMap)))
            }

          case Some("url") =>
            // examples:
            // “www.foo.com:8080/path”
            // “https://myserver?foo=bar”
            // “cnn.com/info”
            // “foo.com/path/path?ext=%23&foo=bla”
            // “localhost”
            val ctx = mutable.Map[String, Any]()
            val parsed = parse.invoke(Symbol.intern("en$core"), text, Clojure.read("[:url]")).asInstanceOf[LazySeq]
            for (x <- parsed.toArray) yield {
              log.debug("parsed url:\n{}", prettyPrintScalaDataStructureAsClojure(x))
              val parsedMap = clojureToScala(x).asInstanceOf[Map[Any, Any]]
              val value = parsedMap(CKeyword(null, "value")).toString
              ctx.put("value", value)
            }
            if (ctx.isEmpty) {
              // reply back with invalid response error
              provider.get ! TextMessage(sender, invalidResponseMessage)
              HandledCompletely
            } else {
              ctx.put("prompt", "url")
              currentPrompt = None
              Inner(ev.copy(context = Some(ctx.toMap)))
            }

          case Some("phone-number") =>
            // examples:
            // “415-123-3444”
            // “+33 4 76095663”
            // “(650)-283-4757 ext 897”
            val ctx = mutable.Map[String, Any]()
            val parsed = parse.invoke(Symbol.intern("en$core"), text, Clojure.read("[:phone-number]")).asInstanceOf[LazySeq]
            for (x <- parsed.toArray) yield {
              log.debug("parsed phone-number:\n{}", prettyPrintScalaDataStructureAsClojure(x))
              val parsedMap = clojureToScala(x).asInstanceOf[Map[Any, Any]]
              val value = parsedMap(CKeyword(null, "value")).toString
              ctx.put("value", value)
            }
            if (ctx.isEmpty) {
              // reply back with invalid response error
              provider.get ! TextMessage(sender, invalidResponseMessage)
              HandledCompletely
            } else {
              ctx.put("prompt", "phone-number")
              currentPrompt = None
              Inner(ev.copy(context = Some(ctx.toMap)))
            }

          case Some("boolean") =>
            // examples:
            // yes
            // true
            // OK
            // righto
            // nope
            // no way
            // ixnay
            val s =
              text.trim
                .toLowerCase
                .replaceAll("[a-z0-9]", " ")
                .replaceAll("\\s+", " ")
            val ctx = mutable.Map[String, Any]()
            if (yesSynonyms.contains(s)) {
              ctx.put("type", "Boolean")
              ctx.put("value", true)
            } else if (noSynonyms.contains(s)) {
              ctx.put("type", "Boolean")
              ctx.put("value", false)
            }
            if (ctx.isEmpty) {
              // reply back with invalid response error
              provider.get ! TextMessage(sender, invalidResponseMessage)
              HandledCompletely
            } else {
              ctx.put("prompt", "boolean")
              currentPrompt = None
              Inner(ev.copy(context = Some(ctx.toMap)))
            }

          case Some("selection") =>
            selection match {
              case Some(options) if options.nonEmpty =>
                val ctx = mutable.Map[String, Any]()
                val parsed = parse.invoke(Symbol.intern("en$core"), text, Clojure.read("[:number, :ordinal]")).asInstanceOf[LazySeq]
                val values = for (x <- parsed.toArray) yield {
                  log.debug("parsed selection:\n{}", prettyPrintScalaDataStructureAsClojure(x))
                  val parsedMap = clojureToScala(x).asInstanceOf[Map[Any, Any]]
                  parsedMap(CKeyword(null, "value")).asInstanceOf[Int]
                }
                if (values.isEmpty) {
                  val (dist: Int, idx: Int) =
                    options.map(b => editDist(text, b))
                      .zipWithIndex
                      .sortBy(-_._1)
                      .head
                  val threshold = ceil(text.length * 0.4).toInt
                  if (dist < threshold) {
                    if (idx > 0 && idx < options.length) {
                      val selected = options(idx - 1)
                      ctx.put("value", selected)
                      ctx.put("index", idx)
                    }
                  }
                } else {
                  val idx = values.head
                  if (idx > 0 && idx < options.length) {
                    val selected = options(idx - 1)
                    ctx.put("value", selected)
                    ctx.put("index", idx)
                  }
                }
                if (ctx.isEmpty) {
                  // reply back with invalid response error
                  provider.get ! TextMessage(sender, invalidResponseMessage)
                  HandledCompletely
                } else {
                  ctx.put("prompt", "boolean")
                  currentPrompt = None
                  selection = None
                  Inner(ev.copy(context = Some(ctx.toMap)))
                }
              case _ =>
                // reply back with invalid response error
                provider.get ! TextMessage(sender, invalidResponseMessage)
                HandledCompletely
            }
          case prompt =>
            log.warning("unhandled prompt {}", prompt)
            Inner(ev)
        }
      }
  }

  def getTimePrecision(grain: String): TimeMillis = grain match {
    case "minute" => TimeMillis.MINUTE
    case "hour" => TimeMillis.HOUR
    case "day" => TimeMillis.DAY
    case "week" => TimeMillis.WEEK
    case "month" => TimeMillis.MONTH
  }

  def editDist[A](a: Iterable[A], b: Iterable[A]): Int =
    ((0 to b.size).toList /: a) ((prev, x) =>
      (prev zip prev.tail zip b).scanLeft(prev.head + 1) {
        case (h, ((d, v), y)) => min(min(h + 1, v + 1), d + (if (x == y) 0 else 1))
      }) last
}