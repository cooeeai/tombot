package utils

import akka.actor.ActorRef
import models.events.{Exchange, QuickReply, TextMessage}

/**
  * Created by markmo on 20/11/2016.
  */
trait General {

  def say(provider: ActorRef, historyActor: ActorRef, sender: String, inText: String, outText: String): Unit = {
    historyActor ! Exchange(Some(inText), outText)
    provider ! TextMessage(sender, outText)
  }

  def sendQuickReply(provider: ActorRef, historyActor: ActorRef, sender: String, inText: String, outText: String): Unit = {
    historyActor ! Exchange(Some(inText), outText)
    provider ! QuickReply(sender, outText)
  }

  def sendMultiMessage(provider: ActorRef, maxMessageLength: Int, sender: String, outText: String): Unit =
    outText.split("\n").foldLeft(0, 0, List[(Int, String)]()) {
      case ((group, len, lines), line) =>
        val len1 = len + line.length
        if (len1 < maxMessageLength) {
          (group, len1, (group, line) :: lines)
        } else {
          (group + 1, line.length, (group + 1, line) :: lines)
        }
    }._3 groupBy (_._1) foreach {
      case (_, ls) =>
        val lines = ls.map(_._2).reverse
        provider ! TextMessage(sender, lines mkString "\n")
    }

  val shrugEmoji = "¯\\_(ツ)_/¯ "

  val newLine = "\n"

}
