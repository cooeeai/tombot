package engines

import akka.actor.{Actor, ActorLogging}
import apis.witapi.{Entity, WitJsonSupport}
import com.google.inject.Inject
import engines.AnalyzeActor.Analyze
import engines.GreetActor.Greet
import example.BuyConversationActor.Qualify
import models.events._
import modules.akkaguice.NamedActor
import services.{IntentService, UserService}
import spray.json._

/**
  * Created by markmo on 21/11/2016.
  */
class WitIntentActor @Inject()(intentService: IntentService, userService: UserService)
  extends Actor with ActorLogging with WitJsonSupport {

  import context.dispatcher

  def receive = {

    case ev@TextResponse(platform, from, text, _) =>
      log.debug("WitIntentActor received TextResponse")

      // avoid closing over mutable state
      val currentSender = sender()

      intentService.getIntent(text) map { meaning =>
        log.debug("received meaning:\n{}", meaning.toJson.prettyPrint)

        meaning.getIntent match {

          case Some(Entity(confidence, "greet", _)) =>
            log.debug("responding to [greet] intent")
            log.debug("looking up user with id [{}]", from)
            userService.getUser(from) match {
              case Some(user) =>
                log.debug("found user: {}", user)
                currentSender ! IntentVote(confidence, Greet(platform, from, user, text))
              case None =>
                log.warning("user not found")
                currentSender ! IntentVote(0.0, ev)
            }

          case Some(Entity(confidence, "buy", _)) =>
            log.debug("responding to [buy] intent")
            val productType = meaning.getEntityValue("product_type")
            currentSender ! IntentVote(confidence, Qualify(platform, from, productType, text), multistep = true)

          case Some(Entity(confidence, "analyze", _)) =>
            log.debug("responding to [analyze] intent")
            // not a conversational intent, therefore do not become
            currentSender ! IntentVote(confidence, Analyze(platform, from, text))

          case _ =>
            log.warning("responding to [unknown] intent")
            currentSender ! IntentVote(0.0, ev)
        }
      }

  }

}

object WitIntentActor extends NamedActor {
  override final val name = "WitIntentActor"
}
