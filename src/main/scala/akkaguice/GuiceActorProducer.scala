package akkaguice

import akka.actor.{Actor, IndirectActorProducer}
import com.google.inject.name.Names
import com.google.inject.{Injector, Key}

/**
  * A creator for actors that allows us to return actor prototypes that are created by Guice
  * (and therefore injected with any dependencies needed by that actor). Since all untyped actors
  * implement the Actor trait, we need to use a name annotation on each actor (defined in the Guice
  * module) so that the name-based lookup obtains the correct actor from Guice.
  *
  * Created by markmo on 27/07/2016.
  */
class GuiceActorProducer(val injector: Injector, val actorName: String) extends IndirectActorProducer {

  override def actorClass = classOf[Actor]

  override def produce() =
    injector.getBinding(Key.get(classOf[Actor], Names.named(actorName))).getProvider.get()

}
