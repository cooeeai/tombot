package conversationengine

import java.util.concurrent.TimeoutException

import akka.actor._
import akka.contrib.pattern.ReceivePipeline
import akka.pattern.after
import akka.stream.Materializer
import apis.ciscospark.SparkTempMembership
import com.google.inject.Inject
import com.google.inject.name.Named
import com.typesafe.config.Config
import conversationengine.ConciergeActor.{Data, State}
import conversationengine.events._
import modules.akkaguice.{GuiceAkkaExtension, NamedActor}
import services._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.concurrent.duration._

/**
  * Created by markmo on 9/09/2016.
  */
class ConciergeActor @Inject()(config: Config,
                               @Named(FacebookService.name) provider: MessagingProvider,
                               sparkService: SparkService,
                               alchemyService: AlchemyService,
                               rulesService: RulesService,
                               implicit val system: ActorSystem,
                               implicit val fm: Materializer)
  extends Actor
    with ActorLogging
    with ReceivePipeline
    with LoggingInterceptor
    with FSM[State, Data] {

  import ConciergeActor._
  import rulesService._
  import utils.RegexUtils._

  implicit val timeout = 60 second

  implicit class FutureExtensions[T](f: Future[T]) {
    def withTimeout(timeout: => Throwable)(implicit duration: FiniteDuration, system: ActorSystem): Future[T] = {
      Future firstCompletedOf Seq(f, after(duration, system.scheduler)(Future.failed(timeout)))
    }
  }

  val conversationEngineDefault = config.getString("conversation.engine")

  // form conversation actor
  val form = context.actorOf(GuiceAkkaExtension(context.system).props(FormActor.name))

  // live agent conversation actor
  val agent = context.actorOf(GuiceAkkaExtension(context.system).props(AgentConversationActor.name))

  val LoginCommand = command("login")

  val HistoryCommand = command("history")

  val AlchemyCommand = command("alchemy")

  startWith(UsingBot, ConversationContext(
    botActor = getConversationActor(conversationEngineDefault), // bot conversation actor
    tempMemberships = Map[String, SparkTempMembership](),
    agentName = "Mark"))

  when(UsingBot) {

    case Event(ev: TextLike, ctx: ConversationContext) =>
      val platform = ev.platform
      val sender = ev.sender
      val text = ev.text

      if (LoginCommand matches text) {
        provider.sendLoginCard(sender)

      } else if (HistoryCommand matches text) {
        ctx.botActor ! ShowHistory(sender)

      } else if (AlchemyCommand matches text) {
        // alchemy command - show keywords
        val keywords = alchemyService.getKeywords(text.substring(8).trim)
        provider.sendTextMessage(sender, "Keywords:\n" + formatKeywords(keywords))

      } else if (isQuestion(text)) {
        // hears question
        log.debug("text is interpreted as a question")
        getContent(text) match {

          case Some(content) =>
            log.debug(s"found content in response to question [$content]")
            provider.sendTextMessage(sender, content)

          case None =>
            log.debug("no content")
            ctx.botActor ! Respond(platform, sender, text)

        }

      } else {
        ctx.botActor ! Respond(platform, sender, text)
      }
      stay using ctx

    case Event(Fallback(sender, history), ctx: ConversationContext) =>
      provider.sendTextMessage(sender, s"${ctx.agentName} (Human) is joining the conversation")
      lazy val fut = sparkService.setupTempRoom(sender) withTimeout new TimeoutException("future timed out")
      val tempMembership = Await.result(fut, timeout)
      log.debug(s"setting up temporary membership to room [${tempMembership.roomId}] for sender [$sender]")

      // print transcript history
      history map {
        case Exchange(Some(userSaid), botSaid) => s"user: $userSaid\ntombot: $botSaid"
        case Exchange(None, botSaid) => s"tombot: $botSaid"
      } foreach { text =>
        sparkService.postMessage(Some(tempMembership.roomId), None, None, text, None)

        // TODO
        // implement message send queue - send next after acknowledgement
        // hack due to messages posting out of sequence
        Thread.sleep(2000)
      }
      val ctx1 = ctx.copy(tempMemberships = ctx.tempMemberships + (sender -> tempMembership))
      goto(UsingHuman) using ctx1

    case Event(FillForm(sender, goal), ctx: ConversationContext) =>
      form ! NextQuestion(sender)
      goto(FillingForm) using ctx

    case Event(SwitchConversationEngine(sender, engine), ctx: ConversationContext) =>
      val ctx1 = ctx.copy(botActor = getConversationActor(engine.toString))
      provider.sendTextMessage(sender, "switched conversation engine to " + engine)
      stay using ctx1

    // TODO
    case Event(ev: SparkMessageEvent, ctx: ConversationContext) =>
      agent ! ev
      goto(UsingHuman) using ctx

    case Event(ev, ctx: ConversationContext) =>
      ctx.botActor ! ev
      stay using ctx

  }

  when(FillingForm) {

    case Event(ev: TextLike, ctx: ConversationContext) =>
      form ! ev
      stay using ctx

    case Event(ev: EndFillForm, ctx: ConversationContext) =>
      ctx.botActor ! ev
      goto(UsingBot) using ctx

  }

  when(UsingHuman) {

    case Event(ev: SparkMessageEvent, ctx: ConversationContext) =>
      agent ! ev
      stay using ctx

    case Event(SparkRoomLeftEvent(sender), ctx: ConversationContext) =>
      provider.sendTextMessage(sender, s"${ctx.agentName} (Human) is leaving the conversation")
      val tempMembership = ctx.tempMemberships(sender)
      sparkService.deleteWebhook(tempMembership.leaveRoomWebhookId)
      sparkService.deleteWebhook(tempMembership.webhookId)
      sparkService.deleteTeam(tempMembership.teamId)
      goto(UsingBot) using ctx

    case Event(ev: TextLike, ctx: ConversationContext) =>
      val tempMembership = ctx.tempMemberships(ev.sender)
      agent ! SparkWrappedEvent(tempMembership.roomId, tempMembership.personId, ev)
      stay using ctx

  }

  whenUnhandled {

    case Event(Reset, ctx: ConversationContext) =>
      form ! Reset
      ctx.botActor ! Reset
      goto(UsingBot) using ConversationContext(
        botActor = getConversationActor(conversationEngineDefault),
        tempMemberships = Map[String, SparkTempMembership](),
        agentName = "Mark")

    case Event(ev, ctx: ConversationContext) =>
      log.error(s"$name received invalid event [${ev.toString}] while in state [${this.stateName}]")
      stay using ctx

  }

  initialize()

  def formatKeywords(keywords: Map[String, Double]) = {
    keywords map {
      case (keyword, relevance) => f"$keyword ($relevance%2.2f)"
    } mkString "\n"
  }

  def getConversationActor(engineName: String): ActorRef = engineName.toLowerCase match {
    case "watson" => context.actorOf(GuiceAkkaExtension(context.system).props(WatsonConversationActor.name))
    case _ => context.actorOf(GuiceAkkaExtension(context.system).props(IntentActor.name))
  }

  def command(name: String) = s"""^[/:]$name.*""".r

}

object ConciergeActor extends NamedActor {

  override final val name = "ConciergeActor"

  sealed trait State

  case object UsingBot extends State

  case object FillingForm extends State

  case object UsingHuman extends State

  sealed trait Data

  case class ConversationContext(botActor: ActorRef,
                                 tempMemberships: Map[String, SparkTempMembership],
                                 agentName: String) extends Data

}
