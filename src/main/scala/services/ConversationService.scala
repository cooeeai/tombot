package services

import akka.actor.{ActorRef, ActorSystem}
import akka.event.LoggingAdapter
import com.google.inject.{Inject, Injector, Singleton}
import engines.{ConciergeActor, LookupBusImpl}
import modules.akkaguice.ActorInject

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

/**
  * Created by markmo on 13/08/2016.
  */
@Singleton
class ConversationService @Inject()(logger: LoggingAdapter,
                                    userService: UserService,
                                    bus: LookupBusImpl,
                                    system: ActorSystem,
                                    val injector: Injector)
  extends Conversation with ActorInject {

  import system.dispatcher

  implicit val timeout: akka.util.Timeout = 30 seconds

  def converse(sender: String, message: Any): Unit = {
    logger.debug("looking up user linked to sender [{}]", sender)
    val user = userService.getUser(sender)
    val uid = user match {
      case Some(u) => u.id
      case None => sender
    }
    logger.debug("looking up actor for user/{}", uid)
    system.actorSelection("user/" + uid).resolveOne onComplete {
      case Success(ref) =>
        logger.debug("found actor")
        ref ! message
      case Failure(e) =>
        logger.debug(e.getMessage)
        logger.debug("creating new actor")
        val ref = injectTopActor[ConciergeActor](uid)
        bus subscribe(ref, s"authenticated:$sender")
        bus subscribe(ref, s"delivered:$sender")
        ref ! message
    }
  }

  def getConversationActor(sender: String): Future[ActorRef] = {
    logger.debug("looking up user linked to sender [{}]", sender)
    val user = userService.getUser(sender)
    val uid = user match {
      case Some(u) => u.id
      case None => sender
    }
    val p = Promise[ActorRef]()
    logger.debug("looking up actor for user/{}", uid)
    system.actorSelection("user/" + uid).resolveOne onComplete {
      case Success(ref) =>
        logger.debug("found actor")
        p success ref
      case Failure(e) =>
        logger.debug(e.getMessage)
        logger.debug("creating new actor")
        val ref = injectTopActor[ConciergeActor](uid)
        bus subscribe(ref, s"authenticated:$sender")
        bus subscribe(ref, s"delivered:$sender")
        p success ref
    }
    p.future
  }

}
