package apis.intellexer

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{JsonFormat, DefaultJsonProtocol, JsValue}

/**
  * Created by markmo on 20/12/2016.
  */

/**
  * information about the text
  *
  * @param id document identifier
  * @param size document size in bytes
  * @param title document title
  * @param url source of the request
  * @param error information about processing errors
  * @param sizeFormat formatted document size
  */
case class IlxSummarizerDoc(id: Int, size: Int, title: String, url: String, error: JsValue, sizeFormat: Int)

/**
  * summary items (important document sentences)
  *
  * @param text text of the summary item
  * @param rank item rank. Larger rank means greater importance of the sentence
  * @param weight item weight
  */
case class IlxSummarizerItem(text: String, rank: Int, weight: Double)

/**
  *
  * @param children
  * @param mp
  * @param sentenceIds
  * @param st
  * @param text
  * @param w weight
  */
case class IlxTreeNode(children: List[IlxTreeNode], mp: Boolean, sentenceIds: List[Int], st: Int, text: String, w: Double)

/**
  *
  * @param summarizerDoc information about the text
  * @param structure document structure
  * @param topics array of detected document topics
  * @param items summary items (important document sentences)
  * @param totalItemsCount total number of processed sentences
  * @param conceptTree tree of important document concepts
  * @param namedEntityTree tree of relations among the detected entities
  */
case class IlxSummary(summarizerDoc: IlxSummarizerDoc,
                      structure: String,
                      topics: List[String],
                      items: List[IlxSummarizerItem],
                      totalItemsCount: Int,
                      conceptTree: IlxTreeNode,
                      namedEntityTree: IlxTreeNode)

trait IlxJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val ilxSummarizerDocJsonFormat = jsonFormat6(IlxSummarizerDoc)
  implicit val ilxSummarizerItemJsonFormat = jsonFormat3(IlxSummarizerItem)
  implicit val ilxTreeNodeJsonFormat: JsonFormat[IlxTreeNode] = lazyFormat(jsonFormat6(IlxTreeNode))
  implicit val ilxSummaryJsonFormat = jsonFormat7(IlxSummary)
}