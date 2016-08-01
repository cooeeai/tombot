package memory

/**
  * Created by markmo on 30/07/2016.
  */
object models {

  val purchase = Map(
    'name -> None,
    'paymentMethod -> Map(
      'cardholderName -> None,
      'cardNumber -> None,
      'expiryDate -> None
    ),
    'address -> None
  )

}