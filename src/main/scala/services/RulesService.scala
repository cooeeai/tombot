package services

import akka.event.LoggingAdapter
import com.google.inject.Inject

import scala.annotation.tailrec
import scala.util.matching.Regex

/**
  * Created by markmo on 18/08/2016.
  */
class RulesService @Inject()(logger: LoggingAdapter) {

  import RulesService._

  def getContent(text: String): Option[String] = {
    logger.debug(s"looking up content for [$text]")

    @tailrec
    def loop(xs: List[(RulesExecutor, String)]): Option[String] = xs match {
      case Nil => None
      case (rule, content) :: rest =>
        if (rule.execute(text)) Some(clean(content)) else loop(rest)
    }

    loop(content)
  }

  private def clean(text: String) = text.trim.replaceAll("\\n", " ")

  private def questionRule =
    defineRule startsWith "what|where|how|why|which|can|does|who|will|compare|help" caseInsensitive() build()

  def isQuestion(text: String) = questionRule execute text

}

object RulesService {

  class RulesExecutor(rules: List[List[Regex]], isCaseSensitive: Boolean) {

    def execute(text: String): Boolean = {
      val trimmed = if (isCaseSensitive) text.trim.toLowerCase else text.trim
      rules forall { xs =>
        xs exists (x => (x findFirstIn trimmed).nonEmpty)
      }
    }

  }

  class RulesBuilder(rules: List[List[Regex]], isCaseSensitive: Boolean) {

    def and() = new RulesBuilder(List() :: rules, isCaseSensitive)

    def startsWith(text: String) = new RulesBuilder((text.split("\\|").toList.map(x => s"^$x".r) ++ rules.head) :: rules.tail, isCaseSensitive)

    def contains(text: String) = new RulesBuilder((text.split("\\|").toList.map(_.r) ++ rules.head) :: rules.tail, isCaseSensitive)

    def caseInsensitive() = new RulesBuilder(rules, true)

    def build() = new RulesExecutor(rules.reverse, isCaseSensitive)

  }

  def defineRule = new RulesBuilder(List(Nil), false)

  val helpText = "I can help you find and buy a phone"

  def content: List[(RulesExecutor, String)] = List(

    (defineRule contains "help" build(), helpText),

    (defineRule contains "what" and() contains "know" build(), helpText),

    (defineRule startsWith "what"
      and() contains "bot|virtual agent"
      and() contains "is" caseInsensitive() build(),
      """
        |A Bot, otherwise known as a chatbot, is an application that operates as a user in a messaging app. It can
        |receive and respond to messages using natural language.
      """.stripMargin
      ),

    (defineRule startsWith "what"
      and() contains "bot|virtual agent"
      and() contains "do" caseInsensitive() build(),
      """
        |A Bot can perform a variety of tasks, such as answering a question, taking an order, interact with external
        |services such as scheduling a meeting, booking a flight, and ordering an Uber.
      """.stripMargin
      ),

    (defineRule startsWith "how|can"
      and() contains "bot|virtual agent"
      and() contains "interact|engage|communicate|talk" caseInsensitive() build(),
      """
        |You can interact with a Bot by texting it, using natural language. Some Bots you can talk to. It’s possible
        |that a Bot could be triggered by an image, movement, or by entering a geographic area.
        | """.stripMargin
      ),

    (defineRule startsWith "which|what|who"
      and() contains "bot|virtual agent"
      and() contains "use|uses|using" caseInsensitive() build(),
      """
        |Local companies include Jetstar. You can ask Jess, Jetstar's virtual agent, various questions such as,
        |"Can I fly with Jetstar if I'm pregnant?" Optus’ Olivia can also answer a range of questions. Internationally,
        |Hyatt provide customer service and concierge services via a Facebook Messenger Bot.
      """.stripMargin
      ),

    (defineRule startsWith "why"
      and() contains "bot|virtual agent"
      and() contains "use" caseInsensitive() build(),
      """
        |Scale. It's expensive to keep growing call centre capacity. Bots have the potential to increase talk time
        |within some domains, which allows human interaction to focus on quality rather than volume.
      """.stripMargin
      ),

    (defineRule startsWith "how|will"
      and() contains "bot|virtual agent"
      and() contains "money|business model|business case" caseInsensitive() build(),
      """
        |Bots as a Service can curate other products and services. Sales Bots can qualify leads, and enable the
        |purchase directly. A comment on the H&M sales bot was, "The bot is actually trying to get to know me better,
        |and it does this in a friendly and non-salesy manner, using real-world words and emojis."
      """.stripMargin
      ),

    (defineRule startsWith "what|which"
      and() contains "bot|virtual agent"
      and() contains "capabilities|function" caseInsensitive() build(),
      """
        |Hands-free interaction using voice. Personality to increase engagement. Convenience by not needing to
        |switch apps. Ease of use by responding to natural language. Increased customer understanding by being
        |able to ask questions of the user. A balance of sales and service.
      """.stripMargin
      ),

    (defineRule startsWith "how"
      and() contains "bot|virtual agent"
      and() contains "get|buy|build|acquire|purchase" caseInsensitive() build(),
      """
        |Components can be bought, but there is likely to be custom integrations and extensions. Using APIs,
        |such as Google's Natural Language Processing API, a developer can combine various third-party products
        |and services to construct a Bot platform, like building using Lego blocks.
      """.stripMargin
      ),

    (defineRule startsWith "what"
      and() contains "conversational platform"
      and() contains "is" caseInsensitive() build(),
      """
        |A Conversational Platform manages millions of real-time conversations and orchestrates calls to multiple
        |internal and external APIs. A key point of difference compared to existing customer interaction engines,
        |is that it enables a two-way conversation with customers, over time, and potentially across device.
      """.stripMargin
      ),

    (defineRule startsWith "what"
      and() contains "artificial intelligence|ai|virtual agent"
      and() contains "is" caseInsensitive() build(),
      """
        |AI, or Artificial Intelligence, can be loosely classified as Weak AI, or Artificial Narrow Intelligence
        |(ANI), and Strong AI, or Artificial General Intelligence (AGI). ANI uses machine learning and is good at
        |specific tasks. AGI could perform any intellectual task that a human can. We have yet to build an AGI.
      """.stripMargin
      ),

    (defineRule startsWith "what|compare"
      and() contains "artificial intelligence|ai|virtual agent"
      and() contains "machine learning"
      and() contains "difference" caseInsensitive() build(),
      """
        |Machine learning uses computer algorithms that iteratively learn from data, allowing computers to find
        |hidden insights. AI uses machine learning, and combines it with a human-computer interface, and
        |control systems to perform a number of tasks such as as driving a car, and setting up a meeting.
      """.stripMargin
      ),

    (defineRule startsWith "what"
      and() contains "cognitive"
      and() contains "is" caseInsensitive() build(),
      """
        |IDC defines cognitive systems as possessing several key capabilities that use unstructured information in
        |combination with machine learning and human feedback to generate predictive and/or prescriptive actions.
      """.stripMargin
      ),

    (defineRule startsWith "what|compare"
      and() contains "artificial intelligence|ai|virtual agent"
      and() contains "cognitive"
      and() contains "difference" caseInsensitive() build(),
      "For practical purposes in the enterprise today, there is no real difference."
      )

  )

}
