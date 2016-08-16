package memory

import apis.facebookmessenger.FacebookAddress

/**
  * Created by markmo on 30/07/2016.
  */
object models {

  val actions = Map(
    'purchase -> Map(
      'name -> None,
      'paymentMethod -> Map(
        'cardholderName -> None,
        'cardNumber -> None,
        'expiryDate -> None
      ),
      'address -> FacebookAddress
    )
  )

}