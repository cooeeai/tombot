package controllers

import java.util.Calendar

import akka.event.LoggingAdapter
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import com.google.inject.Inject
import com.ibm.watson.developer_cloud.personality_insights.v3.model.{ContentItem, ProfileOptions}
import com.typesafe.config.Config
import services.{MyRedisClient, PersonalityInsightsService}

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Created by markmo on 2/12/2016.
  */
class PersonalityInsightsController @Inject()(config: Config,
                                              logger: LoggingAdapter,
                                              redis: MyRedisClient,
                                              service: PersonalityInsightsService) {

  import StatusCodes._

  val sampleText = config.getString("sample-text")

  val sampleSize = config.getInt("personality-max-sample-lines")

  val routes =
    pathPrefix("personality") {
      path(Segment) { userId =>
        logger.debug("personality endpoint called with userId [{}]", userId)
//        if (redis.isConnected) {
//          logger.debug("redis connected")
          complete {
            //            for {
            //              history <- redis.lrange(userId, 0, sampleSize)
            //            } yield {
            //              val created = Calendar.getInstance().getTime
            //              val contentItems = history.filter(_.startsWith("req:")).zipWithIndex map {
            //                case (line, i) =>
            //                  val item = new ContentItem()
            //                  item.setContent(line.toString.substring(4))
            //                  item.setContentType("text/plain")
            //                  item.setCreated(created)
            //                  item.setId(i.toString)
            //                  item.setLanguage("en")
            //                  item
            //              }
            //              val options =
            //                new ProfileOptions.Builder()
            //                  .contentItems(contentItems.toList)
            //                  .build()
            //              service.getProfile(options).map(_.toString)
            //            }
            val created = Calendar.getInstance().getTime
            val contentItems = for {
              (line, i) <- sampleText.split("\n").zipWithIndex
            } yield {
              val item = new ContentItem()
              item.setContent(line.toString.substring(4))
              item.setContentType("text/plain")
              item.setCreated(created)
              item.setId(i.toString)
              item.setLanguage("en")
              item
            }
            val options =
              new ProfileOptions.Builder()
                .contentItems(contentItems.toList)
                .consumptionPreferences(true)
                .build()
            service.getProfile(options).map(_.toString)
          }
//        } else {
//          complete(NotFound)
//        }
      }
    }

}
