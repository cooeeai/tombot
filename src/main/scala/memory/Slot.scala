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
                caption: Option[String] = None,
                enum: Option[List[String]] = None,
                shortlist: Option[List[String]] = None,
                prompt: Option[String] = None) {

  def nextQuestion: Option[Question] = {

    def loop(slot: Slot): (Boolean, Option[Question]) = {
      val Slot(key, question, children, value, _, _, _, _, _, confirm, confirmed, _, enum, shortlist, prompt) = slot
      val select = enum.isDefined

      lazy val response =
        if (select) {
          val printedOptions = if (shortlist.isDefined) {
            printOptions(shortlist.get)
          } else {
            printOptions(enum.get)
          }
          val q = question.get + "\n\n" + printedOptions
          (true, Some(Question(key, q, confirmation = false, select, prompt)))
        } else {
          (true, Some(Question(key, question.get, confirmation = false, select, prompt)))
        }

      if (value.isEmpty) {
        if (children.isDefined) {
          val qs = children.get.map(child => loop(child))
          if (qs.forall(!_._1)) {
            // if all child slots have been filled then this parent slot is done
            if (confirm.isDefined && !confirmed) {
              (true, Some(Question(key, confirm.get + "\n" + slot.printValue, confirmation = true, select, prompt)))
            } else {
              (false, None)
            }

          } else if (question.isDefined && qs.forall(_._1)) {
            // if all child slots have questions (and therefore need to be filled),
            // and a question is defined at the parent slot (and therefore a
            // composite response can be given), then ask the next question from
            // this slot
            response

          } else {
            // if a composite response is not possible, or some, but not all,
            // child slots have already been answered, then ask the next question
            // from the first unanswered child slot
            qs.find(_._1).get
          }
        } else {
          if (question.isDefined) {
            // if no child slots then ask this question if defined
            response
          } else {
            if (confirm.isDefined && !confirmed) {
              (true, Some(Question(key, confirm.get + "\n" + slot.printValue, confirmation = true, select, prompt)))
            } else {
              (true, None)
            }
          }
        }
      } else {
        if (confirm.isDefined && !confirmed) {
          (true, Some(Question(key, confirm.get + "\n" + slot.printValue, confirmation = true, select, prompt)))
        } else {
          (false, None)
        }
      }
    }

    loop(this)._2
  }

  def numberQuestions: Int = {

    def loop(slot: Slot): Int = {
      val Slot(_, question, children, value, _, _, _, _, _, confirm, confirmed, _, _, _, _) = slot
      if (value.isEmpty) {
        if (children.isDefined) {
          val qs = children.get.map(child => loop(child))
          if (qs.forall(_ == 0)) {
            // if all child slots have been filled
            if (confirm.isDefined && !confirmed) 1 else 0

          } else if (question.isDefined && qs.forall(_ > 0)) {
            // if all child slots have questions (and therefore need to be filled),
            // and a question is defined at the parent slot (and therefore a
            // composite response can be given), then ask the next question from
            // this slot
            1

          } else {
            // if a composite response is not possible, or some, but not all,
            // child slots have already been answered, then ask the next question
            // from the first unanswered child slot
            qs.sum
          }
        } else {
          1
        }
      } else {
        if (confirm.isDefined && !confirmed) 1 else 0
      }
    }

    loop(this)
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
      Slot(
        key,
        question,
        children,
        value,
        validateFn,
        invalidMessage,
        parseApi,
        parseExpr,
        parseFn,
        confirm,
        confirmed = true,
        caption,
        enum,
        shortlist,
        prompt
      )
    } else if (children.isDefined) {
      val slots = children.get.map(_.confirmSlot(key))
      Slot(
        this.key,
        question,
        Some(slots),
        value,
        validateFn,
        invalidMessage,
        parseApi,
        parseExpr,
        parseFn,
        confirm,
        confirmed,
        caption,
        enum,
        shortlist,
        prompt
      )
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
      Slot(
        this.key,
        question,
        Some(slots),
        value = None,
        validateFn,
        invalidMessage,
        parseApi,
        parseExpr,
        parseFn,
        confirm,
        confirmed = false,
        caption,
        enum,
        shortlist,
        prompt
      )
    } else {
      Slot(
        this.key,
        question,
        children,
        value = None,
        validateFn,
        invalidMessage,
        parseApi,
        parseExpr,
        parseFn,
        confirm,
        confirmed = false,
        caption,
        enum,
        shortlist,
        prompt
      )
    }

  def emptySlot(key: String): Slot =
    if (this.key == key) {
      empty()
    } else if (children.isDefined) {
      val slots = children.get.map(_.emptySlot(key))
      Slot(
        this.key,
        question,
        Some(slots),
        value,
        validateFn,
        invalidMessage,
        parseApi,
        parseExpr,
        parseFn,
        confirm,
        confirmed,
        caption,
        enum,
        shortlist,
        prompt
      )
    } else {
      this
    }

  def updateSlot(key: String, slot: Slot): Slot =
    if (this.key == key) {
      slot
    } else if (children.isDefined) {
      val slots = children.get.map(_.updateSlot(key, slot))
      Slot(
        this.key,
        question,
        Some(slots),
        value,
        validateFn,
        invalidMessage,
        parseApi,
        parseExpr,
        parseFn,
        confirm,
        confirmed,
        caption,
        enum,
        shortlist,
        prompt
      )
    } else {
      this
    }

  def getValue[T](key: String): Option[T] =
    traverse(this).find(_.key == key) flatMap (_.value map (_.asInstanceOf[T]))

  private def traverse(slot: Slot): Stream[Slot] =
    slot #:: slot.children.map(_.foldLeft(Stream.empty[Slot]) {
      case (a, s) => a #::: traverse(s)
    }).getOrElse(Stream.empty[Slot])

  def answers: List[QA] = {
    def dfs(slot: Slot): List[QA] = {
      val Slot(_, question, children, value, _, _, _, _, _, _, _, _, _, _, _) = slot
      if (question.isDefined) {
        if (value.isDefined) {
          List(QA(question.get, value.get.toString))
        } else {
          Nil
        }
      } else if (children.isDefined) {
        children.get.flatMap(dfs)
      } else {
        Nil
      }
    }
    dfs(this)
  }

  def printAnswers: String =
    answers map {
      case QA(q, a) => s"? $q\n\n> $a"
    } mkString "\n"

  def printOptions(options: List[String]): String =
    options.zipWithIndex map {
      case (opt, i) => (i + 1) + ". " + opt
    } mkString "\n"

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

    def a(l: List[String]) = "[" + l.mkString(", ") + "]"

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
    if (enum.nonEmpty) {
      j += n + k("enum") + a(enum.get)
      n = n1
    }
    if (shortlist.nonEmpty) {
      j += n + k("shortlist") + a(shortlist.get)
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

case class Question(slotKey: String, question: String, confirmation: Boolean, select: Boolean, prompt: Option[String])

case class SlotError(key: String, message: String)

case class QA(question: String, answer: String)

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
