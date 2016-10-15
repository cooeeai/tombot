import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Route, MalformedRequestContentRejection, RejectionHandler}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import com.google.inject.Guice
import com.typesafe.config.Config
import controllers._
import modules.akkaguice.AkkaModule
import modules.config.ConfigModule
import modules.conversation.ConversationModule
import modules.logging.LoggingModule
import net.codingwell.scalaguice.InjectorExtensions._

import scala.util.Properties

/**
  * Created by markmo on 16/07/2016.
  */
object Main extends App {

  import StatusCodes._

  val injector = Guice.createInjector(
    new ConfigModule(),
    new LoggingModule(),
    new AkkaModule(),
    new ConversationModule()
  )

  implicit val system = injector.instance[ActorSystem]
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  val http = Http()
  val config = injector.instance[Config]
  val logger = injector.instance[LoggingAdapter]

  implicit def myRejectionHandler = {
    val handler = RejectionHandler.newBuilder().handle {
      case MalformedRequestContentRejection(message, e) =>
        logger.error(message)
        extractRequest { request =>
          logger.error(request._4.toString)
          complete(BadRequest)
        }
      case e =>
        logger.error(e.toString)
        complete(BadRequest)
    }
    handler.result()
  }

  val facebookController = injector.instance[FacebookController]
  val skypeController = injector.instance[SkypeController]
  val sparkController = injector.instance[SparkController]
  val addressController = injector.instance[ValidationController]
  val smsController = injector.instance[SMSController]

  val port = Properties.envOrElse("PORT", "8080").toInt

//  val proxy = Route { context =>
//    val request = context.request
//    println("Opening connection to " + request.uri.authority.host.address)
//    val flow = Http(system).outgoingConnection(request.uri.authority.host.address(), 80)
//    val handler = Source.single(context.request)
//      .via(flow)
//      .runWith(Sink.head)
//      .flatMap(context.complete(_))
//    handler
//  }

  val bindingFuture =
    http.bindAndHandle(
      facebookController.routes ~
        skypeController.routes ~
        sparkController.routes ~
        addressController.routes ~
        smsController.routes,
      config.getString("http.interface"), config.getInt("http.port"))

  facebookController.setupWelcomeGreeting()

//  println("Server online at http://localhost:8080/\nPress RETURN to stop...")
//  StdIn.readLine() // let it run until user presses return
//  bindingFuture
//    .flatMap(_.unbind()) // trigger unbinding from the port
//    .onComplete(_ => system.terminate()) // and shutdown when done
}
