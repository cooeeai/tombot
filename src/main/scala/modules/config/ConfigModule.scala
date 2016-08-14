package modules.config

import com.google.inject.{AbstractModule, Provider}
import com.typesafe.config.{Config, ConfigFactory}
import modules.config.ConfigModule.ConfigProvider
import net.codingwell.scalaguice.ScalaModule

/**
  * Created by markmo on 28/07/2016.
  */
object ConfigModule {

  class ConfigProvider extends Provider[Config] {
    override def get() = ConfigFactory.load()
  }

}

/**
  * Binds the application configuration to the [[Config]] interface.
  *
  * The modules.config is bound as an eager singleton so that errors in the modules.config are detected
  * as early as possible.
  */
class ConfigModule extends AbstractModule with ScalaModule {

  override def configure(): Unit =
    bind[Config].toProvider[ConfigProvider].asEagerSingleton()

}
