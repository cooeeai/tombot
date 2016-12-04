package engines

import akka.actor.Actor
import com.google.inject.Inject
import models.events.{Exchange, GetHistory}
import modules.akkaguice.NamedActor
import services.MyRedisClient

import scala.collection.mutable

/**
  * Created by markmo on 20/11/2016.
  */
class HistoryActor @Inject()(redis: MyRedisClient) extends Actor {

  val history = mutable.ArrayBuffer.empty[Exchange]

  val key = context.parent.path.name

  def receive = {

    case ev: Exchange =>
      history += ev
      if (redis.isConnected) {
        redis.lpush(key, "res:" + ev.response)
        redis.lpush(key, "req:" + ev.request.getOrElse(""))
      }

    case GetHistory => sender() ! history.toList

  }

}

object HistoryActor extends NamedActor {

  override final val name = "HistoryActor"

}
