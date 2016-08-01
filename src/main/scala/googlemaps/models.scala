package googlemaps

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import facebookmessenger.Address
import spray.json.DefaultJsonProtocol

/**
  * Created by markmo on 30/07/2016.
  */
case class AddressComponent(longName: String, shortName: String, types: List[String])

case class Location(lat: Double, lng: Double)

case class Viewport(northeast: Location, southwest: Location)

case class Geometry(location: Location, locationType: String, viewport: Viewport)

case class MapsResults(addressComponents: List[AddressComponent], formattedAddress: String, geometry: Geometry, placeId: String, types: List[String]) {

  def getComponent(name: String) =
    addressComponents.find(_.types.contains(name)) match {
      case Some(AddressComponent(longName, _, _)) => longName
      case None => ""
    }

  def getAddress = {
    var street1 = getComponent("street_address")
    if (street1.isEmpty) {
      street1 = getComponent("street_number") + " " + getComponent("route")
    }
    Address(
      street1 = street1,
      street2 = "",
      city = getComponent("locality"),
      postcode = getComponent("postal_code"),
      state = getComponent("administrative_area_level_1"),
      country = getComponent("country")
    )
  }
}

case class MapsResponse(results: List[MapsResults], status: String)

trait MapsJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val addressComponentJsonFormat = jsonFormat(AddressComponent, "long_name", "short_name", "types")
  implicit val locationJsonFormat = jsonFormat2(Location)
  implicit val viewportJsonFormat = jsonFormat2(Viewport)
  implicit val geometryJsonFormat = jsonFormat(Geometry, "location", "location_type", "viewport")
  implicit val mapsResultsJsonFormat = jsonFormat(MapsResults, "address_components", "formatted_address", "geometry", "place_id", "types")
  implicit val mapsResponseJsonFormat = jsonFormat2(MapsResponse)
}
