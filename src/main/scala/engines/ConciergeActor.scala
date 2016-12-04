package engines

import java.util.concurrent.TimeoutException

import akka.actor._
import akka.contrib.pattern.ReceivePipeline
import akka.pattern.ask
import apis.ciscospark.SparkTempMembership
import apis.facebookmessenger.{FacebookMessageDeliveredEvent, FacebookMessageReadEvent}
import com.google.inject.{Inject, Injector}
import com.typesafe.config.Config
import engines.AgentConversationActor.{SparkMessageEvent, SparkRoomLeftEvent, SparkWrappedEvent}
import engines.ConciergeActor.{Data, State}
import engines.GreetActor.Greet
import engines.interceptors.{LoggingInterceptor, PlatformSwitchInterceptor}
import example.BuyConversationActor
import models.ConversationEngine
import models.events._
import modules.akkaguice.{ActorInject, NamedActor}
import services.SparkSendQueue.{TextMessage => SparkTextMessage}
import services._
import utils.General

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

/**
  * Created by markmo on 9/09/2016.
  */
class ConciergeActor @Inject()(config: Config,
                               sparkService: SparkService,
                               smallTalkService: SmallTalkService,
                               val injector: Injector,
                               greetActorFactory: GreetActor.Factory,
                               buyIntentActorFactory: BuyConversationActor.Factory,
                               liveAgentActorFactory: AgentConversationActor.Factory,
                               formActorFactory: FormActor.Factory)
  extends ActorInject
    with ActorLogging
    with ReceivePipeline
    with LoggingInterceptor
    with PlatformSwitchInterceptor
    with FutureExtensions
    with General
    with FSM[State, Data] {

  import ConciergeActor._
  import ConversationEngine._
  import context.dispatcher

  implicit val akkaTimeout: akka.util.Timeout = 30 seconds

  val defaultConversationEngine = ConversationEngine.withName(config.getString("settings.default-engine"))
  val maxMessageLength = config.getInt("settings.max-message-length")
  val voteThreshold = config.getDouble("settings.vote-threshold")

  val defaultProvider = injectActor[FacebookSendQueue]("provider")
  val defaultAgentProvider = injectActor[SparkSendQueue]("agentProvider")
  val historyActor = injectActor[HistoryActor]("history")
  val greetActor = injectActor(greetActorFactory(defaultProvider, historyActor), "greet")
  val formActor = injectActor(formActorFactory(defaultProvider), "form")
  val liveAgentActor = injectActor(liveAgentActorFactory(defaultProvider, defaultAgentProvider, historyActor), "agent")
  val defaultConversationActor = getConversationActor(defaultConversationEngine)

  // TODO
  // do these need to be children?
  val intentResolvers = Vector(
    injectActor[CommandIntentActor]("command"),
    injectActor[RuleIntentActor]("rule"),
    injectActor[WitIntentActor]("wit")
    //    injectActor[ApiAiIntentActor]("apiai"),
    //    injectActor[WolframAlphaIntentActor]("alpha")
  )

  val initialData = ConciergeContext(
    provider = defaultProvider,
    child = defaultConversationActor,
    tempMemberships = Map[String, SparkTempMembership](),
    agentName = "Mark")

  startWith(WithoutIntent, initialData)

  when(WithoutIntent) {

    case Event(ev: TextResponse, _) =>
      resolveIntent(ev)
      stay

    case Event(StartMultistep, _) =>
      goto(WithIntent)

  }

  when(WithIntent) {

    case Event(ev: TextResponse, ctx: ConciergeContext) =>
      ctx.provider ! ev
      stay

  }

  when(FillingForm) {

    case Event(ev: TextResponse, _) =>
      formActor ! ev
      stay

    case Event(ev: EndFillForm, ctx: ConciergeContext) =>
      ctx.child ! ev
      goto(WithoutIntent)

  }

  when(UsingHuman) {

    case Event(ev: SparkMessageEvent, _) =>
      liveAgentActor ! ev
      stay

    case Event(SparkRoomLeftEvent(sender), ctx: ConciergeContext) =>
      val message = s"${ctx.agentName} (Human) is leaving the conversation"
      ctx.provider ! TextMessage(sender, message)
      val tempMembership = ctx.tempMemberships(sender)
      sparkService.deleteWebhook(tempMembership.leaveRoomWebhookId)
      sparkService.deleteWebhook(tempMembership.webhookId)
      sparkService.deleteTeam(tempMembership.teamId)
      goto(WithoutIntent)

    case Event(ev: TextResponse, ctx: ConciergeContext) =>
      val tempMembership = ctx.tempMemberships(ev.sender)
      liveAgentActor ! SparkWrappedEvent(tempMembership.roomId, tempMembership.personId, ev)
      stay

  }

  whenUnhandled {

    case Event(IntentVote(_, ev, multistep), ctx: ConciergeContext) =>
      if (multistep) {
        self ! StartMultistep
      }
      self ! ev
      stay

    case Event(IntentUnknown(sender, text), _) =>
      log.debug("intent unknown")
      val message = smallTalkService.getSmallTalkResponse(sender, text)
      self ! Say(sender, text, message)
      stay

    case Event(ev: Greet, _) =>
      greetActor ! ev
      stay

    case Event(ev: QuickReplyResponse, ctx: ConciergeContext) =>
      ctx.child ! ev
      stay

    case Event(Say(sender, text, message), ctx: ConciergeContext) =>
      say(ctx.provider, historyActor, sender, text, message)
      stay

    case Event(ev@(_: FacebookMessageDeliveredEvent | _: FacebookMessageReadEvent | _: LoginCard), ctx: ConciergeContext) =>
      ctx.provider ! ev
      stay

    case Event(ShowHistory(sender), ctx: ConciergeContext) =>
      ask(historyActor, GetHistory).mapTo[History] onComplete { history =>
        sendMultiMessage(ctx.provider, maxMessageLength, sender, formatHistory(history.get))
      }
      stay

    case Event(ev@SetProvider(platform, _, ref, wrappedEvent, _, handleEventImmediately), ctx: ConciergeContext) =>
      currentPlatform = Some(platform)
      if (handleEventImmediately) {
        log.debug("handle {} immediately", wrappedEvent)
        self ! wrappedEvent
        ctx.child ! ev.copy(event = NullEvent)
      } else {
        ctx.child ! ev
      }
      formActor ! ev
      liveAgentActor ! ev
      stay using ctx.copy(provider = ref)

    case Event(SetEngine(sender, engine), ctx: ConciergeContext) =>
      ctx.provider ! TextMessage(sender, "set conversation engine to " + engine)
      stay using ctx.copy(child = getConversationActor(engine))

    case Event(Fallback(sender, history), ctx: ConciergeContext) =>
      val message = s"${ctx.agentName} (Human) is joining the conversation"
      ctx.provider ! TextMessage(sender, message)
      for {
        tempMembership <- sparkService.setupTempRoom(sender)
          .withTimeout(new TimeoutException("future timed out"))(futureTimeout, context.system)
      } yield {
        log.debug("setup temporary membership to room [{}] for sender [{}]", tempMembership.roomId, sender)

        // print transcript history
        history map {
          case Exchange(Some(request), response) => s"user: $request\ntombot: $response"
          case Exchange(None, response) => s"tombot: $response"
        } foreach { text =>
          liveAgentActor ! SparkTextMessage(Some(tempMembership.roomId), None, None, text, None)
        }
        self ! UpdateTempMemberships(ctx.tempMemberships + (sender -> tempMembership))
      }
      stay

    case Event(UpdateTempMemberships(tempMemberships), ctx: ConciergeContext) =>
      goto(UsingHuman) using ctx.copy(tempMemberships = tempMemberships)

    case Event(FillForm(sender, goal), _) =>
      formActor ! NextQuestion(sender)
      goto(FillingForm)

    // TODO
    case Event(ev: SparkMessageEvent, _) =>
      liveAgentActor ! ev
      goto(UsingHuman)

    case Event(Reset, ctx: ConciergeContext) =>
      formActor ! Reset
      ctx.child ! Reset
      goto(WithoutIntent) using initialData

    case Event(ev, ctx: ConciergeContext) =>
      log.warning("{} received unhandled request {} in state {}/{}", name, ev, stateName, ctx)
      ctx.child ! ev
      stay

  }

  initialize()

  def resolveIntent(ev: TextResponse): Unit = {
    log.debug("resolving intent")

    // TODO
    // parallelize
    // break at first certain score (1.0)
    Future.traverse(intentResolvers)(r => ask(r, ev).mapTo[IntentVote]) onComplete {

      case Success(votes) if votes.isEmpty =>
        log.debug("no votes returned")
        self ! IntentUnknown(ev.sender, ev.text)

      case Success(votes) if votes.nonEmpty =>
        val top = votes.sortBy(-_.probability).head
        if (top.probability > voteThreshold) {
          log.debug("winning vote: {}", top)
          self ! top
        } else {
          self ! IntentUnknown(ev.sender, ev.text)
        }

      case Failure(e) =>
        log.error(e, e.getMessage)
        self ! IntentUnknown(ev.sender, ev.text)

    }
  }

  def getConversationActor(engine: ConversationEngine): ActorRef = engine match {
    case Watson => injectActor[WatsonConversationActor]("watson")
    case Cooee => injectActor(buyIntentActorFactory(defaultProvider, historyActor), "cooee")
  }

  def formatHistory(history: History) =
    history map {
      case Exchange(Some(request), response) => s"$request <- $response"
      case Exchange(None, response) => s" <- $response"
    } mkString newLine

}

object ConciergeActor extends NamedActor {

  override final val name = "ConciergeActor"

  sealed trait State

  case object WithoutIntent extends State

  case object WithIntent extends State

  case object FillingForm extends State

  case object UsingHuman extends State

  sealed trait Data

  case class ConciergeContext(provider: ActorRef,
                              child: ActorRef,
                              tempMemberships: TempMembershipMap,
                              agentName: String) extends Data

}