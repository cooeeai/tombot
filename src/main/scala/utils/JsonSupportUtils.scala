package utils

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._

/**
  * Created by markmo on 12/11/2016.
  */
trait JsonSupportUtils extends DefaultJsonProtocol with SprayJsonSupport {

  implicit object AnyJsonFormat extends JsonFormat[Any] {

    def write(a: Any) = a match {
      case n@(_: Int | _: BigInt | _: Long | _: Double | _: Float | _: BigDecimal) => JsNumber(n.toString)
      case s: String => JsString(s)
      case b: Boolean if b => JsTrue
      case b: Boolean if !b => JsFalse
      case x :: xs => JsArray((x :: xs).toVector map (_.toJson))
      case null => JsNull
      case o => o.toJson
    }

    // TODO
    def read(value: JsValue): Any = value match {
      case JsString(str) => str
      case JsNumber(num) => if (isInt(num)) num.toInt else num.toDouble
      case JsBoolean(bool) => bool
      case JsNull => null
      case JsObject(fields) => fields.map({
        case (key, JsString(str)) => (key, str)
        case (key, JsNumber(num)) => (key, if (isInt(num)) num.toInt else num.toDouble)
        case (key, JsBoolean(bool)) => (key, bool)
        case (key, JsNull) => (key, null)
        case (key, other) => (key, other.toString)
      })
      case x => throw DeserializationException("JsObject expected, got " + x)
    }

  }

  def isInt(d: BigDecimal) = (d % 1) == 0

}
