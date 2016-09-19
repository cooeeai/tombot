package utils

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._

/**
  * Created by markmo on 12/09/2016.
  */
trait MapJsonProtocol extends DefaultJsonProtocol with SprayJsonSupport {

  implicit object mapJsonFormat extends JsonFormat[Map[String, Any]] {

    def write(map: Map[String, Any]): JsValue =
      JsObject(map.mapValues {
        case v: Boolean => JsBoolean(v)
        case v: Int => JsNumber(v)
        case v: String => JsString(v)
        case v: Map[_, _] => write(v.asInstanceOf[Map[String, Any]])
        case v: Any => JsString(v.toString)
      })

    def read(value: JsValue) = ???

  }

}