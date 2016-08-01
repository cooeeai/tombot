package akkaguice

import akka.actor._
import com.google.inject.Injector

/**
  * Created by markmo on 27/07/2016.
  */
object GuiceAkkaExtension extends ExtensionId[GuiceAkkaExtensionImpl] with ExtensionIdProvider {

  /**
    * Register ourself with the ExtensionIdProvider
    *
    * @return GuiceAkkaExtension
    */
  override def lookup() = GuiceAkkaExtension

  /**
    * Called by Akka to create an instance of the extension
    *
    * @param system ExtendedActorSystem
    * @return GuiceAkkaExtensionImpl
    */
  override def createExtension(system: ExtendedActorSystem) = new GuiceAkkaExtensionImpl

  /**
    * Java API: Retrieve the extension for the given system
    *
    * @param system ActorSystem
    * @return GuiceAkkaExtensionImpl
    */
  override def get(system: ActorSystem): GuiceAkkaExtensionImpl = super.get(system)

}

/**
  * Created by markmo on 27/07/2016.
  */
class GuiceAkkaExtensionImpl extends Extension {

  private var injector: Injector = _

  def initialize(injector: Injector): Unit = {
    this.injector = injector
  }

  def props(actorName: String) = Props(classOf[GuiceActorProducer], injector, actorName)

}

/**
  * A convenience trait for an actor companion object to extend, to provide names.
  */
trait NamedActor {

  def name: String

}

/**
  * Mix in with Guice Modules that contain providers for top-level actor refs.
  */
trait GuiceAkkaActorRefProvider {

  def propsFor(system: ActorSystem, name: String) = GuiceAkkaExtension(system).props(name)

  def provideActorRef(system: ActorSystem, name: String): ActorRef = system.actorOf(propsFor(system, name))

}
