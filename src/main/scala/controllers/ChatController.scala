package controllers

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import apis.facebookmessenger.{FacebookJsonSupport, FacebookPostbackButton, FacebookLinkButton, FacebookElement}
import chat.ChatRooms
import com.google.inject.Inject
import models.{ItemPostbackAction, ItemLinkAction, Item}
import services.{CatalogService, Conversation}
import spray.json._

/**
  * Created by markmo on 21/10/2016.
  */
class ChatController @Inject()(logger: LoggingAdapter,
                               conversationService: Conversation,
                               catalogService: CatalogService,
                               implicit val system: ActorSystem,
                               implicit val fm: Materializer) extends FacebookJsonSupport {

  val echoService: Flow[Message, Message, _] = Flow[Message] map {
    case TextMessage.Strict(text) => TextMessage("ECHO: " + text)
    case _ => TextMessage("Message type unsupported")
  }

  import apis.facebookmessenger.Builder._

  val testService: Flow[Message, Message, _] = Flow[Message] map {
    case TextMessage.Strict(text) =>
      val sender = "123"
      val items = catalogService.items("mobile")
      val elements = itemsToFacebookElements(items)
      val payload = (
        genericTemplate
          forSender sender
          withElements elements
          build()
        )
      TextMessage(payload.toJson.compactPrint)
    case _ =>
      TextMessage("Message type unsupported")
  }

  val routes =
    path("ws-echo") {
      get {
        handleWebSocketMessages(echoService)
      }
    } ~
    pathPrefix("ws-chat" / IntNumber) { chatId =>
      parameter('name) { username =>
        handleWebSocketMessages(testService)
        //handleWebSocketMessages(ChatRooms.findOrCreate(chatId).webSocketFlow(username))
      }
    }

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
