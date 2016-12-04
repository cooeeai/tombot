package engines

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.contrib.pattern.ReceivePipeline
import akka.pattern.ask
import akka.util.Timeout
import apis.facebookmessenger.{FacebookMessageDeliveredEvent, FacebookMessageReadEvent}
import apis.witapi.WitJsonSupport
import com.google.inject.assistedinject.Assisted
import com.google.inject.{Inject, Injector}
import com.typesafe.config.Config
import engines.AnalyzeActor.Analyze
import engines.GreetActor.Greet
import engines.interceptors.{LoggingInterceptor, PlatformSwitchInterceptor}
import example.BuyConversationActor
import example.BuyConversationActor.Qualify
import models.events._
import models.{IntentType, Platform}
import modules.akkaguice.{ActorInject, NamedActor}
import services.{IntentService, UserService}
import spray.json._
import utils.General

import scala.concurrent.duration._
import scala.util.Random

/**
  * Created by markmo on 18/09/2016.
  */
class IntentActor @Inject()(config: Config,
                            intentService: IntentService,
                            userService: UserService,
                            val injector: Injector,
                            buyIntentActorFactory: BuyConversationActor.Factory,
                            greetIntentActorFactory: GreetActor.Factory,
                            @Assisted historyActor: ActorRef)
  extends Actor
    with ActorInject
    with ActorLogging
    with WitJsonSupport
    with ReceivePipeline
    with LoggingInterceptor
    with PlatformSwitchInterceptor
    with General {

  import IntentActor._
  import Platform._
  import context.dispatcher

  implicit val timeout: Timeout = 30 seconds

  val maxFailCount = config.getInt("settings.max-fail-count")
  val maxMessageLength = 300

  val defaultPlatform = Facebook
  val defaultProvider = provider(defaultPlatform)

  /**
    * Create actors for each intent
    *
    * I considered lazy creation, however, since checking an actor's existence
    * returns a Future, creating the actors up front results in simpler code.
    */
  override def preStart: Unit = {
    injectActor(greetIntentActorFactory(defaultProvider, historyActor), "greet")
    injectActor(buyIntentActorFactory(defaultProvider, historyActor), "buy")
    injectActor(buyIntentActorFactory(defaultProvider, historyActor), "analyze")
  }

  def receive = withoutIntent(defaultProvider, 0)

  def withIntent(provider: ActorRef, intent: Intent, failCount: Int): Receive =
    defaultReceive(provider) orElse {

      case ev@(_: QuickReplyResponse | _: TextResponse) =>
        // bypass intent parsing, pass message to current intent
        context.actorSelection(intent.intent) ! ev

      case Unhandled(ev: TextResponse) =>
        context become withoutIntent(provider, failCount)
        self ! ev

      case ev@SetProvider(_, _, ref, _, _, _) =>
        context become withIntent(ref, intent, failCount)
        IntentType.values foreach { in =>
          context.actorSelection(in.toString) ! ev
        }

    }

  def withoutIntent(provider: ActorRef, failCount: Int): Receive =
    defaultReceive(provider) orElse {

/*      case TextResponse(platform, sender, text) =>
        // determine intent
        intentService.getIntent(text) map { meaning =>
          log.debug("received meaning:\n{}", meaning.toJson.prettyPrint)
          meaning.getIntent match {

            case Some(in@"greet") =>
              log.debug("responding to [greet] intent")
              // not a conversational intent, therefore do not become
              log.debug("looking up user with id [{}]", sender)
              userService.getUser(sender) match {
                case Some(user) =>
                  log.debug("found user: {}", user)
                  context.actorSelection(in) ! Greet(platform, sender, user, text)
                case None =>
                  log.warning("user not found")
              }

            case Some(in@"buy") =>
              log.debug("responding to [buy] intent")
              val productType = meaning.getEntityValue("product_type")
              context become withIntent(provider, Intent(in), failCount)
              context.actorSelection(in) ! Qualify(platform, sender, productType, text)

            case Some(in@"analyze") =>
              log.debug("responding to [analyze] intent")
              // not a conversational intent, therefore do not become
              context.actorSelection(in) ! Analyze(platform, sender, text)

            case _ =>
              log.warning("responding to [unknown] intent")
              val k = failCount + 1
              log.debug("fail count={}", k)
              if (k > maxFailCount) {
                context become withoutIntent(provider, 0)
                ask(historyActor, GetHistory).mapTo[History] onComplete { history =>
                  context.parent ! Fallback(sender, history.get)
                }
              } else {
                val message = randomShrug
                historyActor ! Exchange(Some(text), message)
                context become withoutIntent(provider, k)
                provider ! TextMessage(sender, message)
              }
          }
        }*/

      case ev@SetProvider(_, _, ref, _, _, _) =>
        context become withoutIntent(ref, failCount)
        IntentType.values foreach { in =>
          context.actorSelection(in.toString) ! ev
        }

    }

  def defaultReceive(provider: ActorRef): Receive = {

    case ev@(_: FacebookMessageDeliveredEvent | _: FacebookMessageReadEvent) =>
      provider ! ev

    case ShowHistory(sender) =>
      ask(historyActor, GetHistory).mapTo[History] onComplete { history =>
        sendMultiMessage(provider, maxMessageLength, sender, formatHistory(history.get))
      }

    case ev@(_: Fallback | _: FillForm) =>
      context.parent ! ev

    case Reset =>
      IntentType.values foreach { in =>
        context.actorSelection(in.toString) ! Reset
      }

  }

  def formatHistory(history: History) =
    history map {
      case Exchange(Some(request), response) => s"$request <- $response"
      case Exchange(None, response) => s" <- $response"
    } mkString "\n"

}

object IntentActor extends NamedActor with General {

  override final val name = "IntentActor"

  trait Factory {
    def apply(historyActor: ActorRef): Actor
  }

  final case class Intent(intent: String)

  val random = new Random

  val shrugs = Vector(
    "I'm sorry, I did not understand. These arms aren't even real.",
    "I'm sorry. I'm having trouble understanding the question.",
    "I think I may have misunderstood your last statement.",
    "I'm sorry. I didn't quite grasp what you just said.",
    "I don't think I'm qualified to answer that yet.",
    "I'm a bit confused by that last part.",
    "I'm not totally sure about that.",
    "I'm not sure I follow.",
    "I'm afraid I don't understand.",
    "I'm a bit confused."
  )

  def randomShrug = shrugEmoji + shrugs(random.nextInt(shrugs.size))

}