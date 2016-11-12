package conversationengine

import akka.actor.{Actor, ActorLogging, FSM}
import akka.contrib.pattern.ReceivePipeline
import apis.witapi.WitJsonSupport
import com.google.inject.{Inject, Injector}
import conversationengine.IntentActor.{Data, State}
import conversationengine.events._
import modules.akkaguice.{ActorInject, NamedActor}
import services.{IntentService, UserService}
import spray.json._

import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Created by markmo on 18/09/2016.
  */
class IntentActor @Inject()(sender: String,
                            intentService: IntentService,
                            userService: UserService,
                            val injector: Injector)
  extends Actor
    with ActorInject
    with ActorLogging
    with WitJsonSupport
    with ReceivePipeline
    with LoggingInterceptor
    with FSM[State, Data] {

  import IntentActor._

  val child = injectActor[ConversationActor]("buy")

  startWith(Active, Uninitialized)

  when(Active) {

    case Event(ev: Confirm, _) =>
      child ! ev
      stay

    case Event(ev: TextLike, _) =>
      val platform = ev.platform
      val sender = ev.sender
      val text = ev.text

      intentService.getIntent(text) map { meaning =>
        log.debug("received meaning:\n" + meaning.toJson.prettyPrint)
        val intent = meaning.getIntent
        log.debug("intent: " + intent.getOrElse("None"))

        intent match {

          case Some("buy") =>
            log.debug("responding to [buy] intent")
            child ! Qualify(platform, sender, meaning.getEntityValue("product_type"), text)

          case Some("greet") =>
            log.debug("responding to [greet] intent")
            log.debug(s"looking up user with id [$sender]")

            userService.getUser(sender) match {
              case Some(user) =>
                log.debug("user: " + user)
                child ! Greet(platform, sender, user, text)
              case None =>
                log.warning("user not found")
            }

          case Some("analyze") =>
            log.debug("responding to [analyze] intent")
            child ! Analyze(platform, sender, text)

          case _ =>
            log.debug("responding to [unknown] intent")
            child ! Respond(platform, sender, text)

        }
      }
      stay

    case Event(Deactivate, _) =>
      goto(Inactive)

  }

  when(Inactive) {

    case Event(Activate, _) =>
      goto(Active)

  }

  whenUnhandled {

    case Event(Reset, _) =>
      child ! Reset
      goto(Active)

    // TODO
    case Event(ev: Fallback, _) =>
      context.parent ! ev
      stay

    case Event(ev: FillForm, _) =>
      context.parent ! ev
      stay

    case Event(ev, _) =>
      child ! ev
      stay

  }

  initialize()

}

object IntentActor extends NamedActor {

  override final val name = "IntentActor"

  sealed trait State
  case object Active extends State
  case object Inactive extends State

  sealed trait Data
  case object Uninitialized extends Data

}