package controllers

import akka.event.LoggingAdapter
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import apis.telegram.{TelegramJsonSupport, TelegramUpdate}
import com.google.inject.Inject

/**
  * Created by markmo on 10/11/2016.
  */
class TelegramController @Inject()(logger: LoggingAdapter) extends TelegramJsonSupport {

  import StatusCodes._

  val routes =
    path("telegram-webhook") {
      post {
        logger.info("telegram-webhook called")
        entity(as[TelegramUpdate]) { update =>
          complete(OK)
        }
      }
    }

}
