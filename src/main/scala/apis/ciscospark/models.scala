package apis.ciscospark

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol

/**
  * Created by markmo on 8/09/2016.
  */
case class SparkPerson(id: String, emails: List[String], displayName: String, avatar: String, orgId: String, roles: List[String], licenses: List[String], created: String)

case class SparkPeople(items: List[SparkPerson])

case class SparkRoomRequest(title: String, teamId: String)

case class SparkRoom(id: String, title: String, roomType: String, isLocked: Boolean, teamId: String, lastActivity: String, created: String)

case class SparkRooms(items: List[SparkRoom])

case class SparkMembershipRequest(roomId: String, personId: Option[String], personEmail: Option[String], isModerator: Boolean)

case class SparkMembership(id: String, roomId: String, personId: String, isModerator: Boolean, isMonitor: Boolean, created: String)

case class SparkMemberships(items: List[SparkMembership])

case class SparkMessageRequest(roomId: Option[String], toPersonId: Option[String], toPersonEmail: Option[String], text: String, files: Option[List[String]])

case class SparkMessage(id: String, roomId: String, roomType: String, toPersonId: Option[String], toPersonEmail: Option[String], text: Option[String], markdown: Option[String], files: Option[List[String]], personId: String, personEmail: String, created: String, mentionedPeople: Option[List[String]])

case class SparkMessages(items: List[SparkMessage])

case class SparkTeam(id: String, name: String, created: String)

case class SparkTeams(items: List[SparkTeam])

case class SparkTeamMembershipRequest(teamId: String, personId: String, personEmail: String, isModerator: Boolean)

case class SparkTeamMembership(id: String, teamId: String, personId: String, personEmail: String, isModerator: Boolean, created: String)

case class SparkTeamMemberships(items: List[SparkTeamMembership])

case class SparkWebhookRequest(name: String, targetUrl: String, resource: String, event: String, filter: Option[String], secret: String)

case class SparkWebhook(id: String, name: String, targetUrl: String, resource: String, event: String, filter: Option[String], secret: String, created: String)

case class SparkWebhooks(items: List[SparkWebhook])

case class SparkWebhookResponseData(id: String, roomId: String, roomType: String, personId: String, personEmail: String, created: String)

case class SparkWebhookResponse(id: String, name: String, targetUrl: String, resource: String, event: String, filter: Option[String], actorId: String, created: String, data: SparkWebhookResponseData)

case class SparkTempMembership(teamId: String, roomId: String, personId: String, webhookId: String, leaveRoomWebhookId: String)

trait SparkJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val sparkPersonJsonFormat = jsonFormat8(SparkPerson)
  implicit val sparkPeopleJsonFormat = jsonFormat1(SparkPeople)
  implicit val sparkRoomRequestJsonFormat = jsonFormat2(SparkRoomRequest)
  implicit val sparkRoomJsonFormat = jsonFormat(SparkRoom, "id", "title", "type", "isLocked", "teamId", "lastActivity", "created")
  implicit val sparkRoomsJsonFormat = jsonFormat1(SparkRooms)
  implicit val sparkMembershipRequestJsonFormat = jsonFormat4(SparkMembershipRequest)
  implicit val sparkMembershipJsonFormat = jsonFormat6(SparkMembership)
  implicit val sparkMembershipsJsonFormat = jsonFormat1(SparkMemberships)
  implicit val sparkMessageRequestJsonFormat = jsonFormat5(SparkMessageRequest)
  implicit val sparkMessageJsonFormat = jsonFormat12(SparkMessage)
  implicit val sparkMessagesJsonFormat = jsonFormat1(SparkMessages)
  implicit val sparkTeamJsonFormat = jsonFormat3(SparkTeam)
  implicit val sparkTeamsJsonFormat = jsonFormat1(SparkTeams)
  implicit val sparkTeamMembershipRequestJsonFormat = jsonFormat4(SparkTeamMembershipRequest)
  implicit val sparkTeamMembershipJsonFormat = jsonFormat6(SparkTeamMembership)
  implicit val sparkTeamMembershipsJsonFormat = jsonFormat1(SparkTeamMemberships)
  implicit val sparkWebhookRequestJsonFormat = jsonFormat6(SparkWebhookRequest)
  implicit val sparkWebhookJsonFormat = jsonFormat8(SparkWebhook)
  implicit val sparkWebhooksJsonFormat = jsonFormat1(SparkWebhooks)
  implicit val sparkWebhookResponseDataJsonFormat = jsonFormat6(SparkWebhookResponseData)
  implicit val sparkWebhookResponseJsonFormat = jsonFormat9(SparkWebhookResponse)
}