package services

import com.google.inject.Inject
import com.typesafe.config.Config
import spray.json._

/**
  * Created by markmo on 17/07/2016.
  */
class CatalogService @Inject()(config: Config) {

  val api = config.getString("api.host")

  def getItems = List(
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
          payload = JsString("Order for iPhone 6s 64GB Space Grey")
        ))
    ),
    Item(
      title = "iPhone 6s Plus 64GB Silver",
      subtitle = "5.5 inch (diagonal) Retina HD display",
      itemURL = "https://www.telstra.com.au/mobile-phones/mobiles-on-a-plan/iphone-6s",
      imageURL = s"$api/img/iphone-6s-plus-front-silver-400.jpg",
      actions = List(
        ItemPostbackAction(
          title = "Buy",
          payload = JsString("Order for iPhone 6s Plus 64GB Silver")
        )
      )
    )
  )

}

object ItemActionType extends Enumeration {
  val Link, Postback = Value
}

sealed trait ItemAction {
  val actionType: ItemActionType.Value
}

case class ItemLinkAction(title: String, url: String) {
  override val actionType: ItemActionType.Link
}

case class ItemPostbackAction(title: String, payload: JsValue) {
  override val actionType: ItemActionType.Postback
}

case class Item(title: String, subtitle: String, itemURL: String, imageURL: String, actions: List[ItemAction])
