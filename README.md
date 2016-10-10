# tombot

A Bot Framework using Scala and akka-http.

The purpose of this project is to create a robust bot framework for the JVM and enterprise use. It aims to support
non-trivial use cases:
* Massive scalability
* Multi-platform conversations
* Complex transactions
* Deep personalisation
* Maintainable code base as bot capabilities grow
* Highly productive for commercial applications

I believe development for bots requires new design patterns and techniques, similar to how desktop and web application
development had to evolve to create mobile apps. It includes system and UX design techniques to make use of
natural language text and voice interfaces. It also involves combining rule-based and machine learning-based
program control. I hope to support efforts to define these new design patterns and techniques.

Includes Scala APIs for
* Facebook Messenger
* Skype
* Google NLP
* Google Maps
* Wit.ai

Integrates the following services
* Address Service using [Google Geocoding API](https://developers.google.com/maps/documentation/geocoding/start)
* Alchemy Keywords Service from [IBM Bluemix](http://www.ibm.com/watson/developercloud/alchemy-language.html)
* [Facebook Messenger API](https://developers.facebook.com/docs/messenger-platform)
* [Humour Service](https://github.com/KiaFathi/tambalAPI)
* Intent Parsing Service from [Wit.ai](https://wit.ai/)
* [Google NLP API](https://cloud.google.com/natural-language/docs/)
* [Skype API](https://docs.botframework.com/en-us/skype/getting-started)
* Small-talk API from [Houndify](https://www.houndify.com/)
* [Wolfram Alpha Knowledge API](http://www.wolframalpha.com/widgets/)
* Rules Service

### Bot Forms Framework

Lets face it, forms in any paradigm are cumbersome. One must validate input, use the appropriate UI control to
capture a given data type, and ensure require details are complete. In addition, within a bot interface, the
framework should:
* Ask only for information not known, and remember details for a later session
* Confirm some items even if known
* Process unstructured input and natural language
* Enable a composite response to fill in multiple required data elements, e.g. ask for a complete address instead of separate requests for street, city, state, postcode, etc.
* Handle sudden departures from script and left-field questions from the user
* Enable custom functions or API calls to be used to validate or parse input

Think of a form as a sub-conversational flow. Once an intent or goal requiring additional information is determined,
the bot should be able to hand-off to a sub-routine or specialised bot that navigates the user through the
information gathering process.

Form flows can be complex. Form example, when asking for address details, if no address information is known, then
the bot can ask for the full address. However, if city, postcode and state is already known, then the bot should
ask for the street only. Defining any multi-exchange bot conversation is a challenge. Every intent that has
information requirements in order to transact will pile on complexity unless there is built-in support for form
flows generally.
 
This framework allows declarative definition of forms - you define what information you need, and how to validate
and parse responses where required, the framework takes care of how to execute the flow.

#### Core Concepts

A Slot is an item of information. Slots may be organised hierarchically. For example, address is made up of
street, city, postcode, state and country. All child slots must be complete for the parent to be treated as
complete.

Slots may be filled with known details once the user is identified.

The Slot API is as follows:

* key - slot name
* question - optional if the slot has child slots, in which case questions will be asked at a lower level
* children - optional if the slot is at the lowest level, otherwise a list of child slots
* value - optional, may be pre-populated
* validateExpr - optional JavaScript function to validate user input
* validateFn - optional Scala code if the form is defined in Scala
* invalidMessage - optional response to invalid input if validateExpr or validateFn is present
* parseApi - optional URL to API used to validate the input
* parseExpr - optional JavaScript function to parse user input, or if parseApi is given, to convert the API response into the required format
* parseFn - optional Scala code if the form is defined in Scala
* confirm - optional response, which if present, is sent to the user to confirm the slot's details
* caption - optional label for a slot, shown when the parent slot is being confirmed

Functions/expressions have the following signatures:

validateExpr (JavaScript)

    (value: String) => Boolean (true if the input is valid)

validateFn (Scala)

    (value: String) => Boolean (true if the input is valid)
    
parseExpr (JavaScript) - parseApi not present

    (value: String) => JSObject (of key-values where key names correspond to keys of child slots)
    
parseExpr (JavaScript) - parseApi present

    (value: JSObject) => JSObject (of key-values where key names correspond to keys of child slots)
    
parseFn (Scala)

    (value: String) => Map[String, Any] (of key-values where key names correspond to keys of child slots)
    
parseApi - URL String of endpoint containing '%s' where the query string is to be interpolated

The API endpoint is expected to return a JSON response. A response of JSObject, where property names correspond
to keys of child slots, can be used directly. Otherwise, parseExpr can be used to translate the format.

An example form definition is as follows:

    purchase {
      question = "Please provide your full name as <first-name> <last-name>"
      name {
        firstName {
          question = "What is your first name?"
        }
        lastName {
          question = "What is your last name?"
        }
        parseExpr = """
        function (value) {
          var re = /(\S+)\s+(.*)/;
          var match = re.exec(value);
          return {
            firstName: match[1],
            lastName: match[2]
          };
        }
        """
      }
      phone {
        question = "What is your phone number?"
        confirm = "Is this number correct?"
      }
      address {
        question = "Please provide your address as <street> <city> <state> <postcode>"
        street1 {
          question = "What is your street as <street-number> <street-name> <street-type>"
        }
        city {
          question = "What is your city?"
        }
        state {
          question = "What is your state?"
        }
        postcode {
          question = "What is your postcode?"
        }
        country {
          question = "What is your country?"
        }
        parseApi = $address.api.url,
        parseExpr = """
        function (value) {
          return {
            street1: value.street_1,
            city: value.city,
            state: value.state,
            postcode: value.postal_code,
            country: value.country
          };
        }
        """
      }
    }


### Why Scala and Akka?

* Type-safe integrations with messaging providers

For example,

    val a = event.convertTo[FacebookAccountLinkingEvent]

    val sender = a.sender.id
    val recipient = a.recipient.id
    val status = a.accountLinking.status
    val authCode = a.accountLinking.authorizationCode.get

* Type-safe small DSLs

For example, akka-http routes:

    path("authorize") {
      get {
        parameters("redirect_uri", "account_linking_token") { (redirectURI, accountLinkingToken) =>
          ...
      }
    }

For example, building response cards:

    val payload = (
      loginCard
        usingApi api
        forSender sender
        withText "You need to authorize me"
        withButtonTitle "Connect"
        build()
      )

* Actors using the FSM (finite state machine) DSL to implement conversational state

For example,

    class ConversationActor extends Actor with FSM[State, Data] {

      startWith(Starting, Uninitialized)

      when(Qualifying) {

        case Event(Greet(sender, user), _) =>
          greet(sender, user)
          stay

        case Event(Respond(sender, text), _) =>
          provider.sendHeroCard(sender)
          goto(Buying)
      }

    }

    object ConversationActor extends NamedActor {

      // events
      case class Greet(sender: String, user: User)
      case class Respond(sender: String, text: String)

      sealed trait State
      case object Starting extends State
      case object Qualifying extends State
      case object Buying extends State

    }

* Performance and scalability
* Libraries to support large-scale system design

For example, dependency injection using Google Guice

    class FacebookController @Inject()(config: Config,
                                       logger: LoggingAdapter,
                                       intentService: IntentService) {}

* Functional programming to simplify concurrent system design

[![Deploy to Heroku](https://www.herokucdn.com/deploy/button.png)](https://heroku.com/deploy)

<img src="assets/messenger_session.jpg" title="Echo action" width="600" height="805">

Message thread
