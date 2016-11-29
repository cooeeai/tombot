package engines

import akka.actor.Actor
import models.events.{Exchange, GetHistory}
import modules.akkaguice.NamedActor

import scala.collection.mutable

/**
  * Created by markmo on 20/11/2016.
  */
class HistoryActor extends Actor {

  val history = mutable.ArrayBuffer.empty[Exchange]

  def receive = {

    case ev: Exchange => history += ev

    case GetHistory => sender() ! history.toList

  }

}

object HistoryActor extends NamedActor {

  override final val name = "HistoryActor"

}
