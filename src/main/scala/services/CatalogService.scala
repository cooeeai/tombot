package services

import com.google.inject.Inject
import com.typesafe.config.Config
import apis.facebookmessenger.{Element, LinkButton, PostbackButton}
import spray.json.JsString

/**
  * Created by markmo on 17/07/2016.
  */
class CatalogService @Inject()(config: Config) {

  val api = config.getString("api.host")

  def getElements =
    Element(
      title = "iPhone 6s 64GB Space Grey",
      subtitle = "4.7 inch (diagonal) Retina HD display",
      itemURL = "https://www.telstra.com.au/mobile-phones/mobiles-on-a-plan/iphone-6s",
      imageURL = s"$api/img/iphone-6s-front-spacegrey-400.jpg",
      buttons = List(
        LinkButton(
          title = "More info",
          url = "https://www.telstra.com.au/mobile-phones/mobiles-on-a-plan/iphone-6s"
        ),
        PostbackButton(
          title = "Buy",
          payload = JsString("Order for iPhone 6s 64GB Space Grey")
        ))
    ) ::
    Element(
      title = "iPhone 6s Plus 64GB Silver",
      subtitle = "5.5 inch (diagonal) Retina HD display",
      itemURL = "https://www.telstra.com.au/mobile-phones/mobiles-on-a-plan/iphone-6s",
      imageURL = s"$api/img/iphone-6s-plus-front-silver-400.jpg",
      buttons =
        PostbackButton(
          title = "Buy",
          payload = JsString("Order for iPhone 6s Plus 64GB Silver")
        ) :: Nil
    ) :: Nil

}
