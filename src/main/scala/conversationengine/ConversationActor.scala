package conversationengine

import akka.actor._
import akka.contrib.pattern.ReceivePipeline
import apis.facebookmessenger.{FacebookMessageDeliveredEvent, FacebookMessageReadEvent}
import apis.googlenlp._
import com.google.inject.{Inject, Injector}
import com.typesafe.config.Config
import controllers.Platform._
import conversationengine.ConversationActor.{Data, State}
import conversationengine.events._
import modules.akkaguice.{ActorInject, NamedActor}
import services.FacebookSendQueue._
import services._
import spray.json._
import utils.Memoize

import scala.concurrent._
import scala.concurrent.duration._
import scala.util.{Failure, Random, Success}

/**
  * Created by markmo on 27/07/2016.
  */
class ConversationActor @Inject()(config: Config,
                                  languageService: LanguageService,
                                  smallTalkService: SmallTalkService,
                                  catalogService: CatalogService,
                                  bus: LookupBusImpl,
                                  val injector: Injector)
  extends Actor
    with ActorInject
    with ActorLogging
    with ReceivePipeline
    with LoggingInterceptor
    with FutureExtensions
    with Memoize
    with GoogleJsonSupport
    with FSM[State, Data] {

  import ConversationActor._
  import context.dispatcher

  val maxFailCount = config.getInt("max.fail.count")

  val maxMessageLength = 300

  startWith(Qualifying, ConversationContext(
    currentPlatform = Facebook,
    provider = provider(Facebook),
    authenticated = false,
    failCount = 0,
    history = Nil,
    postAction = None
  ))

  when(Qualifying) {

    case Event(Qualify(platform, sender, productType, text), ctx: ConversationContext) =>
      privilegedAction(platform, sender, text, ctx) { c =>
        productType match {
          case Some(t) =>
            catalogService.items.get(t) match {
              case Some(items) =>
                c.provider ! HeroCard(sender, items)
                val c1 = c.copy(history = Exchange(Some(text), "show product catalog") :: c.history)
                goto(Buying) using c1
              case None =>
                val c1 = shrug(sender, text, c)
                stay using c1
            }
          case None =>
            val c1 = shrug(sender, text, c)
            stay using c1
        }
      }
  }

  when(Buying) {

    case Event(Buy(platform, sender, _, text), ctx: ConversationContext) =>
      action(platform, sender, text, ctx) { c =>
        context.parent ! FillForm(sender, "purchase")
        stay using c
      }

    case Event(EndFillForm(sender, slot, history), ctx: ConversationContext) =>
      val c1 = ctx.copy(history = history ++ ctx.history)
      ctx.provider ! ReceiptCard(sender, slot)
      goto(Qualifying) using c1

  }

  whenUnhandled {

    case Event(Welcome(platform, sender), ctx: ConversationContext) =>
      ctx.provider ! TextMessage(sender, "Welcome, login successful")
      bus publish MsgEnvelope(s"authenticated:$sender", Authenticated(sender, self))
      stay

    case Event(Authenticated(sender, ref), ctx: ConversationContext) =>
      if (self != ref) {
        log.debug("transferring state")

        // lookup the concierge actor (grandparent)
        context.actorSelection("../..").resolveOne()(timeout) onComplete {
          case Success(subscriber) =>
            bus unsubscribe subscriber
            ref ! TransferState(sender, ctx)
            context.system.scheduler.scheduleOnce(3 minutes) {
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

    case Event(TransferState(sender, ctx), _) =>
      if (ctx.postAction.isDefined) {
        val action = ctx.postAction.get
        val c1 = ctx.copy(authenticated = true, history = Exchange(None, "logged in") :: ctx.history, postAction = None)
        action(c1)
      } else {
        val c1 = ctx.copy(authenticated = true, history = Exchange(None, "logged in") :: ctx.history)
        stay using c1
      }

    case Event(ev: FacebookMessageDeliveredEvent, ctx: ConversationContext) =>
      ctx.provider ! ev
      stay

    case Event(ev: FacebookMessageReadEvent, ctx: ConversationContext) =>
      ctx.provider ! ev
      stay

    case Event(Confirm(platform, sender, text), ctx: ConversationContext) =>
      stay

    case Event(Reset, _) =>
      goto(Qualifying) using ConversationContext(
        currentPlatform = Facebook,
        provider = provider(Facebook),
        authenticated = false,
        failCount = 0,
        history = Nil,
        postAction = None
      )

    case Event(ShowHistory(sender), ctx: ConversationContext) =>
      multiMessage(sender, formatHistory(ctx.history.reverse), ctx)
      stay

    case Event(Greet(platform, sender, user, text), ctx: ConversationContext) =>
      action(platform, sender, text, ctx) { c =>
        val c1 = greet(sender, user, text, c)
        stay using c1
      }

    case Event(Respond(platform, sender, text), ctx: ConversationContext) =>
      action(platform, sender, text, ctx) { c =>
        val c1 = smallTalkService.getSmallTalkResponse(sender, text) match {
          case "Didn't get that!" => shrug(sender, text, c)
          case reply => say(sender, text, reply, c)
        }
        stay using c1
      }

    case Event(Analyze(platform, sender, text), ctx: ConversationContext) =>
      action(platform, sender, text, ctx) { c =>
        analyze(text) map {
          case Some((entities: List[GoogleEntity], sentiment: GoogleSentiment)) =>
            self ! AnalysisSuccess(platform, sender, text, entities, sentiment)
          case None =>
            self ! AnalysisFailure(platform, sender, text)
        }
        stay
      }

    case Event(AnalysisSuccess(_, sender, text, entities, sentiment), ctx: ConversationContext) =>
      val c1 = say(sender, text,
        shrugEmoji + "Didn't get that, but I can understand that\n" +
          formatEntities(entities) + "\n" + formatSentiment(sentiment), ctx)
      stay using c1

    case Event(AnalysisFailure(_, sender, text), ctx: ConversationContext) =>
      val c1 = shrug(sender, text, ctx)
      stay using c1

    case Event(ev, ctx: ConversationContext) =>
      log.warning("{} received unhandled request {} in state {}/{}", name, ev, stateName, ctx)
      stay
  }

  initialize()

  def action(platform: Platform,
             sender: String,
             text: String,
             ctx: ConversationContext,
             privileged: Boolean = false
            )(f: ConversationContext => State): State = {
    log.debug(s"actioning request from $platform, sender [$sender], [$text], ${if (privileged) "privileged" else "public"}")

    if (ctx.postAction.isDefined) {
      log.debug("post action is defined")
      if (confirmed(text)) {
        log.debug("confirmed")
        val action = ctx.postAction.get
        val history = Exchange(Some(text), "confirmed") :: ctx.history
        val c1 = ctx.copy(history = history, postAction = None)
        action(c1)
      } else {
        log.debug("denied")
        val history = Exchange(Some(text), "denied") :: ctx.history
        val c1 = ctx.copy(history = history, postAction = None)
        stay using c1
      }

    } else if (privileged && !ctx.authenticated) {
      log.debug("call to login")
      say(sender, text, "I need to confirm your identity if that is OK", ctx)
      val history = Exchange(Some(text), "login") :: ctx.history
      val c1 = ctx.copy(history = history, postAction = Some(f))
      context.parent ! Deactivate
      ctx.provider ! LoginCard(sender)
      stay using c1

    } else if (ctx.currentPlatform != platform) {
      log.debug(s"switching from [${ctx.currentPlatform}] to [$platform]")
      val p = provider(platform)
      val c1 = ctx.copy(currentPlatform = platform, provider = p, postAction = Some(f))
      context.parent ! Deactivate
      p ! QuickReply(sender, s"Do you want to carry on our conversation from ${ctx.currentPlatform}?")
      stay using c1

    } else {
      log.debug("executing action")
      f(ctx)
    }
  }

  def privilegedAction(platform: Platform,
                       sender: String,
                       text: String,
                       ctx: ConversationContext
                      )(f: ConversationContext => State): State =
    action(platform, sender, text, ctx, privileged = true)(f)

  lazy val provider: (Platform) => ActorRef = memoize {
    case platform => platform match {
      case Facebook => injectActor[FacebookSendQueue]("queue")
    }
  }

  def confirmed(text: String): Boolean = text.toLowerCase == "yes"

  def say(sender: String, text: String, message: String, ctx: ConversationContext): ConversationContext = {
    ctx.provider ! TextMessage(sender, message)
    ctx.copy(history = Exchange(Some(text), message) :: ctx.history)
  }

  def quickReply(sender: String, text: String, message: String, ctx: ConversationContext): ConversationContext = {
    ctx.provider ! QuickReply(sender, message)
    ctx.copy(history = Exchange(Some(text), message) :: ctx.history)
  }

  def analyze(text: String): Future[Option[(List[GoogleEntity], GoogleSentiment)]] = {
    lazy val entitiesRequest = languageService.getEntities(text)
    lazy val sentimentRequest = languageService.getSentiment(text)

    // assigning the requests here starts them in parallel
    val f1 = entitiesRequest
      .withTimeout(new TimeoutException("entities future timed out"))(timeout, context.system)

    val f2 = sentimentRequest
      .withTimeout(new TimeoutException("sentiment future timed out"))(timeout, context.system)

    (for {
      entitiesResponse <- f1
      sentimentResponse <- f2
    } yield {
      log.debug("entities:\n" + entitiesResponse.toJson.prettyPrint)
      log.debug("sentiment:\n" + sentimentResponse.toJson.prettyPrint)
      Some((entitiesResponse.entities, sentimentResponse.documentSentiment))
    }) recover {
      case e: Throwable =>
        log.error(e, e.getMessage)
        None
    }
  }

  def greet(sender: String, user: User, text: String, ctx: ConversationContext): ConversationContext = {
    val greeting = greetings(random.nextInt(greetings.size)).format(user.firstName)
    say(sender, text, greeting, ctx)
  }

  def shrug(sender: String, text: String, ctx: ConversationContext): ConversationContext = {
    val failCount = ctx.failCount + 1
    log.debug("shrug fail count: " + failCount)
    if (failCount > maxFailCount) {
      //bus publish MsgEnvelope(s"fallback:$sender", Fallback(sender, ctx.history.reverse))
      context.parent ! Fallback(sender, ctx.history.reverse)
      ctx.copy(failCount = 0, history = Nil)
    } else {
      say(sender, text, shrugEmoji + shrugs(random.nextInt(shrugs.size)), ctx.copy(failCount = failCount))
    }
  }

  def multiMessage(sender: String, message: String, ctx: ConversationContext): Unit =
    message.split("\n").foldLeft(0, 0, List[(Int, String)]()) {
      case ((group, len, lines), line) =>
        val len1 = len + line.length
        if (len1 < maxMessageLength) {
          (group, len1, (group, line) :: lines)
        } else {
          (group + 1, line.length, (group + 1, line) :: lines)
        }
    }._3 groupBy (_._1) foreach {
      case (_, ls) =>
        val lines = ls.map(_._2).reverse
        ctx.provider ! TextMessage(sender, lines mkString "\n")
    }

  def formatHistory(history: List[Exchange]) =
    history map {
      case Exchange(Some(userSaid), botSaid) => s"$userSaid <- $botSaid"
      case Exchange(None, botSaid) => s" <- $botSaid"
    } mkString "\n"

  def formatEntities(entities: List[GoogleEntity]) =
    entities map { entity =>
      //val salience = f"${entity.salience}%2.2f"
      //s"${entity.name} (${entity.entityType}, $salience)"
      s"${entity.name} is a ${entity.entityType}"
    } mkString "\n"

  def formatSentiment(sentiment: GoogleSentiment) = {
    //f"Sentiment: ${sentiment.polarity}%2.2f"
    val s = sentiment.polarity match {
      case x if x > 0 => "positive"
      case x if x < 0 => "negative"
      case _ => "neutral"
    }
    s"Sentiment is $s"
  }

}

object ConversationActor extends NamedActor {

  override final val name = "ConversationActor"

  sealed trait State
  case object Qualifying extends State
  case object Buying extends State

  sealed trait Data
  case class ConversationContext(currentPlatform: Platform,
                                 provider: ActorRef,
                                 authenticated: Boolean,
                                 failCount: Int,
                                 history: List[Exchange],
                                 postAction: Option[ConversationContext => FSM.State[State, Data]])
    extends Data

  case class TransferState(sender: String, ctx: ConversationContext)

  val random = new Random

  val shrugEmoji = "¯\\_(ツ)_/¯ "

  val shrugs = Vector(
    //"I'm sorry, I did not understand. I don't even have arms.",
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

  val greetings = Vector(
    "Hi there %s!",
    "Hello %s!",
    "Howdy %s!",
    "Ahoy %s!",
    "‘Ello Mate",
    "What's cookin' Good Lookin'?",
    "Aloha %s!",
    "Hola %s!",
    "Que Pasa %s!",
    "Bonjour %s!",
    "Ciao %s!",
    "Konnichiwa %s!"
  )

}
