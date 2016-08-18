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
        if (rule.execute(text)) Some(content) else loop(rest)
    }

    loop(content)
  }

  private def questionRule =
    defineRule startsWith "what|where|how|why|which|can|does|who|will|compare" caseInsensitive() build()

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

  def content: List[(RulesExecutor, String)] = List(

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
      """.stripMargin
      ),

    (defineRule startsWith "which|what|who"
      and() contains "bot|virtual agent"
      and() contains "use|using" caseInsensitive() build(),
      """
        |Local companies include Jetstar. You can ask Jess, Jetstar's virtual agent, various questions such as,
        |"Can I fly with Jetstar if I'm pregnant?" Optus’ Olivia can also answer a range of questions. Internationally,
        |Hyatt provide customer service and concierge services via a Facebook Messenger Bot. Users can chat with a CNN
        |Bot to get breaking news. Spring Bot is a personal shopping concierge. Users can hail a ride with Uber using
        |Messenger.
      """.stripMargin
      ),

    (defineRule startsWith "why"
      and() contains "bot|virtual agent"
      and() contains "use|using" caseInsensitive() build(),
      """
        |Scale. It's expensive to keep growing call centre capacity. At the same time, cost sensitivity is applying
        |pressure to reduce call durations. This means that although the customer experience would improve with more
        |human interaction, there is an opposite and greater force to reduce human interaction. Bots have the
        |potential to increase talk time within some domains, which allows human interaction to focus on quality
        |rather than volume.
      """.stripMargin
      ),

    (defineRule startsWith "how|will"
      and() contains "bot|virtual agent"
      and() contains "money|business model|business case" caseInsensitive() build(),
      """
        |There are a number of potential business models. Bots as a Service can be a curation layer for other
        |products and services. Sales Bots can qualify leads, and enable the purchase directly. A comment on the
        |H&M sales bot was, "The bot is actually trying to get to know me better, and it does this in a friendly
        |and non-salesy manner, using real-world words and emojis.
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
        |A Bot is more than an App; it is a platform. Components of the platform can be bought from vendors.
        |Messaging platforms, such as Facebook Messenger, Google Allo, and Slack, provide APIs that enable
        |developers to build interfaces. Combined with available APIs, such as Google's Natural Language
        |Processing API, a developer can combine various third-party products and services to construct a
        |Bot platform, like building using Lego blocks. Areas of core value from product vendors include design
        |and management tools, integration with human processes, and domain-specific deep learning models. Another
        |term for the platform is "Conversational Platform".
      """.stripMargin
      ),

    (defineRule startsWith "what"
      and() contains "conversational platform"
      and() contains "is" caseInsensitive() build(),
      """
        |A Conversational Platform manages millions of real-time conversations, keeping track of the state of each
        |conversation, and orchestrating access to multiple services such as natural language processing / understanding,
        |customer authentication and identity, and access to internal services such as product catalogues, shopping
        |cart, and content provisioning. A key point of different compared to existing customer interaction engines,
        |is that it enables a two-way conversation with customers, over time, and potentially across device.
      """.stripMargin
      ),

    (defineRule startsWith "what"
      and() contains "artificial intelligence|ai|virtual agent"
      and() contains "is" caseInsensitive() build(),
      """
        |AI, or Artificial Intelligence, can be loosely classified as Weak AI, or Artificial Narrow Intelligence (ANI),
        |and Strong AI, or Artificial General Intelligence (AGI). Artificial Narrow Intelligence uses machine learning
        |that approximates or exceeds human intelligence or efficiency at specific things, such as filtering spam,
        |translating speech, assigning airline gates, searching the Internet, etc. Smart Bots use ANI today. Artificial
        |General Intelligence is an adaptable computer that can perform a range of tasks, which involve the ability to
        |reason, plan, and solve problems. We have yet to build an AGI. Or, as computer scientist Donald Knuth puts it,
        |"AI has by now succeeded in doing essentially everything that requires 'thinking' but has failed to do most of
        |what people and animals do 'without thinking.'"
      """.stripMargin
      ),

    (defineRule startsWith "what|compare"
      and() contains "artificial intelligence|ai|virtual agent"
      and() contains "machine learning"
      and() contains "difference" caseInsensitive() build(),
      """
        |Machine learning uses computer algorithms that iteratively learn from data, allowing computers to find hidden
        |insights and perform a number of decisioning activities such as filtering spam, detecting objects in images,
        |and predicting values. AI uses machine learning, and combines it with a human-computer interface, and
        |capabilities/integrations to perform a number of tasks such as providing answers to questions, controlling
        |another system such as driving a car, and coordinating with other humans or agents, for example, to setup
        |a meeting.
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
