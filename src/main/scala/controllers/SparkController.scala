package controllers

import akka.event.LoggingAdapter
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import apis.ciscospark.{SparkJsonSupport, SparkWebhookResponse}
import com.google.inject.Inject
import com.typesafe.config.Config
import engines.AgentConversationActor.{SparkMessageEvent, SparkRoomLeftEvent}
import services.ConversationService
import spray.json.JsObject

/**
  * Created by markmo on 10/09/2016.
  */
class SparkController @Inject()(config: Config,
                                logger: LoggingAdapter,
                                conversationService: ConversationService)
  extends SparkJsonSupport {

  import StatusCodes._
  import conversationService._

  val routes =
    pathPrefix("sparkwebhook-message-created") {
      path(Segment) { sender =>
        post {
          logger.info("sparkwebhook message-created posted")
          //        entity(as[SparkMessageResponse]) { response =>
          //          converse(sender, SparkMessageEvent(sender, response.data))
          //          complete(OK)
          //        }
          entity(as[JsObject]) { json =>
            logger.debug("sparkwebhook response:\n{}", json.prettyPrint)
            val response = json.convertTo[SparkWebhookResponse]
            converse(sender, SparkMessageEvent(sender, response.data))
            complete(OK)
          }
        }
      }
    } ~
    pathPrefix("sparkwebhook-membership-deleted") {
      path(Segment) { sender =>
        post {
          logger.info("sparkwebhook membership-deleted posted")
          //        entity(as[SparkMessageResponse]) { response =>
          //          converse(sender, SparkMessageEvent(sender, response.data))
          //          complete(OK)
          //        }
          entity(as[JsObject]) { json =>
            logger.debug("sparkwebhook response:\n{}", json.prettyPrint)
            converse(sender, SparkRoomLeftEvent(sender))
            complete(OK)
          }
        }
      }
    }

}
