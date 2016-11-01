package utils

import scala.collection.mutable

/**
  * Created by markmo on 1/11/2016.
  */
trait Memoize {

  def memoize[I, O](f: I => O): collection.Map[I, O] = new mutable.HashMap[I, O]() { self =>
    override def apply(key: I) = self.synchronized(getOrElseUpdate(key, f(key)))
  }

}
