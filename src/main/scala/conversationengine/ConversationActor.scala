package conversationengine

import akka.actor._
import akka.contrib.pattern.ReceivePipeline
import akka.pattern.after
import akka.stream.Materializer
import apis.googlemaps.MapsJsonSupport
import apis.googlenlp._
import com.google.inject.Inject
import com.typesafe.config.Config
import controllers.Platform
import conversationengine.ConversationActor.{Data, State}
import conversationengine.events._
import modules.akkaguice.NamedActor
import services._
import spray.json._

import scala.concurrent._
import scala.concurrent.duration._
import scala.util.Random

/**
  * Created by markmo on 27/07/2016.
  */
class ConversationActor @Inject()(config: Config,
                                  facebookService: FacebookService,
                                  skypeService: SkypeService,
                                  addressService: AddressService,
                                  languageService: LanguageService,
                                  humourService: HumourService,
                                  smallTalkService: SmallTalkService,
                                  bus: LookupBusImpl,
                                  implicit val system: ActorSystem,
                                  implicit val fm: Materializer)
  extends Actor
    with ActorLogging
    with MapsJsonSupport
    with GoogleJsonSupport
    with ReceivePipeline
    with LoggingInterceptor
    with FSM[State, Data] {

  import ConversationActor._
  import Platform._
  import system.dispatcher

  implicit val timeout = 30 second

  implicit class FutureExtensions[T](f: Future[T]) {
    def withTimeout(timeout: => Throwable)(implicit duration: FiniteDuration, system: ActorSystem): Future[T] = {
      Future firstCompletedOf Seq(f, after(duration, system.scheduler)(Future.failed(timeout)))
    }
  }

  var currentPlatform: Option[Platform] = None

  var provider: MessagingProvider = facebookService

  val maxFailCount = config.getInt("max.fail.count")

  val maxMessageLength = 300

  var failCount = 0

  startWith(Qualifying, ConversationContext(authenticated = false, history = Nil, postAction = None))

  when(Qualifying) {

    case Event(Welcome(platform, sender), cc: ConversationContext) =>
      provider.sendTextMessage(sender, "Welcome, login successful")
      bus publish MsgEnvelope(s"authenticated:$sender", Authenticated(sender, self))
      stay

    case Event(TransferState(sender, ctx), _) =>
      if (ctx.postAction.isDefined) {
        val action = ctx.postAction.get
        val ctx1 = ctx.copy(authenticated = true, history = Exchange(None, "logged in") :: ctx.history, postAction = None)
        action(ctx1)
      } else {
        val ctx1 = ctx.copy(authenticated = true, history = Exchange(None, "logged in") :: ctx.history)
        stay using ctx1
      }

    case Event(Qualify(platform, sender, productType, text), cc: ConversationContext) =>
      productType match {
        case Some(t) => privilegedAction(platform, sender, text, cc) { ctx =>
          val ctx1 = say(sender, text, s"What type of $t did you have in mind?", ctx)
          goto(Qualifying) using ctx1
        }
        case None => privilegedAction(platform, sender, text, cc) { ctx =>
          val ctx1 = say(sender, text, "What did you want to buy?", ctx)
          stay using ctx1
        }
      }

    case Event(Respond(platform, sender, text), cc: ConversationContext) =>
      if (text == "iphone") {
        action(platform, sender, text, cc) { ctx =>
          provider.sendHeroCard(sender)
          val ctx1 = ctx.copy(history = Exchange(Some(text), "show product catalog") :: ctx.history)
          goto(Buying) using ctx1
        }
      } else {
        action(platform, sender, text, cc) { ctx =>
          val ctx1 = say(sender, text, "Sorry, I didn't understand that", ctx)
          stay using ctx1
        }
      }
  }

  when(Buying) {

    case Event(Buy(platform, sender, _, text), cc: ConversationContext) =>
      action(platform, sender, text, cc) { ctx =>
        context.parent ! FillForm(sender, "purchase")
        stay using ctx
      }

    case Event(EndFillForm(sender, slot, history), cc: ConversationContext) =>
      val ctx1 = cc.copy(history = history ++ cc.history)
      provider.sendReceiptCard(sender, slot)
      goto(Qualifying) using ctx1

  }

  whenUnhandled {

    case Event(Authenticated(sender, ref), cc: ConversationContext) =>
      ref ! TransferState(sender, cc)
      context.stop(self)
      stay

    case Event(Confirm(platform, sender, text), cc: ConversationContext) =>
      stay using cc

    case Event(Reset, cc: ConversationContext) =>
      failCount = 0
      val ctx1 = cc.copy(authenticated = false, history = Nil, postAction = None)
      goto(Qualifying) using ctx1

    case Event(ShowHistory(sender), cc: ConversationContext) =>
      multiMessage(sender, formatHistory(cc.history.reverse))
      stay

    case Event(Greet(platform, sender, user, text), cc: ConversationContext) =>
      action(platform, sender, text, cc) { ctx =>
        val ctx1 = greet(sender, user, text, ctx)
        stay using ctx1
      }

    case Event(Respond(platform, sender, text), cc: ConversationContext) =>
      action(platform, sender, text, cc) { ctx =>
        val ctx1 = smallTalkService.getSmallTalkResponse(sender, text) match {
          case "Didn't get that!" => shrug(sender, text, ctx)
          case reply => say(sender, text, reply, ctx)
        }
        stay using ctx1
      }

    case Event(Analyze(platform, sender, text), cc: ConversationContext) =>
      action(platform, sender, text, cc) { ctx =>
        val ctx1 = Await.result(analyze(sender, text, ctx), timeout)
        stay using ctx1
      }

    case Event(BillEnquiry(platform, sender, text), cc: ConversationContext) =>
      privilegedAction(platform, sender, text, cc) { ctx =>
        val ctx1 = quickReply(sender, text, "Your current balance is $41.25. Would you like to pay it now?", ctx)
        stay using ctx1
      }

    case Event(ev, cc: ConversationContext) =>
      log.error(s"$name received invalid event [${ev.toString}] while in state [${this.stateName}]")
      stay using cc
  }

  initialize()

  def action(platform: Platform,
             sender: String,
             text: String,
             ctx: ConversationContext,
             privileged: Boolean = false
            )(b: ConversationContext => State): State = {
    log.debug(s"actioning request from $platform, sender [$sender], [$text], ${if (privileged) "privileged" else "public"}")

    if (ctx.postAction.isDefined) {
      log.debug("post action is defined")
      if (confirmed(text)) {
        log.debug("confirmed")
        val action = ctx.postAction.get
        val ctx1 = ctx.copy(history = Exchange(Some(text), "confirmed") :: ctx.history, postAction = None)
        action(ctx1)
      } else {
        log.debug("denied")
        val ctx1 = ctx.copy(history = Exchange(Some(text), "denied") :: ctx.history, postAction = None)
        stay using ctx1
      }

    } else if (privileged && !ctx.authenticated) {
      log.debug("call to login")
      val ctx1 = ctx.copy(history = Exchange(Some(text), "login") :: ctx.history, postAction = Some(b))
      context.parent ! Deactivate
      provider.sendLoginCard(sender)
      stay using ctx1

    } else if (currentPlatform.isDefined && currentPlatform.get != platform) {
      log.debug(s"switching from [${currentPlatform.get}] to [$platform]")
      val ctx1 = ctx.copy(postAction = Some(b))
      val oldPlatform = currentPlatform.get
      currentPlatform = Some(platform)
      provider = platform match {
        case Facebook => facebookService
        case Skype => skypeService
      }
      context.parent ! Deactivate
      provider.sendQuickReply(sender, s"Do you want to carry on our conversation from $oldPlatform?")
      stay using ctx1

    } else if (currentPlatform.isEmpty) {
      log.debug(s"setting platform to [$platform]")
      currentPlatform = Some(platform)
      provider = platform match {
        case Facebook => facebookService
        case Skype => skypeService
      }
      b(ctx)

    } else {
      log.debug("executing standard action")
      b(ctx)
    }
  }

  def privilegedAction(platform: Platform,
                       sender: String,
                       text: String,
                       ctx: ConversationContext
                      )(b: ConversationContext => State): State = action(platform, sender, text, ctx, privileged = true)(b)

  def confirmed(text: String): Boolean = text.toLowerCase == "yes"

  def say(sender: String, text: String, message: String, ctx: ConversationContext): ConversationContext = {
    provider.sendTextMessage(sender, message)
    ctx.copy(history = Exchange(Some(text), message) :: ctx.history)
  }

  def quickReply(sender: String, text: String, message: String, ctx: ConversationContext): ConversationContext = {
    provider.sendQuickReply(sender, message)
    ctx.copy(history = Exchange(Some(text), message) :: ctx.history)
  }

  def analyze(sender: String, text: String, ctx: ConversationContext): Future[ConversationContext] = {
    lazy val entitiesRequest = languageService.getEntities(text)
    lazy val sentimentRequest = languageService.getSentiment(text)
    val f1 = entitiesRequest withTimeout new TimeoutException("entities future timed out")
    val f2 = sentimentRequest withTimeout new TimeoutException("sentiment future timed out")
    val f3 = for {
      entitiesResponse <- f1
      sentimentResponse <- f2
    } yield {
      log.debug("entities:\n" + entitiesResponse.toJson.prettyPrint)
      log.debug("sentiment:\n" + sentimentResponse.toJson.prettyPrint)
      say(sender, text, shrugEmoji + "Didn't get that, but I can understand that\n" +
        formatEntities(entitiesResponse.entities) + "\n" +
        formatSentiment(sentimentResponse.documentSentiment), ctx)
    }
    f3 recover {
      case e: Throwable =>
        log.error(e.getMessage)
        ctx
    }
  }

  def greet(sender: String, user: User, text: String, ctx: ConversationContext): ConversationContext = {
    val greeting = greetings(random.nextInt(greetings.size)).format(user.firstName)
    say(sender, text, greeting, ctx)
  }

  def shrug(sender: String, text: String, ctx: ConversationContext): ConversationContext = {
    failCount += 1
    if (failCount > maxFailCount) {
      context.parent ! Fallback(sender, ctx.history.reverse)
      failCount = 0
      ctx.copy(history = Nil)
    } else {
      say(sender, text, shrugEmoji + shrugs(random.nextInt(shrugs.size)), ctx)
    }
  }

  def multiMessage(sender: String, message: String): Unit =
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
        provider.sendTextMessage(sender, lines mkString "\n")
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

  case class ConversationContext(authenticated: Boolean,
                                 history: List[Exchange],
                                 postAction: Option[ConversationContext => FSM.State[State, Data]]) extends Data

  case class TransferState(sender: String, ctx: ConversationContext)

  case class Authenticated(sender: String, ref: ActorRef)

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
    "Hello %s, my name is Inigo Montoya",
    "I'm Batman",
    "‘Ello Mate",
    "What's cookin' Good Lookin'?",
    "Aloha %s!",
    "Hola %s!",
    "Que Pasa %s!",
    "Bonjour %s!",
    "Hallo %s!",
    "Ciao %s!",
    "Konnichiwa %s!"
  )

}
