package chat

import akka.actor.{Actor, ActorLogging, ActorRef}
import apis.facebookmessenger._
import example.BuyConversationActor.Buy
import models._
import models.events._
import services.ConversationService
import spray.json._
import spray.json.lenses.JsonLenses._

/**
  * Created by markmo on 22/10/2016.
  */
class ChatRoomActor(roomId: Int, conversationService: ConversationService)
  extends Actor with ActorLogging with FacebookJsonSupport with AddressJsonSupport {

  import context.dispatcher

  var participants: Map[String, ActorRef] = Map.empty[String, ActorRef]

  def receive = {
    case UserJoined(name, actorRef) =>
      participants += name -> actorRef
      broadcast(SystemMessage(s"User $name joined channel..."))
      log.debug("User {} joined channel[{}]", name, roomId)
      conversationService.getConversationActor(name) map { ref =>
        ref ! SetProvider(Platform.Web, None, self, NullEvent, name, handleEventImmediately = true)
      }

    case UserLeft(name) =>
      log.debug("User {} left channel[{}]", name, roomId)
      broadcast(SystemMessage(s"User $name left channel[$roomId]"))
      participants -= name

    case msg@IncomingMessage(sender, message) =>
      log.debug("received {}", message)
      if (message.startsWith("{")) {
        // postback
        val json = message.parseJson
        val payload = json.extract[String]('payload)
        conversationService.converse(sender, Buy(Platform.Web, sender, payload))
      } else if (message.startsWith("postback:")) {
        val payload = message.substring(9)
        conversationService.converse(sender, Buy(Platform.Web, sender, payload))
      } else {
        conversationService.converse(sender, TextResponse(Platform.Web, sender, message))
      }
    //broadcast(msg)

    case TextMessage(sender, message) =>
      broadcast(ChatMessage(sender, message))

    case HeroCard(sender, items) =>
      import Builder._
      val elements = itemsToFacebookElements(items)
      val payload = (
        genericTemplate
          forSender sender
          withElements elements
          build()
        )
      val json = payload.toJson
      log.debug("sending payload:\n{}", json.prettyPrint)
      broadcast(ChatMessage(sender, json.compactPrint))

    case QuickReply(sender, text) =>
      import Builder._
      val payload = (
        quickReply
          forSender sender
          withText text
          build()
        )
      val json = payload.toJson
      log.debug("sending payload:\n{}", json.prettyPrint)
      broadcast(ChatMessage(sender, json.compactPrint))

    case AddressCard(sender, address) =>
      val response = AddressResponse(address, address.toString)
      broadcast(ChatMessage(sender, response.toJson.compactPrint))
  }

  def broadcast(message: ChatMessage): Unit = participants.values.foreach(_ ! message)

  private def itemsToFacebookElements(items: List[Item]): List[FacebookElement] =
    items map { item =>
      FacebookElement(
        title = item.title,
        subtitle = item.subtitle,
        itemURL = item.itemURL,
        imageURL = item.imageURL,
        buttons = item.actions map {
          case ItemLinkAction(title, url) => FacebookLinkButton(title, url)
          case ItemPostbackAction(title, payload) => FacebookPostbackButton(title, payload)
        }
      )
    }

}
