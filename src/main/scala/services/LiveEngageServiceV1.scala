package services

import com.google.inject.Inject
import com.typesafe.config.Config
import org.scribe.builder.ServiceBuilder
import org.scribe.builder.api.DefaultApi10a
import org.scribe.model.{OAuthRequest, Response, Token, Verb}
import org.scribe.oauth.OAuthService

/**
  * Created by markmo on 21/10/2016.
  */
class LiveEngageServiceV1 @Inject()(config: Config) {

  val baseURL = config.getString("services.liveperson.chat.url")

  val apiKey = System.getenv("LIVEENGAGE_API_KEY")
  val apiSecret = System.getenv("LIVEENGAGE_API_SECRET")
  val accessToken = System.getenv("LIVEENGAGE_ACCESS_TOKEN")
  val accessTokenSecret = System.getenv("LIVEENGAGE_ACCESS_TOKEN_SECRET")
  val accountNumber = System.getenv("LIVEENGAGE_ACCOUNT_NUMBER")

  def getEngagementHistory: String = {
    val service: OAuthService =
      new ServiceBuilder()
        .provider(classOf[EHAPI])
        .apiKey(apiKey)
        .apiSecret(apiSecret)
        .build()

    val token = new Token(accessToken, accessTokenSecret)

    val request = new OAuthRequest(Verb.POST, s"$baseURL/interaction_history/api/account/$accountNumber/interactions/search?offset=0&limit=100")
    request.addHeader("Content-Type", "application/json")
    service.signRequest(token, request)
    val response: Response = request.send()
    response.getBody
  }

  // Chat APIs not currently supported
  // https://connect.liveperson.com/content/about-note-following-apis-are-not-currently-supported-liveengage-20
  // https://connect.liveperson.com/liveperson-developers/document-section/chat-api-javascript-usage-guide-version-3
  def sendTextMessage(text: String): String = {
    val service: OAuthService =
      new ServiceBuilder()
        .provider(classOf[EHAPI])
        .apiKey(apiKey)
        .apiSecret(apiSecret)
        .build()

    val token = new Token(accessToken, accessTokenSecret)

    val request = new OAuthRequest(Verb.POST, s"$baseURL/$accountNumber/chat/request")
    request.addHeader("Content-Type", "application/json")
    service.signRequest(token, request)
    val response: Response = request.send()
    response.getBody
  }

}

class EHAPI extends DefaultApi10a {

  override def getRequestTokenEndpoint() = null

  override def getAccessTokenEndpoint() = null

  override def getAuthorizationUrl(requestToken: Token) = null

}