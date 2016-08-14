package models

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol

/**
  * Created by markmo on 7/08/2016.
  */
case class LoginForm(username: String, password: String, redirectURI: String, successURI: String)

trait LoginFormJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val loginFormJsonFormat = jsonFormat(LoginForm, "username", "password", "redirect-uri", "redirect-success-uri")
}