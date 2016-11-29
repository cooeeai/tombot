package engines

import akka.actor.{ActorLogging, ActorRef, FSM}
import akka.contrib.pattern.ReceivePipeline
import engines.ComplexConversationActor.{Data, State}
import engines.interceptors.{LoggingInterceptor, LoginInterceptor}
import engines.receivers.{LoginReceiver, PlatformSwitchReceiver, QuickReplyReceiver}
import models.Platform._
import models.events.{TextResponse, Unhandled}
import utils.General

import scala.concurrent.duration._

/**
  * Created by markmo on 21/11/2016.
  */
trait ComplexConversationActor
  extends ActorLogging
    with ReceivePipeline
    with LoggingInterceptor
    with PlatformSwitchReceiver
    with LoginInterceptor
    with LoginReceiver
    with QuickReplyReceiver
    with General
    with FSM[State, Data] {

  implicit val timeout: akka.util.Timeout = 30 seconds

  val handleEventDefault: StateFunction =
    platformSwitchReceive orElse quickReplyReceive orElse loginReceive

  def shrug(platform: Platform, sender: String, text: String): Unit =
    context.parent ! Unhandled(TextResponse(platform, sender, text))

}

object ComplexConversationActor {

  trait State

  trait Data

  case class ConversationContext(provider: ActorRef,
                                 authenticated: Boolean,
                                 postAction: Option[ConversationContext => FSM.State[State, Data]]) extends Data

  case class TransferState(sender: String, ctx: ConversationContext)

}