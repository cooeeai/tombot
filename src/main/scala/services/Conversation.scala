package services

import akka.actor.ActorRef

import scala.concurrent.Future

/**
  * Created by markmo on 19/09/2016.
  */
trait Conversation {

  def converse(sender: String, message: Any): Unit

  def getConversationActor(sender: String): Future[ActorRef]

}
