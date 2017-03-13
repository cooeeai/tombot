package services

import akka.actor.ActorSystem
import com.google.inject.name.Named
import com.google.inject.{Inject, Singleton}
import com.typesafe.config.Config
import redis.RedisClient

import scala.concurrent.duration.FiniteDuration

/**
  * Created by markmo on 2/12/2016.
  */
@Singleton
class MyRedisClient @Inject()(@Named("redisConnectTimeout") connectTimeout: FiniteDuration,
                              config: Config,
                              system: ActorSystem)
  extends RedisClient(host = config.getString("redis.host"), connectTimeout = Some(connectTimeout))(system) {

  var connected = false

  override def onConnectStatus: (Boolean) => Unit = (status: Boolean) => {
    connected = status
  }

  def isConnected = connected

}
