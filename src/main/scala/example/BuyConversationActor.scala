package example

import akka.actor._
import com.google.inject.Inject
import com.google.inject.assistedinject.Assisted
import engines.ComplexConversationActor.{ConversationContext, State}
import engines.{ComplexConversationActor, LookupBusImpl}
import models.Platform.Platform
import models.events._
import modules.akkaguice.NamedActor
import services._

/**
  * Created by markmo on 27/07/2016.
  */
class BuyConversationActor @Inject()(smallTalkService: SmallTalkService,
                                     catalogService: CatalogService,
                                     val bus: LookupBusImpl,
                                     @Assisted("defaultProvider") defaultProvider: ActorRef,
                                     @Assisted("historyActor") val historyActor: ActorRef)
  extends ComplexConversationActor {

  import BuyConversationActor._

  val initialData = ConversationContext(
    provider = defaultProvider,
    authenticated = false,
    postAction = None
  )

  startWith(Qualifying, initialData)

  when(Qualifying) {

    case Event(Qualify(platform, sender, productType, text), ctx: ConversationContext) =>
      productType match {
        case Some(t) =>
          catalogService.items.get(t) match {
            case Some(items) =>
              ctx.provider ! HeroCard(sender, items)
              historyActor ! Exchange(Some(text), "show product catalog")
              goto(Buying)
            case None =>
              // TODO
              // no matching items - system error
              shrug(platform, sender, text)
              stay
          }
        case None =>
          shrug(platform, sender, text)
          stay
      }
  }

  when(Buying) {

    case Event(Buy(platform, sender, productType), _) =>
      context.parent ! FillForm(sender, "purchase")
      stay

    case Event(EndFillForm(sender, slot), ctx: ConversationContext) =>
      ctx.provider ! ReceiptCard(sender, slot)
      goto(Qualifying)

  }

  whenUnhandled(handleEventDefault orElse {

    case Event(Reset, _) =>
      goto(Qualifying) using initialData

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

object BuyConversationActor extends NamedActor {

  override final val name = "BuyConversationActor"

  /**
    * The types of the factory method's parameters must be distinct. To use multiple parameters
    * of the same type, use a named `@Assisted` annotation to disambiguate the parameters. The
    * names must be applied to the factory method's parameters:
    */
  trait Factory {
    def apply(@Assisted("defaultProvider") defaultProvider: ActorRef,
              @Assisted("historyActor") historyActor: ActorRef): Actor
  }

  case object Qualifying extends State

  case object Buying extends State

  case class Qualify(platform: Platform, sender: String, productType: Option[String], text: String)
    extends PlatformAware// with Privileged

  case class Buy(platform: Platform, sender: String, productType: String) extends PlatformAware

}
