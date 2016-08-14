package services

import com.google.inject.Inject
import com.typesafe.config.Config
import apis.facebookmessenger.ReceiptElement

/**
  * Created by markmo on 20/07/2016.
  */
class PaymentService @Inject()(config: Config) {

  val api = config.getString("api.host")

  def getElements =
    ReceiptElement(
      title = "iPhone 6s 64GB Space Grey",
      subtitle = "4.7 inch (diagonal) Retina HD display",
      quantity = 1,
      price = BigDecimal("1047.00"),
      currency = "AUD",
      imageURL = s"$api/img/iphone-6s-front-spacegrey-400-sq.jpg"
    ) :: Nil

}
