package engines.receivers

import akka.actor.{ActorLogging, FSM}
import engines.ComplexConversationActor.{ConversationContext, Data, State}
import models.events.QuickReplyResponse

/**
  * Created by markmo on 18/11/2016.
  */
trait QuickReplyReceiver extends ActorLogging with FSM[State, Data] {

  val quickReplyReceive: StateFunction = {
    case Event(QuickReplyResponse(platform, sender, text), ctx: ConversationContext) =>
      ctx.postAction match {
        case Some(action) =>
          log.debug("post action is defined")
          if (confirmed(text)) {
            log.debug("confirmed")
            //historyActor ! Exchange(Some(text), "confirmed")
            action(ctx.copy(postAction = None))
          } else {
            log.debug("denied")
            //historyActor ! Exchange(Some(text), "denied")
            stay using ctx.copy(postAction = None)
          }
        case None => stay
      }
  }

  private def confirmed(text: String): Boolean = text.toLowerCase == "yes"

}
