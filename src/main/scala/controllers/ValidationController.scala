package controllers

import akka.http.scaladsl.server.Directives._
import com.google.inject.Inject
import models.AddressJsonSupport
import services.AddressService

import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Created by markmo on 8/10/2016.
  */
class ValidationController @Inject()(addressService: AddressService) extends AddressJsonSupport {

  val routes =
    path("address") {
      get {
        parameters("q") { q =>
          complete {
            addressService.getAddress(q) map { response =>
              response.results.head.getAddress
            }
          }
        }
      }
    }

}
