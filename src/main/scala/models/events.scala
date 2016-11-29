package models

import akka.actor.ActorRef
import apis.ciscospark.SparkTempMembership
import memory.Slot
import models.ConversationEngine.ConversationEngine
import models.Platform.Platform

/**
  * Created by markmo on 4/10/2016.
  */
object events {

  case class Exchange(request: Option[String], response: String)

  type History = List[Exchange]

  type TempMembershipMap = Map[String, SparkTempMembership]

  trait PlatformAware {
    val platform: Platform
    val sender: String
  }

  trait Privileged {
    val sender: String
    val text: String
  }

  case class IntentVote(probability: Double, event: Any, multistep: Boolean = false)

  case class TextResponse(platform: Platform, sender: String, text: String) extends PlatformAware

  case class IntentUnknown(sender: String, text: String)

  case class Say(sender: String, text: String, message: String)

  case class QuickReplyResponse(platform: Platform, sender: String, text: String) extends PlatformAware

  case class Login(event: Any, sender: String, text: String)

  case class Welcome(platform: Platform, sender: String)

  case class Authenticated(sender: String, ref: ActorRef)

  case class Unhandled(event: TextResponse)

  case class ShowHistory(sender: String)

  case class SetProvider(platform: Platform,
                         previous: Option[Platform],
                         ref: ActorRef,
                         event: Any,
                         sender: String,
                         handleEventImmediately: Boolean = false)

  case class SetEngine(sender: String, engine: ConversationEngine)

  case class Fallback(sender: String, history: List[Exchange])

  case class FillForm(sender: String, goal: String)

  case class EndFillForm(sender: String, slot: Slot)

  case class NextQuestion(sender: String)

  case class UpdateTempMemberships(tempMemberships: TempMembershipMap)

  sealed trait SendEvent

  case class TextMessage(sender: String, text: String) extends SendEvent

  case class Prompt[T](message: TextMessage) extends SendEvent

  case class LoginCard(sender: String, conversationId: String = "") extends SendEvent

  case class HeroCard(sender: String, items: List[Item]) extends SendEvent

  case class ReceiptCard(sender: String, slot: Slot) extends SendEvent

  case class QuickReply(sender: String, text: String) extends SendEvent

  case object GetHistory

  case object StartMultistep

  case object NullEvent

  case object Reset

}