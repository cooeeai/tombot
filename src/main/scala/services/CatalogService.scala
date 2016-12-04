package services

import com.google.inject.Inject
import com.typesafe.config.Config
import models.{Item, ItemLinkAction, ItemPostbackAction}
import spray.json._

/**
  * Created by markmo on 17/07/2016.
  */
class CatalogService @Inject()(config: Config) {

  val api = config.getString("api.host")

  def items = Map(
    "mobile" -> List(
      Item(
        title = "iPhone 6s 64GB Space Grey",
        subtitle = "4.7 inch (diagonal) Retina HD display",
        itemURL = "https://www.telstra.com.au/mobile-phones/mobiles-on-a-plan/iphone-6s",
        imageURL = s"$api/img/iphone-6s-front-spacegrey-400.jpg",
        actions = List(
          ItemLinkAction(
            title = "More info",
            url = "https://www.telstra.com.au/mobile-phones/mobiles-on-a-plan/iphone-6s"
          ),
          ItemPostbackAction(
            title = "Buy",
            payload = JsString("iPhone 6s 64GB Space Grey")
          ))
      ),
      Item(
        title = "iPhone 6s Plus 64GB Silver",
        subtitle = "5.5 inch (diagonal) Retina HD display",
        itemURL = "https://www.telstra.com.au/mobile-phones/mobiles-on-a-plan/iphone-6s",
        imageURL = s"$api/img/iphone-6s-plus-front-silver-400.jpg",
        actions = List(
          ItemLinkAction(
            title = "More info",
            url = "https://www.telstra.com.au/mobile-phones/mobiles-on-a-plan/iphone-6s"
          ),
          ItemPostbackAction(
            title = "Buy",
            payload = JsString("iPhone 6s Plus 64GB Silver")
          )
        )
      )
    )
  )

}
