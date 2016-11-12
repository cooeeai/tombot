package apis.telegram

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.google.common.base.CaseFormat
import spray.json._
import spray.json.lenses.JsonLenses._
import utils.JsonSupportUtils

/**
  * Created by markmo on 9/11/2016.
  */

/**
  * This object represents a Telegram user or bot.
  *
  * @param id        Unique identifier for this user or bot
  * @param firstName User‘s or bot’s first name
  * @param lastName  Optional. User‘s or bot’s last name
  * @param username  Optional. User‘s or bot’s username
  */
case class TelegramUser(id: Int, firstName: String, lastName: String, username: String)

/**
  * This object represents a chat.
  *
  * @param id                          Int Unique identifier for this chat.
  * @param chatType                    Type of chat, can be either “private”, “group”, “supergroup” or “channel”
  * @param title                       Optional. Title, for supergroups, channels and group chats
  * @param username                    Optional. Username, for private chats, supergroups and channels if available
  * @param firstName                   Optional. First name of the other party in a private chat
  * @param lastName                    Optional. Last name of the other party in a private chat
  * @param allMembersAreAdministrators Optional. True if a group has ‘All Members Are Admins’ enabled
  */
case class TelegramChat(id: Int,
                        chatType: String,
                        title: Option[String],
                        username: Option[String],
                        firstName: Option[String],
                        lastName: Option[String],
                        allMembersAreAdministrators: Option[Boolean])

/**
  * This object represents one special entity in a text message. For example, hashtags, usernames, URLs, etc.
  *
  * @param entityType Type of the entity. Can be mention (@username), hashtag, bot_command, url, email,
  *                   bold (bold text), italic (italic text), code (monowidth string), pre (monowidth block),
  *                   text_link (for clickable text URLs), text_mention (for users without usernames)
  * @param offset     Offset in UTF-16 code units to the start of the entity
  * @param length     Length of the entity in UTF-16 code units
  * @param url        Optional. For “text_link” only, url that will be opened after user taps on the text
  * @param user       Optional. For “text_mention” only, the mentioned user
  */
case class TelegramMessageEntity(entityType: String,
                                 offset: Int,
                                 length: Int,
                                 url: Option[String],
                                 user: Option[TelegramUser])

/**
  * This object represents one size of a photo or a file / sticker thumbnail.
  *
  * @param fileId   Unique identifier for this file
  * @param width    Photo width
  * @param height   Photo height
  * @param fileSize Optional. File size
  */
case class TelegramPhotoSize(fileId: String, width: Int, height: Int, fileSize: Int)

/**
  * This object represents an audio file to be treated as music by the Telegram clients.
  *
  * @param fileId    Unique identifier for this file
  * @param duration  Duration of the audio in seconds as defined by sender
  * @param performer Optional. Performer of the audio as defined by sender or by audio tags
  * @param title     Optional. Title of the audio as defined by sender or by audio tags
  * @param mimeType  Optional. MIME type of the file as defined by sender
  * @param fileSize  Optional. File size
  */
case class TelegramAudio(fileId: String,
                         duration: Int,
                         performer: Option[String],
                         title: Option[String],
                         mimeType: Option[String],
                         fileSize: Option[Int])

/**
  * This object represents a general file (as opposed to photos, voice messages and audio files).
  *
  * @param fileId   Unique file identifier
  * @param thumb    Optional. Document thumbnail as defined by sender
  * @param fileName Optional. Original filename as defined by sender
  * @param mimeType Optional. MIME type of the file as defined by sender
  * @param fileSize Optional. File size
  */
case class TelegramDocument(fileId: String,
                            thumb: Option[TelegramPhotoSize],
                            fileName: Option[String],
                            mimeType: Option[String],
                            fileSize: Option[Int])

/**
  * This object represents a sticker.
  *
  * @param fileId   Unique identifier for this file
  * @param width    Sticker width
  * @param height   Sticker height
  * @param thumb    Optional. Sticker thumbnail in .webp or .jpg format
  * @param emoji    Optional. Emoji associated with the sticker
  * @param fileSize Optional. File size
  */
case class TelegramSticker(fileId: String,
                           width: Int,
                           height: Int,
                           thumb: Option[TelegramPhotoSize],
                           emoji: Option[String],
                           fileSize: Option[Int])

/**
  * This object represents a video file.
  *
  * @param fileId   Unique identifier for this file
  * @param width    Video width as defined by sender
  * @param height   Video height as defined by sender
  * @param duration Duration of the video in seconds as defined by sender
  * @param thumb    Optional. Video thumbnail
  * @param mimeType Optional. MIME type of a file as defined by sender
  * @param fileSize Optional. File size
  */
case class TelegramVideo(fileId: String,
                         width: Int,
                         height: Int,
                         duration: Int,
                         thumb: Option[TelegramPhotoSize],
                         mimeType: Option[String],
                         fileSize: Option[Int])

/**
  * This object represents a voice note.
  *
  * @param fileId   Unique identifier for this file
  * @param duration Duration of the audio in seconds as defined by sender
  * @param mimeType Optional. MIME type of the file as defined by sender
  * @param fileSize Optional. File size
  */
case class TelegramVoice(fileId: String,
                         duration: Int,
                         mimeType: Option[String],
                         fileSize: Option[Int])

/**
  * This object represents a phone contact.
  *
  * @param phoneNumber Contact's phone number
  * @param firstName   Contact's first name
  * @param lastName    Optional. Contact's last name
  * @param userId      Optional. Contact's user identifier in Telegram
  */
case class TelegramContact(phoneNumber: String, firstName: String, lastName: String, userId: Int)

/**
  * This object represents a point on the map.
  *
  * @param longitude Longitude as defined by sender
  * @param latitude  Latitude as defined by sender
  */
case class TelegramLocation(longitude: Float, latitude: Float)

/**
  * This object represents a venue.
  *
  * @param location     Venue location
  * @param title        Name of the venue
  * @param address      Address of the venue
  * @param foursquareId Optional. Foursquare identifier of the venue
  */
case class TelegramVenue(location: TelegramLocation, title: String, address: String, foursquareId: String)

/**
  * You can provide an animation for your game so that it looks stylish in chats (check out Lumberjack for
  * an example). This object represents an animation file to be displayed in the message containing a game.
  *
  * @param fileId   Unique file identifier
  * @param thumb    Optional. Animation thumbnail as defined by sender
  * @param fileName Optional. Original animation filename as defined by sender
  * @param mimeType Optional. MIME type of the file as defined by sender
  * @param fileSize Optional. File size
  */
case class TelegramAnimation(fileId: String,
                             thumb: Option[TelegramPhotoSize],
                             fileName: Option[String],
                             mimeType: Option[String],
                             fileSize: Option[String])

/**
  * This object represents a game. Use BotFather to create and edit games.
  * Their short names will act as unique identifiers.
  *
  * @param title        Title of the game
  * @param description  Description of the game
  * @param photo        Photo that will be displayed in the game message in chats.
  * @param text         Optional. Brief description of the game or high scores included in the game message.
  *                     Can be automatically edited to include current high scores for the game when the bot
  *                     calls setGameScore, or manually edited using editMessageText. 0-4096 characters.
  * @param textEntities Optional. Special entities that appear in text, such as usernames, URLs, bot commands, etc.
  * @param animation    Optional. Animation that will be displayed in the game message in chats. Upload via BotFather
  */
case class TelegramGame(title: String,
                        description: String,
                        photo: List[TelegramPhotoSize],
                        text: Option[String],
                        textEntities: List[TelegramMessageEntity],
                        animation: Option[TelegramAnimation])

/**
  * This object represents a message.
  *
  * @param id                    Unique message identifier
  * @param from                  Optional. Sender, can be empty for messages sent to channels
  * @param date                  Date the message was sent in Unix time
  * @param chat                  Conversation the message belongs to
  * @param forwardFrom           Optional. For forwarded messages, sender of the original message
  * @param forwardFromChat       Optional. For messages forwarded from a channel, information about the original
  *                              channel
  * @param forwardDate           Optional. For forwarded messages, date the original message was sent in Unix time
  * @param replyToMessage        Optional. For replies, the original message. Note that the Message object in this
  *                              field will not contain further reply_to_message fields even if it itself is a reply.
  * @param editDate              Optional. Date the message was last edited in Unix time
  * @param text                  Optional. For text messages, the actual UTF-8 text of the message, 0-4096 characters.
  * @param entities              Optional. For text messages, special entities like usernames, URLs, bot commands,
  *                              etc. that appear in the text
  * @param audio                 Optional. Message is an audio file, information about the file
  * @param document              Optional. Message is a general file, information about the file
  * @param game                  Optional. Message is a game, information about the game
  * @param photo                 Optional. Message is a photo, available sizes of the photo
  * @param sticker               Optional. Message is a sticker, information about the sticker
  * @param video                 Optional. Message is a video, information about the video
  * @param voice                 Optional. Message is a voice message, information about the file
  * @param caption               Optional. Caption for the document, photo or video, 0-200 characte
  * @param contact               Optional. Message is a shared contact, information about the contact
  * @param location              Optional. Message is a shared location, information about the location
  * @param venue                 Optional. Message is a venue, information about the venue
  * @param newChatMember         Optional. A new member was added to the group, information about them (this member
  *                              may be the bot itself)
  * @param leftChatMember        Optional. A member was removed from the group, information about them (this member
  *                              may be the bot itself)
  * @param newChatTitle          Optional. A chat title was changed to this value
  * @param newChatPhoto          Optional. A chat photo was change to this value
  * @param deleteChatPhoto       Optional. Service message: the chat photo was deleted
  * @param groupChatCreated      Optional. Service message: the group has been created
  * @param supergroupChatCreated Optional. Service message: the supergroup has been created. This field can‘t be
  *                              received in a message coming through updates, because bot can’t be a member of
  *                              a supergroup when it is created. It can only be found in reply_to_message if
  *                              someone replies to a very first message in a directly created supergroup.
  * @param channelChatCreated    Optional. Service message: the channel has been created. This field can‘t be received
  *                              in a message coming through updates, because bot can’t be a member of a channel when
  *                              it is created. It can only be found in reply_to_message if someone replies to a very
  *                              first message in a channel.
  * @param migrateToChatId       Optional. The group has been migrated to a supergroup with the specified identifier.
  *                              This number may be greater than 32 bits and some programming languages may have
  *                              difficulty/silent defects in interpreting it. But it smaller than 52 bits, so a signed
  *                              64 bit integer or double-precision float type are safe for storing this identifier.
  * @param migrateFromChatId     Optional. The supergroup has been migrated from a group with the specified identifier.
  *                              This number may be greater than 32 bits and some programming languages may have
  *                              difficulty/silent defects in interpreting it. But it smaller than 52 bits, so a signed
  *                              64 bit integer or double-precision float type are safe for storing this identifier.
  * @param pinnedMessage         Optional. Specified message was pinned. Note that the Message object in this field
  *                              will not contain further reply_to_message fields even if it is itself a reply.
  */
case class TelegramMessage(id: Int,
                           from: Option[TelegramUser],
                           date: Int,
                           chat: TelegramChat,
                           forwardFrom: Option[TelegramUser],
                           forwardFromChat: Option[TelegramChat],
                           forwardDate: Option[Int],
                           replyToMessage: Option[TelegramMessage],
                           editDate: Option[Int],
                           text: Option[String],
                           entities: Option[List[TelegramMessageEntity]],
                           audio: Option[TelegramAudio],
                           document: Option[TelegramDocument],
                           game: Option[TelegramGame],
                           photo: Option[List[TelegramPhotoSize]],
                           sticker: Option[TelegramSticker],
                           video: Option[TelegramVideo],
                           voice: Option[TelegramVoice],
                           caption: Option[String],
                           contact: Option[TelegramContact],
                           location: Option[TelegramLocation],
                           venue: Option[TelegramVenue],
                           newChatMember: Option[TelegramUser],
                           leftChatMember: Option[TelegramUser],
                           newChatTitle: Option[String],
                           newChatPhoto: Option[List[TelegramPhotoSize]],
                           deleteChatPhoto: Option[Boolean],
                           groupChatCreated: Option[Boolean],
                           supergroupChatCreated: Option[Boolean],
                           channelChatCreated: Option[Boolean],
                           migrateToChatId: Option[Int],
                           migrateFromChatId: Option[Int],
                           pinnedMessage: Option[TelegramMessage])

/**
  * This object represent a user's profile pictures.
  *
  * @param totalCount Total number of profile pictures the target user has
  * @param photos     Requested profile pictures (in up to 4 sizes each)
  */
case class TelegramUserProfilePhotos(totalCount: Int, photos: List[TelegramPhotoSize])

/**
  * This object represents a file ready to be downloaded. The file can be downloaded via the link
  * https://api.telegram.org/file/bot<token>/<file_path>. It is guaranteed that the link will be valid for
  * at least 1 hour. When the link expires, a new one can be requested by calling getFile.
  *
  * Maximum file size to download is 20 MB
  *
  * @param fileId   Unique identifier for this file
  * @param fileSize Optional. File size, if known
  * @param filePath Optional. File path. Use https://api.telegram.org/file/bot<token>/<file_path> to get the file.
  */
case class TelegramFile(fileId: String, fileSize: Int, filePath: String)

/**
  * This object represents an incoming inline query. When the user sends an empty query, your bot could return
  * some default or trending results.
  *
  * @param id       Unique identifier for this query
  * @param from     Sender
  * @param location Optional. Sender location, only for bots that request user location
  * @param query    Text of the query (up to 512 characters)
  * @param offset   Offset of the results to be returned, can be controlled by the bot
  */
case class TelegramInlineQuery(id: String,
                               from: TelegramUser,
                               location: Option[TelegramLocation],
                               query: String,
                               offset: String)

/**
  * Represents a result of an inline query that was chosen by the user and sent to their chat partner.
  *
  * @param id              The unique identifier for the result that was chosen
  * @param from            The user that chose the result
  * @param location        Optional. Sender location, only for bots that require user location
  * @param inlineMessageId Optional. Identifier of the sent inline message. Available only if there is
  *                        an inline keyboard attached to the message. Will be also received in callback
  *                        queries and can be used to edit the message.
  * @param query           The query that was used to obtain the result
  */
case class TelegramChosenInlineResult(id: String,
                                      from: TelegramUser,
                                      location: Option[TelegramLocation],
                                      inlineMessageId: Option[String],
                                      query: String)

/**
  * This object represents an incoming callback query from a callback button in an inline keyboard. If the button
  * that originated the query was attached to a message sent by the bot, the field message will be present. If
  * the button was attached to a message sent via the bot (in inline mode), the field inline_message_id will be
  * present. Exactly one of the fields data or game_short_name will be present.
  *
  * @param id              Unique identifier for this query
  * @param from            Sender
  * @param message         Optional. Message with the callback button that originated the query. Note that message
  *                        content and message date will not be available if the message is too old
  * @param inlineMessageId Optional. Identifier of the message sent via the bot in inline mode, that originated
  *                        the query.
  * @param chatInstance    Identifier, uniquely corresponding to the chat to which the message with the callback
  *                        button was sent. Useful for high scores in games.
  * @param data            Optional. Data associated with the callback button. Be aware that a bad client can send
  *                        arbitrary data in this field.
  * @param gameShortName   Optional. Short name of a Game to be returned, serves as the unique identifier for the game
  */
case class TelegramCallbackQuery(id: String,
                                 from: TelegramUser,
                                 message: Option[TelegramMessage],
                                 inlineMessageId: Option[String],
                                 chatInstance: String,
                                 data: Option[String],
                                 gameShortName: Option[String])

/**
  * This object represents an incoming update.
  *
  * Only one of the optional parameters can be present in any given update.
  *
  * @param id                 The update‘s unique identifier. Update identifiers start from a certain positive number
  *                           and increase sequentially. This ID becomes especially handy if you’re using Webhooks,
  *                           since it allows you to ignore repeated updates or to restore the correct update sequence,
  *                           should they get out of order.
  * @param message            Optional. New incoming message of any kind — text, photo, sticker, etc.
  * @param editedMessage      Optional. New version of a message that is known to the bot and was edited
  * @param inlineQuery        Optional. New incoming inline query
  * @param chosenInlineResult Optional. The result of an inline query that was chosen by a user and sent to
  *                           their chat partner.
  * @param callbackQuery      Optional. New incoming callback query
  */
case class TelegramUpdate(id: Int,
                          message: Option[TelegramMessage],
                          editedMessage: Option[TelegramMessage],
                          inlineQuery: Option[TelegramInlineQuery],
                          chosenInlineResult: Option[TelegramChosenInlineResult],
                          callbackQuery: Option[TelegramCallbackQuery])

/**
  * A placeholder, currently holds no information.
  */
case class TelegramCallbackGame()

/**
  * This object represents one button of an inline keyboard. You must use exactly one of the optional fields.
  *
  * @param text                         Label text on the button
  * @param url                          Optional. HTTP url to be opened when button is pressed
  * @param callbackData                 Optional. Data to be sent in a callback query to the bot when button is pressed,
  *                                     1-64 bytes
  * @param switchInlineQuery            Optional. If set, pressing the button will prompt the user to select one of
  *                                     their chats, open that chat and insert the bot‘s username and the specified
  *                                     inline query in the input field. Can be empty, in which case just the bot’s
  *                                     username will be inserted.
  * @param switchInlineQueryCurrentChat Optional. If set, pressing the button will insert the bot‘s username and
  *                                     the specified inline query in the current chat's input field. Can be empty,
  *                                     in which case only the bot’s username will be inserted.
  * @param callbackGame                 Optional. Description of the game that will be launched when the user presses
  *                                     the button.
  */
case class TelegramInlineKeyboardButton(text: String,
                                        url: Option[String],
                                        callbackData: Option[String],
                                        switchInlineQuery: Option[String],
                                        switchInlineQueryCurrentChat: Option[String],
                                        callbackGame: Option[TelegramCallbackGame])

trait TelegramReplyMarkup

/**
  * This object represents an inline keyboard that appears right next to the message it belongs to.
  *
  * @param inlineKeyboard Array of button rows, each represented by an Array of InlineKeyboardButton objects
  */
case class TelegramInlineKeyboardMarkup(inlineKeyboard: List[List[TelegramInlineKeyboardButton]])
  extends TelegramReplyMarkup

/**
  * This object represents one button of the reply keyboard. For simple text buttons String can be used instead of
  * this object to specify text of the button. Optional fields are mutually exclusive.
  *
  * @param text            Text of the button. If none of the optional fields are used, it will be sent to the bot
  *                        as a message when the button is pressed
  * @param requestContact  Optional. If True, the user's phone number will be sent as a contact when the button is
  *                        pressed. Available in private chats only
  * @param requestLocation Optional. If True, the user's current location will be sent when the button is pressed.
  *                        Available in private chats only
  */
case class TelegramKeyboardButton(text: String, requestContact: Option[Boolean], requestLocation: Option[Boolean])

/**
  * This object represents a custom keyboard with reply options.
  *
  * @param keyboard        Array of button rows, each represented by an Array of KeyboardButton objects
  * @param resizeKeyboard  Optional. Requests clients to resize the keyboard vertically for optimal fit (e.g., make
  *                        the keyboard smaller if there are just two rows of buttons). Defaults to false, in which
  *                        case the custom keyboard is always of the same height as the app's standard keyboard.
  * @param oneTimeKeyboard Optional. Requests clients to hide the keyboard as soon as it's been used. The keyboard
  *                        will still be available, but clients will automatically display the usual letter-keyboard
  *                        in the chat – the user can press a special button in the input field to see the custom
  *                        keyboard again. Defaults to false.
  * @param selective       Optional. Use this parameter if you want to show the keyboard to specific users only.
  *                        Targets: 1) users that are @mentioned in the text of the Message object; 2) if the bot's
  *                        message is a reply (has reply_to_message_id), sender of the original message.
  */
case class TelegramReplyKeyboardMarkup(keyboard: List[List[TelegramKeyboardButton]],
                                       resizeKeyboard: Option[Boolean],
                                       oneTimeKeyboard: Option[Boolean],
                                       selective: Option[Boolean]) extends TelegramReplyMarkup

/**
  * Upon receiving a message with this object, Telegram clients will hide the current custom keyboard and display
  * the default letter-keyboard. By default, custom keyboards are displayed until a new keyboard is sent by a bot.
  * An exception is made for one-time keyboards that are hidden immediately after the user presses a button.
  *
  * @see TelegramReplyKeyboardMarkup
  * @param hideKeyboard Requests clients to hide the custom keyboard
  * @param selective    Optional. Use this parameter if you want to hide keyboard for specific users only.
  *                     Targets: 1) users that are @mentioned in the text of the Message object;
  *                     2) if the bot's message is a reply (has reply_to_message_id), sender of the original message.
  */
case class TelegramReplyKeyboardHide(hideKeyboard: Boolean, selective: Option[Boolean]) extends TelegramReplyMarkup

/**
  * Upon receiving a message with this object, Telegram clients will display a reply interface to the user (act as
  * if the user has selected the bot‘s message and tapped ’Reply'). This can be extremely useful if you want to
  * create user-friendly step-by-step interfaces without having to sacrifice privacy mode.
  *
  * @param forceReply Shows reply interface to the user, as if they manually selected the bot‘s message and
  *                   tapped ’Reply'
  * @param selective  Optional. Use this parameter if you want to force reply from specific users only.
  *                   Targets: 1) users that are @mentioned in the text of the Message object;
  *                   2) if the bot's message is a reply (has reply_to_message_id), sender of the original message.
  */
case class TelegramForceReply(forceReply: Boolean, selective: Option[Boolean]) extends TelegramReplyMarkup

/**
  * Send Message Payload.
  *
  * @param chatId                Unique identifier for the target chat or username of the target channel (in the
  *                              format @channelusername)
  * @param text                  Text of the message to be sent
  * @param parseMode             Optional. Send Markdown or HTML, if you want Telegram apps to show bold, italic,
  *                              fixed-width text or inline URLs in your bot's message.
  * @param disableWebPagePreview Optional. Disables link previews for links in this message
  * @param disableNotification   Optional. Sends the message silently. iOS users will not receive a notification,
  *                              Android users will receive a notification with no sound.
  * @param replyToMessageId      Optional. If the message is a reply, ID of the original message
  * @param replyMarkup           Optional. 	Additional interface options. A JSON-serialized object for an inline
  *                              keyboard, custom reply keyboard, instructions to hide reply keyboard or to force
  *                              a reply from the user.
  */
case class TelegramSendMessage(chatId: Int,
                               text: String,
                               parseMode: Option[String],
                               disableWebPagePreview: Option[Boolean],
                               disableNotification: Option[Boolean],
                               replyToMessageId: Option[Int],
                               replyMarkup: Option[TelegramReplyMarkup])

case class TelegramFailResult(status: Boolean, code: Int, description: String)

case class TelegramResult[T](status: Boolean, result: T)

trait TelegramJsonSupport extends DefaultJsonProtocol with SprayJsonSupport with JsonSupportUtils {

  implicit val telegramUserJsonFormat = jsonFormat(TelegramUser, "id", "first_name", "last_name", "username")
  implicit val telegramChatJsonFormat = jsonFormat(TelegramChat, "id", "type", "title", "username", "first_name", "last_name", "all_members_are_administrators")
  implicit val telegramMessageEntityJsonFormat = jsonFormat(TelegramMessageEntity, "type", "offset", "length", "url", "user")
  implicit val telegramPhotoSizeJsonFormat = jsonFormat(TelegramPhotoSize, "field_id", "width", "height", "file_size")
  implicit val telegramAudioJsonFormat = jsonFormat(TelegramAudio, "file_id", "duration", "performer", "title", "mime_type", "file_size")
  implicit val telegramDocumentJsonFormat = jsonFormat(TelegramDocument, "file_id", "thumb", "file_name", "mime_type", "file_size")
  implicit val telegramStickerJsonFormat = jsonFormat(TelegramSticker, "file_id", "width", "height", "thumb", "emoji", "file_size")
  implicit val telegramVideoJsonFormat = jsonFormat(TelegramVideo, "file_id", "width", "height", "duration", "thumb", "mime_type", "file_size")
  implicit val telegramVoiceJsonFormat = jsonFormat(TelegramVoice, "file_id", "duration", "mime_type", "file_size")
  implicit val telegramContactJsonFormat = jsonFormat(TelegramContact, "phone_number", "first_name", "last_name", "user_id")
  implicit val telegramLocationJsonFormat = jsonFormat2(TelegramLocation)
  implicit val telegramVenueJsonFormat = jsonFormat(TelegramVenue, "location", "title", "address", "foursquare_id")
  implicit val telegramAnimationJsonFormat = jsonFormat(TelegramAnimation, "file_id", "thumb", "file_name", "mime_type", "file_size")
  implicit val telegramGameJsonFormat = jsonFormat(TelegramGame, "title", "description", "photo", "text", "text_entities", "animation")

  // get around the fact that scala functions cannot accept more than 22 args
  implicit object telegramMessageJsonFormat extends RootJsonFormat[TelegramMessage] {

    def write(m: TelegramMessage) = objToJson(m)

    def read(value: JsValue) = TelegramMessage(
      value.extract[Int]('id),
      value.extract[TelegramUser]('from.?),
      value.extract[Int]('date),
      value.extract[TelegramChat]('chat),
      value.extract[TelegramUser]('forward_from.?),
      value.extract[TelegramChat]('forward_from_chat.?),
      value.extract[Int]('forward_date.?),
      value.extract[TelegramMessage]('reply_to_message.?),
      value.extract[Int]('edit_date.?),
      value.extract[String]('text.?),
      value.extract[List[TelegramMessageEntity]]('entities.?),
      value.extract[TelegramAudio]('audio.?),
      value.extract[TelegramDocument]('document.?),
      value.extract[TelegramGame]('game.?),
      value.extract[List[TelegramPhotoSize]]('photo.?),
      value.extract[TelegramSticker]('sticker.?),
      value.extract[TelegramVideo]('video.?),
      value.extract[TelegramVoice]('voice.?),
      value.extract[String]('caption.?),
      value.extract[TelegramContact]('contact.?),
      value.extract[TelegramLocation]('location.?),
      value.extract[TelegramVenue]('venue.?),
      value.extract[TelegramUser]('new_chat_member.?),
      value.extract[TelegramUser]('left_chat_member.?),
      value.extract[String]('new_chat_title.?),
      value.extract[List[TelegramPhotoSize]]('new_chat_photo.?),
      value.extract[Boolean]('delete_chat_photo.?),
      value.extract[Boolean]('group_chat_created.?),
      value.extract[Boolean]('supergroup_chat_created.?),
      value.extract[Boolean]('channel_chat_created.?),
      value.extract[Int]('migrate_to_chat_id.?),
      value.extract[Int]('migrate_from_chat_id.?),
      value.extract[TelegramMessage]('pinned_message.?)
    )

  }

  implicit val telegramUserProfilePhotosJsonFormat = jsonFormat(TelegramUserProfilePhotos, "total_count", "photos")
  implicit val telegramFileJsonFormat = jsonFormat(TelegramFile, "file_id", "file_size", "file_path")
  implicit val telegramInlineQueryJsonFormat = jsonFormat5(TelegramInlineQuery)
  implicit val telegramChosenInlineResultJsonFormat = jsonFormat(TelegramChosenInlineResult, "result_id", "from", "location", "inline_message_id", "query")
  implicit val telegramCallbackQueryJsonFormat = jsonFormat(TelegramCallbackQuery, "id", "from", "message", "inline_message_id", "chat_instance", "data", "game_short_name")
  implicit val telegramUpdateJsonFormat = jsonFormat(TelegramUpdate, "update_id", "message", "edited_message", "inline_query", "chosen_inline_query", "callback_query")
  implicit val telegramCallbackGameJsonFormat = jsonFormat0(TelegramCallbackGame)
  implicit val telegramInlineKeyboardButtonJsonFormat = jsonFormat(TelegramInlineKeyboardButton, "text", "url", "callback_data", "switch_inline_query", "switch_inline_query_current_chat", "callback_game")
  implicit val telegramInlineKeyboardMarkupJsonFormat = jsonFormat(TelegramInlineKeyboardMarkup, "inline_keyboard")
  implicit val telegramKeyboardButtonJsonFormat = jsonFormat(TelegramKeyboardButton, "text", "request_contact", "request_location")
  implicit val telegramReplyKeyboardMarkupJsonFormat = jsonFormat(TelegramReplyKeyboardMarkup, "keyboard", "resize_keyboard", "one_time_keyboard", "selective")
  implicit val telegramReplyKeyboardHideJsonFormat = jsonFormat(TelegramReplyKeyboardHide, "hide_keyboard", "selective")
  implicit val telegramForceReplyJsonFormat = jsonFormat(TelegramForceReply, "force_reply", "selective")

  implicit object telegramReplyMarkupJsonFormat extends RootJsonFormat[TelegramReplyMarkup] {

    def write(m: TelegramReplyMarkup) = m match {
      case i: TelegramInlineKeyboardMarkup => JsObject(
        "inline_keyboard" -> i.inlineKeyboard.toJson
      )
      case r: TelegramReplyKeyboardMarkup => objToJson(r)
      case h: TelegramReplyKeyboardHide => objToJson(h)
      case f: TelegramForceReply => objToJson(f)
    }

    def read(value: JsValue) =
      value.extract[List[List[TelegramInlineKeyboardButton]]]('inline_keyboard.?) match {
        case Some(inlineKeyboard) => TelegramInlineKeyboardMarkup(inlineKeyboard)
        case _ => value.extract[List[List[TelegramKeyboardButton]]]('keyboard.?) match {
          case Some(keyboard) => TelegramReplyKeyboardMarkup(
            keyboard,
            value.extract[Boolean]('resize_keyboard.?),
            value.extract[Boolean]('one_time_keyboard.?),
            value.extract[Boolean]('selective.?)
          )
          case _ => value.extract[Boolean]('hide_keyboard.?) match {
            case Some(hideKeyboard) => TelegramReplyKeyboardHide(
              hideKeyboard,
              value.extract[Boolean]('selective.?)
            )
            case _ => value.extract[Boolean]('force_reply.?) match {
              case Some(forceReply) => TelegramForceReply(
                forceReply,
                value.extract[Boolean]('selective.?)
              )
              case _ => throw DeserializationException("TelegramReplyMarkup expected")
            }
          }
        }
      }

  }

  implicit val telegramSendMessageJsonFormat = jsonFormat(TelegramSendMessage, "chat_id", "text", "parse_mode", "disable_web_page_preview", "disable_notification", "reply_to_message_id", "reply_markup")

  implicit val telegramFailResultJsonFormat = jsonFormat(TelegramFailResult, "ok", "error_code", "description")

  implicit def telegramResultJsonFormat[T: JsonFormat] = jsonFormat(TelegramResult.apply[T], "ok", "result")

  def getFields(v: Product) = v.getClass.getDeclaredFields.map(_.getName).zip(v.productIterator.to)

  def camelCaseToUnderscore(str: String) = CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, str)

  def objToJson(o: Product) = JsObject(
    getFields(o).foldLeft(Map.empty[String, JsValue]) {
      case (a, (k, Some(v: String))) => a + (camelCaseToUnderscore(k) -> JsString(v))
      case (a, (k, Some(v@(_: Int | _: BigInt | _: Long | _: Double | _: Float)))) => a + (camelCaseToUnderscore(k) -> JsNumber(v.toString))
      case (a, (k, Some(v: Boolean))) => a + (camelCaseToUnderscore(k) -> JsBoolean(v))
      case (a, (k, Some(v))) => a + (camelCaseToUnderscore(k) -> v.toJson)
      case (a, (_, None)) => a
      case (a, (k, v: String)) => a + (camelCaseToUnderscore(k) -> JsString(v))
      case (a, (k, v@(_: Int | _: BigInt | _: Long | _: Double | _: Float))) => a + (camelCaseToUnderscore(k) -> JsNumber(v.toString))
      case (a, (k, v: Boolean)) => a + (camelCaseToUnderscore(k) -> JsBoolean(v))
      case (a, (k, v)) => a + (camelCaseToUnderscore(k) -> v.toJson)
    }.toSeq: _*)

}