package apis.liveengage

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, JsValue}

/**
  * Created by markmo on 26/11/2016.
  */

case class LpMessagingLoginRequest(config: LpConfig, accountId: String, otk: String)

case class LpMessagingLoginResponse(csrf: String,
                                    wsuk: String,
                                    glob: String,
                                    config: LpConfig,
                                    csdsCollectionResponse: LpCsdsCollectionResponse,
                                    accountData: LpAccountData,
                                    sessionTTl: String)

case class LpRing(ringId: String, ringState: String, convId: String, consumerId: String, skillId: String)

case class LpContentEvent(convId: String,
                          sequence: Int,
                          consumerId: String,
                          serverTimestamp: Long,
                          eventType: String,
                          message: String,
                          contentType: String)

case class LpResolveEvent(convId: String)

case class LpUserPrivateData(mobileNum: String,
                             mail: String,
                             pushNotificationData: JsValue,
                             serviceName: String,
                             certName: String,
                             token: String)

case class LpUserProfile(firstName: String,
                         lastName: String,
                         userId: String,
                         avatarUrl: String,
                         role: Option[List[String]],
                         backgndImgUri: String,
                         description: String,
                         privateDate: Option[LpUserPrivateData])

case class LpTextMessage(convId: String, message: String)

trait LpMessagingJsonSupport extends DefaultJsonProtocol with SprayJsonSupport with LpChatJsonSupport {
  implicit val lpMessagingLoginRequestJsonFormat = jsonFormat3(LpMessagingLoginRequest)
  implicit val lpMessagingLoginResponseJsonFormat = jsonFormat7(LpMessagingLoginResponse)
  implicit val lpRingJsonFormat = jsonFormat5(LpRing)
  implicit val lpContentEventJsonFormat = jsonFormat(LpContentEvent, "convId", "sequence", "consumerId", "serverTimestamp", "type", "message", "contentType")
  implicit val lpResolveEventJsonFormat = jsonFormat1(LpResolveEvent)
  implicit val lpUserPrivateDataJsonFormat = jsonFormat6(LpUserPrivateData)
  implicit val lpUserProfileJsonFormat = jsonFormat8(LpUserProfile)
  implicit val lpTextMessageJsonFormat = jsonFormat2(LpTextMessage)
}