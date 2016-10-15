package apis.telstra

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol

/**
  * Created by markmo on 15/10/2016.
  */
case class SMSMessage(to: String, body: String)

case class SMSMessageResponse(messageId: String)

case class SMSMessageStatus(to: String, receivedTimestamp: String, sentTimestamp: String, status: String)

/**
  *
  * @param messageId             String ID returned from the Send SMS operation
  * @param status                String
  *                              READ - The message has been received by the network and is being processed for delivery to the handset or the message has been received by the handset.
  *                              UNDVBL - SMS API was not able to deliver the message to the intended recipient on the specified channel.
  * @param acknowledgedTimestamp String The timestamp when the message is received.
  * @param content               String The content of the response message.
  */
case class SMSReply(messageId: String, status: String, acknowledgedTimestamp: String, content: String)

trait TelstraJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val smsMessageJsonFormat = jsonFormat2(SMSMessage)
  implicit val smsMessageResponseJsonFormat = jsonFormat1(SMSMessageResponse)
  implicit val smsMessageStatusJsonFormat = jsonFormat4(SMSMessageStatus)
  implicit val smsReplyJsonFormat = jsonFormat4(SMSReply)
}

object Builder {

  class SMSMessageBuilder(to: Option[String], body: Option[String]) {

    def forSender(value: String) = new SMSMessageBuilder(Some(value), body)

    def withText(value: String) = new SMSMessageBuilder(to, Some(value))

    def build() = SMSMessage(to.getOrElse(""), body.getOrElse(""))

  }

  def sms = new SMSMessageBuilder(None, None)

}