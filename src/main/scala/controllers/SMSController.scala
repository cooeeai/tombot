package controllers

import akka.event.LoggingAdapter
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import apis.telstra.{SMSMessage, SMSReply, TelstraJsonSupport}
import com.google.inject.Inject
import com.typesafe.config.Config
import models.Platform
import models.events.TextResponse
import services.{Conversation, SMSService}

import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Created by markmo on 15/10/2016.
  */
class SMSController @Inject()(config: Config,
                              logger: LoggingAdapter,
                              smsService: SMSService,
                              conversationService: Conversation)
  extends TelstraJsonSupport {

  import Platform._
  import StatusCodes._
  import conversationService._

  val routes =
    path("sms") {
      post {
        logger.info("SMS webhook called")
        entity(as[SMSReply]) { reply =>
          // TODO
          // cache messageId and sender details
          smsService.getMessageStatus(reply.messageId) map { messageStatus =>
            // no need to check status as message has been delivered
            val sender = messageStatus.to
            converse(sender, TextResponse(SMS, sender, reply.content))
          }
          complete(OK)
        }
      }
    } ~
    path("sms-send") {
      post {
        logger.info("SMS send request")
        entity(as[SMSMessage]) { message =>
          smsService.sendTextMessage(message.to, message.body)
          complete(OK)
        }
      }
    }

}
