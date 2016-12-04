package engines.receivers

import akka.actor.FSM
import engines.ComplexConversationActor.{ConversationContext, Data, State}
import models.events.{QuickReply, SetProvider}

/**
  * Created by markmo on 14/11/2016.
  */
trait PlatformSwitchReceiver extends FSM[State, Data] {

  val platformSwitchReceive: StateFunction = {
    case Event(SetProvider(platform, previous, ref, ev, sender, _), ctx: ConversationContext) =>
      if (previous.isDefined) {
        ref ! QuickReply(sender, s"Do you want to carry on our conversation from ${previous.get}?")
        stay using ctx.copy(provider = ref, postAction = Some((currentActor, ctx) => {
          currentActor ! ev
          stay
        }))
      } else {
        // TODO
        // test that state is updated before ev is received by self
        self ! ev
        stay using ctx.copy(provider = ref)
      }
  }

}
