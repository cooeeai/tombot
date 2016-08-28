package conversationengine

import akka.actor.{Actor, ActorLogging, ActorSystem, FSM}
import akka.pattern.after
import akka.stream.Materializer
import apis.googlemaps.MapsJsonSupport
import apis.googlenlp._
import com.google.inject.Inject
import conversationengine.ConversationActor.{Data, State}
import modules.akkaguice.NamedActor
import services._
import spray.json._

import scala.concurrent._
import scala.concurrent.duration._
import scala.util.Random

/**
  * Created by markmo on 27/07/2016.
  */
class ConversationActor @Inject()(facebookService: FacebookService,
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
    with FSM[State, Data] {

  import ConversationActor._
  import system.dispatcher

  implicit val timeout = 20.second

  implicit class FutureExtensions[T](f: Future[T]) {

    def withTimeout(timeout: => Throwable)(implicit duration: FiniteDuration, system: ActorSystem): Future[T] = {
      Future firstCompletedOf Seq(f, after(duration, system.scheduler)(Future.failed(timeout)))
    }

  }

  var currentProvider = "facebook"

  var provider: MessagingProvider = facebookService

  var isAuthenticated = false

  def testPlatformChange(platform: String, sender: String) = {
    if (currentProvider != platform) {
      val oldPlatform = currentProvider
      provider = if (platform == "facebook") facebookService else skypeService
      currentProvider = platform
      provider.sendTextMessage(sender, s"Do you want to carry on our conversation from $oldPlatform?")
      // TODO
    }
  }

  startWith(Starting, Uninitialized)

  when(Starting) {
    case Event(Qualify(platform, sender, productType), _) =>
      log.debug("received Buy event")
      testPlatformChange(platform, sender)
      if (isAuthenticated) {
        productType match {
          case Some(typ) =>
            provider.sendTextMessage(sender, s"What type of $typ did you have in mind?")
            goto(Qualifying) using Offer
          case None =>
            provider.sendTextMessage(sender, "What did you want to buy?")
            stay
        }
      } else {
        provider.sendLoginCard(sender)
        stay
      }
    case Event(Welcome(platform, sender), _) =>
      log.debug("received Authenticate event")
      isAuthenticated = true
      testPlatformChange(platform, sender)
      provider.sendTextMessage(sender, "Welcome, login successful")
      stay
  }

  when(Qualifying) {
    case Event(Qualify(platform, sender, productType), _) =>
      log.debug("received Qualify event")
      testPlatformChange(platform, sender)
      productType match {
        case Some(typ) => provider.sendTextMessage(sender, s"What type of $typ did you have in mind?")
        case None => provider.sendTextMessage(sender, "What did you want to buy?")
      }
      stay
    case Event(Respond(platform, sender, text), _) =>
      log.debug("received Respond event")
      testPlatformChange(platform, sender)
      if (text == "iphone") {
        provider.sendHeroCard(sender)
        goto(Buying)
      } else {
        provider.sendTextMessage(sender, "Sorry, I didn't understand that")
        stay
      }
  }

  when(Buying) {
    case Event(Buy(platform, sender, _), _) =>
      log.debug("received Buy event")
      testPlatformChange(platform, sender)
      provider.sendTextMessage(sender, "What address should I send the order to?")
      stay
    case Event(Respond(platform, sender, text), _) =>
      log.debug("received Respond event")
      log.debug("looking up address: " + text)
      testPlatformChange(platform, sender)

      // TODO wrap in a function
      lazy val f = addressService.getAddress(text)
      val f1 = f withTimeout new TimeoutException("future timed out")
      val f2 = f1 map { response =>
        log.debug("received address lookup response:\n" + response.toJson.prettyPrint)
        if (response.results.nonEmpty) {
          provider.sendReceiptCard(sender, response.results.head.getAddress)
          goto(Starting)
        } else {
          provider.sendTextMessage(sender, "Sorry, I could not interpret that")
          stay
        }
      }
      f2.failed map { e =>
        log.error(e.getMessage)
        stay
      }
      Await.result(f2, timeout)
  }

  whenUnhandled {
    case Event(Greet(platform, sender, user), _) =>
      log.debug("received Greet event")
      testPlatformChange(platform, sender)
      greet(sender, user)
      stay
    case Event(Respond(platform, sender, text), _) =>
      log.debug("received Respond event")
      testPlatformChange(platform, sender)
      val reply = smallTalkService.getSmallTalkResponse(sender, text)
      if (reply == "Didn't get that!") {
        //analyze(sender, text)
        shrug(sender)
      } else {
        provider.sendTextMessage(sender, reply)
      }
      stay
    case Event(Analyze(platform, sender, text), _) =>
      log.debug("received Analyze event")
      testPlatformChange(platform, sender)
      analyze(sender, text)
      stay
    case _ =>
      log.error("invalid event")
      stay
  }

  initialize()

  def analyze(sender: String, text: String) = {
    lazy val entitiesRequest = languageService.getEntities(text)
    lazy val sentimentRequest = languageService.getSentiment(text)
    val f1 = entitiesRequest withTimeout new TimeoutException("entities future timed out")
    val f2 = sentimentRequest withTimeout new TimeoutException("sentiment future timed out")
    val f3 = for {
      entitiesResponse <- f1
      sentimentResponse <- f2
    } yield {
      log.debug("received entities response:\n" + entitiesResponse.toJson.prettyPrint)
      log.debug("received sentiment response:\n" + sentimentResponse.toJson.prettyPrint)
      val message =
        "¯\\_(ツ)_/¯ Didn't get that, but I can understand that\n" +
          formatEntities(entitiesResponse.entities) + "\n" + formatSentiment(sentimentResponse.documentSentiment)
      provider.sendTextMessage(sender, message)
    }
    f3 recover {
      case e: Throwable => log.error(e.getMessage)
    }
  }

  def greet(sender: String, user: User) =
    provider.sendTextMessage(sender, greetings(random.nextInt(greetings.size)) + " " + user.firstName + "!")

  def shrug(sender: String) = {
    val joke = if (random.nextInt(10) < 3) "\nHow about a joke instead?\n" + humourService.getJoke else ""
    provider.sendTextMessage(sender, "¯\\_(ツ)_/¯ " + shrugs(random.nextInt(shrugs.size)) + joke)
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

  // events
  case class Greet(platform: String, sender: String, user: User)
  case class Qualify(platform: String, sender: String, productType: Option[String])
  case class Buy(platform: String, sender: String, productType: String)
  case class Respond(platform: String, sender: String, text: String)
  case class Welcome(platform: String, sender: String)
  case class Analyze(platform: String, sender: String, text: String)

  sealed trait State
  case object Starting extends State
  case object Greeted extends State
  case object Qualifying extends State
  case object Buying extends State

  sealed trait Data
  case object Uninitialized extends Data
  case object Offer extends Data

  val random = new Random

  val shrugs = Vector(
    //"I'm sorry, I did not understand. I don't even have arms.",
    "I'm sorry, I did not understand. These arms aren't even real.",
    "I'm not that bright sometimes",
    "That's outside my circle of knowledge"
  )

  val greetings = Vector(
    "Hi there", "Hello"
  )

}
