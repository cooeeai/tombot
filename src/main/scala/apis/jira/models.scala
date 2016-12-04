package apis.jira

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._
import spray.json.lenses.JsonLenses._

/**
  * Created by markmo on 15/10/2016.
  */
sealed trait JIRAIssueType {
  def name: String
}

case object JIRABug extends JIRAIssueType {
  override val name = "Bug"
}

case class JIRAProject(key: String)

case class JIRAIssue(project: JIRAProject, summary: String, description: String, issueType: JIRAIssueType)

case class JIRAIssueEnvelope(fields: JIRAIssue)

case class JIRACreatedIssueResponse(id: String, key: String, self: String)

trait JIRAJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {

  implicit object jiraIssueTypeJsonFormat extends RootJsonFormat[JIRAIssueType] {

    def write(t: JIRAIssueType) = t match {
      case JIRABug => JsObject("name" -> JsString("Bug"))
    }

    def read(value: JsValue) =
      value.extract[String]('name) match {
        case "Bug" => JIRABug
        case _ => throw DeserializationException("JIRAIssueType expected")
      }

  }

  implicit val jiraProjectJsonFormat = jsonFormat1(JIRAProject)
  implicit val jiraIssueJsonFormat = jsonFormat(JIRAIssue, "project", "summary", "description", "issuetype")
  implicit val jiraIssueEnvelopeJsonFormat = jsonFormat1(JIRAIssueEnvelope)
  implicit val jiraCreatedIssueResponseJsonFormat = jsonFormat3(JIRACreatedIssueResponse)

}

object Builder {

  class IssueBuilder(projectKey: Option[String], summary: Option[String], description: Option[String], issueType: Option[JIRAIssueType]) {

    def forProject(value: String) = new IssueBuilder(Some(value), summary, description, issueType)

    def withSummary(value: String) = new IssueBuilder(projectKey, Some(value), description, issueType)

    def withDescription(value: String) = new IssueBuilder(projectKey, summary, Some(value), issueType)

    def withIssueType(value: JIRAIssueType) = new IssueBuilder(projectKey, summary, description, Some(value))

    def build() =
      JIRAIssueEnvelope(
        JIRAIssue(
          project = JIRAProject(projectKey.getOrElse("")),
          summary = summary.getOrElse(""),
          description = description.getOrElse(""),
          issueType = issueType.getOrElse(JIRABug)
        ))

  }

  def issue = new IssueBuilder(None, None, None, None)

}