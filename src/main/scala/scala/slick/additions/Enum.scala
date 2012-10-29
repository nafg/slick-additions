package scala.slick
package additions

import lifted.{ BaseTypeMapper, MappedTypeMapper }
import jdbc.GetResult


trait Bitmaskable[A] {
  def bitmasked: A => Bitmasked
}

trait Bitmasked {
  type Value

  def bitFor: Value => Int

  def forBit: Int => Value

  def values: Iterable[Value]

  def longToSet: Long => Set[Value] = bm => values.toSeq.filter(v => 0 != (bm & (1 << bitFor(v)))).toSet

  def setToLong: Set[Value] => Long = _.foldLeft(0L){ (bm, v) => bm + (1L << bitFor(v)) }

  implicit lazy val enumTypeMapper: BaseTypeMapper[Value] =
    MappedTypeMapper.base[Value, Int](bitFor, forBit)
  implicit lazy val enumSetTypeMapper: BaseTypeMapper[Set[Value]] =
   MappedTypeMapper.base[Set[Value], Long](setToLong, longToSet)

  implicit lazy val getResult: GetResult[Value] = GetResult(r => forBit(r.nextInt))
  implicit lazy val getSetResult: GetResult[Set[Value]] = GetResult(r => longToSet(r.nextLong))
}

object Bitmaskable {
  implicit def enumeration[E <: Enumeration]: Bitmaskable[E] = new Bitmaskable[E] {
    def bitmasked = e => new Bitmasked {
      type Value = E#Value
      def bitFor = _.id
      def forBit = e apply _
      def values = e.values.toSeq
    }
  }
  implicit def enum[E <: Enum]: Bitmaskable[E] = new Bitmaskable[E] {
    def bitmasked = e => e
  }
}

/**
 * Mix this in to a subclass of `Enumeration`
 * to get an implicit `BaseTypeMapper` and `GetResult`
 * for `V` and `Set[V]`.
 */
trait BitmaskedEnumeration extends Bitmasked { this: Enumeration =>
  def bitFor = _.id
  def forBit = apply(_)
}

/**
 * An alternative to `Enumeration`, including an implicit `BaseTypeMapper` and `GetResult`
 * for `Value` and `Set[Value]`.
 */
trait Enum extends Bitmasked {
  type Value <: ValueBase
  trait ValueBase { this: Value =>
    /**
     * Convenience upcast
     */
    val value: Value = this
  }
  val values: Seq[Value]

  def bitFor: Value => Int = values.indexOf(_)

  def forBit = values(_)

}
