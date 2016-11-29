package services

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import apis.liveengage._
import com.google.inject.Inject
import spray.json._

import scala.concurrent.Future

/**
  * Created by markmo on 26/11/2016.
  */
class LiveEngageService @Inject()(logger: LoggingAdapter,
                                  implicit val system: ActorSystem,
                                  implicit val fm: Materializer)
  extends LpChatJsonSupport with LpMessagingJsonSupport {

  import system.dispatcher

  val brandId = System.getenv("LP_BRAND_ID")
  val username = System.getenv("LP_USERNAME")
  val password = System.getenv("LP_PASSWORD")

  val http = Http()

  def getServices(domain: String, serviceTypes: Set[String]): Future[List[LpService]] = {
    logger.info("getting selected LiveEngage services")
    logger.debug("domain: {}", domain)
    logger.debug("serviceTypes: {}", serviceTypes)
    for {
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.GET,
        uri = s"http://$domain/csdr/account/$brandId/service/baseURI.json?version=1.0"))
      entity <- Unmarshal(response.entity).to[LpCsdsCollectionResponse]
    } yield {
      entity.baseURIs filter { service =>
        serviceTypes contains service.service
      }
    }
  }

  def getOTK(adminURL: String): Future[String] = {
    logger.info("getting OTK")
    logger.debug("adminURL: {}", adminURL)
    val data = FormData(Map(
      "site" -> brandId,
      "user" -> username,
      "pass" -> password,
      "stId" -> username,
      "usrId" -> username,
      "Inf" -> "2",
      "addUsrPrm" -> "no",
      "lang" -> "en-US",
      "lpservice" -> "liveEngage",
      "servicepath" -> "a/~~accountid~~/#,~~ssokey~~"
    )).toEntity

    for {
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = adminURL,
        entity = data))
    } yield {
      response.status match {
        case StatusCodes.Found =>
          response.headers.find(_.is("location")) map { header =>
            val redirectURL = header.value()
            redirectURL.substring(redirectURL.lastIndexOf("/#,") + 3)
          } match {
            case Some(token) => token
            case None => ""
          }
        case _ => ""
      }
    }
  }

  def getMessagingToken(liveEngageURL: String, otk: String): Future[String] = {
    logger.info("getting messaging token")
    logger.debug("liveEngageURL: {}", liveEngageURL)
    //logger.debug(s"otk: [$otk]")
    val payload = LpMessagingLoginRequest(LpConfig(), brandId, otk)

    for {
      request <- Marshal(payload).to[RequestEntity]
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = liveEngageURL,
        entity = request))
      entity <- Unmarshal(response.entity).to[LpMessagingLoginResponse]
    } yield entity.glob
  }

  def login(): Future[LpLoginResponse] = {
    logger.info("logging in to LiveEngage")
    val creds = LpCredentials(username, password)
    for {
      request <- Marshal(creds).to[RequestEntity]
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = s"https://va.agentvep.liveperson.net/api/account/$brandId/login?v=1.2",
        entity = request))
      entity <- Unmarshal(response.entity).to[LpLoginResponse]
    } yield entity
  }

  def createAgentSessionURL(accessToken: String): Future[String] = {
    logger.info("creating agent session")
    logger.debug("accessToken [{}]", accessToken)
    val payload = JsObject("loginData" -> JsObject())
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    for {
      request <- Marshal(payload).to[RequestEntity]
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = s"https://va.agentvep.liveperson.net/api/account/$brandId/agentSession?v=1&NC=true",
        headers = List(authorization),
        entity = request))
      entity <- Unmarshal(response.entity).to[LpAgentSessionResponse]
    } yield entity.agentSessionLocation.link.url
  }

  def getRingCount(agentSessionURL: String, accessToken: String): Future[LpRequestData] = {
    logger.info("get ring count")
    logger.debug("agentSessionURL: " + agentSessionURL)
    logger.debug("accessToken [{}]", accessToken)
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    for {
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.GET,
        uri = s"$agentSessionURL/incomingRequests?v=1&NC=true",
        headers = List(authorization)))
      entity <- Unmarshal(response.entity).to[LpRequestData]
    } yield entity
  }

  def takeChat(agentSessionURL: String, accessToken: String): Future[String] = {
    logger.info("take chat")
    //logger.debug("agentSessionURL: " + agentSessionURL)
    //logger.debug(s"accessToken: [$accessToken]")
    val payload = "{}"
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    for {
      request <- Marshal(payload).to[RequestEntity]
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = s"$agentSessionURL/incomingRequests?v=1&NC=true",
        headers = List(authorization),
        entity = request))
      entity <- Unmarshal(response.entity).to[LpTakeChatResponse]
    } yield entity.chatLocation.link.url
  }

  def getChatConversation(chatURL: String, accessToken: String, from: Int = 0): Future[LpChatConversation] = {
    logger.info("get conversation details")
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    for {
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.GET,
        uri = s"$chatURL?from=$from&v=1&NC=true",
        headers = List(authorization)))
      entity <- Unmarshal(response.entity).to[LpChatConversation]
    } yield entity
  }

  def continueConversation(nextURL: String, accessToken: String): Future[LpChatConversation] = {
    logger.info("continue conversation")
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    for {
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.GET,
        uri = s"$nextURL&v=1&NC=true",
        headers = List(authorization)))
      entity <- Unmarshal(response.entity).to[String]
    } yield {
      val json = entity.parseJson
      //logger.debug("LiveEngage conversation...\n{}", json.prettyPrint)
      try {
        json.convertTo[LpChatConversation]
      } catch {
        case e: Throwable =>
          logger.error(e, e.getMessage)
          throw new RuntimeException(e.getMessage, e)
      }
    }
  }

  def getNextEvents(chatConversation: LpChatConversation, accessToken: String): Future[LpChatConversation] = {
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    val url = chatConversation.getNextEventsURL
    for {
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.GET,
        uri = s"$url?v=1&NC=true",
        headers = List(authorization)))
      entity <- Unmarshal(response.entity).to[LpChatConversation]
    } yield entity
  }

  def getChatEvents(eventsURL: String, accessToken: String, from: Int = 0): Future[LpChatEvents] = {
    logger.info("get chat events")
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    for {
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.GET,
        uri = s"$eventsURL?from=$from&v=1&NC=true",
        headers = List(authorization)))
      entity <- Unmarshal(response.entity).to[LpChatEvents]
    } yield entity
  }

  def sendTextMessage(eventsURL: String, accessToken: String, text: String): Future[LpChatResponse] = {
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    val event = LpMessage(LpMessageEvent(eventType = "line", text = text, textType = "plain"))
    for {
      request <- Marshal(event).to[RequestEntity]
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = s"$eventsURL?v=1&NC=true",
        headers = List(authorization),
        entity = request))
      entity <- Unmarshal(response.entity).to[LpChatResponse]
    } yield entity
  }

  def getSessionInfo(agentSessionURL: String, accessToken: String): Future[LpInfoResponse] = {
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    for {
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.GET,
        uri = s"$agentSessionURL?v=1&NC=true",
        headers = List(authorization)))
      entity <- Unmarshal(response.entity).to[LpInfoResponse]
    } yield entity
  }

  def getAvailableSkillsForTransfer(transferURL: String, accessToken: String): Future[LpTransferResponse] = {
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    for {
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.GET,
        uri = s"$transferURL?v=1&NC=true",
        headers = List(authorization)))
      entity <- Unmarshal(response.entity).to[LpTransferResponse]
    } yield entity
  }

  def transferToSkill(transferURL: String, accessToken: String, skillId: String, text: String): Unit = {
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    val payload = LpTransferRequest(LpSkillTransfer(LpSkillId(skillId), text))
    for {
      request <- Marshal(payload).to[RequestEntity]
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = s"$transferURL?v=1&NC=true",
        headers = List(authorization),
        entity = request))
    } yield ()
  }

  def transferToAgent(transferURL: String, accessToken: String, agentId: String, text: String): Unit = {
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    val payload = LpTransferRequest(LpAgentTransfer(LpAgentId(agentId), text))
    for {
      request <- Marshal(payload).to[RequestEntity]
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = s"$transferURL?v=1&NC=true",
        headers = List(authorization),
        entity = request))
    } yield ()
  }

  def getAvailableAgents(availableAgentsURL: String, accessToken: String): Future[LpAvailableAgentsResponse] = {
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    for {
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.GET,
        uri = s"$availableAgentsURL?v=1&NC=true",
        headers = List(authorization)))
      entity <- Unmarshal(response.entity).to[LpAvailableAgentsResponse]
    } yield entity
  }

  def getAvailableAgents(availableAgentsURL: String, accessToken: String, skillName: String): Future[LpAvailableAgentsResponse] = {
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    for {
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.GET,
        uri = s"$availableAgentsURL?skill=$skillName&chatState=Online&v=1&NC=true",
        headers = List(authorization)))
      entity <- Unmarshal(response.entity).to[LpAvailableAgentsResponse]
    } yield entity
  }

  def getVisitSessionResources(visitSessionURL: String, accessToken: String): Future[LpVisitSessionResponse] = {
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    for {
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.GET,
        uri = s"$visitSessionURL?v=1&NC=true",
        headers = List(authorization)))
      entity <- Unmarshal(response.entity).to[LpVisitSessionResponse]
    } yield entity
  }

  def getVisitSessionDetails(visitInfoURL: String, accessToken: String): Future[LpVisitSessionDetailsResponse] = {
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    for {
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.GET,
        uri = s"$visitInfoURL?v=1&NC=true",
        headers = List(authorization)))
      entity <- Unmarshal(response.entity).to[LpVisitSessionDetailsResponse]
    } yield entity
  }

  def getVisitSessionCustomVariables(customVariablesURL: String, accessToken: String): Future[LpCustomVariablesResponse] = {
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    for {
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.GET,
        uri = s"$customVariablesURL?v=1&NC=true",
        headers = List(authorization)))
      entity <- Unmarshal(response.entity).to[LpCustomVariablesResponse]
    } yield entity
  }

}
