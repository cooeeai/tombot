package engines

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
import engines.interceptors.{EmojiInterceptor, LoggingInterceptor, PlatformSwitchInterceptor}
import example.BuyConversationActor
import models.events._
import models.{ConversationEngine, IntentResolutionEvaluationStrategy, IntentResolutionSelectionStrategy}
import modules.akkaguice.{ActorInject, NamedActor}
import services.SparkSendQueue.{TextMessage => SparkTextMessage}
import services._
import utils.General

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Random, Success}

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
                               formActorFactory: FormActor.Factory,
                               watsonConversationFactory: WatsonConversationActor.Factory,
                               wvaConversationFactory: WvaConversationActor.Factory)
  extends ActorInject
    with ActorLogging
    with ReceivePipeline
    with LoggingInterceptor
    with PlatformSwitchInterceptor
    with EmojiInterceptor
    with FutureExtensions
    with General
    with FSM[State, Data] {

  import ConciergeActor._
  import ConversationEngine._
  import IntentResolutionSelectionStrategy._
  import context.dispatcher

  implicit val akkaTimeout: akka.util.Timeout = 30 seconds

  val defaultConversationEngine = ConversationEngine.withName(config.getString("settings.default-engine"))
  val maxFailCount = config.getInt("settings.max-fail-count")
  val maxMessageLength = config.getInt("settings.max-message-length")
  val voteThreshold = config.getDouble("settings.vote-threshold")

  val intentResolutionEvaluationStrategy =
    IntentResolutionEvaluationStrategy withName config.getString("settings.intent-resolution-strategy.evaluation")
  val intentResolutionSelectionStrategy =
    IntentResolutionSelectionStrategy withName config.getString("settings.intent-resolution-strategy.selection")

  val defaultProvider = injectActor[FacebookSendQueue]("provider")
  val defaultAgentProvider = injectActor[SparkSendQueue]("agentProvider")
  val historyActor = injectActor[HistoryActor]("history")
  val greetActor = injectActor(greetActorFactory(defaultProvider, historyActor), "greet")
  val formActor = injectActor(formActorFactory(defaultProvider), "form")
  val liveAgentActor = injectActor(liveAgentActorFactory(defaultProvider, defaultAgentProvider, historyActor), "agent")
  val defaultConversationActor = getConversationActor(defaultConversationEngine)

  // TODO
  // do these need to be children?
  // sequence in preferred order of execution
  val intentResolvers = List(
    injectActor[CommandIntentActor]("command"),
    injectActor[WitIntentActor]("wit"),
    injectActor[RuleIntentActor]("rule"),
    injectActor[ApiAiIntentActor]("apiai"),
    injectActor[WolframAlphaIntentActor]("alpha")
  )

  val initialData = ConciergeContext(
    provider = defaultProvider,
    child = defaultConversationActor,
    tempMemberships = Map[String, SparkTempMembership](),
    agentName = "Mark")

  if (defaultConversationEngine == WVA) {
    // let WVA resolve intent
    startWith(WithIntent, initialData)
  } else {
    startWith(WithoutIntent, initialData)
  }

  when(WithoutIntent) {

    case Event(ev: TextResponse, _) =>
      resolveIntent2(ev)
      stay

    case Event(StartMultistep, _) =>
      goto(WithIntent)

  }

  when(WithIntent) {

    case Event(ev: TextResponse, ctx: ConciergeContext) =>
      // TODO
      // was this a mistake?
      //ctx.provider ! ev
      ctx.child ! ev
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

    case Event(ev: InitiateChat, ctx: ConciergeContext) =>
      ctx.child ! ev
      stay

    case Event(IntentVote(_, ev, multistep), ctx: ConciergeContext) =>
      if (multistep) {
        self ! StartMultistep
      }
      self ! ev
      stay

    case Event(IntentUnknown(sender, text), ctx: ConciergeContext) =>
      log.debug("intent unknown")
      if (ctx.failCount > maxFailCount) {
        self ! Fallback(sender, Nil)
        goto(WithoutIntent) using ctx.copy(failCount = 0)
      } else {
        val message = smallTalkService.getSmallTalkResponse(sender, text)
        self ! Say(sender, text, message)
        goto(WithoutIntent) using ctx.copy(failCount = ctx.failCount + 1)
      }

    case Event(Unhandled(ev@TextResponse(_, sender, _, _)), ctx: ConciergeContext) =>
      if (ctx.failCount > maxFailCount) {
        self ! Fallback(sender, Nil)
        goto(WithoutIntent) using ctx.copy(failCount = 0)
      } else {
        // TODO
        // is this the best pattern to resend message after state change?
        context.system.scheduler.scheduleOnce(50 milliseconds) {
          self ! ev
        }
        goto(WithoutIntent) using ctx.copy(failCount = ctx.failCount + 1)
      }

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
      if (handleEventImmediately && wrappedEvent != NullEvent) {
        log.debug("handle {} immediately", wrappedEvent)
        self ! wrappedEvent
        val notification = ev.copy(event = NullEvent)
        ctx.child ! notification
        formActor ! notification
        liveAgentActor ! notification
      } else {
        ctx.child ! ev
        formActor ! ev
        liveAgentActor ! ev
      }
      stay using ctx.copy(provider = ref)

    case Event(SetEngine(sender, engine), ctx: ConciergeContext) =>
      ctx.provider ! TextMessage(sender, "set conversation engine to " + engine)
      stay using ctx.copy(child = getConversationActor(engine))

    case Event(Fallback(sender, history), ctx: ConciergeContext) =>
      val message = "Hold on...transferring you to one of my human coworkers"
      ctx.provider ! TextMessage(sender, message)
      ctx.provider ! TransferToAgent
      //      for {
      //        tempMembership <- sparkService.setupTempRoom(sender)
      //          .withTimeout(new TimeoutException("future timed out"))(futureTimeout, context.system)
      //      } yield {
      //        log.debug("setup temporary membership to room [{}] for sender [{}]", tempMembership.roomId, sender)
      //
      //        // print transcript history
      //        history map {
      //          case Exchange(Some(request), response) => s"user: $request\ntombot: $response"
      //          case Exchange(None, response) => s"tombot: $response"
      //        } foreach { text =>
      //          liveAgentActor ! SparkTextMessage(Some(tempMembership.roomId), None, None, text, None)
      //        }
      //        self ! UpdateTempMemberships(ctx.tempMemberships + (sender -> tempMembership))
      //      }
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
      // TODO
      // required?
      //ctx.child ! ev
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
        if (intentResolutionSelectionStrategy == TopScore) {
          val sorted =
            votes
              .filter(_.probability > voteThreshold)
              .sortBy(-_.probability)
          sorted match {
            case x :: _ =>
              log.debug("winning vote: {}", x)
              self ! x
            case _ =>
              self ! IntentUnknown(ev.sender, ev.text)
          }
        } else {
          // random
          val filtered = votes.filter(_.probability > voteThreshold)
          if (filtered.isEmpty) {
            self ! IntentUnknown(ev.sender, ev.text)
          } else {
            val winningVote = filtered(random.nextInt(filtered.length))
            log.debug("winning vote: {}", winningVote)
            self ! winningVote
          }
        }

      case Failure(e) =>
        log.error(e, e.getMessage)
        self ! IntentUnknown(ev.sender, ev.text)

    }
  }

  // bail out at first minimally viable vote
  def resolveIntent2(ev: TextResponse): Unit = {

    def loop(resolvers: List[ActorRef]): Future[Option[IntentVote]] = resolvers match {
      case Nil => Future.successful(None)
      case x :: xs =>
        ask(x, ev).mapTo[IntentVote] flatMap { vote =>
          if (vote.probability > voteThreshold) {
            log.debug("first minimally viable vote: {}", vote)
            Future.successful(Some(vote))
          } else {
            loop(xs)
          }
        }
    }

    loop(intentResolvers) map {
      case Some(vote) =>
        self ! vote
      case None =>
        IntentUnknown(ev.sender, ev.text)
    }
  }

  // bail out at first certain vote
  def resolveIntent3(ev: TextResponse): Unit = {

    def loop(resolvers: List[ActorRef], votes: List[IntentVote]): Future[List[IntentVote]] =
      resolvers match {
        case Nil => Future.successful(votes)
        case x :: xs =>
          ask(x, ev).mapTo[IntentVote] flatMap { vote =>
            if (vote.probability == 1.0) {
              log.debug("winning vote: {}", vote)
              Future.successful(vote :: votes)
            } else {
              loop(xs, vote :: votes)
            }
          }
      }

    loop(intentResolvers, Nil) map {
      case x :: xs =>
        if (intentResolutionSelectionStrategy == TopScore) {
          self ! (x :: xs).sortBy(-_.probability).head
        } else {
          // random
          val list = x :: xs
          self ! list(random.nextInt(list.length))
        }
      case Nil =>
        IntentUnknown(ev.sender, ev.text)
    }
  }

  def getConversationActor(engine: ConversationEngine): ActorRef = engine match {
    case Watson => injectActor(watsonConversationFactory(defaultProvider, historyActor), "watson")
    case WVA => injectActor(wvaConversationFactory(defaultProvider, historyActor), "wva")
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
                              agentName: String,
                              failCount: Int = 0) extends Data

  val random = new Random

}