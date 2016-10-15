package apis.jira

import java.util.{Map => JMap}

import net.oauth.OAuth.Parameter
import net.oauth.client.OAuthClient
import net.oauth.client.httpclient4.HttpClient4
import net.oauth.signature.RSA_SHA1
import net.oauth.{OAuth, OAuthAccessor, OAuthConsumer, OAuthServiceProvider}

import scala.collection.JavaConversions._
import scala.collection.mutable

/**
  * Created by markmo on 15/10/2016.
  */
class AtlassianOAuthClient(consumerKey: String, privateKey: String, baseURL: String, callback: String) {

  final val servletBaseURL = "/plugins/servlet"

  def getRequestToken: TokenSecretVerifierHolder =
    try {
      val client = new OAuthClient(new HttpClient4)
      val cb: List[OAuth.Parameter] = if (callback == null || callback.isEmpty) {
        Nil
      } else {
        List(new OAuth.Parameter(OAuth.OAUTH_CALLBACK, callback))
      }
      val message = client.getRequestTokenResponse(accessor, "POST", cb)
      TokenSecretVerifierHolder(
        token = accessor.requestToken,
        secret = accessor.tokenSecret,
        verifier = message.getParameter(OAuth.OAUTH_VERIFIER)
      )
    } catch {
      case e: Exception =>
        throw new RuntimeException("Failed to obtain request token", e)
    }

  def swapRequestTokenForAccessToken(requestToken: String, tokenSecret: String, oauthVerifier: String) =
    try {
      accessor.requestToken = requestToken
      accessor.tokenSecret = tokenSecret
      val client = new OAuthClient(new HttpClient4)
      val message = client.getAccessToken(accessor, "POST", List(new Parameter(OAuth.OAUTH_VERIFIER, oauthVerifier)))
      message.getToken
    } catch {
      case e: Exception =>
        throw new RuntimeException("Failed to swap request token with access token", e)
    }

  def makeAuthenticatedRequest(url: String, accessToken: String): String =
    try {
      accessor.accessToken = accessToken
      val client = new OAuthClient(new HttpClient4)
      val response = client.invoke(accessor, url, mutable.Set[JMap.Entry[_, _]]())
      response.readBodyAsString
    } catch {
      case e: Exception =>
        throw new RuntimeException("Failed to make an authenticated request", e)
    }

  lazy val accessor: OAuthAccessor = {
    val serviceProvider = new OAuthServiceProvider(getRequestTokenURL, getAuthorizeURL, getAccessTokenURL)
    val consumer = new OAuthConsumer(callback, consumerKey, null, serviceProvider)
    consumer.setProperty(RSA_SHA1.PRIVATE_KEY, privateKey)
    consumer.setProperty(OAuth.OAUTH_SIGNATURE_METHOD, OAuth.RSA_SHA1)
    new OAuthAccessor(consumer)
  }

  //  private def memoize[I, O](f: I => O): collection.Map[I, O] = new mutable.HashMap[I, O]() {
  //    self =>
  //    override def apply(key: I) = self.synchronized(getOrElseUpdate(key, f(key)))
  //  }

  private def getAccessTokenURL: String = baseURL + servletBaseURL + "/oauth/access-token"

  private def getRequestTokenURL: String = baseURL + servletBaseURL + "/oauth/request-token"

  def getAuthorizeURLForToken(token: String): String = getAuthorizeURL + "?oauth_token=" + token

  private def getAuthorizeURL: String = baseURL + servletBaseURL + "/oauth/authorize"

}

case class TokenSecretVerifierHolder(token: String, verifier: String, secret: String)
