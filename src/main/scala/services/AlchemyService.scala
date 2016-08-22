package services

import java.util

import akka.event.LoggingAdapter
import com.google.inject.Inject
import com.ibm.watson.developer_cloud.alchemy.v1.AlchemyLanguage
import com.ibm.watson.developer_cloud.alchemy.v1.model._

import scala.collection.JavaConversions._

/**
  * Created by markmo on 20/08/2016.
  */
class AlchemyService @Inject()(logger: LoggingAdapter) {

  val apiKey = System.getenv("ALCHEMY_API_KEY")

  def getKeywords(text: String): Map[String, Double] = {
    logger.info(s"getting keywords from [$text]")
    val service = new AlchemyLanguage()
    service.setApiKey(apiKey)
    val params = new util.HashMap[String, Object]()
    params.put(AlchemyLanguage.TEXT, text)
    val sentimentResponse: DocumentSentiment = service.getSentiment(params).execute()
    logger.debug("sentiment response: " + sentimentResponse.toString)
    val keywordsResponse: Keywords = service.getKeywords(params).execute()
    logger.debug("keywords response: " + keywordsResponse.toString)
    val maybeSentimentType = if (sentimentResponse.getSentiment != null) {
      Some(sentimentResponse.getSentiment.getType)
    } else {
      None
    }
    logger.debug("message sentiment is " + maybeSentimentType)
    keywordsResponse.getKeywords filter { keyword: Keyword =>
      logger.debug(s"[${keyword.getText}] sentiment is ${keyword.getSentiment}")
      maybeSentimentType match {
        case Some(sentimentType) =>
          if (keyword.getSentiment != null) {
            sentimentType == keyword.getSentiment.getType
          } else {
            true
          }
        case None => true
      }
    } map { keyword: Keyword =>
      keyword.getText -> keyword.getRelevance.toDouble
    } toMap
  }

}
