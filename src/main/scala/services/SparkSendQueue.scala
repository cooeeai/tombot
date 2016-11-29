package services

import akka.actor.FSM
import akka.contrib.pattern.ReceivePipeline
import apis.ciscospark.SparkMessage
import com.google.inject.Inject
import engines.interceptors.LoggingInterceptor
import modules.akkaguice.NamedActor
import services.SparkSendQueue.{Data, State}

import scala.concurrent.Future

/**
  * Created by markmo on 22/11/2016.
  */
class SparkSendQueue @Inject()(sparkService: SparkService)
  extends ReceivePipeline
    with LoggingInterceptor
    with FSM[State, Data] {

  import SparkSendQueue._
  import sparkService._

  // import execution context for send calls
  import context.dispatcher

  startWith(Idle, Uninitialized)

  when(Idle) {
    case Event(ev: SendEvent, _) =>
      send(ev) map { response =>
        self ! response
      }
      goto(WaitingForResponse) using Todo(Vector.empty)
  }

  when(WaitingForResponse) {
    case Event(SparkMessage, t: Todo) =>
      if (t.queue.isEmpty) {
        goto(Idle)
      } else {
        send(t.queue.head._2) map { response =>
          self ! response
        }
        goto(WaitingForResponse) using t.copy(queue = t.queue.tail)
      }

    case Event(ev: SendEvent, t@Todo(q)) =>
      stay using t.copy(queue = q :+(timestamp, ev))
  }

  whenUnhandled {
    case Event(ev, s) =>
      log.warning("{} received unhandled request {} in state {}/{}", name, ev, stateName, s)
      stay
  }

  initialize()

  def send(ev: SendEvent): Future[SparkMessage] = ev match {
    case TextMessage(roomId, toPersonId, toPersonEmail, text, files) =>
      postMessage(roomId, toPersonId, toPersonEmail, text, files)
  }

  def timestamp: Long = System.currentTimeMillis / 1000

}

object SparkSendQueue extends NamedActor {

  override final val name = "SparkSendQueue"

  sealed trait State
  case object Idle extends State
  case object WaitingForResponse extends State

  sealed trait Data
  case object Uninitialized extends Data
  final case class Todo(queue: Seq[(Long, SendEvent)]) extends Data

  sealed trait SendEvent
  case class TextMessage(roomId: Option[String],
                         toPersonId: Option[String],
                         toPersonEmail: Option[String],
                         text: String,
                         files: Option[List[String]]) extends SendEvent

}