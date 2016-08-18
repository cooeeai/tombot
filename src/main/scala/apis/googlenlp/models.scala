package apis.googlenlp

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._

/**
  * Created by markmo on 17/08/2016.
  */
case class GoogleDocument(documentType: String, language: Option[String], content: Option[String], gcsContentUri: Option[String])

case class GoogleEntitiesRequest(document: GoogleDocument, encodingType: String)

case class GoogleSentimentRequest(document: GoogleDocument)

case class GoogleTextSpan(content: String, beginOffset: Int)

case class GoogleEntityMention(text: GoogleTextSpan)

case class GoogleEntity(name: String, entityType: String, metadata: Map[String, String], salience: Double, mentions: List[GoogleEntityMention])

case class GoogleEntities(entities: List[GoogleEntity], language: String)

case class GoogleSentiment(polarity: Double, magnitude: Double)

case class GoogleDocumentSentiment(documentSentiment: GoogleSentiment, language: String)

trait GoogleJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val googleDocumentJsonFormat = jsonFormat(GoogleDocument, "type", "language", "content", "gcsContentUri")
  implicit val googleEntitiesRequestJsonFormat = jsonFormat2(GoogleEntitiesRequest)
  implicit val googleSentimentRequestJsonFormat = jsonFormat1(GoogleSentimentRequest)
  implicit val googleTextSpanJsonFormat = jsonFormat2(GoogleTextSpan)
  implicit val googleEntityMentionJsonFormat = jsonFormat1(GoogleEntityMention)
  implicit val googleEntityJsonFormat = jsonFormat(GoogleEntity, "name", "type", "metadata", "salience", "mentions")
  implicit val googleEntitiesJsonFormat = jsonFormat2(GoogleEntities)
  implicit val googleSentimentJsonFormat = jsonFormat2(GoogleSentiment)
  implicit val googleDocumentSentimentJsonFormat = jsonFormat2(GoogleDocumentSentiment)
}

object Builder {

  class EntitiesRequestBuilder(content: Option[String]) {

    def withContent(value: String) = new EntitiesRequestBuilder(Some(value))

    def build() =
      GoogleEntitiesRequest(
        document = GoogleDocument(
          documentType = "PLAIN_TEXT",
          language = None,
          content = content,
          gcsContentUri = None
        ),
        encodingType = "UTF8"
      )

  }

  def entitiesRequest = new EntitiesRequestBuilder(None)

  class SentimentRequestBuilder(content: Option[String]) {

    def withContent(value: String) = new SentimentRequestBuilder(Some(value))

    def build() =
      GoogleSentimentRequest(
        document = GoogleDocument(
          documentType = "PLAIN_TEXT",
          language = None,
          content = content,
          gcsContentUri = None
        )
      )

  }

  def sentimentRequest = new SentimentRequestBuilder(None)

}