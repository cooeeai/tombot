package services

import com.ibm.watson.developer_cloud.http.ServiceCallback
import com.ibm.watson.developer_cloud.personality_insights.v3.PersonalityInsights
import com.ibm.watson.developer_cloud.personality_insights.v3.model.{Profile, ProfileOptions}

import scala.concurrent.{Future, Promise}

/**
  * Created by markmo on 1/12/2016.
  */
class PersonalityInsightsService {

  val username = System.getenv("PERSONALITY_USERNAME")
  val password = System.getenv("PERSONALITY_PASSWORD")

  val service = new PersonalityInsights("2016-10-20", username, password)

  def getProfile(text: String): Future[Profile] = {
    val p = Promise[Profile]()
    val call = service.getProfile(text)
    call.enqueue(new ServiceCallback[Profile]() {

      override def onResponse(profile: Profile): Unit = {
        p success profile
      }

      override def onFailure(e: Exception): Unit = {
        p failure e
      }
    })
    p.future
  }

  def getProfile(options: ProfileOptions): Future[Profile] = {
    val p = Promise[Profile]()
    val call = service.getProfile(options)
    call.enqueue(new ServiceCallback[Profile]() {

      override def onResponse(profile: Profile): Unit = {
        p success profile
      }

      override def onFailure(e: Exception): Unit = {
        p failure e
      }
    })
    p.future
  }

}
