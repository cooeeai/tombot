package utils

import com.typesafe.config.Config

/**
  * Created by markmo on 14/09/2016.
  */
object ConfigOptional {

  implicit class RichConfig(val underlying: Config) extends AnyVal {

    def getOptionalBoolean(path: String): Option[Boolean] = if (underlying.hasPath(path)) {
      Some(underlying.getBoolean(path))
    } else {
      None
    }

    def getOptionalString(path: String): Option[String] = if (underlying.hasPath(path)) {
      Some(underlying.getString(path))
    } else {
      None
    }

    def getOptionalInt(path: String): Option[Int] = if (underlying.hasPath(path)) {
      Some(underlying.getInt(path))
    } else {
      None
    }

    def getOptionalConfig(path: String): Option[Config] = if (underlying.hasPath(path)) {
      Some(underlying.getConfig(path))
    } else {
      None
    }

  }

}
