package apis.liveengage

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._
import spray.json.lenses.JsonLenses._

/**
  * Created by markmo on 26/11/2016.
  */

case class LpCredentials(username: String, password: String)

case class LpConfig(loginName: Option[String] = None,
                    userId: Option[String] = None,
                    userPrivileges: Option[Set[Int]] = None,
                    serverCurrentTime: Option[Long] = None,
                    timeDiff: Option[Int] = None,
                    serverTimeZoneName: Option[String] = None,
                    serverTimeGMTDiff: Option[Int] = None,
                    isLPA: Option[Boolean] = None,
                    isAdmin: Option[Boolean] = None,
                    accountTimeZoneId: Option[String] = None)

case class LpService(account: String, baseURI: String, service: String)

case class LpCsdsCollectionResponse(baseURIs: List[LpService])

case class LpAgentGroupItem(id: Int, deleted: Boolean, name: String)

case class LpAgentGroupsData(items: List[LpAgentGroupItem], revision: Int)

case class LpFeatureValue(value: Boolean)

case class LpFeature(compoundFeatureID: String, startDate: String, endDate: String, value: LpFeatureValue, isDeleted: Boolean)

case class LpProvisionData(features: List[LpFeature], revision: Int)

case class LpPropertyValue(value: JsValue)

case class LpSetting(id: String, createdDate: String, modifiedDate: Option[String], settingType: String, propertyValue: LpPropertyValue, deleted: Boolean)

case class LpSettingsData(settings: List[LpSetting], revision: Int)

case class LpAccountData(provisionData: Option[LpProvisionData], settingsData: Option[LpSettingsData], agentGroupsData: LpAgentGroupsData)

case class LpLoginResponse(csrf: String,
                           wsuk: String,
                           config: LpConfig,
                           csdsCollectionResponse: LpCsdsCollectionResponse,
                           accountData: LpAccountData,
                           sessionTTl: String,
                           bearer: String) {

  def getMessagingURL: String = csdsCollectionResponse.baseURIs.find(_.service == "asyncMessagingEnt").get.baseURI

  def getAdminURL: String = csdsCollectionResponse.baseURIs.find(_.service == "adminArea").get.baseURI

  def getLiveEngageURL: String = csdsCollectionResponse.baseURIs.find(_.service == "liveEngage").get.baseURI

}

case class LpLink(url: String, rel: String)

case class LpLocation(link: LpLink)

case class LpAgentSessionResponse(agentSessionLocation: LpLocation)

case class LpTakeChatResponse(chatLocation: LpLocation)

case class LpIncomingRequestsData(ringingCount: String, link: Option[LpLink])

case class LpRequestData(incomingRequests: LpIncomingRequestsData)

case class LpResponseError(time: String, message: String, internalCode: Int)

case class LpResponseFailure(error: LpResponseError)

trait LpEvent

case class LpStateEvent(id: String, eventType: String, time: String, state: String) extends LpEvent

case class LpLineEvent(id: String,
                       eventType: String,
                       time: String,
                       textType: String,
                       text: String,
                       by: String,
                       source: String,
                       systemMessageId: Option[Int],
                       subType: String) extends LpEvent

case class LpEvents(link: List[LpLink], event: Option[Either[LpEvent, List[LpEvent]]])

case class LpChatInfo(state: String,
                      chatSessionKey: String,
                      agentName: String,
                      agentId: Long,
                      startTime: String,
                      duration: Long,
                      lastUpdate: String,
                      chatTimeout: Long,
                      visitorId: Long,
                      agentTyping: String,
                      visitorTyping: String,
                      visitorName: String,
                      rtSessionId: Long,
                      sharkVisitorId: String,
                      sharkSessionId: String,
                      sharkContextId: Long,
                      engagementId: Long,
                      campaignId: Long,
                      language: String,
                      link: List[LpLink])

case class LpChat(link: List[LpLink], events: LpEvents, info: LpChatInfo)

case class LpChatEvents(events: LpEvents) {

  def getLastVisitorEvent: Option[LpLineEvent] = events.event match {
    case Some(Right(ev :: vs)) =>
      (ev :: vs).reverse.find {
        case ev: LpLineEvent if ev.source == "visitor" => true
        case _ => false
      } match {
        case Some(ev: LpLineEvent) => Some(ev)
        case _ => None
      }
    case Some(Left(ev: LpLineEvent)) if ev.source == "visitor" => Some(ev)
    case _ => None
  }

}

case class LpChatConversation(chat: LpChat) {

  def getEventsURL: String = chat.link.find(_.rel == "events").get.url

  def getNextURL: String = chat.link.find(_.rel == "next").get.url

  def getNextEventsURL: String = chat.events.link.find(_.rel == "next").get.url

  def getTransferURL: String = chat.link.find(_.rel == "transfer").get.url

  def getVisitSessionURL: String = chat.link.find(_.rel == "visit-id").get.url

  def getLastVisitorEvent: Option[LpLineEvent] = chat.events.event match {
    case Some(Right(ev :: vs)) =>
      (ev :: vs).reverse.find {
        case ev: LpLineEvent if ev.source == "visitor" => true
        case _ => false
      } match {
        case Some(ev: LpLineEvent) => Some(ev)
        case _ => None
      }
    case Some(Left(ev: LpLineEvent)) if ev.source == "visitor" => Some(ev)
    case _ => None
  }

}

case class LpMessageEvent(eventType: String, text: String, textType: String)

case class LpMessage(event: LpMessageEvent)

case class LpChatResponse(chatEventLocation: LpLocation)

case class LpAgentSessionId(link: List[LpLink])

case class LpInfoResponse(agentSessionId: LpAgentSessionId)

case class LpErrorMessage(errormsg: String)

case class LpTransferError(transfer: LpErrorMessage)

case class LpSkill(id: String, name: String, onlineAgents: Int)

case class LpTransferAvailableSkills(skill: List[LpSkill])

case class LpTransferResponse(transfer: LpTransferAvailableSkills)

case class LpSkillId(id: String)

case class LpAgentId(id: String)

trait LpTransfer

case class LpSkillTransfer(skill: LpSkillId, text: String) extends LpTransfer

case class LpAgentTransfer(agent: LpAgentId, text: String) extends LpTransfer

case class LpTransferRequest(transfer: LpTransfer)

case class LpSkills(skill: List[String])

case class LpSkillInfo(id: String, name: String)

case class LpSkillsInfo(skillInfo: List[LpSkillInfo])

case class LpAgent(id: String,
                   chatState: String,
                   voiceState: String,
                   maxChats: Int,
                   username: String,
                   nickname: String,
                   email: String,
                   privilegeGroup: String,
                   skills: LpSkills,
                   skillsInfo: LpSkillsInfo,
                   chats: Int,
                   agentGroupName: String,
                   agentGroupId: String,
                   elapsedTimeInState: Long)

case class LpAgents(agent: List[LpAgent])

case class LpAvailableAgents(agents: LpAgents, link: LpLink)

case class LpAvailableAgentsResponse(availableAgents: LpAvailableAgents) {

  def getNextBestAgent(skillId: String): Option[LpAgent] =
    availableAgents.agents.agent
      .sortBy(_.chats)
      .find(a => a.skills.skill.contains(skillId) && (a.maxChats == -1 || a.chats < a.maxChats))

  def getNextBestAgent: Option[LpAgent] =
    availableAgents.agents.agent
      .sortBy(_.chats)
      .find(a => a.maxChats == -1 || a.chats < a.maxChats)

}

case class LpVisitSessionId(link: List[LpLink])

case class LpVisitSessionResponse(visitId: LpVisitSessionId) {

  def getInfoURL = visitId.link.find(_.rel == "info").get.url

  def getCustomVariablesURL = visitId.link.find(_.rel == "custom-variables").get.url

}

case class LpVariable(key: String, value: String)

case class LpVariables(info: List[LpVariable])

case class LpVisitInfo(link: LpLink,
                       isInCurrentChat: Boolean,
                       visitorId: Long,
                       sessionInfo: LpVariables,
                       visitorInfo: LpVariables,
                       time: LpVariables)

case class LpVisitSessionDetailsResponse(visitInfo: LpVisitInfo)

case class LpCustomVariable(source: String, scope: String, name: String, time: String, displayName: String, value: String)

case class LpCustomVariables(link: LpLink, customVariable: List[LpCustomVariable])

case class LpCustomVariablesResponse(customVariables: LpCustomVariables)

trait LpChatJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val lpCredentialsJsonFormat = jsonFormat2(LpCredentials)
  implicit val lpConfigJsonFormat = jsonFormat10(LpConfig)
  implicit val lpServiceJsonFormat = jsonFormat3(LpService)
  implicit val lpCsdsCollectionResponseJsonFormat = jsonFormat1(LpCsdsCollectionResponse)
  implicit val lpAgentGroupItemJsonFormat = jsonFormat3(LpAgentGroupItem)
  implicit val lpAgentGroupsDataJsonFormat = jsonFormat2(LpAgentGroupsData)
  implicit val lpFeatureValueJsonFormat = jsonFormat1(LpFeatureValue)
  implicit val lpFeatureJsonFormat = jsonFormat5(LpFeature)
  implicit val lpProvisionDataJsonFormat = jsonFormat2(LpProvisionData)
  implicit val lpPropertyValueJsonFormat = jsonFormat1(LpPropertyValue)
  implicit val lpSettingJsonFormat = jsonFormat(LpSetting, "id", "createdDate", "modifiedDate", "type", "propertyValue", "deleted")
  implicit val lpSettingsDataJsonFormat = jsonFormat2(LpSettingsData)
  implicit val lpAccountDataJsonFormat = jsonFormat3(LpAccountData)
  implicit val lpLoginResponseJsonFormat = jsonFormat7(LpLoginResponse)
  implicit val lpLinkJsonFormat = jsonFormat(LpLink, "@href", "@rel")
  implicit val lpLocationJsonFormat = jsonFormat1(LpLocation)
  implicit val lpAgentSessionResponseJsonFormat = jsonFormat1(LpAgentSessionResponse)
  implicit val lpTakeChatResponseJsonFormat = jsonFormat1(LpTakeChatResponse)
  implicit val lpIncomingRequestsDataJsonFormat = jsonFormat2(LpIncomingRequestsData)
  implicit val lpRequestDataJsonFormat = jsonFormat1(LpRequestData)
  implicit val lpResponseErrorJsonFormat = jsonFormat3(LpResponseError)
  implicit val lpResponseFailureJsonFormat = jsonFormat1(LpResponseFailure)

  implicit object lpEventJsonFormat extends RootJsonFormat[LpEvent] {

    def write(ev: LpEvent) = ev match {
      case s: LpStateEvent => JsObject(
        "@id" -> JsString(s.id),
        "@type" -> JsString(s.eventType),
        "time" -> JsString(s.time),
        "state" -> JsString(s.state)
      )
      case l: LpLineEvent =>
        val base = Map(
          "@id" -> JsString(l.id),
          "@type" -> JsString(l.eventType),
          "time" -> JsString(l.time),
          "textType" -> JsString(l.textType),
          "text" -> JsString(l.text),
          "by" -> JsString(l.by),
          "source" -> JsString(l.source),
          "subType" -> JsString(l.subType)
        )
        l.systemMessageId match {
          case Some(systemMessageId) =>
            JsObject(base + ("systemMessageId" -> JsNumber(systemMessageId)))
          case None =>
            JsObject(base)
        }
    }

    def read(value: JsValue) =
      value.extract[String]("@type") match {
        case "state" => LpStateEvent(
          value.extract[String]("@id"),
          value.extract[String]("@type"),
          value.extract[String]("time"),
          value.extract[String]("state")
        )
        case "line" => LpLineEvent(
          value.extract[String]("@id"),
          value.extract[String]("@type"),
          value.extract[String]("time"),
          value.extract[String]("textType"),
          value.extract[String]("text"),
          value.extract[String]("by"),
          value.extract[String]("source"),
          value.extract[Int]('systemMessageId.?),
          value.extract[String]("subType")
        )
        case _ => throw DeserializationException("LpEvent expected")
      }

  }

  implicit val lpStateEventJsonFormat = jsonFormat(LpStateEvent, "@id", "@type", "time", "state")
  implicit val lpLineEventJsonFormat = jsonFormat(LpLineEvent, "@id", "@type", "time", "textType", "text", "by", "source", "systemMessageId", "subType")
  implicit val lpEventsJsonFormat = jsonFormat2(LpEvents)
  implicit val lpChatInfoJsonFormat = jsonFormat20(LpChatInfo)
  implicit val lpChatJsonFormat = jsonFormat3(LpChat)
  implicit val lpChatEventsJsonFormat = jsonFormat1(LpChatEvents)
  implicit val lpChatConversationJsonFormat = jsonFormat1(LpChatConversation)
  implicit val lpMessageEventJsonFormat = jsonFormat(LpMessageEvent, "@type", "text", "textType")
  implicit val lpMessageJsonFormat = jsonFormat1(LpMessage)
  implicit val lpChatResponseJsonFormat = jsonFormat1(LpChatResponse)
  implicit val lpAgentSessionIdJsonFormat = jsonFormat1(LpAgentSessionId)
  implicit val lpInfoResponseJsonFormat = jsonFormat1(LpInfoResponse)
  implicit val lpErrorMessageJsonFormat = jsonFormat1(LpErrorMessage)
  implicit val lpTransferErrorJsonFormat = jsonFormat1(LpTransferError)
  implicit val lpSkillJsonFormat = jsonFormat3(LpSkill)
  implicit val lpTransferAvailableSkillsJsonFormat = jsonFormat1(LpTransferAvailableSkills)
  implicit val lpTransferResponseJsonFormat = jsonFormat1(LpTransferResponse)
  implicit val lpSkillIdJsonFormat = jsonFormat1(LpSkillId)
  implicit val lpAgentIdJsonFormat = jsonFormat1(LpAgentId)

  implicit object lpTransferJsonFormat extends RootJsonFormat[LpTransfer] {

    def write(t: LpTransfer) = t match {
      case s: LpSkillTransfer => JsObject(
        "skill" -> JsObject("id" -> JsString(s.skill.id)),
        "text" -> JsString(s.text)
      )
      case a: LpAgentTransfer => JsObject(
        "agent" -> JsObject("id" -> JsString(a.agent.id)),
        "text" -> JsString(a.text)
      )
    }

    def read(value: JsValue) = {
      val fields = value.asJsObject.fields
      if (fields.contains("skill")) {
        LpSkillTransfer(
          LpSkillId(value.extract[String]('skill / 'id)),
          value.extract[String]('text)
        )
      } else {
        LpAgentTransfer(
          LpAgentId(value.extract[String]('agent / 'id)),
          value.extract[String]('text)
        )
      }
    }

  }

  implicit val lpSkillTransferJsonFormat = jsonFormat2(LpSkillTransfer)
  implicit val lpAgentTransferJsonFormat = jsonFormat2(LpAgentTransfer)
  implicit val lpTransferRequestJsonFormat = jsonFormat1(LpTransferRequest)
  implicit val lpSkillsJsonFormat = jsonFormat1(LpSkills)
  implicit val lpSkillInfoJsonFormat = jsonFormat2(LpSkillInfo)
  implicit val lpSkillsInfoJsonFormat = jsonFormat1(LpSkillsInfo)
  implicit val lpAgentJsonFormat = jsonFormat(LpAgent, "@id", "@chatState", "@voiceState", "@maxChats", "userName", "nickname", "email", "privilegeGroup", "skills", "skillsInfo", "chats", "agentGroupName", "agentGroupId", "elapsedTimeInState")
  implicit val lpAgentsJsonFormat = jsonFormat1(LpAgents)
  implicit val lpAvailableAgentsJsonFormat = jsonFormat2(LpAvailableAgents)
  implicit val lpAvailableAgentsResponseJsonFormat = jsonFormat1(LpAvailableAgentsResponse)
  implicit val lpVisitSessionIdJsonFormat = jsonFormat1(LpVisitSessionId)
  implicit val lpVisitSessionResponseJsonFormat = jsonFormat1(LpVisitSessionResponse)
  implicit val lpVariableJsonFormat = jsonFormat(LpVariable, "@name", "$")
  implicit val lpVariablesJsonFormat = jsonFormat1(LpVariables)
  implicit val lpVisitInfoJsonFormat = jsonFormat6(LpVisitInfo)
  implicit val lpVisitSessionDetailsResponseJsonFormat = jsonFormat1(LpVisitSessionDetailsResponse)
  implicit val lpCustomVariableJsonFormat = jsonFormat(LpCustomVariable, "@source", "@scope", "name", "time", "displayName", "value")
  implicit val lpCustomVariablesJsonFormat = jsonFormat2(LpCustomVariables)
  implicit val lpCustomVariablesResponseJsonFormat = jsonFormat1(LpCustomVariablesResponse)

}