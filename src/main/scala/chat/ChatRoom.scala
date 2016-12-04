package chat

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.scaladsl._
import akka.stream.{FlowShape, OverflowStrategy}
import services.ConversationService

/**
  * Created by markmo on 22/10/2016.
  */
class ChatRoom(roomId: Int, conversationService: ConversationService, system: ActorSystem) {

  private[this] val chatRoomActor = system.actorOf(Props(classOf[ChatRoomActor], roomId, conversationService), s"chat$roomId")

  def webSocketFlow(user: String): Flow[Message, Message, _] =
    Flow.fromGraph(GraphDSL.create(Source.actorRef[ChatMessage](bufferSize = 5, OverflowStrategy.fail)) {
      implicit builder =>
        chatSource => // source provided as argument

          import GraphDSL.Implicits._

          // flow used as input - takes Message
          val fromWebSocket = builder.add(
            Flow[Message] collect {
              case TextMessage.Strict(text) => IncomingMessage(user, text)
            }
          )

          // flow used as output - returns Message
          val backToWebSocket = builder.add(
            Flow[ChatMessage] map {
              case ChatMessage(author, text) =>
                //TextMessage(s"[$author]: $text")
                TextMessage(text)
            }
          )

          // send messages to the actor - if send also UserLeft(user) before stream completes
          val chatActorSink = Sink.actorRef[ChatEvent](chatRoomActor, UserLeft(user))

          // merge both pipes
          val merge = builder.add(Merge[ChatEvent](2))

          // materialized value of actor sitting in room
          val actorAsSource = builder.materializedValue.map(actor => UserJoined(user, actor))

          // Message from web socket is converted into IncomingMessage and sent to each user in room
          fromWebSocket ~> merge.in(0)

          // if source actor just created, should be sent as UserJoined and registered as participant in room
          actorAsSource ~> merge.in(1)

          // merges both pipes above and forwards messages to the room represented by ChatRoomActor
          merge ~> chatActorSink

          // actor already in room, so each message from room is used as source and pushed back into web socket
          chatSource ~> backToWebSocket

          // expose ports
          FlowShape(fromWebSocket.in, backToWebSocket.out)
    })

  def sendMessage(message: ChatMessage): Unit = chatRoomActor ! message

}

object ChatRoom {

  def apply(roomId: Int, conversationService: ConversationService)(implicit system: ActorSystem) =
    new ChatRoom(roomId, conversationService, system)

}
