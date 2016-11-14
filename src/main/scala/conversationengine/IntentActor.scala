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

/**
  * Created by markmo on 18/09/2016.
  */
class IntentActor @Inject()(intentService: IntentService,
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
  import context.dispatcher

  /**
    * Create actors for each intent
    *
    * I considered lazy creation, however, since checking an actor's existence
    * returns a Future, creating the actors up front results in simpler code.
    */
  override def preStart: Unit = {
    injectActor[ConversationActor]("greet")
    injectActor[ConversationActor]("buy")
    injectActor[ConversationActor]("analyze")
  }

  startWith(Active, Uninitialized)

  when(Active) {

    case Event(ev: Confirm, intent: Intent) =>
      context.actorSelection(intent.intent) ! ev
      stay

    case Event(ev: TextLike, _) =>
      val platform = ev.platform
      val sender = ev.sender
      val text = ev.text

      intentService.getIntent(text) map { meaning =>
        log.debug("received meaning:\n" + meaning.toJson.prettyPrint)
        meaning.getIntent match {
          case Some(intent@"greet") =>
            log.debug("responding to [greet] intent")
            self ! ChangeIntent(intent)
            log.debug(s"looking up user with id [$sender]")

            userService.getUser(sender) match {
              case Some(user) =>
                log.debug("found user: " + user)
                context.actorSelection(intent) ! Greet(platform, sender, user, text)

              case None =>
                log.warning("user not found")
            }

          case Some(intent@"buy") =>
            log.debug("responding to [buy] intent")
            self ! ChangeIntent(intent)
            val productType = meaning.getEntityValue("product_type")
            context.actorSelection(intent) ! Qualify(platform, sender, productType, text)

          case Some(intent@"analyze") =>
            log.debug("responding to [analyze] intent")
            self ! ChangeIntent(intent)
            context.actorSelection(intent) ! Analyze(platform, sender, text)

          case _ =>
            log.debug("responding to [unknown] intent")
          // TODO
          // specialized actor to handle unknown intents?
        }
      }
      stay

    case Event(ChangeIntent(intent), _) =>
      stay using Intent(intent)

    case Event(Deactivate, _) =>
      goto(Inactive)

  }

  when(Inactive) {

    case Event(Activate, _) =>
      goto(Active)

  }

  whenUnhandled {

    case Event(Reset, intent: Intent) =>
      IntentType.values foreach { x =>
        context.actorSelection(x.toString) ! Reset
      }
      goto(Active)

    // TODO
    case Event(ev: Fallback, _) =>
      context.parent ! ev
      stay

    case Event(ev: FillForm, _) =>
      context.parent ! ev
      stay

    case Event(ev, intent: Intent) =>
      // TODO
      // should this be under Inactive?
      // which non-TextLike events may be sent?
      context.actorSelection(intent.intent) ! ev
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
  final case class Intent(intent: String) extends Data

  final case class ChangeIntent(intent: String)

}