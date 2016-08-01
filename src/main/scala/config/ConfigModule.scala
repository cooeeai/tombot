package config

import com.google.inject.{AbstractModule, Provider}
import com.typesafe.config.{Config, ConfigFactory}
import config.ConfigModule.ConfigProvider
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
  * The config is bound as an eager singleton so that errors in the config are detected
  * as early as possible.
  */
class ConfigModule extends AbstractModule with ScalaModule {

  override def configure(): Unit = {
    bind[Config].toProvider[ConfigProvider].asEagerSingleton()
  }

}
