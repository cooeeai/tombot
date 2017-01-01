package apis.mailgun

import spray.json.JsValue

/**
  * Created by markmo on 20/12/2016.
  */

/**
  * Note Unlike other event webhooks (due to frequency of delivered events), Delivered Event
  * will only POST once, right after delivery, and won’t attempt again in case of failure to
  * POST successfully.
  *
  * @param event Event name (“delivered”)
  * @param recipient Intended recipient
  * @param domain Domain that sent the original message
  * @param headers String list of all MIME headers dumped to a JSON string (order of headers preserved)
  * @param messageId String id of the original message delivered to the recipient
  * @param customVariables Your own custom JSON object included in the header of the original message
  * @param timestamp Number of seconds passed since January 1, 1970
  * @param token Randomly generated string with length 50
  * @param signature String with hexadecimal digits generate by HMAC algorithm
  * @param body plain body text
  */
case class MailgunMessageDeliveredResponse(event: String,
                                           recipient: String,
                                           domain: String,
                                           headers: Map[String, List[String]],
                                           messageId: String,
                                           customVariables: Map[String, String],
                                           timestamp: Long,
                                           token: String,
                                           signature: String,
                                           body: String)

case class MailgunMessageReceivedResponse(recipient: String,
                                          headers: Map[String, List[String]],
                                          bodyPlain: String,
                                          subject: String,
                                          timestamp: Long,
                                          sender: String,
                                          signature: String,
                                          received: String,
                                          strippedSignature: String,
                                          references: String,
                                          token: String,
                                          contentType: String,
                                          from: String,
                                          to: String,
                                          messageId: String,
                                          date: String,
                                          strippedHTML: String,
                                          attachmentCount: Int,
                                          strippedText: String,
                                          replyTo: String,
                                          bodyHTML: String,
                                          customVariables: Map[String, Any])
