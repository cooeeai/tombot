package utils

import akka.actor.ActorSystem

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

/**
  * Created by markmo on 7/10/2016.
  */
object LangUtils {

  implicit val timeout = 20 second

  implicit class FutureExtensions[T](f: Future[T]) {
    def withTimeout(timeout: => Throwable)(implicit duration: FiniteDuration, system: ActorSystem): Future[T] = {
      Future firstCompletedOf Seq(f, after(duration, system.scheduler)(Future.failed(timeout)))
    }
  }

}
