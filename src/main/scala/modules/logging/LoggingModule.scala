package modules.logging

import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import com.google.inject.{AbstractModule, Inject, Provider}
import modules.logging.LoggingModule.LoggingProvider
import net.codingwell.scalaguice.ScalaModule

/**
  * Created by markmo on 28/07/2016.
  */
object LoggingModule {

  class LoggingProvider @Inject()(val system: ActorSystem) extends Provider[LoggingAdapter] {
    override def get() = Logging(system, "tombot")
  }

}

class LoggingModule extends AbstractModule with ScalaModule {

  override def configure(): Unit =
    bind[LoggingAdapter].toProvider[LoggingProvider].asEagerSingleton()

}
