package engines

import java.util.concurrent.TimeoutException

import akka.actor.{Actor, ActorRef}
import apis.googlenlp.{GoogleEntity, GoogleJsonSupport, GoogleSentiment}
import com.google.inject.Inject
import com.google.inject.assistedinject.Assisted
import models.Platform.Platform
import models.events.{PlatformAware, TextResponse, Unhandled}
import modules.akkaguice.NamedActor
import services.LanguageService
import spray.json._
import utils.General

import scala.concurrent.Future

/**
  * Created by markmo on 20/11/2016.
  */
class AnalyzeActor @Inject()(languageService: LanguageService,
                             @Assisted("defaultProvider") val defaultProvider: ActorRef,
                             @Assisted("historyActor") val historyActor: ActorRef)
  extends SimpleConversationActor
    with FutureExtensions
    with GoogleJsonSupport
    with General {

  import AnalyzeActor._
  import context.dispatcher

  override def withProvider(provider: ActorRef): Receive = {

    case Analyze(platform, sender, text) =>
      analyze(text) map {
        case Some((entities: Entities, sentiment: Sentiment)) =>
          self ! AnalysisSuccess(platform, sender, text, entities, sentiment)
        case None =>
          self ! AnalysisFailure(platform, sender, text)
      }

    case AnalysisSuccess(_, sender, text, entities, sentiment) =>
      val message = shrugEmoji + "Didn't get that, but I can understand that\n" +
        formatEntities(entities) + "\n" + formatSentiment(sentiment)
      say(provider, historyActor, sender, text, message)

    case AnalysisFailure(platform, sender, text) =>
      context.parent ! Unhandled(TextResponse(platform, sender, text))

  }

  def analyze(text: String): Future[Option[(Entities, Sentiment)]] = {
    lazy val entitiesRequest = languageService.getEntities(text)
    lazy val sentimentRequest = languageService.getSentiment(text)

    // assigning the requests here starts them in parallel
    val f1 = entitiesRequest
      .withTimeout(new TimeoutException("entities future timed out"))(futureTimeout, context.system)

    val f2 = sentimentRequest
      .withTimeout(new TimeoutException("sentiment future timed out"))(futureTimeout, context.system)

    (for {
      entitiesResponse <- f1
      sentimentResponse <- f2
    } yield {
      log.debug("entities:\n{}", entitiesResponse.toJson.prettyPrint)
      log.debug("sentiment:\n{}", sentimentResponse.toJson.prettyPrint)
      Some((entitiesResponse.entities, sentimentResponse.documentSentiment))
    }) recover {
      case e: Throwable =>
        log.error(e, e.getMessage)
        None
    }
  }

  def formatEntities(entities: Entities) =
    entities map { entity =>
      s"${entity.name} is a ${entity.entityType}"
    } mkString "\n"

  def formatSentiment(sentiment: Sentiment) = {
    val s = sentiment.polarity match {
      case x if x > 0 => "positive"
      case x if x < 0 => "negative"
      case _ => "neutral"
    }
    s"Sentiment is $s"
  }

}

object AnalyzeActor extends NamedActor {

  override final val name = "AnalyzeIntentActor"

  trait Factory {
    def apply(@Assisted("defaultProvider") defaultProvider: ActorRef,
              @Assisted("historyActor") historyActor: ActorRef): Actor
  }

  type Entities = List[GoogleEntity]
  type Sentiment = GoogleSentiment

  case class Analyze(platform: Platform, sender: String, text: String) extends PlatformAware

  case class AnalysisSuccess(platform: Platform, sender: String, text: String, entities: Entities, sentiment: Sentiment)

  case class AnalysisFailure(platform: Platform, sender: String, text: String)

}
