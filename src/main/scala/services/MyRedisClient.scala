package services

import akka.actor.ActorSystem
import com.google.inject.name.Named
import com.google.inject.{Inject, Singleton}
import redis.RedisClient

import scala.concurrent.duration.FiniteDuration

/**
  * Created by markmo on 2/12/2016.
  */
@Singleton
class MyRedisClient @Inject()(@Named("redisConnectTimeout") connectTimeout: FiniteDuration,
                              system: ActorSystem)
  extends RedisClient(connectTimeout = Some(connectTimeout))(system) {

  var connected = false

  override def onConnectStatus: (Boolean) => Unit = (status: Boolean) => {
    connected = status
  }

  def isConnected = connected

}
