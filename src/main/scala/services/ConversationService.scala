package services

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.util.Timeout
import modules.akkaguice.GuiceAkkaExtension
import com.google.inject.Inject
import com.typesafe.config.Config
import conversationengine.ConversationActor

import scala.concurrent.duration._
import scala.util.{Failure, Success}

/**
  * Created by markmo on 13/08/2016.
  */
class ConversationService @Inject()(config: Config,
                                    logger: LoggingAdapter,
                                    system: ActorSystem) {

  import system.dispatcher

  implicit val timeout: Timeout = 5.seconds

  def converse(sender: String, message: Any) = {
    logger.debug(s"looking up actor for user/" + sender)
    system.actorSelection("user/" + sender).resolveOne().onComplete {
      case Success(actor) =>
        logger.debug("found actor")
        actor ! message
      case Failure(ex) =>
        logger.debug(ex.getMessage)
        logger.debug("creating new actor")
        val actor = system.actorOf(GuiceAkkaExtension(system).props(ConversationActor.name), sender)
        actor ! message
    }
  }

}
