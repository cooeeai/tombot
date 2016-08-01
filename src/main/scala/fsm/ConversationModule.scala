package fsm

import akka.actor.Actor
import com.google.inject.AbstractModule
import com.google.inject.name.Names
import net.codingwell.scalaguice.ScalaModule

/**
  * Created by markmo on 30/07/2016.
  */
class ConversationModule extends AbstractModule with ScalaModule {

  override def configure(): Unit = {
    bind[Actor].annotatedWith(Names.named(ConversationActor.name)).to[ConversationActor]
  }

}
