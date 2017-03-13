package example

import akka.actor.{Actor, ActorRef}
import apis.facebookmessenger._
import com.google.inject.Inject
import com.google.inject.assistedinject.Assisted
import engines.ComplexConversationActor.{ConversationContext, State}
import engines.{ComplexConversationActor, LookupBusImpl}
import models.Platform._
import models.events._
import modules.akkaguice.NamedActor
import services.SmallTalkService
import spray.json.JsString

/**
  * Created by markmo on 24/02/2017.
  */
class RechargeConversationActor @Inject()(smallTalkService: SmallTalkService,
                                          val bus: LookupBusImpl,
                                          @Assisted("defaultProvider") defaultProvider: ActorRef,
                                          @Assisted("historyActor") val historyActor: ActorRef)
  extends ComplexConversationActor {

  import RechargeConversationActor._

  val initialData = ConversationContext(
    provider = defaultProvider,
    authenticated = false,
    postAction = None
  )

  startWith(Ready, initialData)

  when(Ready) {

    case Event(Recharge(platform, sender, text), ctx: ConversationContext) =>
      import Builder._
      val card = (
        genericTemplate
          forSender sender
          withTitle "Telstra 24x7 App"
          withSubtitle "Open the app, tap your pre-paid mobile number and tap ‘Recharge’."
          withImageURL "https://firebasestorage.googleapis.com/v0/b/bot-tools.appspot.com/o/-KdL0Tm7Q2CLqda7CbTt%2Ffull%2Ftelstra-carousel_24x7app.png?alt=media&token=f677120a-35cb-45af-acf1-8d43d5546f02"
          addButton FacebookPostbackButton("Open 24x7 App", JsString("https://www.telstra.com.au/my-account/telstra-24x7-app"))
          build()
        )
      ctx.provider ! Card(sender, card)
      historyActor ! Exchange(Some(text), "show recharge card")
      stay

  }

  whenUnhandled(handleEventDefault orElse {

    case Event(Reset, _) =>
      goto(Ready) using initialData

    case Event(TextResponse(platform, sender, text, _), ctx: ConversationContext) =>
      smallTalkService.getSmallTalkResponse(sender, text) match {
        case "Didn't get that!" => shrug(platform, sender, text)
        case reply => say(ctx.provider, historyActor, sender, text, reply)
      }
      stay

    case Event(ev, ctx: ConversationContext) =>
      log.warning("{} received unhandled request {} in state {}/{}", name, ev, stateName, ctx)
      stay

  })

  initialize()

}

object RechargeConversationActor extends NamedActor {

  override final val name = "RechargeConversationActor"

  /**
    * The types of the factory method's parameters must be distinct. To use multiple parameters
    * of the same type, use a named `@Assisted` annotation to disambiguate the parameters. The
    * names must be applied to the factory method's parameters:
    */
  trait Factory {
    def apply(@Assisted("defaultProvider") defaultProvider: ActorRef,
              @Assisted("historyActor") historyActor: ActorRef): Actor
  }

  case object Ready extends State

  case class Recharge(platform: Platform, sender: String, text: String) extends PlatformAware

}