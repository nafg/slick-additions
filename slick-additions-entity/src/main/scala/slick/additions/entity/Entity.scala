package slick.additions.entity


sealed trait EntityRef[K, +A] {
  def transform[B](f: A => B): EntityRef[K, B]
  def updated[B](value: B): EntityRef[K, B]
  def widen[B >: A]: EntityRef[K, B]
}

sealed trait Lookup[K, +A] extends EntityRef[K, A] {
  def key: K
  override def transform[B](f: A => B): Lookup[K, B]
  override def updated[B](value: B): Lookup[K, B]
  override def widen[B >: A]: Lookup[K, B]
  def foldLookup[X](key: EntityKey[K, A] => X, ent: KeyedEntity[K, A] => X): X = this match {
    case ek: EntityKey[K, A]   => key(ek)
    case ke: KeyedEntity[K, A] => ent(ke)
  }
  def valueOption: Option[A] = foldLookup(_ => None, ke => Some(ke.value))
  def sameKey(that: Lookup[K, _]) = this.key == that.key
  def toEntityKey: EntityKey[K, A]
}

case class EntityKey[K, +A](override val key: K) extends Lookup[K, A] {
  override def transform[B](f: A => B): EntityKey[K, B] = copy()
  override def updated[B](value: B) = ModifiedEntity(key, value)
  override def widen[B >: A]: EntityKey[K, B] = transform(identity)
  override def toEntityKey = this
}

sealed trait Entity[K, +A] extends EntityRef[K, A] {
  def keyOption: Option[K]

  def value: A

  def isSaved: Boolean

  override def transform[B](f: A => B): Entity[K, B]
  override def updated[B](value: B): Entity[K, B]
  override def widen[B >: A]: Entity[K, B]
  def modify[B](f: A => B): Entity[K, B]

  def duplicate = new KeylessEntity[K, A](value)

  def foldEnt[X](keyless: KeylessEntity[K, A] => X, keyed: KeyedEntity[K, A] => X): X = this match {
    case kl: KeylessEntity[K, A] => keyless(kl)
    case ke: KeyedEntity[K, A]   => keyed(ke)
  }
}
case class KeylessEntity[K, +A](override val value: A) extends Entity[K, A] {
  override val keyOption = None

  final override def isSaved = false

  override def equals(that: Any) = this eq that.asInstanceOf[AnyRef]

  override def transform[B](f: A => B): KeylessEntity[K, B] = copy(value = f(value))
  override def updated[B](value: B): KeylessEntity[K, B] = copy(value = value)
  override def widen[B >: A]: KeylessEntity[K, B] = transform(identity)
  override def modify[B](f: A => B) = transform(f)

  override def toString = s"KeylessEntity($value)"
}

sealed trait KeyedEntity[K, +A] extends Entity[K, A] with Lookup[K, A] {
  override def keyOption = Some(key)

  override def transform[B](f: A => B): KeyedEntity[K, B]
  override def widen[B >: A]: KeyedEntity[K, B]
  override def modify[B](f: A => B): ModifiedEntity[K, B] = ModifiedEntity[K, B](key, f(value))
  override def updated[B](value: B): ModifiedEntity[K, B] = ModifiedEntity(key, value)

  def toSaved: SavedEntity[K, A] = SavedEntity(key, value)
  def asLookup: Lookup[K, A] = this
  override def toEntityKey: EntityKey[K, A] = EntityKey(key)
}
object KeyedEntity {
  def apply[K, A](key: K, value: A): KeyedEntity[K, A] = SavedEntity[K, A](key, value)
  def unapply[K, A](ke: KeyedEntity[K, A]): Option[(K, A)] = Some((ke.key, ke.value))
}
case class SavedEntity[K, +A](override val key: K, override val value: A) extends KeyedEntity[K, A] {
  final override def isSaved = true
  override def transform[B](f: A => B): SavedEntity[K, B] = copy(value = f(value))
  override def widen[B >: A]: SavedEntity[K, B] = transform(identity)
}
case class ModifiedEntity[K, +A](override val key: K, override val value: A) extends KeyedEntity[K, A] {
  final override def isSaved = false
  override def transform[B](f: A => B): ModifiedEntity[K, B] = copy(value = f(value))
  override def widen[B >: A]: ModifiedEntity[K, B] = transform(identity)
}
