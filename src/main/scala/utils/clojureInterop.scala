package utils

import java.io.StringWriter
import java.util.{List => JList, Map => JMap, Set => JSet}

import clojure.java.api.Clojure
import clojure.lang._

import spray.json._

import scala.collection.JavaConversions._

/**
  * Created by markmo on 13/09/2016.
  */
object ClojureInterop {

  type Expr = Any

  sealed trait CExpr {

    def value: Expr

    def metadata: Map[Expr, Expr]

    override def equals(other: Any) = other match {
      case that: CExpr => this.value == that.value
    }
  }

  // CNumber, CString, CBoolean, CMap, etc. can be derived from this to add metadata

  case class CKeyword(ns: String, name: String, meta: Map[Expr, Expr] = Map()) extends CExpr {

    def metadata = meta

    override def value = toString

    def getName = name

    override def toString =
      if (ns == null || ns.isEmpty) s":$name" else s":$ns/$name"

  }

  case class CSymbol(ns: String, name: String, meta: Map[Expr, Expr] = Map()) extends CExpr {

    def metadata = meta

    override def value = toString

    override def toString =
      if (ns == null || ns.isEmpty) name else s"$ns/$name"

  }

  // converts clojure String into a scala data structure
  def readCString(v: String): Expr = clojureToScala(RT.readString(v))

  // converts a scala data structure into a clojure String
  def writeExpr(expr: Expr): String = {
    val prStr: IFn = Clojure.`var`("clojure.core", "pr-str")
    prStr.invoke(scalaToClojure(expr)).asInstanceOf[String]
  }

  def clojureToScala(expr: Expr): Any = expr match {
    case _: IPersistentVector => expr.asInstanceOf[JList[Expr]].toVector.map(subexpr => clojureToScala(subexpr))
    case _: IPersistentList => expr.asInstanceOf[JList[Expr]].toList.map(subexpr => clojureToScala(subexpr))
    case _: IPersistentMap => expr.asInstanceOf[JMap[Expr, Expr]].toMap.map { case (k, v) => (clojureToScala(k), clojureToScala(v)) }
    case _: IPersistentSet => expr.asInstanceOf[JSet[Expr]].toSet.asInstanceOf[Set[Expr]].map(subexpr => clojureToScala(subexpr))
    case _: JList[Expr @unchecked] => expr.asInstanceOf[JList[Expr]].toList.map(subexpr => clojureToScala(subexpr))
    case v: Number => v
    case v: String => v
    case v: Boolean => v
    case null => null
    case v: Keyword => CKeyword(v.getNamespace, v.getName)
    case v: Symbol => CSymbol(v.getNamespace, v.getName)
    case _ => throw new Exception(s"expr=$expr (${expr.getClass}) is not Iterable")
  }

  def scalaToClojure(expr: Expr): Any = expr match {
    case CKeyword(ns, name, _) => Keyword.intern(ns, name)
    case CSymbol(ns, name, _) => Symbol.intern(ns, name)
    case m: Map[Expr @unchecked, Expr @unchecked] => PersistentHashMap.create(m.map { case (k, v) => (scalaToClojure(k), scalaToClojure(v)) })
    case s: Set[Expr @unchecked] => PersistentHashSet.create(s.map(subexpr => scalaToClojure(subexpr)).toList)
    case v: Vector[Expr @unchecked] => PersistentVector.create(v.map(subexpr => scalaToClojure(subexpr)))
    case l: List[Expr @unchecked] => PersistentList.create(l.map(subexpr => scalaToClojure(subexpr)))
    case _ => expr
  }

  // binding of the clojure 'pprint' function in scala
  val pprint = {
    val require = Clojure.`var`("clojure.core", "require")
    require.invoke(Clojure.read("clojure.pprint"))
    Clojure.`var`("clojure.pprint", "pprint")
  }

  // example of how to call the clojure 'pprint' function in scala
  def prettyPrintScalaDataStructureAsClojure(data: Any): String = {
    val writer = new StringWriter()
    pprint.invoke(scalaToClojure(data), writer)
    writer.toString
  }

}