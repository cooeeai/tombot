import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive0, Route}

/**
  * Created by markmo on 4/12/2016.
  */
trait CorsSupport {

  //this directive adds access control headers to normal responses
  private def addAccessControlHeaders: Directive0 = {
    respondWithHeaders(
      //`Access-Control-Allow-Origin`(HttpOrigin("https", Host("desolate-mesa-84759.herokuapp.com"))),
      `Access-Control-Allow-Origin`(HttpOrigin("http", Host("localhost", 3000))),
      `Access-Control-Allow-Credentials`(true),
      `Access-Control-Allow-Headers`("Authorization", "Content-Type", "X-Requested-With", "X-IBM-Client-Id", "X-IBM-Client-Secret", "X-Request-ID")
    )
  }

  //this handles preflight OPTIONS requests
  private def preflightRequestHandler: Route = options {
    complete(HttpResponse(StatusCodes.OK).withHeaders(`Access-Control-Allow-Methods`(OPTIONS, POST, PUT, GET, DELETE)))
  }

  def corsHandler(r: Route) = addAccessControlHeaders {
    preflightRequestHandler ~ r
  }
}
