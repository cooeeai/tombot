package controllers

import akka.event.LoggingAdapter
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import apis.wva.{WvaJsonSupport, WvaMessageRequest}
import com.google.inject.{Inject, Injector}
import models.Platform
import models.events.{InitiateChat, TextResponse}
import modules.akkaguice.ActorInject
import spray.json._

import scala.concurrent.Future
import scala.concurrent.duration._

/**
  * Created by markmo on 18/12/2016.
  */
class WvaChatController @Inject()(logger: LoggingAdapter,
                                  val injector: Injector)
  extends WvaJsonSupport with ActorInject {

  import Platform._
  import StatusCodes._

  implicit val timeout: akka.util.Timeout = 5 seconds

  val actor = injectTopActor[SynchronousRequestActor]("synchronous-request-actor")

  val routes =
    pathPrefix("bots") {
      logger.debug("WVA bots endpoint called")
      pathPrefix(Segment) { botId =>
        logger.debug("with bot ID {}", botId)
        pathPrefix("dialogs") {
          pathEndOrSingleSlash {
            post {
              logger.debug("post received")
              entity(as[WvaMessageRequest]) { req =>
                logger.debug(req.toJson.prettyPrint)
                // initiate chat
                val sender = req.userID.getOrElse("1234")
                complete {
                  ask(actor, InitiateChat(WVA, sender))
                    .mapTo[Future[JsValue]]
                }
              }
            }
          } ~
          pathPrefix(Segment) { chatId =>
            pathPrefix("messages") {
              pathEndOrSingleSlash {
                post {
                  entity(as[WvaMessageRequest]) { req =>
                    req.message match {
                      case Some(message) =>
                        // send message
                        val sender = req.userID.getOrElse("1234")
                        complete {
                          ask(actor, TextResponse(WVA, sender, message))
                            .mapTo[Future[JsValue]]
                        }
                      case None =>
                        logger.debug("no message posted")
                        complete(OK)
                    }
                  }
                }
              }
            }
          }
      }
    }
  }

}
