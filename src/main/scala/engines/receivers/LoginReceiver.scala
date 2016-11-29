package engines.receivers

import akka.actor.{ActorLogging, ActorRef, FSM}
import engines.ComplexConversationActor.{ConversationContext, Data, State, TransferState}
import engines.{LookupBusImpl, MsgEnvelope}
import models.events._

import scala.concurrent.duration._
import scala.util.{Failure, Success}

/**
  * Created by markmo on 18/11/2016.
  */
trait LoginReceiver extends ActorLogging with FSM[State, Data] {

  val bus: LookupBusImpl
  val historyActor: ActorRef
  implicit val timeout: akka.util.Timeout

  import context.dispatcher

  val loginReceive: StateFunction = {

    case Event(Login(ev, sender, text), ctx: ConversationContext) =>
      if (ctx.authenticated) {
        self ! ev
        stay
      } else {
        historyActor ! Exchange(Some(text), "login")
        ctx.provider ! TextMessage(sender, "I need to confirm your identity if that is OK")
        ctx.provider ! LoginCard(sender)
        stay using ctx.copy(postAction = Some(ctx => {
          self ! ev
          stay
        }))
      }

    case Event(Welcome(platform, sender), ctx: ConversationContext) =>
      ctx.provider ! TextMessage(sender, "Welcome, login successful")
      bus publish MsgEnvelope(s"authenticated:$sender", Authenticated(sender, self))
      stay

    case Event(Authenticated(sender, ref), ctx: ConversationContext) => {
      log.debug("self {}", self.toString())
      log.debug("ref {}", ref.toString())
      if (self != ref) {
        log.debug("transferring state")

        // lookup the concierge actor (grandparent)
        context.actorSelection("../..").resolveOne onComplete {
          case Success(subscriber) =>
            bus unsubscribe subscriber
            ref ! TransferState(sender, ctx)
            context.system.scheduler.scheduleOnce(60 seconds) {
              context stop subscriber
            }
          case Failure(e) =>
            log.error(e, e.getMessage)
        }

      } else {
        log.debug("re-authenticating")
        self ! TransferState(sender, ctx)
      }
      stay
    }

    case Event(TransferState(sender, ctx), _) =>
      ctx.postAction match {
        case Some(action) =>
          historyActor ! Exchange(None, "logged in")
          action(ctx.copy(authenticated = true, postAction = None))
        case None =>
          historyActor ! Exchange(None, "logged in")
          stay using ctx.copy(authenticated = true)
      }

  }
}
