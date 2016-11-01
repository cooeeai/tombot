package services

import akka.actor.FSM
import akka.contrib.pattern.ReceivePipeline
import apis.facebookmessenger.{FacebookDelivery, FacebookMessageDeliveredEvent, FacebookMessageReadEvent, FacebookRead}
import com.google.inject.Inject
import conversationengine.LoggingInterceptor
import memory.Slot
import models.Item
import modules.akkaguice.NamedActor
import services.FacebookSendQueue.{Data, State}

import scala.concurrent.Future

/**
  * Created by markmo on 27/10/2016.
  */
class FacebookSendQueue @Inject()(facebookService: FacebookService)
  extends ReceivePipeline
    with LoggingInterceptor
    with FSM[State, Data] {

  import FacebookSendQueue._
  import context.dispatcher
  import facebookService._

  startWith(Idle, Uninitialized)

  when(Idle) {
    case Event(ev: SendEvent, _) =>
      println("got here")
      send(ev) map { response =>
        self ! response
      }
      goto(WaitingForResponse) using Todo(None, Vector.empty[(Long, SendEvent)])
  }

  when(WaitingForResponse) {
    case Event(response: SendResponse, t: Todo) =>
      if (t.queue.length < 2) {
        goto(Idle)
      } else {
        goto(Active) using Todo(Some(response.messageId), t.queue.tail)
      }

    case Event(ev: SendEvent, t@Todo(_, q)) =>
      stay using t.copy(queue = q :+ (timestamp, ev))
  }

  when(Active) {
    case Event(ev: SendEvent, t@Todo(_, q)) =>
      stay using t.copy(queue = q :+ (timestamp, ev))

    case Event(FacebookMessageDeliveredEvent(_, _, FacebookDelivery(messageIds, watermark, _)), t: Todo) =>
      if (messageIds.isDefined) {
        for (mid <- messageIds.get) {
          if (t.current.get == mid) {
            send(t.queue.head._2) map { response =>
              self ! response
            }
          }
        }
      } else {
        val (ts, ev) = t.queue.head
        if (ts < watermark) {
          send(ev) map { response =>
            self ! response
          }
        }
      }
      goto(WaitingForResponse)

    case Event(FacebookMessageReadEvent(_, _, FacebookRead(watermark, _)), t: Todo) =>
      val (ts, ev) = t.queue.head
      if (ts < watermark) {
        send(ev) map { response =>
          self ! response
        }
      }
      goto(WaitingForResponse)
  }

  whenUnhandled {
    case Event(ev, s) =>
      log.warning("received unhandled request {} in state {}/{}", ev, stateName, s)
      stay
  }

  initialize()

  def send(ev: SendEvent): Future[SendResponse] = ev match {
    case TextMessage(sender, text) => sendTextMessage(sender, text)
    case LoginCard(sender, conversationId) => sendLoginCard(sender, conversationId)
    case HeroCard(sender, items) => sendHeroCard(sender, items)
    case ReceiptCard(sender, slot) => sendReceiptCard(sender, slot)
    case QuickReply(sender, text) => sendQuickReply(sender, text)
  }

  def timestamp: Long = System.currentTimeMillis / 1000

}

object FacebookSendQueue extends NamedActor {

  override final val name = "FacebookSendQueue"

  sealed trait State
  case object Idle extends State
  case object Active extends State
  case object WaitingForResponse extends State

  sealed trait Data
  case object Uninitialized extends Data
  final case class Todo(current: Option[String], queue: Seq[(Long, SendEvent)]) extends Data

  sealed trait SendEvent
  case class TextMessage(sender: String, text: String) extends SendEvent
  case class LoginCard(sender: String, conversationId: String = "") extends SendEvent
  case class HeroCard(sender: String, items: List[Item]) extends SendEvent
  case class ReceiptCard(sender: String, slot: Slot) extends SendEvent
  case class QuickReply(sender: String, text: String) extends SendEvent

}
