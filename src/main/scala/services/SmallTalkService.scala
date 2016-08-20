package services

import akka.event.LoggingAdapter
import com.Hound.HoundJSON.{ConversationStateJSON, HoundServerJSON, RequestInfoJSON}
import com.Hound.HoundRequester.HoundCloudRequester
import com.google.inject.Inject

import scala.collection.mutable

/**
  * Created by markmo on 20/08/2016.
  */
class SmallTalkService @Inject()(logger: LoggingAdapter) {

  val clientId = System.getenv("HOUNDIFY_CLIENT_ID")

  val clientKey = System.getenv("HOUNDIFY_CLIENT_KEY")

  val stateMap = mutable.Map[String, ConversationStateJSON]()

  def getSmallTalkResponse(sender: String, text: String): String = {
    val textRequestUrlBase = HoundCloudRequester.default_text_request_url_base()
    val voiceRequestUrlBase = HoundCloudRequester.default_voice_request_url_base()
    val requester = new HoundCloudRequester(clientId, clientKey, sender, textRequestUrlBase, voiceRequestUrlBase)
    val requestInfo = new RequestInfoJSON()
    try {
      val response: HoundServerJSON =
        if (stateMap.contains(sender)) {
          requester.do_text_request(text, stateMap(sender), requestInfo)
        } else {
          requester.do_text_request(text, null, requestInfo)
        }
      val first = response.getAllResults.firstElement
      val state = first.getConversationState
      stateMap(sender) = state
      first.getWrittenResponse
    } catch {
      case e: Throwable =>
        e.printStackTrace()
        logger.error(e.getMessage)
        "Didn't get that!"
    }
  }

}
