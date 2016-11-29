package engines.receivers

import akka.actor.FSM
import apis.facebookmessenger.{FacebookMessageDeliveredEvent, FacebookMessageReadEvent}
import engines.ComplexConversationActor.{ConversationContext, Data, State}

/**
  * Created by markmo on 20/11/2016.
  */
trait MessageDeliveredReceiver extends FSM[State, Data] {

  val messageDeliveredReceive: StateFunction = {
    case Event(ev@(_: FacebookMessageDeliveredEvent | _: FacebookMessageReadEvent), ctx: ConversationContext) =>
      ctx.provider ! ev
      stay
  }

}
