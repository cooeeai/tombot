package chat

import akka.actor.ActorSystem
import services.ConversationService

/**
  * Created by markmo on 22/10/2016.
  */
object ChatRooms {

  var rooms: Map[Int, ChatRoom] = Map.empty[Int, ChatRoom]

  def findOrCreate(roomId: Int, conversationService: ConversationService)(implicit system: ActorSystem): ChatRoom =
    rooms.getOrElse(roomId, createNewChatRoom(roomId, conversationService))

  private def createNewChatRoom(roomId: Int, conversationService: ConversationService)(implicit system: ActorSystem): ChatRoom = {
    val room = ChatRoom(roomId, conversationService)
    rooms += roomId -> room
    room
  }

}
