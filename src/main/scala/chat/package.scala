import scala.language.implicitConversions

/**
  * Created by markmo on 22/10/2016.
  */
package object chat {
  implicit def chatEventToChatMessage(event: IncomingMessage): ChatMessage = ChatMessage(event.sender, event.message)
}
