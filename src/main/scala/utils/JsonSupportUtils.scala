package utils

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._

/**
  * Created by markmo on 12/11/2016.
  */
trait JsonSupportUtils extends DefaultJsonProtocol with SprayJsonSupport {

  implicit object AnyJsonFormat extends JsonFormat[Any] {

    def write(a: Any) = a match {
      case n@(_: Int | _: BigInt | _: Long | _: Double | _: Float) => JsNumber(n.toString)
      case s: String => JsString(s)
      case b: Boolean if b => JsTrue
      case b: Boolean if !b => JsFalse
      case x :: xs => JsArray((x :: xs).toVector map (_.toJson))
      case null => JsNull
      case o => o.toJson
    }

    def read(value: JsValue) = ??? // not used

  }

}
