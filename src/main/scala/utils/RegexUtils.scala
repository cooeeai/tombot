package utils

import scala.language.implicitConversions
import scala.util.matching.Regex

/**
  * Created by markmo on 19/10/2016.
  */
object RegexUtils {

  class RichRegex(underlying: Regex) {
    def matches(text: String) = underlying.pattern.matcher(text).matches
  }

  implicit def regexToRichRegex(regex: Regex): RichRegex = new RichRegex(regex)

}
