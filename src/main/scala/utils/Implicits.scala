package utils

import apis.liveengage.LpErrorResponse
import services.LiveEngageChatActor.LpException
import spray.json.{JsBoolean, JsNumber, JsString, JsValue}

import scala.concurrent.Future
import scala.language.implicitConversions

/**
  * Created by markmo on 5/12/2016.
  */
object Implicits {

  class RichString(str: String) {

    def isInt: Boolean = str.forall(_.isDigit)

    def toHTML: String =
      str.split("\n") map {
        case "" => "<br>"
        case ln => s"<p>$ln</p>"
      } mkString

  }

  implicit def stringToRichString(str: String): RichString = new RichString(str)


  implicit def jsonValueToString(value: JsValue): String = value match {
    case JsString(str) => str
    case JsNumber(num) => num.toString
    case JsBoolean(bool) => bool.toString
    case other => other.toString
  }


  type LpErrorHandler = (LpErrorResponse => Unit)

  class RichEither[T](either: Either[LpErrorResponse, T], errorHandler: LpErrorHandler) {

    def rightFuture = either.fold(handleError, Future.successful)

    def handleError(e: LpErrorResponse) = {
      errorHandler(e)
      Future.failed(LpException(e.error.message))
    }

  }

  implicit def eitherToRichEither[T](either: Either[LpErrorResponse, T])
                                    (implicit errorHandler: LpErrorHandler = _ => ()): RichEither[T] =
    new RichEither[T](either, errorHandler)


  type StringErrorHandler = (String => Unit)

  class RichStringErrorEither[T](either: Either[String, T], errorHandler: StringErrorHandler) {

    def rightFuture = either.fold(handleError, Future.successful)

    def handleError(e: String) = {
      errorHandler(e)
      Future.failed(new Exception(e))
    }

  }

  implicit def eitherToRichStringErrorEither[T](either: Either[String, T])
                                               (implicit errorHandler: StringErrorHandler = _ => ()): RichStringErrorEither[T] =
    new RichStringErrorEither[T](either, errorHandler)

}
