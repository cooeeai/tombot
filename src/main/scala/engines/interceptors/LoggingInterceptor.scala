package engines.interceptors

import akka.actor.ActorLogging
import akka.contrib.pattern.ReceivePipeline
import akka.contrib.pattern.ReceivePipeline.Inner

/**
  * Created by markmo on 10/10/2016.
  */
trait LoggingInterceptor extends ActorLogging {
  this: ReceivePipeline =>

  pipelineOuter {
    case ev =>
      log.debug("{} received {}", getClass.getSimpleName, ev.toString)
      Inner(ev)
  }

}
