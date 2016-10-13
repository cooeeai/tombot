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

import scala.collection.mutable
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

  val agentName = "Mark"

  val tempMemberships = mutable.Map[String, SparkTempMembership]()

  implicit val timeout = 30 second

  implicit class FutureExtensions[T](f: Future[T]) {
    def withTimeout(timeout: => Throwable)(implicit duration: FiniteDuration, system: ActorSystem): Future[T] = {
      Future firstCompletedOf Seq(f, after(duration, system.scheduler)(Future.failed(timeout)))
    }
  }

  val conversationEngine = config.getString("conversation.engine")

  // bot conversation actor
  val bot = if (conversationEngine == "watson") {
    context.actorOf(GuiceAkkaExtension(context.system).props(WatsonConversationActor.name))
  } else {
    context.actorOf(GuiceAkkaExtension(context.system).props(IntentActor.name))
  }

  // form conversation actor
  val form = context.actorOf(GuiceAkkaExtension(context.system).props(FormActor.name))

  // agent conversation actor
  val agent = context.actorOf(GuiceAkkaExtension(context.system).props(AgentConversationActor.name))

  startWith(UsingBot, Uninitialized)

  when(UsingBot) {

    case Event(ev: TextLike, _) =>
      val platform = ev.platform
      val sender = ev.sender
      val text = ev.text

      if (text startsWith "/login") {
        provider.sendLoginCard(sender)

      } else if (text startsWith "/history") {
        bot ! ShowHistory(sender)

      } else if (text startsWith "/alchemy") {
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
            bot ! Respond(platform, sender, text)

        }

      } else {
        bot ! Respond(platform, sender, text)
      }
      stay

    case Event(Fallback(sender, history), _) =>
      provider.sendTextMessage(sender, s"$agentName (Human) is joining the conversation")
      lazy val fut = sparkService.setupTempRoom(sender) withTimeout new TimeoutException("future timed out")
      val tempMembership = Await.result(fut, timeout)
      tempMemberships(sender) = tempMembership

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
      goto(UsingHuman)

    case Event(FillForm(sender, goal), _) =>
      form ! NextQuestion(sender)
      goto(FillingForm)

    case Event(ev, _) =>
      bot ! ev
      stay

  }

  when(FillingForm) {

    case Event(ev: TextLike, _) =>
      form ! ev
      stay

    case Event(ev: EndFillForm, _) =>
      bot ! ev
      goto(UsingBot)

  }

  when(UsingHuman) {

    case Event(ev: SparkMessageEvent, _) =>
      agent ! ev
      stay

    case Event(SparkRoomLeftEvent(sender), _) =>
      provider.sendTextMessage(sender, s"$agentName (Human) is leaving the conversation")
      val tempMembership = tempMemberships(sender)
      sparkService.deleteWebhook(tempMembership.leaveRoomWebhookId)
      sparkService.deleteWebhook(tempMembership.webhookId)
      sparkService.deleteTeam(tempMembership.teamId)
      goto(UsingBot)

    case Event(ev: TextLike, _) =>
      val tempMembership = tempMemberships(ev.sender)
      agent ! SparkWrappedEvent(tempMembership.roomId, tempMembership.personId, ev)
      stay

  }

  whenUnhandled {

    case Event(Reset, _) =>
      form ! Reset
      bot ! Reset
      goto(UsingBot)

    case Event(ev, _) =>
      log.error(s"$name received invalid event [${ev.toString}] while in state [${this.stateName}]")
      stay

  }

  initialize()

  def formatKeywords(keywords: Map[String, Double]) = {
    keywords map {
      case (keyword, relevance) => f"$keyword ($relevance%2.2f)"
    } mkString "\n"
  }

}

object ConciergeActor extends NamedActor {

  override final val name = "ConciergeActor"

  sealed trait State

  case object UsingBot extends State

  case object FillingForm extends State

  case object UsingHuman extends State

  sealed trait Data

  case object Uninitialized extends Data

}
