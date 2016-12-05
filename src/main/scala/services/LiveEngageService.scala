package services

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken, RawHeader}
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

  def getOTK(adminUrl: String): Future[String] = {
    logger.info("getting OTK")
    logger.debug("adminUrl: {}", adminUrl)
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
        uri = adminUrl,
        entity = data))
    } yield {
      response.status match {
        case StatusCodes.Found =>
          response.headers.find(_.is("location")) map { header =>
            val redirectUrl = header.value()
            redirectUrl.substring(redirectUrl.lastIndexOf("/#,") + 3)
          } match {
            case Some(token) => token
            case None => ""
          }
        case _ => ""
      }
    }
  }

  def getMessagingToken(liveEngageUrl: String, otk: String): Future[String] = {
    logger.info("getting messaging token")
    logger.debug("liveEngageUrl: {}", liveEngageUrl)
    //logger.debug(s"otk: [$otk]")
    val payload = LpMessagingLoginRequest(LpConfig(), brandId, otk)

    for {
      request <- Marshal(payload).to[RequestEntity]
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = liveEngageUrl,
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

  def createAgentSessionUrl(accessToken: String): Future[String] = {
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

  def getRingCount(agentSessionUrl: String, accessToken: String): Future[Either[LpErrorResponse, LpRequestData]] = {
    logger.info("get ring count")
    logger.debug("agentSessionUrl: " + agentSessionUrl)
    logger.debug("accessToken [{}]", accessToken)
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    for {
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.GET,
        uri = s"$agentSessionUrl/incomingRequests?v=1&NC=true",
        headers = List(authorization)))
      entity <- Unmarshal(response.entity).to[Either[LpErrorResponse, LpRequestData]]
    } yield entity
  }

  def takeChat(agentSessionUrl: String, accessToken: String): Future[Either[LpErrorResponse, String]] = {
    logger.info("take chat")
    //logger.debug("agentSessionUrl: " + agentSessionUrl)
    //logger.debug(s"accessToken: [$accessToken]")
    val payload = "{}"
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    for {
      request <- Marshal(payload).to[RequestEntity]
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = s"$agentSessionUrl/incomingRequests?v=1&NC=true",
        headers = List(authorization),
        entity = request))
      entity <- Unmarshal(response.entity).to[Either[LpErrorResponse, LpTakeChatResponse]]
    } yield transformEither[LpTakeChatResponse, String](entity, _.chatLocation.link.url)
  }

  def getChatConversation(chatUrl: String, accessToken: String, from: Int = 0): Future[Either[LpErrorResponse, LpChatConversation]] = {
    logger.info("get conversation details")
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    for {
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.GET,
        uri = s"$chatUrl?from=$from&v=1&NC=true",
        headers = List(authorization)))
      entity <- Unmarshal(response.entity).to[Either[LpErrorResponse, LpChatConversation]]
    } yield entity
  }

  def continueConversation(nextUrl: String, accessToken: String): Future[Either[LpErrorResponse, LpChatConversation]] = {
    logger.info("continue conversation")
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    for {
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.GET,
        uri = s"$nextUrl&v=1&NC=true",
        headers = List(authorization)))
      entity <- Unmarshal(response.entity).to[Either[LpErrorResponse, LpChatConversation]]
    } yield entity
//      entity <- Unmarshal(response.entity).to[String]
//    } yield {
//      val json = entity.parseJson
//      logger.debug(json.prettyPrint)
//      try {
//        json.convertTo[Either[LpErrorResponse, LpChatConversation]]
//      } catch {
//        case e: Throwable =>
//          logger.error(e, e.getMessage)
//          throw new RuntimeException(e.getMessage, e)
//      }
//    }
  }

  def getNextEvents(chatConversation: LpChatConversation, accessToken: String): Future[Either[LpErrorResponse, LpChatConversation]] = {
    logger.info("get next events")
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    val url = chatConversation.getNextEventsUrl
    for {
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.GET,
        uri = s"$url?v=1&NC=true",
        headers = List(authorization)))
      entity <- Unmarshal(response.entity).to[Either[LpErrorResponse, LpChatConversation]]
    } yield entity
  }

  def getChatEvents(eventsUrl: String, accessToken: String, from: Int = 0): Future[Either[LpErrorResponse, LpChatEvents]] = {
    logger.info("get chat events")
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    for {
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.GET,
        uri = s"$eventsUrl?from=$from&v=1&NC=true",
        headers = List(authorization)))
      entity <- Unmarshal(response.entity).to[Either[LpErrorResponse, LpChatEvents]]
    } yield entity
  }

  def sendTextMessage(eventsUrl: String, accessToken: String, text: String, textType: String = "plain"): Future[Either[LpErrorResponse, LpChatResponse]] = {
    logger.info("send text message in {} format", textType)
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    val event = LpMessage(LpMessageEvent(eventType = "line", text = text, textType))
    for {
      request <- Marshal(event).to[RequestEntity]
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = s"$eventsUrl?v=1&NC=true",
        headers = List(authorization),
        entity = request))
      entity <- Unmarshal(response.entity).to[Either[LpErrorResponse, LpChatResponse]]
    } yield entity
  }

  def getSessionInfo(agentSessionUrl: String, accessToken: String): Future[Either[LpErrorResponse, LpInfoResponse]] = {
    logger.info("get session info")
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    for {
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.GET,
        uri = s"$agentSessionUrl?v=1&NC=true",
        headers = List(authorization)))
      entity <- Unmarshal(response.entity).to[Either[LpErrorResponse, LpInfoResponse]]
    } yield entity
  }

  def getAvailableSkillsForTransfer(transferUrl: String, accessToken: String): Future[Either[LpErrorResponse, LpTransferResponse]] = {
    logger.info("get available skills for transfer")
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    for {
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.GET,
        uri = s"$transferUrl?v=1&NC=true",
        headers = List(authorization)))
      entity <- Unmarshal(response.entity).to[Either[LpErrorResponse, LpTransferResponse]]
    } yield entity
  }

  def transferToSkill(transferUrl: String, accessToken: String, skillId: String, text: String): Unit = {
    logger.info("transfer to agent with given skill")
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    val payload = LpTransferRequest(LpSkillTransfer(LpSkillId(skillId), text))
    for {
      request <- Marshal(payload).to[RequestEntity]
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = s"$transferUrl?v=1&NC=true",
        headers = List(authorization),
        entity = request))
    } yield ()
  }

  def transferToAgent(transferUrl: String, accessToken: String, agentId: String, text: String): Unit = {
    logger.info("transfer to specific agent")
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    val payload = LpTransferRequest(LpAgentTransfer(LpAgentId(agentId), text))
    for {
      request <- Marshal(payload).to[RequestEntity]
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = s"$transferUrl?v=1&NC=true",
        headers = List(authorization),
        entity = request))
    } yield ()
  }

  def getAvailableAgents(availableAgentsUrl: String, accessToken: String): Future[Either[LpErrorResponse, LpAvailableAgentsResponse]] = {
    logger.info("get available agents")
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    for {
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.GET,
        uri = s"$availableAgentsUrl?v=1&NC=true",
        headers = List(authorization)))
      entity <- Unmarshal(response.entity).to[Either[LpErrorResponse, LpAvailableAgentsResponse]]
    } yield entity
  }

  def getAvailableAgents(availableAgentsUrl: String, accessToken: String, skillName: String): Future[Either[LpErrorResponse, LpAvailableAgentsResponse]] = {
    logger.info("get available agents with given skill")
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    for {
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.GET,
        uri = s"$availableAgentsUrl?skill=$skillName&chatState=Online&v=1&NC=true",
        headers = List(authorization)))
      entity <- Unmarshal(response.entity).to[Either[LpErrorResponse, LpAvailableAgentsResponse]]
    } yield entity
  }

  def getVisitSessionResources(visitSessionUrl: String, accessToken: String): Future[Either[LpErrorResponse, LpVisitSessionResponse]] = {
    logger.info("get session resources")
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    for {
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.GET,
        uri = s"$visitSessionUrl?v=1&NC=true",
        headers = List(authorization)))
      entity <- Unmarshal(response.entity).to[Either[LpErrorResponse, LpVisitSessionResponse]]
    } yield entity
  }

  def getVisitSessionDetails(visitInfoUrl: String, accessToken: String): Future[Either[LpErrorResponse, LpVisitSessionDetailsResponse]] = {
    logger.info("get session details")
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    for {
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.GET,
        uri = s"$visitInfoUrl?v=1&NC=true",
        headers = List(authorization)))
      entity <- Unmarshal(response.entity).to[Either[LpErrorResponse, LpVisitSessionDetailsResponse]]
    } yield entity
  }

  def getVisitSessionCustomVariables(customVariablesUrl: String, accessToken: String): Future[Either[LpErrorResponse, LpCustomVariablesResponse]] = {
    logger.info("get session custom variables")
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    for {
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.GET,
        uri = s"$customVariablesUrl?v=1&NC=true",
        headers = List(authorization)))
      entity <- Unmarshal(response.entity).to[Either[LpErrorResponse, LpCustomVariablesResponse]]
    } yield entity
  }

  // TODO
  // expected result is either a JSON error object or empty response body
  // using `String` works but does not seem semantically correct
  def setAgentTyping(agentTypingUrl: String, accessToken: String, isTyping: Boolean): Future[Either[LpErrorResponse, String]] = {
    val status = if (isTyping) "typing" else "not-typing"
    logger.info("set agent typing status to {}", status)
    val payload = LpAgentTypingStatus(status)
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    for {
      request <- Marshal(payload).to[RequestEntity]
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = s"$agentTypingUrl?v=1&NC=true",
        entity = request,
        headers = List(authorization, RawHeader("X-HTTP-Method-Override", "PUT"))))
      entity <- Unmarshal(response.entity).to[Either[LpErrorResponse, String]]
    } yield entity
  }

  def transformEither[R, T](either: Either[LpErrorResponse, R], fn: R => T) = either match {
    case Left(e) => Left(e)
    case Right(r) => Right(fn(r))
  }

}
