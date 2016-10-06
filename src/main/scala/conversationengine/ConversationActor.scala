package conversationengine

import akka.actor.{Actor, ActorLogging, ActorSystem, FSM}
import akka.pattern.after
import akka.stream.Materializer
import apis.googlemaps.MapsJsonSupport
import apis.googlenlp._
import com.google.inject.Inject
import com.typesafe.config.Config
import controllers.Platforms
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
    with FSM[State, Data] {

  import ConversationActor._
  import Platforms._
  import system.dispatcher
  import utils.LangUtils._

  val maxFailCount = config.getInt("max.fail.count")

  var currentProvider = Facebook

  var provider: MessagingProvider = facebookService

  var isAuthenticated = false

  var postAuthAction: () => this.State = () => stay

  var isAddressVerified = false

  var isPaymentVerified = false

  var failCount = 0

  val history = mutable.ListBuffer[Exchange]()

  startWith(Qualifying, Uninitialized)

  when(Qualifying) {

    case Event(Qualify(platform, sender, productType, text), _) =>
      log.debug(s"$name received Qualify event")
      testPlatformChange(platform, sender)
      productType match {

        case Some(typ) =>
          postAuthAction = () => {
            context.parent ! Deactivate
            val message = s"What type of $typ did you have in mind?"
            history append Exchange(Some(text), message)
            provider.sendTextMessage(sender, message)
            goto(Qualifying)
          }

        case None =>
          postAuthAction = () => {
            val message = "What did you want to buy?"
            history append Exchange(Some(text), message)
            provider.sendTextMessage(sender, message)
            stay
          }

      }
      if (isAuthenticated) {
        postAuthAction()
      } else {
        history append Exchange(None, "login")
        provider.sendLoginCard(sender)
        stay
      }

    case Event(Respond(platform, sender, text), _) =>
      log.debug(s"$name received Respond event")
      testPlatformChange(platform, sender)

      if (text == "iphone") {
        history append Exchange(Some(text), "showing product")
        provider.sendHeroCard(sender)
        goto(Buying)

      } else {
        val message = "Sorry, I didn't understand that"
        history append Exchange(Some(text), message)
        provider.sendTextMessage(sender, message)
        stay
      }
  }

  when(Buying) {

    case Event(Buy(platform, sender, _, text), _) =>
      log.debug(s"$name received Buy event")
      testPlatformChange(platform, sender)
      context.parent ! FillForm(sender, "purchase")
//      val message = "Shall I use the card ending in 1234?"
//      history append Exchange(Some(text), message)
//      provider.sendQuickReply(sender, message)
      stay

    case Event(FormDone(sender, slot), _) =>
      provider.sendReceiptCard(sender, slot)
      goto(Qualifying)

//    case Event(Respond(platform, sender, text), _) =>
//      log.debug("received Respond event")
//      testPlatformChange(platform, sender)
//
//      if (isPaymentVerified) {
//        log.debug("looking up address: " + text)
//        // TODO wrap in a function
//        lazy val f = addressService.getAddress(text)
//        val f1 = f withTimeout new TimeoutException("future timed out")
//        val f2 = f1 map { response =>
//          log.debug("received address lookup response:\n" + response.toJson.prettyPrint)
//          if (response.results.nonEmpty) {
//            isAddressVerified = true
//            val address = response.results.head.getAddress
//            val message = address.toString
//            history append Exchange(Some(text), message)
//            provider.sendReceiptCard(sender, address)
//            goto(Starting)
//          } else {
//            val message = "Sorry, I could not interpret that"
//            history append Exchange(Some(text), message)
//            provider.sendTextMessage(sender, message)
//            stay
//          }
//        }
//        f2.failed map { e =>
//          log.error(e.getMessage)
//          stay
//        }
//        Await.result(f2, timeout)
//      } else {
//        isPaymentVerified = true
//        val message = "What address should I send the order to?"
//        history append Exchange(Some(text), message)
//        provider.sendTextMessage(sender, message)
//        stay
//      }

  }

  whenUnhandled {

    case Event(Greet(platform, sender, user, text), _) =>
      log.debug("received Greet event")
      testPlatformChange(platform, sender)
      greet(sender, user, text)
      stay

    case Event(Respond(platform, sender, text), _) =>
      log.debug("received Respond event")
      testPlatformChange(platform, sender)
      val reply = smallTalkService.getSmallTalkResponse(sender, text)
      if (reply == "Didn't get that!") {
        //analyze(sender, text)
        shrug(sender, text)
      } else {
        history append Exchange(Some(text), reply)
        provider.sendTextMessage(sender, reply)
      }
      stay

    case Event(Analyze(platform, sender, text), _) =>
      log.debug("received Analyze event")
      testPlatformChange(platform, sender)
      analyze(sender, text)
      stay

    case Event(BillEnquiry(platform, sender, text), _) =>
      log.debug("received BillEnquiry event")
      testPlatformChange(platform, sender)
      if (isAuthenticated) {
        val message = "Your current balance is $41.25. Would you like to pay it now?"
        history append Exchange(Some(text), message)
        provider.sendQuickReply(sender, message)
      } else {
        postAuthAction = () => {
          val message = "Your current balance is $41.25. Would you like to pay it now?"
          history append Exchange(Some(text), message)
          provider.sendQuickReply(sender, message)
          stay
        }
        history append Exchange(None, "login")
        provider.sendLoginCard(sender)
      }
      stay

    case Event(PostAuth(sender), _) =>
      log.debug("received PostAuth event")
      postAuthAction()

    case Event(Welcome(platform, sender), _) =>
      log.debug("received Authenticate event")
      isAuthenticated = true
      testPlatformChange(platform, sender)
      history append Exchange(None, "logged in")
      //provider.sendTextMessage(sender, "Welcome, login successful")
      stay

    case ev =>
      log.error(s"invalid event [${ev.toString}] while in state [${this.stateName}]")
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
      history append Exchange(Some(text), message)
      provider.sendTextMessage(sender, message)
    }
    f3 recover {
      case e: Throwable => log.error(e.getMessage)
    }
  }

  def greet(sender: String, user: User, text: String) = {
    val greeting = greetings(random.nextInt(greetings.size)) + " " + user.firstName + "!"
    history append Exchange(Some(text), greeting)
    provider.sendTextMessage(sender, greeting)
  }

  def shrug(sender: String, text: String) = {
    //val joke = if (random.nextInt(10) < 3) "\nHow about a joke instead?\n" + humourService.getJoke else ""
    failCount += 1
    if (failCount > maxFailCount) {
      context.parent ! Fallback(sender, history.toList)
      failCount = 0
      history.clear()
    } else {
      val message = "¯\\_(ツ)_/¯ " + shrugs(random.nextInt(shrugs.size)) // + joke
      history append Exchange(Some(text), message)
      provider.sendTextMessage(sender, message)
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

  def testPlatformChange(platform: Platforms.Value, sender: String) = {
    if (currentProvider != platform) {
      val oldPlatform = currentProvider
      provider = if (platform == Facebook) facebookService else skypeService
      currentProvider = platform
      provider.sendTextMessage(sender, s"Do you want to carry on our conversation from $oldPlatform?")
      // TODO
    }
  }

}

object ConversationActor extends NamedActor {

  override final val name = "ConversationActor"

  sealed trait State

  case object Qualifying extends State

  case object Buying extends State

  sealed trait Data

  case object Uninitialized extends Data

  val random = new Random

  val shrugs = Vector(
    //"I'm sorry, I did not understand. I don't even have arms.",
    "I'm sorry, I did not understand. These arms aren't even real."
    //    "I'm not that bright sometimes",
    //    "That's outside my circle of knowledge"
  )

  val greetings = Vector(
    "Hi there", "Hello"
  )

}
