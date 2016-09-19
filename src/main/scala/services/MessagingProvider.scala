package services

import memory.Slot

import scala.concurrent.Future

/**
  * Created by markmo on 14/08/2016.
  */
trait MessagingProvider {

  def sendTextMessage(sender: String, text: String): Unit

  def sendLoginCard(sender: String, conversationId: String = ""): Unit

  def sendHeroCard(sender: String): Unit

  def sendReceiptCard(sender: String, slot: Slot): Unit

  def sendQuickReply(sender: String, text: String): Unit

  def getUserProfile(sender: String): Future[String]

}
