package chat

import akka.actor.ActorSystem

/**
  * Created by markmo on 22/10/2016.
  */
object ChatRooms {

  var rooms: Map[Int, ChatRoom] = Map.empty[Int, ChatRoom]

  def findOrCreate(roomId: Int)(implicit system: ActorSystem): ChatRoom = rooms.getOrElse(roomId, createNewChatRoom(roomId))

  private def createNewChatRoom(roomId: Int)(implicit system: ActorSystem): ChatRoom = {
    val room = ChatRoom(roomId)
    rooms += roomId -> room
    room
  }

}
