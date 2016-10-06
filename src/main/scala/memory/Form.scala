package memory

import akka.event.LoggingAdapter
import com.google.inject.Inject
import services.AddressService
import spray.json._

import scala.concurrent._

/**
  * Created by markmo on 6/10/2016.
  */
class Form @Inject()(logger: LoggingAdapter,
                     addressService: AddressService) {

  import utils.LangUtils._

  val data =
    Slot(
      "purchase",
      children = Some(List(
        Slot(
          "name",
          Some("Please provide your full name as <first-name> <last-name>)?"),
          children = Some(List(
            Slot(
              "firstName",
              Some("What is your first name?")
            ),
            Slot(
              "lastName",
              Some("What is your last name?")
            ))
          ),
          parseFn = Some((value) => {
            val re = """(\S+)\s+(.*)""".r
            value match {
              case re(firstName, lastName) =>
                Map(
                  "firstName" -> firstName,
                  "lastName" -> lastName
                )
              case _ => Map()
            }
          })
        ),
        Slot(
          "phone",
          Some("What is your phone number?")
        ),
        Slot(
          "paymentMethod",
          children = Some(List(
            Slot(
              "cardholderName",
              Some("What is the card holder's name?")
            ),
            Slot(
              "cardNumber",
              Some("What is the card number?")
            ),
            Slot(
              "securityCode",
              Some("What is the security code for the card?")
            ),
            Slot(
              "expiryDate",
              Some("Please provide the expiry date as e.g. 01/18"),
              children = Some(List(
                Slot("expiryMonth"),
                Slot("expiryYear")
              ))
            )
          ))
        ),
        Slot(
          "address",
          Some("Please provide your address as <street> <city> <state> <postcode>"),
          children = Some(List(
            Slot(
              "street1",
              Some("What is your street as <street-number> <street-name> <street-type>")
            ),
            Slot(
              "city",
              Some("What is your city?")
            ),
            Slot(
              "state",
              Some("What is your state?")
            ),
            Slot(
              "postcode",
              Some("What is your postcode?")
            ),
            Slot(
              "country",
              Some("What is your country?")
            )
          )),
          parseFn = Some((value) => {
            lazy val f = addressService.getAddress(value)
            val f1 = f withTimeout new TimeoutException("future timed out")
            val f2 = f1 map { response =>
              logger.debug("received address lookup response:\n" + response.toJson.prettyPrint)
              if (response.results.nonEmpty) {
                val address = response.results.head.getAddress
                Map(
                  "street1" -> address.street1,
                  "city" -> address.city,
                  "state" -> address.state,
                  "postcode" -> address.postcode,
                  "country" -> address.country
                )
              } else {
                Map()
              }
            }
            f2.failed map { e =>
              logger.error(e.getMessage)
              Map()
            }
            Await.result(f2, timeout)
          })
        ),
        Slot(
          "coupon",
          Some("Please provide any coupon code, or reply with 'none'")
        )
      ))
    )

}
