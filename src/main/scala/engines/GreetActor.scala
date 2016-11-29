package engines

import akka.actor._
import com.google.inject.Inject
import com.google.inject.assistedinject.Assisted
import models.Platform.Platform
import models.events._
import modules.akkaguice.NamedActor
import services.User
import utils.General

import scala.util.Random

/**
  * Created by markmo on 27/07/2016.
  */
class GreetActor @Inject()(@Assisted("defaultProvider") val defaultProvider: ActorRef,
                           @Assisted("historyActor") val historyActor: ActorRef)
  extends SimpleConversationActor with General {

  import GreetActor._

  override def withProvider(provider: ActorRef): Receive = {

    case Greet(_, sender, user, text) =>
      say(provider, historyActor, sender, text, randomGreeting(user.firstName))

  }

}

object GreetActor extends NamedActor {

  override final val name = "GreetActor"

  trait Factory {
    def apply(@Assisted("defaultProvider") defaultProvider: ActorRef,
              @Assisted("historyActor") historyActor: ActorRef): Actor
  }

  case class Greet(platform: Platform, sender: String, user: User, text: String) extends PlatformAware

  def randomGreeting(name: String) =
    greetings(random.nextInt(greetings.size)).format(name)

  private val random = new Random

  private val greetings = Vector(
    "Hi there %s!",
    "Hello %s!",
    "Howdy %s!",
    "Ahoy %s!",
    "‘Ello Mate",
    "What's cookin' Good Lookin'?",
    "Aloha %s!",
    "Hola %s!",
    "Que Pasa %s!",
    "Bonjour %s!",
    "Ciao %s!",
    "Konnichiwa %s!"
  )

}
