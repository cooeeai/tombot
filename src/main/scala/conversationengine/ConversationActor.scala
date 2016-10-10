package conversationengine

import akka.actor.{Actor, ActorLogging, ActorSystem, FSM}
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

import scala.collection.mutable
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

  implicit val timeout = 20 second

  implicit class FutureExtensions[T](f: Future[T]) {
    def withTimeout(timeout: => Throwable)(implicit duration: FiniteDuration, system: ActorSystem): Future[T] = {
      Future firstCompletedOf Seq(f, after(duration, system.scheduler)(Future.failed(timeout)))
    }
  }

  var currentPlatform = Facebook

  var provider: MessagingProvider = facebookService

  val maxFailCount = config.getInt("max.fail.count")

  var failCount = 0

  val history = mutable.ListBuffer[Exchange]()

  var authenticated = false

  var postAction: Option[Action] = None

  startWith(Qualifying, Uninitialized)

  when(Qualifying) {

    case Event(Qualify(platform, sender, productType, text), _) =>
      productType match {
        case Some(t) => privilegedAction(platform, sender, text) {
          say(sender, text, s"What type of $t did you have in mind?")
          goto(Qualifying)
        }
        case None => privilegedAction(platform, sender, text) {
          say(sender, text, "What did you want to buy?")
          stay
        }
      }

    case Event(Respond(platform, sender, text), _) =>
      if (text == "iphone") {
        action(platform, sender, text) {
          history += Exchange(Some(text), "show product catalog")
          provider.sendHeroCard(sender)
          goto(Buying)
        }
      } else {
        action(platform, sender, text) {
          say(sender, text, "Sorry, I didn't understand that")
          stay
        }
      }
  }

  when(Buying) {

    case Event(Buy(platform, sender, _, text), _) =>
      action(platform, sender, text) {
        context.parent ! FillForm(sender, "purchase")
        stay
      }

    case Event(EndFillForm(sender, slot), _) =>
      provider.sendReceiptCard(sender, slot)
      goto(Qualifying)

  }

  whenUnhandled {

    case Event(Confirm(platform, sender, text), _) =>
      stay

    case Event(Reset, _) =>
      authenticated = false
      postAction = None
      failCount = 0
      history.clear()
      goto(Qualifying)

    case Event(Greet(platform, sender, user, text), _) =>
      action(platform, sender, text) {
        greet(sender, user, text)
        stay
      }

    case Event(Respond(platform, sender, text), _) =>
      action(platform, sender, text) {
        smallTalkService.getSmallTalkResponse(sender, text) match {
          case "Didn't get that!" => shrug(sender, text)
          case reply => say(sender, text, reply)
        }
        stay
      }

    case Event(Analyze(platform, sender, text), _) =>
      action(platform, sender, text) {
        analyze(sender, text)
        stay
      }

    case Event(BillEnquiry(platform, sender, text), _) =>
      privilegedAction(platform, sender, text) {
        quickReply(sender, text, "Your current balance is $41.25. Would you like to pay it now?")
        stay
      }

    case Event(Welcome(platform, sender), _) =>
      authenticated = true
      history += Exchange(None, "logged in")
      //provider.sendTextMessage(sender, "Welcome, login successful")
      val action = postAction.get
      postAction = None
      action.block

    case Event(ev, _) =>
      log.error(s"$name received invalid event [${ev.toString}] while in state [${this.stateName}]")
      stay
  }

  initialize()

  def action(platform: Platform.Value,
             sender: String,
             text: String,
             privileged: Boolean = false
            )(b: => State): State = {
    log.debug(s"actioning request from $platform, sender [$sender], [$text], ${if (privileged) "privileged" else "public"}")
    if (postAction.isDefined) {
      log.debug("post action is defined")
      if (confirmed(text)) {
        log.debug("confirmed")
        history += Exchange(Some(text), "confirmed")
        val action = postAction.get
        postAction = None
        action.block
      } else {
        log.debug("denied")
        history += Exchange(Some(text), "denied")
        postAction = None
        stay
      }
    } else if (privileged && !authenticated) {
      log.debug("call to login")
      val action = new Action {
        lazy val block = b
      }
      postAction = Some(action)
      history += Exchange(Some(text), "login")
      context.parent ! Deactivate
      provider.sendLoginCard(sender)
      log.debug("postAction " + postAction)
      stay
    } else if (currentPlatform != platform) {
      log.debug(s"switching from [$currentPlatform] to [$platform]")
      val action = new Action {
        lazy val block = b
      }
      postAction = Some(action)
      val oldPlatform = currentPlatform
      currentPlatform = platform
      provider = platform match {
        case Facebook => facebookService
        case Skype => skypeService
      }
      context.parent ! Deactivate
      provider.sendQuickReply(sender, s"Do you want to carry on our conversation from $oldPlatform?")
      stay
    } else {
      log.debug("executing standard action")
      b
    }
  }

  def privilegedAction(platform: Platform.Value,
                       sender: String,
                       text: String
                      )(b: => State): State = action(platform, sender, text, privileged = true)(b)

  private def confirmed(text: String): Boolean = text.toLowerCase == "yes"

  private def say(sender: String, text: String, message: String): Unit = {
    history += Exchange(Some(text), message)
    provider.sendTextMessage(sender, message)
  }

  private def quickReply(sender: String, text: String, message: String): Unit = {
    history += Exchange(Some(text), message)
    provider.sendQuickReply(sender, message)
  }

  def analyze(sender: String, text: String) = {
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
        formatSentiment(sentimentResponse.documentSentiment))
    }
    f3 recover {
      case e: Throwable => log.error(e.getMessage)
    }
  }

  def greet(sender: String, user: User, text: String) = {
    val greeting = greetings(random.nextInt(greetings.size)).format(user.firstName)
    say(sender, text, greeting)
  }

  def shrug(sender: String, text: String) = {
    failCount += 1
    if (failCount > maxFailCount) {
      context.parent ! Fallback(sender, history.toList)
      failCount = 0
      history.clear()
    } else {
      say(sender, text, shrugEmoji + shrugs(random.nextInt(shrugs.size)))
    }
  }

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

  case object Uninitialized extends Data

  abstract class Action {
    val block: FSM.State[State, Data]
  }

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
