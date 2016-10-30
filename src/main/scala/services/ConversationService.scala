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

  implicit val timeout: Timeout = 60 seconds

  def converse(sender: String, message: Any): Unit = {
    logger.debug(s"looking up user linked to sender [$sender]")
    val user = userService.getUser(sender)
    val uid = user match {
      case Some(u) => u.id
      case None => sender
    }
    logger.debug(s"looking up actor for user/" + uid)
    system.actorSelection("user/" + uid).resolveOne() onComplete {
      case Success(ref) =>
        logger.debug("found actor")
        ref ! message
      case Failure(e) =>
        logger.debug(e.getMessage)
        logger.debug("creating new actor")
        val ref = system.actorOf(GuiceAkkaExtension(system).props(ConciergeActor.name), uid)
        bus subscribe(ref, s"authenticated:$sender")
        ref ! message
    }
  }

}
