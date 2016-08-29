package services

import akka.event.LoggingAdapter
import com.google.inject.Inject
import com.wolfram.alpha.{WAEngine, WAException, WAPlainText}

/**
  * Created by markmo on 29/08/2016.
  */
class KnowledgeService @Inject()(logger: LoggingAdapter) {

  val appid = System.getenv("WOLFRAM_API_KEY")

  def getFacts(text: String): Option[String] = {
    val engine = new WAEngine()
    engine.setAppID(appid)
    engine.addFormat("plaintext")
    val query = engine.createQuery()
    query.setInput(text)
    try {
      val queryResult = engine.performQuery(query)
      if (queryResult.isError) {
        None
      } else if (!queryResult.isSuccess) {
        // query was not understood, no results available
        None
      } else {
        val pod = queryResult.getPods.head
        if (pod.isError) {
          None
        } else {
          val subpod = pod.getSubpods.head
          val element = subpod.getContents.find(_.isInstanceOf[WAPlainText])
          Some(element.asInstanceOf[WAPlainText].getText)
        }
      }
    } catch {
      case e: WAException =>
        e.printStackTrace()
        logger.error(e.getMessage)
        None
    }
  }

}
