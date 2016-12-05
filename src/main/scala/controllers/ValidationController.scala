package controllers

import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives._
import com.google.inject.Inject
import models.AddressJsonSupport
import services.AddressService
import spray.json._

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
              HttpEntity(ContentTypes.`application/json`,
                response.results.head.getAddress.toJson.compactPrint)
            }
          }
        }
      }
    }

}
