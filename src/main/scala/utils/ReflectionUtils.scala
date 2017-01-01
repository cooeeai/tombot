package utils

import scala.reflect.runtime.universe._

/**
  * Created by markmo on 20/12/2016.
  */
object ReflectionUtils {

  def classAccessors[T: TypeTag]: List[MethodSymbol] = typeOf[T].members.collect {
    case m: MethodSymbol if m.isCaseAccessor => m
  }.toList

}
