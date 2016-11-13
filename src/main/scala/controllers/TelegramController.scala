package controllers

import akka.event.LoggingAdapter
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import apis.telegram.{TelegramJsonSupport, TelegramUpdate}
import com.google.inject.{Singleton, Inject}
import services.TelegramService
import spray.json._

import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Created by markmo on 10/11/2016.
  */
@Singleton
class TelegramController @Inject()(logger: LoggingAdapter,
                                   telegramService: TelegramService)
  extends TelegramJsonSupport {

  import StatusCodes._

  telegramService.setWebhook() map { result =>
    if (result.result) {
      logger.info("setup of Telegram webhook successful")
      telegramService.getWebhookInfo map { info =>
        logger.debug(info.toJson.prettyPrint)
      }
    } else {
      logger.error("setup of Telegram webhook failed")
    }
  }

  val routes =
    path("telegram-webhook") {
      post {
        logger.info("telegram-webhook called")
        entity(as[TelegramUpdate]) { update =>
          val message = update.message
          message match {
            case Some(msg) =>
              val chat = msg.chat
              telegramService.sendMessage(chat.id, "Hello World")
            case None => // do nothing
          }
          complete(OK)
        }
      }
    }

}
