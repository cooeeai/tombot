package apis.wva

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, JsValue}

/**
  * Created by markmo on 18/12/2016.
  */

/**
  * Used to specify the name of the widget to be displayed.
  *
  * Standard layout types include:
  *
  * * cc-validator Credit card widget for Make Payment flow
  * * form Creates a generic form from the field names in the Store object
  * * show-locations Map widget along with store location data for Find Nearest Store flow
  * * choose-location-type UI returns what type of location is being returned - outputs: zipcode, latlong
  * * request-geolocation-zipcode Requests user to input the desired zipcode
  * * request-geolocation-latlong Requests user to share current browser or device location
  *
  * @param name Layout name.
  * @param index Optional. When a dialog node has multiple lines of text along with a layout,
  *              an optional index property can be used to denote at which position the layout
  *              should be rendered. For example, to display the layout after the first string
  *              of text (array item 0), specify "index" : "0".
  */
case class WvaLayout(name: String, index: Option[String])

/**
  *
  * @param oneOf Requires input to be one of a range of values
  * @param someOf Requires input to be any of a range of values (for example a multiple selection textbox,
  *               select all that apply)
  * @param range Requires input to be in a range of values
  */
case class WvaInputValidation(oneOf: Option[List[String]],
                              someOf: Option[List[String]],
                              range: Option[List[BigDecimal]])

/**
  * Rules that are used to validate the input provided by a user.
  *
  * @param regex A regular expression that indicates what values are allowed
  * @param message A message to display to users if their input does not meet the regular expression requirements
  */
case class WvaFormLayoutValidation(regex: String, message: String)

/**
  * The form layout is a flexible widget that can be used anywhere in the dialog when a user input is needed
  * that does not need to be sent back to the dialog.
  *
  * @param name Variable name that can be used by the application to reference it
  * @param label Display name for the field in the form
  * @param required Optional. Specifies whether it is mandatory for a user to fill out the field.
  *                 False by default.
  * @param validations Optional. Rules that are used to validate the input provided by a user.
  */
case class WvaFormLayoutField(name: String,
                              label: String,
                              required: Option[Boolean],
                              validations: Option[List[WvaFormLayoutValidation]])

case class WvaArgs(variables: List[String])

case class WvaAction(name: String, args: Option[Either[WvaArgs, Map[String, String]]])

/**
  * Provides an output dialog from the bot.
  *
  * @param text String response from bot. May be an array.
  * @param layout Optional. Used to specify the name of the widget to be displayed.
  * @param inputvalidation Optional.
  * @param store Directs the widget to store these values on the client system for integration and transactions,
  *              without returning data to the Watson Virtual Agent Cloud infrastructure in any way. The dialog
  *              waits for a callback with SUCCESS or FAILURE.
  * @param action Using action, we can instruct the channel to execute methods that require access to private
  *               variables, systems of record, or those that the bot typically cannot handle (for example,
  *               completing a payment transaction, updating the billing address, or retrieving private
  *               information from a system of record).
  * @param variables
  */
case class WvaOutput(text: Option[Either[List[String], String]],
                     layout: Option[WvaLayout],
                     inputvalidation: Option[WvaInputValidation],
                     store: Option[List[WvaFormLayoutField]],
                     action: Option[WvaAction],
                     variables: Option[Boolean])

/**
  * The request property of context is used to call a method on the bot back end and then call the channel,
  * passing along any additional information if needed. For example, when the dialog invokes a request to
  * retrieve the list of stores near a location, the bot executes this method and then passes along the data
  * for nearest stores for the channel to display.
  *
  * @param name Name of the method being called
  * @param args Optional. List of arguments mapped as name: value
  * @param result Optional. Result passed back from the bot once the request is executed
  */
case class WvaRequestContext(name: String, args: Option[Map[String, String]], result: Option[String])

/**
  * Variable values can be persisted in the context section of the response.
  *
  * @param output
  * @param context
  */
case class WvaResponse(output: WvaOutput, context: Option[Map[String, JsValue]])

case class WvaMessageSystemContext(dialogRequestCounter: Int, dialogStack: List[String], dialogTurnCounter: Int)

case class WvaMessageContext(conversationId: String, system: WvaMessageSystemContext)

case class WvaIntent(confidence: Double, intent: String)

case class WvaLogData(entities: List[Map[String, JsValue]], intents: List[WvaIntent])

case class WvaAddress(address: String, lat: Double, lng: Double, timezone: String)

case class WvaPhone(number: String, phoneType: String)

case class WvaOpeningTimes(isOpen: Boolean,
                           open: Option[String],
                           close: Option[String],
                           openMeridiem: Option[String],
                           closeMeridiem: Option[String])

case class WvaStoreLocation(address: WvaAddress,
                            days: List[WvaOpeningTimes],
                            description: Option[String],
                            email: Option[String],
                            hasDays: String,
                            hasPhones: String,
                            label: String,
                            phones: Option[List[WvaPhone]])

case class WvaMessage(context: WvaMessageContext,
                      data: Option[Map[String, JsValue]],
                      entities: Option[List[Map[String, JsValue]]],
                      inputvalidation: Option[WvaInputValidation],
                      intents: Option[List[WvaIntent]],
                      layout: Option[WvaLayout],
                      logData: Option[WvaLogData],
                      nodePosition: String,
                      text: List[String])

case class WvaMessageResponse(message: WvaMessage) {

  def layoutName = message.layout match {
    case Some(l) => l.name
    case None => "none"
  }
}

case class WvaStartChatResponse(botId: String, dialogId: String, message: WvaMessage)

case class WvaMessageRequest(message: Option[String], userID: Option[String])

trait WvaJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val wvaLayoutJsonFormat = jsonFormat2(WvaLayout)
  implicit val wvaInputValidationJsonFormat = jsonFormat3(WvaInputValidation)
  implicit val wvaFormLayoutValidationJsonFormat = jsonFormat2(WvaFormLayoutValidation)
  implicit val wvaFormLayoutFieldJsonFormat = jsonFormat4(WvaFormLayoutField)
  implicit val wvaArgsJsonFormat = jsonFormat1(WvaArgs)
  implicit val wvaActionJsonFormat = jsonFormat2(WvaAction)
  implicit val wvaOutputJsonFormat = jsonFormat6(WvaOutput)
  implicit val wvaRequestContextJsonFormat = jsonFormat3(WvaRequestContext)
  implicit val wvaResponseJsonFormat = jsonFormat2(WvaResponse)
  implicit val wvaMessageSystemContextJsonFormat = jsonFormat(WvaMessageSystemContext, "dialog_request_counter", "dialog_stack", "dialog_turn_counter")
  implicit val wvaMessageContextJsonFormat = jsonFormat(WvaMessageContext, "conversation_id", "system")
  implicit val wvaIntentJsonFormat = jsonFormat2(WvaIntent)
  implicit val wvaLogDataJsonFormat = jsonFormat2(WvaLogData)
  implicit val wvaAddressJsonFormat = jsonFormat4(WvaAddress)
  implicit val wvaPhoneJsonFormat = jsonFormat(WvaPhone, "number", "type")
  implicit val wvaOpeningTimesJsonFormat = jsonFormat5(WvaOpeningTimes)
  implicit val wvaStoreLocationJsonFormat = jsonFormat8(WvaStoreLocation)
  implicit val wvaMessageJsonFormat = jsonFormat(WvaMessage, "context", "data", "entities", "inputvalidation", "intents", "layout", "log_data", "node_position", "text")
  implicit val wvaMessageResponseJsonFormat = jsonFormat1(WvaMessageResponse)
  implicit val wvaStartChatResponseJsonFormat = jsonFormat(WvaStartChatResponse, "bot_id", "dialog_id", "message")
  implicit val wvaMessageRequestJsonFormat = jsonFormat2(WvaMessageRequest)
}