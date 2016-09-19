package conversationengine

import akka.actor.{Actor, ActorLogging}
import apis.witapi.WitJsonSupport
import com.google.inject.Inject
import conversationengine.ConversationActor._
import modules.akkaguice.{GuiceAkkaExtension, NamedActor}
import services.{UserService, IntentService}
import spray.json._

import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Created by markmo on 18/09/2016.
  */
class IntentActor @Inject()(intentService: IntentService, userService: UserService)
  extends Actor with ActorLogging with WitJsonSupport {

  import controllers.Platforms._

  val a = context.actorOf(GuiceAkkaExtension(context.system).props(ConversationActor.name))

  override def receive = {
    case ev: TextLike =>
      log.debug("received TextLike event")
      val sender = ev.sender
      val text = ev.text

      intentService.getIntent(text) map { meaning =>
        log.debug("received meaning:\n" + meaning.toJson.prettyPrint)
        val intent = meaning.getIntent

        intent match {

          case Some("buy") =>
            a ! Qualify(Facebook, sender, meaning.getEntityValue("product_type"), text)

          case Some("greet") =>
            val user = userService.getUser(sender).get
            a ! Greet(Facebook, sender, user, text)

          case Some("analyze") =>
            a ! Analyze(Facebook, sender, text)

          case Some("bill-enquiry") =>
            a ! BillEnquiry(Facebook, sender, text)

          case _ =>
            a ! Respond(Facebook, sender, text)

        }
      }
  }

}

object IntentActor extends NamedActor {

  override final val name = "IntentActor"

}