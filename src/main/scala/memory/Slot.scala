package memory

import humanize.Humanize._

/**
  * Created by markmo on 30/07/2016.
  */
case class Slot(key: String,
                question: Option[String] = None,
                children: Option[List[Slot]] = None,
                value: Option[Any] = None,
                validateFn: Option[(String) => Boolean] = None,
                invalidMessage: Option[String] = None,
                parseApi: Option[String] = None,
                parseExpr: Option[String] = None,
                parseFn: Option[(String) => Map[String, Any]] = None,
                confirm: Option[String] = None,
                confirmed: Boolean = false,
                caption: Option[String] = None) {

  def nextQuestion: Option[Question] = {

    def loop(slot: Slot): (Boolean, Option[Question]) = {
      val Slot(key, question, children, value, _, _, _, _, _, confirm, confirmed, _) = slot
      if (value.isEmpty) {
        if (children.isDefined) {
          val qs = children.get.map(child => loop(child))
          if (qs.forall(!_._1)) {
            // if all child slots have been filled then this parent slot is done
            if (confirm.isDefined && !confirmed) {
              (true, Some(Question(key, confirm.get + "\n" + slot.printValue, confirmation = true)))
            } else {
              (false, None)
            }

          } else if (question.isDefined && qs.forall(_._1)) {
            // if all child slots have questions (and therefore need to be filled),
            // and a question is defined at the parent slot (and therefore a
            // composite response can be given), then ask the next question from
            // this slot
            (true, Some(Question(key, question.get, confirmation = false)))

          } else {
            // if a composite response is not possible, or some, but not all,
            // child slots have already been answered, then ask the next question
            // from the first unanswered child slot
            qs.find(_._1).get
          }
        } else {
          if (question.isDefined) {
            // if no child slots then ask this question if defined
            (true, Some(Question(key, question.get, confirmation = false)))
          } else {
            if (confirm.isDefined && !confirmed) {
              (true, Some(Question(key, confirm.get + "\n" + slot.printValue, confirmation = true)))
            } else {
              (true, None)
            }
          }
        }
      } else {
        if (confirm.isDefined && !confirmed) {
          (true, Some(Question(key, confirm.get + "\n" + slot.printValue, confirmation = true)))
        } else {
          (false, None)
        }
      }
    }

    loop(this)._2
  }

  def printValue: String =
    if (children.isDefined) {
      children.get.map(_.printValue).mkString("\n")
    } else if (value.isDefined) {
      val label = caption.getOrElse(titleize(decamelize(key)))
      s"$label: ${value.get.toString}"
    } else {
      ""
    }

  def confirmSlot(key: String): Slot =
    if (this.key == key) {
      Slot(key, question, children, value, validateFn, invalidMessage, parseApi, parseExpr, parseFn, confirm, confirmed = true, caption)
    } else if (children.isDefined) {
      val slots = children.get.map(_.confirmSlot(key))
      Slot(this.key, question, Some(slots), value, validateFn, invalidMessage, parseApi, parseExpr, parseFn, confirm, confirmed, caption)
    } else {
      this
    }

  def findSlot(key: String): Option[Slot] =
    if (this.key == key) {
      Some(this)
    } else if (children.isDefined) {
      children.get.find(_.findSlot(key).isDefined)
    } else {
      None
    }

  def empty(): Slot =
    if (children.isDefined) {
      val slots = children.get.map(_.empty())
      Slot(this.key, question, Some(slots), None, validateFn, invalidMessage, parseApi, parseExpr, parseFn, confirm, confirmed, caption)
    } else {
      Slot(this.key, question, children, None, validateFn, invalidMessage, parseApi, parseExpr, parseFn, confirm, confirmed, caption)
    }

  def emptySlot(key: String): Slot =
    if (this.key == key) {
      empty()
    } else if (children.isDefined) {
      val slots = children.get.map(_.emptySlot(key))
      Slot(this.key, question, Some(slots), value, validateFn, invalidMessage, parseApi, parseExpr, parseFn, confirm, confirmed, caption)
    } else {
      this
    }

  def getValue(key: String): Option[Any] =
    if (this.key == key) {
      value
    } else if (children.isDefined) {
      children.get.map(_.getValue(key)).find(_.isDefined) match {
        case Some(x) => x
        case None => None
      }
    } else {
      None
    }

  def getString(key: String): String = getValue(key).get.toString

  def toJson(level: Int = 1): String = {
    val i = "  "
    val t = i * level
    val b = "\n"
    val s = "{"
    var n = b + t
    val n1 = "," + b + t

    def q(v: String) = "\"" + v + "\""

    def k(v: String) = q(v) + ": "

    var j = k(key) + s
    if (question.nonEmpty) {
      j += n + k("question") + q(question.get)
      n = n1
    }
    if (value.nonEmpty) {
      j += n + k("value") + q(value.get.toString)
      n = n1
    }
    if (invalidMessage.nonEmpty) {
      j += n + k("invalidMessage") + q(invalidMessage.get.toString)
      n = n1
    }
    if (confirm.nonEmpty) {
      j += n + k("confirm") + q(confirm.get.toString)
      n = n1
    }
    if (caption.nonEmpty) {
      j += n + k("caption") + q(caption.get.toString)
      n = n1
    }
    if (parseApi.nonEmpty) {
      j += n + k("parseApi") + q(parseApi.get.toString)
      n = n1
    }
    if (children.nonEmpty) {
      j += n + children.get.map(_.toJson(level + 1)).mkString(n)
    }
    j + b + (i * (level - 1)) + "}"
  }

  override def toString: String = toJson(1)

}

case class Question(slotKey: String, question: String, confirmation: Boolean)

case class SlotError(key: String, message: String)

/*
object Slot {

  def create(key: String): Slot = {
    val config = ConfigFactory.load(s"forms/$key.conf")
    val root = config.getObject(key)

    import utils.ConfigOptional._

    def loop(key: String, value: ConfigValue): Slot = value match {
      case obj: ConfigObject =>
        val conf = obj.toConfig
        val dataType = conf.getString("type").toLowerCase
        val children = if (dataType == "object") {
          val slots = obj filterNot {
            case (k, _) => Set("type", "question") contains k
          } map {
            case (k, v) => loop(k, v)
          }
          Some(slots.toList)
        } else {
          None
        }

        Slot(
          dataType = dataType,
          key = key,
          question = conf.getOptionalString("question"),
          children = children,
          value = None
        )
    }

    loop(key, root)
  }

}*/
