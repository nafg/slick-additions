package scala.slick
package additions

/**
 * A `Lookup` is a wrapper for a value that can be lazily computed.
 * Once it is computed, its entity is cached and
 * does not need to be computed again.
 * It's different than other lazy computation wrappers
 * in that the computation has access to a typed parameter.
 */
abstract class Lookup[A, Param] {
  @volatile private var _cached = Option.empty[A]

  protected def cached_=(x: Option[A]) = _cached = x

  /**
   * Evaluate the computation, whether or not there is a value cached.
   * Should not cache its result.
   * @return the result of the computation
   */
  protected def compute(implicit param: Param): A

  /**
   * Force the cache to be updaed by recomputing the value
   */
  def recompute(implicit param: Param) {
    cached = Some(compute)
  }

  /**
   * @return the possibly cached value
   */
  def cached: Option[A] = _cached

  /**
   * Return the value.
   * If it hasn't been computed yet, compute it and cache the result.
   * @return the cached value
   * @example {{{ myLookup() }}}
   */
  def apply()(implicit param: Param): A = {
    val ret = cached orElse Some(compute)
    cached = ret
    ret.get
  }
}

object IdGenerator {
  private val ai = new java.util.concurrent.atomic.AtomicInteger
  def next = ai.getAndIncrement
}

class Handle[+A](val value: A, val id: Int = IdGenerator.next) {
  def updated[B >: A](nv: B) = new Handle[B](nv, id)
  def map[B >: A](f: A => B) = updated(f(value))
  override def toString = s"Handle(id = $id, value = $value)"
}

/**
 * Wraps a `Seq` with the ability
 * to create modified versions of itself that remember the
 * modifications on which they are based.
 */
trait DiffSeq[A, +Self <: DiffSeq[A, Self]] { this: Self =>

  /**
   * The initial sequence of items
   */
  def initialItems: Seq[Handle[A]]
  /**
   * The current sequence of items
   */
  def currentItems: Seq[Handle[A]] = initialItems

  /**
   * Items added since
   */
  final def newItems = currentItems filterNot { r => initialItems.exists(_.id == r.id) }
  /**
   * Items removed since
   */
  final def removedItems = initialItems filterNot { r => currentItems.exists(_.id == r.id) }

  /**
   * Items replaced since
   */
  final def replacedItems = currentItems flatMap { c =>
    initialItems.find(i => i.id == c.id && (i.value != c.value)).map(i => (i, c))
  }

  protected def copy(items: Seq[Handle[A]]): Self

  /**
   * Append an item
   */
  def +(x: A)             = copy(currentItems :+ new Handle[A](x))
  /**
   * Append items
   */
  def ++(xs: Iterable[A]) = copy(currentItems ++ xs.map{ new Handle[A](_) })
  /**
   * Remove an item
   */
  def -(x: Handle[A])     = copy(currentItems filterNot { _.id == x.id })
  /**
   * Remove an item
   */
  def -(x: A)             = copy(currentItems filterNot { _.value == x })
  /**
   * Apply a transformation to the items
   */
  def map(f: A => A)      = copy(currentItems map (_ map f))
  /**
   * Replace an item.
   * @example {{{ mySeqLookup.updated(oldHandle, newItem) }}}
   */
  def updated(old: Handle[A], nw: A) = copy(currentItems map { case ref if old.id == ref.id => ref updated nw;  case x => x })
  /**
   * Replace an item by passing it through a function.
   * @example {{{ mySeqLookup.updated(oldHandle, x => f(x)) }}}
   */
  def transform(old: Handle[A], f: A => A) = copy(currentItems map { case ref if old.id == ref.id => ref map f;  case x => x })
  /**
   * Remove all items
   */
  def clear               = copy(Nil)
}

/**
 * Combines `Lookup` and `DiffSeq`.
 * `initialItems` is based on the cached value.
 * `apply` returns `currentItems`, not the
 * cached/computed value, if there
 * are modifications.
 */
trait SeqLookup[A, Param] extends Lookup[Seq[A], Param] with DiffSeq[A, SeqLookup[A, Param]] {
  @volatile private var _cached = Option.empty[Seq[Handle[A]]]

  override protected def cached_=(x: Option[Seq[A]]) = _cached = x map (_ map { new Handle(_) })

  def initialItems: Seq[Handle[A]] = _cached getOrElse Seq.empty

  override def apply()(implicit param: Param): Seq[A] = {
    if(newItems.nonEmpty || replacedItems.nonEmpty || removedItems.nonEmpty)
      currentItems map (_.value)
    else
      super.apply()(param)
  }

  /**
   * Returns the current items, calculating them if possible and necessary.
   */
  def toSeq(implicit slts: SeqLookupToSeq[A, Param]) = slts apply this
}

class SeqLookupToSeq[A, P](val apply: SeqLookup[A, P] => Seq[A])
trait SeqLookupLow {
  implicit def toSeqConverter[A, P]: SeqLookupToSeq[A, P] = new SeqLookupToSeq[A, P](_.currentItems map (_.value))
}
object SeqLookup extends SeqLookupLow {
  implicit def toSeqConverter[A, P](implicit p: P): SeqLookupToSeq[A, P] = new SeqLookupToSeq[A, P](_())
}
