package services

/**
  * Created by markmo on 19/09/2016.
  */
trait Conversation {

  def converse(sender: String, message: Any): Unit

}
