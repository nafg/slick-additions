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

  def transform[B](f: A => B): Entity[K, B]
  def modify[B](f: A => B): Entity[K, B]

  @deprecated("Use modify", "0.9.1")
  def map[B >: A](f: A => B): Entity[K, B]

  def duplicate = new KeylessEntity[K, A](value)

  def foldEnt[X](keyless: KeylessEntity[K, A] => X, keyed: KeyedEntity[K, A] => X): X = this match {
    case kl: KeylessEntity[K, A] => keyless(kl)
    case ke: KeyedEntity[K, A]   => keyed(ke)
  }
}
case class KeylessEntity[K, +A](value: A) extends Entity[K, A] {
  override val keyOption = None

  final override def isSaved = false

  override def equals(that: Any) = this eq that.asInstanceOf[AnyRef]

  override def transform[B](f: A => B): KeylessEntity[K, B] = new KeylessEntity[K, B](f(value))
  override def modify[B](f: A => B) = transform(f)
  final override def map[B >: A](f: A => B): KeylessEntity[K, B] = modify(f)

  override def toString = s"KeylessEntity($value)"
}

sealed trait KeyedEntity[K, +A] extends Entity[K, A] with Lookup[K, A] {
  override def keyOption = Some(key)

  override def transform[B](f: A => B): KeyedEntity[K, B]
  override def modify[B](f: A => B): ModifiedEntity[K, B] = ModifiedEntity[K, B](key, f(value))
  final override def map[B >: A](f: A => B): ModifiedEntity[K, B] = modify(f)

  def toSaved: SavedEntity[K, A] = SavedEntity(key, value)
  def asLookup: Lookup[K, A] = this
}
object KeyedEntity {
  def apply[K, A](key: K, value: A): KeyedEntity[K, A] = SavedEntity[K, A](key, value)
  def unapply[K, A](ke: KeyedEntity[K, A]): Option[(K, A)] = Some((ke.key, ke.value))
}
case class SavedEntity[K, +A](key: K, value: A) extends KeyedEntity[K, A] {
  final override def isSaved = true
  override def transform[B](f: A => B) = SavedEntity(key, f(value))
}
case class ModifiedEntity[K, +A](key: K, value: A) extends KeyedEntity[K, A] {
  final override def isSaved = false
  override def transform[B](f: A => B) = ModifiedEntity(key, f(value))
}
