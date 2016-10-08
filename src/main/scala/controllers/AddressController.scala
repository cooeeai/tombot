package controllers

import akka.http.scaladsl.server.Directives._

/**
  * Created by markmo on 8/10/2016.
  */
class AddressController {

  val routes =
    path("address") {
      get {
        parameters("q") { q =>
        }
      }
    }

}
