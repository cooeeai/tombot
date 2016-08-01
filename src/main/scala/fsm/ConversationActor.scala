package fsm

import akka.actor.{Actor, ActorLogging, ActorSystem, FSM}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, RequestEntity}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.pattern.after
import akka.stream.ActorMaterializer
import akkaguice.NamedActor
import com.google.inject.Inject
import facebookmessenger._
import fsm.ConversationActor.{Data, State}
import googlemaps.MapsJsonSupport
import services.{AddressService, CatalogService, PaymentService}
import spray.json._

import scala.concurrent._
import scala.concurrent.duration._
import scala.util.{Failure, Random, Success}

/**
  * Created by markmo on 27/07/2016.
  */
class ConversationActor @Inject()(
                                   catalogService: CatalogService,
                                   addressService: AddressService,
                                   paymentService: PaymentService,
                                   implicit val system: ActorSystem)
  extends Actor
    with ActorLogging
    with FbJsonSupport
    with MapsJsonSupport
    with FSM[State, Data] {

  import ConversationActor._

  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  implicit val timeout = 20.second

  implicit class FutureExtensions[T](f: Future[T]) {

    def withTimeout(timeout: => Throwable)(implicit duration: FiniteDuration, system: ActorSystem): Future[T] = {
      Future firstCompletedOf Seq(f, after(duration, system.scheduler)(Future.failed(timeout)))
    }

  }

  val http = Http()

  def token = System.getenv("FB_PAGE_ACCESS_TOKEN")

  startWith(Start, Uninitialized)

  when(Start) {
    case Event(Greet(sender), _) =>
      log.debug("received Greet event")
      greet(sender)
      stay
    case Event(Qualify(sender, productType), _) =>
      log.debug("received Buy event")
      productType match {
        case Some(typ) =>
          sendTextMessage(sender, s"What type of $typ did you have in mind?")
          goto(Qualifying) using Offer
        case None =>
          sendTextMessage(sender, "What did you want to buy?")
          stay
      }
    case Event(Respond(sender, _), _) =>
      log.debug("received Respond event")
      shrug(sender)
      stay
    case _ =>
      log.error("invalid event")
      stay
  }

  when(Qualifying) {
    case Event(Greet(sender), _) =>
      log.debug("received Greet event")
      greet(sender)
      stay
    case Event(Qualify(sender, productType), _) =>
      log.debug("received Qualify event")
      productType match {
        case Some(typ) => sendTextMessage(sender, s"What type of $typ did you have in mind?"); stay
        case None => sendTextMessage(sender, "What did you want to buy?"); stay
      }
    case Event(Respond(sender, text), _) =>
      log.debug("received Respond event")
      if (text == "iphone") {
        sendGenericMessage(sender)
        goto(Buying)
      } else {
        sendTextMessage(sender, "Sorry, I didn't understand that")
        stay
      }
    case _ =>
      log.error("invalid event")
      stay
  }

  when(Buying) {
    case Event(Greet(sender), _) =>
      log.debug("received Greet event")
      greet(sender)
      stay
    case Event(Buy(sender, _), _) =>
      sendTextMessage(sender, "What address should I send the order to?")
      stay
    case Event(Respond(sender, text), _) =>
      log.debug("received Respond event")
      log.debug("looking up address: " + text)
      lazy val f = addressService.getAddress(text)
      f withTimeout new TimeoutException("future timed out") onComplete {
        case Success(response) =>
          log.debug("received address lookup response:\n" + response.toJson.prettyPrint)
          if (response.results.nonEmpty) {
            sendReceiptMessage(sender, response.results.head.getAddress)
          } else {
            sendTextMessage(sender, "Sorry, I could not interpret that")
          }
        case Failure(e) => log.error(e.getMessage)
      }
      stay
    case _ =>
      log.error("invalid event")
      stay
  }

  initialize()

  def greet(sender: String) =
    sendTextMessage(sender, greetings(random.nextInt(greetings.size)))

  def shrug(sender: String) =
    sendTextMessage(sender, "¯\\_(ツ)_/¯ " + shrugs(random.nextInt(shrugs.size)))

  def sendTextMessage(sender: String, text: String): Unit = {
    log.info("sending text message: [" + text + "] to sender: " + sender)
    val messageData = JsObject("text" -> JsString(text))
    val payload = JsObject(
      "recipient" -> JsObject("id" -> JsString(sender)),
      "message" -> messageData
    )
    log.debug("sending payload:\n" + payload.toJson.prettyPrint)
    for {
      request <- Marshal(payload).to[RequestEntity]
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = s"https://graph.facebook.com/v2.6/me/messages?access_token=$token",
        entity = request))
      entity <- Unmarshal(response.entity).to[String]
    } yield ()
  }

  def sendGenericMessage(sender: String): Unit = {
    log.info("sending generic message to sender: " + sender)
    val elements = catalogService.getElements
    val payload =
      GenericMessageTemplate(
        Recipient(sender),
        GenericMessage(
          Attachment(
            attachmentType = "template",
            payload = AttachmentPayload(templateType = "generic", elements = elements)
          )
        )
      )
    log.debug("sending payload:\n" + payload.toJson.prettyPrint)
    for {
      request <- Marshal(payload).to[RequestEntity]
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = s"https://graph.facebook.com/v2.6/me/messages?access_token=$token",
        entity = request))
      entity <- Unmarshal(response.entity).to[String]
    } yield ()
  }

  def sendReceiptMessage(sender: String, address: Address): Unit = {
    log.info("sending receipt message to sender: " + sender)
    val elements = paymentService.getElements
    val receiptId = "order" + Math.floor(Math.random() * 1000)
    val payload =
      ReceiptMessageTemplate(
        Recipient(sender),
        ReceiptMessage(
          ReceiptAttachment(
            attachmentType = "template",
            payload = ReceiptPayload(
              templateType = "receipt",
              recipientName = "Peter Chang",
              orderNumber = receiptId,
              currency = "AUD",
              paymentMethod = "Visa 1234",
              orderURL = "",
              timestamp = 1428444852L,
              elements = elements,
              address = address,
              summary = Summary(
                subtotal = BigDecimal("1047.00"),
                shippingCost = BigDecimal("25.00"),
                totalTax = BigDecimal("104.70"),
                totalCost = BigDecimal("942.30")
              ),
              adjustments = List(Adjustment(name = "Coupon DAY1", amount = BigDecimal("-100.00")))
            )
          )
        )
      )
    log.debug("sending payload:\n" + payload.toJson.prettyPrint)
    for {
      request <- Marshal(payload).to[RequestEntity]
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = s"https://graph.facebook.com/v2.6/me/messages?access_token=$token",
        entity = request))
      entity <- Unmarshal(response.entity).to[String]
    } yield ()
  }

}

object ConversationActor extends NamedActor {

  override final val name = "ConversationActor"

  // events
  case class Greet(sender: String)
  case class Qualify(sender: String, productType: Option[String])
  case class Buy(sender: String, productType: String)
  case class Respond(sender: String, message: String)

  sealed trait State
  case object Start extends State
  case object Greeted extends State
  case object Qualifying extends State
  case object Buying extends State

  sealed trait Data
  case object Uninitialized extends Data
  case object Offer extends Data

  val random = new Random

  val shrugs = Vector(
    "I'm sorry, I did not understand. I don't even have arms.",
    "I'm not that bright sometimes",
    "That's outside my circle of knowledge"
  )

  val greetings = Vector(
    "Hi there", "Hello"
  )

}
