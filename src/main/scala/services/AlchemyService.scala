package services

import java.util

import com.ibm.watson.developer_cloud.alchemy.v1.AlchemyLanguage
import com.ibm.watson.developer_cloud.alchemy.v1.model._

import scala.collection.JavaConversions._

/**
  * Created by markmo on 20/08/2016.
  */
class AlchemyService {

  val apiKey = System.getenv("ALCHEMY_API_KEY")

  def getKeywords(text: String): Map[String, Double] = {
    val service = new AlchemyLanguage()
    service.setApiKey(apiKey)
    val params = new util.HashMap[String, Object]()
    params.put(AlchemyLanguage.TEXT, text)
    val sentimentResponse: DocumentSentiment = service.getSentiment(params).execute()
    val keywordsResponse: Keywords = service.getKeywords(params).execute()
    val sentimentType = sentimentResponse.getSentiment.getType
    keywordsResponse.getKeywords filter { keyword: Keyword =>
      keyword.getSentiment.getType == sentimentType
    } map { keyword: Keyword =>
      keyword.getText -> keyword.getRelevance.toDouble
    } toMap
  }

}
