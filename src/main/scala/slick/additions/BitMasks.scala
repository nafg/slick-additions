package slick
package additions

import scala.reflect.ClassTag

import slick.jdbc.GetResult
import slick.relational.RelationalProfile


trait BitMasks {
  this: RelationalProfile =>

  import api._


  trait Bitmaskable[A] {
    def bitmasked: A => Bitmasked
  }

  trait Bitmasked {
    type Value

    implicit def classTag: ClassTag[Value]

    def bitFor: Value => Int

    def forBit: Int => Value

    def values: Iterable[Value]

    def longToSet: Long => Set[Value] = bm => values.toSeq.filter(v => 0 != (bm & (1 << bitFor(v)))).toSet

    def setToLong: Set[Value] => Long = _.foldLeft(0L) { (bm, v) => bm + (1L << bitFor(v)) }

    implicit val enumTypeMapper = MappedColumnType.base[Value, Int](bitFor, forBit)

    implicit lazy val enumSetTypeMapper = MappedColumnType.base[Set[Value], Long](setToLong, longToSet)

    implicit lazy val getResult: GetResult[Value] = GetResult(r => forBit(r.nextInt))
    implicit lazy val getSetResult: GetResult[Set[Value]] = GetResult(r => longToSet(r.nextLong))
  }

  object Bitmaskable {
    implicit def enumeration[E <: Enumeration : ClassTag]: Bitmaskable[E] = new Bitmaskable[E] {
      def bitmasked = e => new Bitmasked {
        def classTag = implicitly[ClassTag[E#Value]]
        type Value = E#Value
        def bitFor = _.id
        def forBit = e.apply
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
  trait BitmaskedEnumeration extends Bitmasked {
    this: Enumeration =>
    def bitFor = _.id
    def forBit = apply
  }

  /**
   * An alternative to `Enumeration`
   */
  trait Enum extends Bitmasked {
    type Value <: ValueBase
    trait ValueBase {
      this: Value =>
      /**
       * Convenience upcast
       */
      val value: Value = this
    }

    def valueBits: Map[Int, Value]

    def values = valueBits.toSeq.sortBy(_._1).map(_._2)

    def bitFor: Value => Int =
      v => valueBits find (_._2 == v) map (_._1) getOrElse sys.error(s"No value $v in Enum $this")

    def forBit = valueBits(_)
  }
}
