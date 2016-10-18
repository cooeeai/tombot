package controllers

import akka.event.LoggingAdapter
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import apis.telstra.{SMSReply, TelstraJsonSupport}
import com.google.inject.Inject
import com.typesafe.config.Config
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
            converse(messageStatus.to, reply.content)
          }
          complete(OK)
        }
      }
    }

}
