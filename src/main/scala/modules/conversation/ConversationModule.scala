package modules.conversation

import akka.actor.Actor
import com.google.inject.AbstractModule
import com.google.inject.name.Names
import conversationengine._
import modules.akkaguice.AkkaGuiceSupport
import net.codingwell.scalaguice.ScalaModule
import services._

/**
  * Created by markmo on 30/07/2016.
  */
class ConversationModule extends AbstractModule with ScalaModule with AkkaGuiceSupport {

  override def configure(): Unit = {
    bind[Actor].annotatedWith(Names.named(ConversationActor.name)).to[ConversationActor]
    bind[Actor].annotatedWith(Names.named(WatsonConversationActor.name)).to[WatsonConversationActor]
    bind[Actor].annotatedWith(Names.named(AgentConversationActor.name)).to[AgentConversationActor]
    bind[Actor].annotatedWith(Names.named(ConciergeActor.name)).to[ConciergeActor]
    bind[Actor].annotatedWith(Names.named(FormActor.name)).to[FormActor]
    bind[Actor].annotatedWith(Names.named(IntentActor.name)).to[IntentActor]
    bind[Actor].annotatedWith(Names.named(FacebookSendQueue.name)).to[FacebookSendQueue]
    bind[MessagingProvider].annotatedWith(Names.named(FacebookService.name)).to[FacebookService]
    bind[MessagingProvider].annotatedWith(Names.named(SkypeService.name)).to[SkypeService]
    bind[Conversation].to[ConversationService]
  }

}
