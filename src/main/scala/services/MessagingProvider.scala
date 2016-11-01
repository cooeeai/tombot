package services

import memory.Slot
import models.{Item, UserProfile}

import scala.concurrent.Future

/**
  * Created by markmo on 14/08/2016.
  */
trait MessagingProvider {

  def sendTextMessage(sender: String, text: String): Future[SendResponse]

  def sendLoginCard(sender: String, conversationId: String = ""): Future[SendResponse]

  def sendHeroCard(sender: String, items: List[Item]): Future[SendResponse]

  def sendReceiptCard(sender: String, slot: Slot): Future[SendResponse]

  def sendQuickReply(sender: String, text: String): Future[SendResponse]

  def getUserProfile(sender: String): Future[UserProfile]

}

case class SendResponse(messageId: String)
