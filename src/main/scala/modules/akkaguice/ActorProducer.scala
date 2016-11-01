package modules.akkaguice

import akka.actor.{Actor, IndirectActorProducer}
import com.google.inject.Injector

/**
  * Created by markmo on 1/11/2016.
  */
private[akkaguice] class ActorProducer[A <: Actor](injector: Injector, clazz: Class[A]) extends IndirectActorProducer {

  def actorClass = clazz

  def produce() = injector.getBinding(clazz).getProvider.get()

}
