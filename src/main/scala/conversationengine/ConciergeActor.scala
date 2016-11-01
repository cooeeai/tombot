package conversationengine

import java.util.concurrent.TimeoutException

import akka.actor._
import akka.contrib.pattern.ReceivePipeline
import akka.stream.Materializer
import apis.ciscospark.SparkTempMembership
import com.google.inject.{Inject, Injector}
import com.typesafe.config.Config
import conversationengine.ConciergeActor.{Data, State}
import conversationengine.ConversationEngine._
import conversationengine.events._
import modules.akkaguice.{ActorInject, NamedActor}
import services.FacebookSendQueue.{LoginCard, TextMessage}
import services._

import scala.concurrent._

/**
  * Created by markmo on 9/09/2016.
  */
class ConciergeActor @Inject()(config: Config,
                               sparkService: SparkService,
                               alchemyService: AlchemyService,
                               rulesService: RulesService,
                               implicit val system: ActorSystem,
                               implicit val fm: Materializer,
                               val injector: Injector)
  extends Actor
    with ActorInject
    with ActorLogging
    with ReceivePipeline
    with LoggingInterceptor
    with FutureExtensions
    with FSM[State, Data] {

  import ConciergeActor._
  import rulesService._
  import utils.RegexUtils._

  val AlchemyCommand = command("alchemy")
  val SwitchEngineCommand = command("engine")
  val HistoryCommand = command("history")
  val LoginCommand = command("login")
  val ResetCommand = command("reset")

  val conversationEngineDefault = config.getString("conversation.engine")

  // form conversation actor
  //val formActor = context.actorOf(GuiceAkkaExtension(context.system).props(FormActor.name))
  val formActor = injectActor[FormActor]

  // live agent conversation actor
  //val agentConversationActor = context.actorOf(GuiceAkkaExtension(context.system).props(AgentConversationActor.name))
  val agentConversationActor = injectActor[AgentConversationActor]

  val provider = injectActor[FacebookSendQueue]

  startWith(UsingBot, ConversationContext(
    actor = getConversationActor(conversationEngineDefault),
    tempMemberships = Map[String, SparkTempMembership](),
    agentName = "Mark"))

  when(UsingBot) {

    case Event(ev: TextLike, ctx: ConversationContext) =>
      log.debug("front door - check for commands")
      val platform = ev.platform
      val sender = ev.sender
      val text = ev.text

      if (AlchemyCommand matches text) {
        log.debug("using alchemy service to show keywords")
        val keywords = alchemyService.getKeywords(text.substring(8).trim)
        provider ! TextMessage(sender, "Keywords:\n" + formatKeywords(keywords))

      } else if (SwitchEngineCommand matches text) {
        log.debug("switching conversation engine")
        if (text contains "watson") {
          log.debug("choosing Watson")
          self ! SwitchConversationEngine(sender, Watson)
        } else {
          log.debug("choosing Cooee")
          self ! SwitchConversationEngine(sender, Cooee)
        }

      } else if (HistoryCommand matches text) {
        log.debug("showing history")
        ctx.actor ! ShowHistory(sender)

      } else if (LoginCommand matches text) {
        log.debug("sending login card")
        provider ! LoginCard(sender)

      } else if (ResetCommand matches text) {
        log.debug("resetting")
        self ! Reset

      } else if (isQuestion(text)) {
        log.debug("text is interpreted as a question")

        getContent(text) match {
          case Some(content) =>
            log.debug(s"found content in response to question [$content]")
            provider ! TextMessage(sender, content)
          case None =>
            log.debug("no content")
            ctx.actor ! Respond(platform, sender, text)
        }

      } else {
        ctx.actor ! Respond(platform, sender, text)

      }
      stay

    case Event(Fallback(sender, history), ctx: ConversationContext) =>
      provider ! TextMessage(sender, s"${ctx.agentName} (Human) is joining the conversation")
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
      val c1 = ctx.copy(tempMemberships = ctx.tempMemberships + (sender -> tempMembership))
      goto(UsingHuman) using c1

    case Event(FillForm(sender, goal), _) =>
      formActor ! NextQuestion(sender)
      goto(FillingForm)

    case Event(SwitchConversationEngine(sender, engine), ctx: ConversationContext) =>
      val c1 = ctx.copy(actor = getConversationActor(engine.toString))
      provider ! TextMessage(sender, "switched conversation engine to " + engine)
      stay using c1

    // TODO
    case Event(ev: SparkMessageEvent, _) =>
      agentConversationActor ! ev
      goto(UsingHuman)

    case Event(ev, ctx: ConversationContext) =>
      ctx.actor ! ev
      stay

  }

  when(FillingForm) {

    case Event(ev: TextLike, _) =>
      formActor ! ev
      stay

    case Event(ev: EndFillForm, ctx: ConversationContext) =>
      ctx.actor ! ev
      goto(UsingBot)

  }

  when(UsingHuman) {

    case Event(ev: SparkMessageEvent, _) =>
      agentConversationActor ! ev
      stay

    case Event(SparkRoomLeftEvent(sender), ctx: ConversationContext) =>
      provider ! TextMessage(sender, s"${ctx.agentName} (Human) is leaving the conversation")
      val tempMembership = ctx.tempMemberships(sender)
      sparkService.deleteWebhook(tempMembership.leaveRoomWebhookId)
      sparkService.deleteWebhook(tempMembership.webhookId)
      sparkService.deleteTeam(tempMembership.teamId)
      goto(UsingBot)

    case Event(ev: TextLike, ctx: ConversationContext) =>
      val tempMembership = ctx.tempMemberships(ev.sender)
      agentConversationActor ! SparkWrappedEvent(tempMembership.roomId, tempMembership.personId, ev)
      stay

  }

  whenUnhandled {

    case Event(Reset, ctx: ConversationContext) =>
      formActor ! Reset
      ctx.actor ! Reset
      goto(UsingBot) using ConversationContext(
        actor = getConversationActor(conversationEngineDefault),
        tempMemberships = Map[String, SparkTempMembership](),
        agentName = "Mark")

    case Event(ev, ctx: ConversationContext) =>
      log.warning("received unhandled request {} in state {}/{}", ev, stateName, ctx)
      stay

  }

  initialize()

  def formatKeywords(keywords: Map[String, Double]) = {
    keywords map {
      case (keyword, relevance) => f"$keyword ($relevance%2.2f)"
    } mkString "\n"
  }

  def getConversationActor(engineName: String): ActorRef = engineName.toLowerCase match {
    case "watson" => injectActor[WatsonConversationActor] //context.actorOf(GuiceAkkaExtension(context.system).props(WatsonConversationActor.name))
    case "cooee" => injectActor[IntentActor] //context.actorOf(GuiceAkkaExtension(context.system).props(IntentActor.name))
    case _ => injectActor[IntentActor] //context.actorOf(GuiceAkkaExtension(context.system).props(IntentActor.name))
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

  case class ConversationContext(actor: ActorRef,
                                 tempMemberships: Map[String, SparkTempMembership],
                                 agentName: String) extends Data

}
