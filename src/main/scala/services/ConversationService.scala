package services

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.util.Timeout
import com.google.inject.Inject
import com.typesafe.config.Config
import conversationengine.{ConciergeActor, LookupBusImpl}
import modules.akkaguice.GuiceAkkaExtension

import scala.concurrent.duration._
import scala.util.{Failure, Success}

/**
  * Created by markmo on 13/08/2016.
  */
class ConversationService @Inject()(config: Config,
                                    logger: LoggingAdapter,
                                    userService: UserService,
                                    bus: LookupBusImpl,
                                    system: ActorSystem)
  extends Conversation {

  import system.dispatcher

  implicit val timeout: Timeout = 30 seconds

  def converse(sender: String, message: Any): Unit = {
    logger.debug(s"looking up user linked to sender [$sender]")
    val user = userService.getUser(sender)
    val userId = user match {
      case Some(usr) => usr.id
      case None => sender
    }
    logger.debug(s"looking up actor for user/" + userId)
    system.actorSelection("user/" + userId).resolveOne().onComplete {
      case Success(actor) =>
        logger.debug("found actor")
        actor ! message
      case Failure(ex) =>
        logger.debug(ex.getMessage)
        logger.debug("creating new actor")
        val actor = system.actorOf(GuiceAkkaExtension(system).props(ConciergeActor.name), userId)
        bus.subscribe(actor, s"authenticated:$userId")
        //bus.subscribe(actor, s"fallback:$userId")
        actor ! message
    }
  }

}
