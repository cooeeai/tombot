package engines.interceptors

import akka.actor.{ActorLogging, ActorRef}
import akka.contrib.pattern.ReceivePipeline
import akka.contrib.pattern.ReceivePipeline.{HandledCompletely, Inner}
import models.Platform._
import models.events.{PlatformAware, SetProvider}
import modules.akkaguice.ActorInject
import services.FacebookSendQueue
import utils.Memoize

/**
  * Created by markmo on 14/11/2016.
  */
trait PlatformSwitchInterceptor extends ActorInject with ActorLogging with Memoize {
  this: ReceivePipeline =>

  var currentPlatform: Option[Platform] = None

  pipelineInner {
    case ev: PlatformAware =>
      val platform = ev.platform
      val sender = ev.sender
      if (currentPlatform.isEmpty || currentPlatform.get != platform) {
        log.debug("setting platform to [{}] from [{}]", platform, currentPlatform.getOrElse("None"))
        val previous = currentPlatform
        currentPlatform = Some(platform)
        self ! SetProvider(platform, previous, provider(platform), ev, sender, handleEventImmediately = true)
        HandledCompletely
      } else {
        Inner(ev)
      }
  }

  lazy val provider: (Platform) => ActorRef = memoize {
    case platform => platform match {
      case _ => injectActor[FacebookSendQueue]("facebook") // facebook is the default provider
    }
  }

}
