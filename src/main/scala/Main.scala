import akka.event.Logging
import com.typesafe.config.ConfigFactory
import services.Service

import scala.util.Properties

/**
  * Created by markmo on 16/07/2016.
  */
object Main extends App with Service {

  override val config = ConfigFactory.load()
  override val logger = Logging(system, getClass)

  val port = Properties.envOrElse("PORT", "8080").toInt

  val bindingFuture = http.bindAndHandle(routes, config.getString("http.interface"), config.getInt("http.port"))

  setupWelcomeGreeting()

//  println("Server online at http://localhost:8080/\nPress RETURN to stop...")
//  StdIn.readLine() // let it run until user presses return
//  bindingFuture
//    .flatMap(_.unbind()) // trigger unbinding from the port
//    .onComplete(_ => system.terminate()) // and shutdown when done
}
