package services

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, RequestEntity}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import apis.ciscospark._
import com.google.inject.Inject
import com.typesafe.config.Config
import spray.json._

import scala.concurrent.Future

/**
  * Created by markmo on 9/09/2016.
  */
class SparkService @Inject()(config: Config,
                             logger: LoggingAdapter,
                             implicit val system: ActorSystem,
                             implicit val fm: Materializer)
  extends SparkJsonSupport {

  import system.dispatcher

  val http = Http()

  val api = config.getString("spark.api.url")

  val accessToken = System.getenv("SPARK_ACCESS_TOKEN")

  def listPeople(): Future[List[SparkPerson]] = {
    logger.info("listing people")
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    for {
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.GET,
        uri = s"$api/people",
        headers = List(authorization)))
      entity <- Unmarshal(response.entity).to[SparkPeople]
    } yield entity.items
  }

  def getPerson(personId: String): Future[SparkPerson] = {
    logger.info(s"get person [$personId]")
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    for {
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.GET,
        uri = s"$api/people/$personId",
        headers = List(authorization)))
      entity <- Unmarshal(response.entity).to[SparkPerson]
    } yield entity
  }

  def getMe: Future[SparkPerson] = {
    logger.info("get me")
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    for {
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.GET,
        uri = s"$api/people/me",
        headers = List(authorization)))
      entity <- Unmarshal(response.entity).to[SparkPerson]
    } yield entity
  }

  def listRooms(): Future[List[SparkRoom]] = {
    logger.info("listing people")
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    for {
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.GET,
        uri = s"$api/rooms",
        headers = List(authorization)))
      entity <- Unmarshal(response.entity).to[SparkRooms]
    } yield entity.items
  }

  def getRoom(roomId: String): Future[SparkRoom] = {
    logger.info(s"get room [$roomId]")
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    for {
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.GET,
        uri = s"$api/rooms/$roomId",
        headers = List(authorization)))
      entity <- Unmarshal(response.entity).to[SparkRoom]
    } yield entity
  }

  def createRoom(title: String, teamId: String): Future[SparkRoom] = {
    logger.info("create room")
    val payload = SparkRoomRequest(title, teamId)
    logger.debug("sending payload:\n" + payload.toJson.prettyPrint)
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    for {
      request <- Marshal(payload).to[RequestEntity]
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = s"$api/rooms",
        headers = List(authorization),
        entity = request))
      entity <- Unmarshal(response.entity).to[SparkRoom]
    } yield entity
  }

  def changeRoomTitle(roomId: String, title: String): Future[SparkRoom] = {
    logger.info(s"update room [$roomId] with title [$title]")
    val payload = JsObject("title" -> JsString(title))
    logger.debug("sending payload:\n" + payload.prettyPrint)
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    for {
      request <- Marshal(payload).to[RequestEntity]
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.PUT,
        uri = s"$api/rooms/$roomId",
        headers = List(authorization),
        entity = request))
      entity <- Unmarshal(response.entity).to[SparkRoom]
    } yield entity
  }

  def deleteRoom(roomId: String): Unit = {
    logger.info(s"delete room [$roomId]")
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    for {
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.DELETE,
        uri = s"$api/rooms/$roomId",
        headers = List(authorization)))
    } yield ()
  }

  def listMemberships(): Future[List[SparkMembership]] = {
    logger.info("listing people")
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    for {
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.GET,
        uri = s"$api/memberships",
        headers = List(authorization)))
      entity <- Unmarshal(response.entity).to[SparkMemberships]
    } yield entity.items
  }

  def getMembership(membershipId: String): Future[SparkMembership] = {
    logger.info(s"get membership [$membershipId]")
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    for {
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.GET,
        uri = s"$api/memberships/$membershipId",
        headers = List(authorization)))
      entity <- Unmarshal(response.entity).to[SparkMembership]
    } yield entity
  }

  def createMembership(roomId: String, personId: Option[String], personEmail: Option[String], isModerator: Boolean): Future[SparkMembership] = {
    logger.info("create membership")
    val payload = SparkMembershipRequest(roomId, personId, personEmail, isModerator)
    logger.debug("sending payload:\n" + payload.toJson.prettyPrint)
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    for {
      request <- Marshal(payload).to[RequestEntity]
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = s"$api/memberships",
        headers = List(authorization),
        entity = request))
      entity <- Unmarshal(response.entity).to[SparkMembership]
    } yield entity
  }

  def updateModeratorStatus(membershipId: String, isModerator: Boolean): Future[SparkMembership] = {
    logger.info(s"update membership [$membershipId] is moderator [$isModerator]")
    val payload = JsObject("isModerator" -> JsBoolean(isModerator))
    logger.debug("sending payload:\n" + payload.prettyPrint)
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    for {
      request <- Marshal(payload).to[RequestEntity]
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.PUT,
        uri = s"$api/memberships/$membershipId",
        headers = List(authorization),
        entity = request))
      entity <- Unmarshal(response.entity).to[SparkMembership]
    } yield entity
  }

  def deleteMembership(membershipId: String): Unit = {
    logger.info(s"delete membership [$membershipId]")
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    for {
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.DELETE,
        uri = s"$api/memberships/$membershipId",
        headers = List(authorization)))
    } yield ()
  }

  def listMessagesInRoom(roomId: String): Future[List[SparkMessage]] = {
    logger.info("listing messages")
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    for {
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.GET,
        uri = s"$api/messages?mentionedPeople=me&roomId=$roomId",
        headers = List(authorization)))
      entity <- Unmarshal(response.entity).to[SparkMessages]
    } yield entity.items
  }

  def getMessage(messageId: String): Future[SparkMessage] = {
    logger.info(s"get message [$messageId]")
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    for {
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.GET,
        uri = s"$api/messages/$messageId",
        headers = List(authorization)))
      entity <- Unmarshal(response.entity).to[SparkMessage]
    } yield entity
  }

  def postMessage(roomId: Option[String],
                  toPersonId: Option[String],
                  toPersonEmail: Option[String],
                  text: String,
                  files: Option[List[String]]
                 ): Future[SparkMessage] = {
    logger.info("post message")
    val payload = SparkMessageRequest(roomId, toPersonId, toPersonEmail, text, files)
    logger.debug("sending payload:\n" + payload.toJson.prettyPrint)
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    for {
      request <- Marshal(payload).to[RequestEntity]
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = s"$api/messages",
        headers = List(authorization),
        entity = request))
      entity <- Unmarshal(response.entity).to[SparkMessage]
    } yield entity
  }

  def deleteMessage(messageId: String): Unit = {
    logger.info(s"delete message [$messageId]")
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    for {
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.DELETE,
        uri = s"$api/messages/$messageId",
        headers = List(authorization)))
    } yield ()
  }

  def listTeams(): Future[List[SparkTeam]] = {
    logger.info("listing teams")
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    for {
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.GET,
        uri = s"$api/teams",
        headers = List(authorization)))
      entity <- Unmarshal(response.entity).to[SparkTeams]
    } yield entity.items
  }

  def getTeam(teamId: String): Future[SparkTeam] = {
    logger.info(s"get team [$teamId]")
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    for {
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.GET,
        uri = s"$api/teams/$teamId",
        headers = List(authorization)))
      entity <- Unmarshal(response.entity).to[SparkTeam]
    } yield entity
  }

  def createTeam(name: String): Future[SparkTeam] = {
    logger.info("create team")
    val payload = JsObject("name" -> JsString(name))
    logger.debug("sending payload:\n" + payload.prettyPrint)
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    for {
      request <- Marshal(payload).to[RequestEntity]
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = s"$api/teams",
        headers = List(authorization),
        entity = request))
      entity <- Unmarshal(response.entity).to[SparkTeam]
    } yield entity
  }

  def updateTeamName(teamId: String, name: String): Future[SparkTeam] = {
    logger.info("update team name")
    val payload = JsObject("name" -> JsString(name))
    logger.debug("sending payload:\n" + payload.toJson.prettyPrint)
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    for {
      request <- Marshal(payload).to[RequestEntity]
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.PUT,
        uri = s"$api/teams/$teamId",
        headers = List(authorization),
        entity = request))
      entity <- Unmarshal(response.entity).to[SparkTeam]
    } yield entity
  }

  def deleteTeam(teamId: String): Unit = {
    logger.info(s"delete team [$teamId]")
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    for {
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.DELETE,
        uri = s"$api/teams/$teamId",
        headers = List(authorization)))
    } yield ()
  }

  def listWebhooks(): Future[List[SparkWebhook]] = {
    logger.info("listing webhooks")
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    for {
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.GET,
        uri = s"$api/webhooks",
        headers = List(authorization)))
      entity <- Unmarshal(response.entity).to[SparkWebhooks]
    } yield entity.items
  }

  def getWebhook(webhookId: String): Future[SparkWebhook] = {
    logger.info(s"get webhook [$webhookId]")
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    for {
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.GET,
        uri = s"$api/webhooks/$webhookId",
        headers = List(authorization)))
      entity <- Unmarshal(response.entity).to[SparkWebhook]
    } yield entity
  }

  def createWebhook(name: String, targetUrl: String, resource: String, event: String, filter: Option[String], secret: String): Future[SparkWebhook] = {
    logger.info("create webhook")
    val payload = SparkWebhookRequest(name, targetUrl, resource, event, filter, secret)
    logger.debug("sending payload:\n" + payload.toJson.prettyPrint)
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    for {
      request <- Marshal(payload).to[RequestEntity]
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = s"$api/webhooks",
        headers = List(authorization),
        entity = request))
      entity <- Unmarshal(response.entity).to[SparkWebhook]
    } yield entity
  }

  def updateWebhook(webhookId: String, name: String, targetUrl: String): Future[SparkWebhook] = {
    logger.info("update webhook")
    val payload = JsObject(
      "name" -> JsString(name),
      "targetUrl" -> JsString(targetUrl)
    )
    logger.debug("sending payload:\n" + payload.prettyPrint)
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    for {
      request <- Marshal(payload).to[RequestEntity]
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.PUT,
        uri = s"$api/webhooks/$webhookId",
        headers = List(authorization),
        entity = request))
      entity <- Unmarshal(response.entity).to[SparkWebhook]
    } yield entity
  }

  def deleteWebhook(webhookId: String): Unit = {
    logger.info(s"delete webhook [$webhookId]")
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    for {
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.DELETE,
        uri = s"$api/webhooks/$webhookId",
        headers = List(authorization)))
    } yield ()
  }

  def listTeamMemberships(): Future[List[SparkTeamMembership]] = {
    logger.info("listing team memberships")
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    for {
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.GET,
        uri = s"$api/team/memberships",
        headers = List(authorization)))
      entity <- Unmarshal(response.entity).to[SparkTeamMemberships]
    } yield entity.items
  }

  def getTeamMembership(membershipId: String): Future[SparkTeamMembership] = {
    logger.info(s"get team membership [$membershipId]")
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    for {
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.GET,
        uri = s"$api/team/memberships/$membershipId",
        headers = List(authorization)))
      entity <- Unmarshal(response.entity).to[SparkTeamMembership]
    } yield entity
  }

  def addMember(teamId: String, personId: String, personEmail: String, isModerator: Boolean): Future[SparkTeamMembership] = {
    logger.info("add member")
    val payload = SparkTeamMembershipRequest(teamId, personId, personEmail, isModerator)
    logger.debug("sending payload:\n" + payload.toJson.prettyPrint)
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    for {
      request <- Marshal(payload).to[RequestEntity]
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = s"$api/team/memberships",
        headers = List(authorization),
        entity = request))
      entity <- Unmarshal(response.entity).to[SparkTeamMembership]
    } yield entity
  }

  def updateTeamModeratorStatus(membershipId: String, isModerator: Boolean): Future[SparkTeamMembership] = {
    logger.info("update webhook")
    val payload = JsObject("isModerator" -> JsBoolean(isModerator))
    logger.debug("sending payload:\n" + payload.prettyPrint)
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    for {
      request <- Marshal(payload).to[RequestEntity]
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.PUT,
        uri = s"$api/team/memberships/$membershipId",
        headers = List(authorization),
        entity = request))
      entity <- Unmarshal(response.entity).to[SparkTeamMembership]
    } yield entity
  }

  def deleteTeamMembership(membershipId: String): Unit = {
    logger.info(s"delete team membership [$membershipId]")
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    for {
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.DELETE,
        uri = s"$api/team/memberships/$membershipId",
        headers = List(authorization)))
    } yield ()
  }

  def setupTempRoom(sender: String): Future[SparkTempMembership] = {
    val api = config.getString("api.host")
    val agentEmail = "m4rkmo@gmail.com"

    for {
      team <- createTeam("Support Team")
      room <- createRoom("Support Room", team.id)
      membership <- createMembership(room.id, None, Some(agentEmail), isModerator = false)
      leaveRoomWebhook <- createWebhook("leave support room", s"$api/sparkwebhook-membership-deleted/$sender", "memberships", "deleted", Some(s"roomId=${room.id}"), "dingdong")
      webhook <- createWebhook("support", s"$api/sparkwebhook-message-created/$sender", "messages", "created", Some(s"roomId=${room.id}"), "dingdong")
    } yield SparkTempMembership(team.id, room.id, membership.personId, webhook.id, leaveRoomWebhook.id)
  }

}
