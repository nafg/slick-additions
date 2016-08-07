package slick.additions.entity


sealed trait EntityRef[K, +A]

sealed trait Lookup[K, +A] extends EntityRef[K, A] {
  def key: K
  def foldLookup[X](key: EntityKey[K, A] => X, ent: KeyedEntity[K, A] => X): X = this match {
    case ek: EntityKey[K, A]   => key(ek)
    case ke: KeyedEntity[K, A] => ent(ke)
  }
  def valueOption: Option[A] = foldLookup(_ => None, ke => Some(ke.value))
}

case class EntityKey[K, +A](key: K) extends Lookup[K, A]


sealed trait Entity[K, +A] extends EntityRef[K, A] {
  def keyOption: Option[K]

  def value: A

  def isSaved: Boolean

  def map[B >: A](f: A => B): Entity[K, B]

  def duplicate = new KeylessEntity[K, A](value)
}
case class KeylessEntity[K, +A](value: A) extends Entity[K, A] {
  val keyOption = None

  final def isSaved = false

  override def equals(that: Any) = this eq that.asInstanceOf[AnyRef]

  def map[B >: A](f: A => B): KeylessEntity[K, B] = new KeylessEntity[K, B](f(value))

  override def toString = s"KeylessEntity($value)"
}

sealed trait KeyedEntity[K, +A] extends Entity[K, A] with Lookup[K, A] {
  override def keyOption = Some(key)

  override def map[B >: A](f: A => B): ModifiedEntity[K, B] = ModifiedEntity[K, B](key, f(value))
}
object KeyedEntity {
  def unapply[K, A](ke: KeyedEntity[K, A]): Option[(K, A)] = Some((ke.key, ke.value))
}
case class SavedEntity[K, +A](key: K, value: A) extends KeyedEntity[K, A] {
  final def isSaved = true
}
case class ModifiedEntity[K, +A](key: K, value: A) extends KeyedEntity[K, A] {
  final def isSaved = false
}
