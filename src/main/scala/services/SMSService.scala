package services

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, RequestEntity}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import apis.telstra.{Builder, SMSMessageResponse, SMSMessageStatus, TelstraJsonSupport}
import com.google.inject.Inject
import com.typesafe.config.Config

import scala.concurrent.Future

/**
  * Created by markmo on 15/10/2016.
  */
class SMSService @Inject()(config: Config,
                           logger: LoggingAdapter,
                           implicit val system: ActorSystem,
                           implicit val fm: Materializer)
  extends TelstraJsonSupport {

  import system.dispatcher

  val http = Http()

  val uri = config.getString("telstra.api.url")

  val accessToken = System.getenv("TELSTRA_ACCESS_TOKEN")

  def sendSMS(sender: String, text: String): Future[SMSMessageResponse] = {
    logger.info(s"sending SMS message [$text] to phone number [$sender]")
    import Builder._
    val authorization = Authorization(OAuth2BearerToken(accessToken))

    val payload = sms forSender sender withText text build()

    for {
      request <- Marshal(payload).to[RequestEntity]
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = uri,
        headers = List(authorization),
        entity = request))
      entity <- Unmarshal(response.entity).to[SMSMessageResponse]
    } yield entity
  }

  /**
    * Status list
    * PEND	The message is pending and has not yet been sent to the intended recipient
    * SENT	The message has been sent to the intended recipient, but has not been delivered yet
    * DELIVRD	The message has been delivered to the intended recipient
    * READ	The message has been read by intended recipient and the recipient's response has been received
    *
    * @param messageId String
    * @return Future[SMSMessageStatus]
    */
  def getMessageStatus(messageId: String): Future[SMSMessageStatus] = {
    logger.info(s"getting status of message [$messageId]")
    val authorization = Authorization(OAuth2BearerToken(accessToken))
    for {
      response <- http.singleRequest(HttpRequest(
        method = HttpMethods.GET,
        uri = s"$uri/$messageId",
        headers = List(authorization)))
      entity <- Unmarshal(response.entity).to[SMSMessageStatus]
    } yield entity
  }

}