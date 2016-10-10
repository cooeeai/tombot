package conversationengine

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
      log.debug(getClass.getSimpleName + " received " + ev.toString)
      Inner(ev)
  }

}
