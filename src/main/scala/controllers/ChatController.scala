package controllers

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import chat.ChatRooms
import com.google.inject.Inject
import services.Conversation

/**
  * Created by markmo on 21/10/2016.
  */
class ChatController @Inject()(logger: LoggingAdapter,
                               conversationService: Conversation,
                               implicit val system: ActorSystem,
                               implicit val fm: Materializer) {

  val echoService: Flow[Message, Message, _] = Flow[Message] map {
    case TextMessage.Strict(text) => TextMessage("ECHO: " + text)
    case _ => TextMessage("Message type unsupported")
  }

  val routes =
    path("ws-echo") {
      get {
        handleWebSocketMessages(echoService)
      }
    } ~
    pathPrefix("ws-chat" / IntNumber) { chatId =>
      parameter('name) { username =>
        handleWebSocketMessages(ChatRooms.findOrCreate(chatId).webSocketFlow(username))
      }
    }

}
