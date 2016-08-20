package apis.jokes

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol

/**
  * Created by markmo on 20/08/2016.
  */
case class Joke(joke: String)

trait JokesJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val jokeJsonSupport = jsonFormat1(Joke)
}