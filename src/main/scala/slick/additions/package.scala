package slick

package object additions {
  @deprecated("Use package scala.slick.additions.entity", "0.3.2")
  type Entity[K, +A] = slick.additions.entity.Entity[K, A]
  @deprecated("Use package scala.slick.additions.entity", "0.3.2")
  type KeylessEntity[K, +A] = slick.additions.entity.KeylessEntity[K, A]
  @deprecated("Use package scala.slick.additions.entity", "0.3.2")
  type KeyedEntity[K, +A] = slick.additions.entity.KeyedEntity[K, A]
  @deprecated("Use package scala.slick.additions.entity", "0.3.2")
  type SavedEntity[K, +A] = slick.additions.entity.SavedEntity[K, A]
  @deprecated("Use package scala.slick.additions.entity", "0.3.2")
  type ModifiedEntity[K, +A] = slick.additions.entity.ModifiedEntity[K, A]

  @deprecated("Use package scala.slick.additions.entity", "0.3.2")
  lazy val KeylessEntity = slick.additions.entity.KeylessEntity
  @deprecated("Use package scala.slick.additions.entity", "0.3.2")
  lazy val KeyedEntity = slick.additions.entity.KeyedEntity
  @deprecated("Use package scala.slick.additions.entity", "0.3.2")
  lazy val SavedEntity = slick.additions.entity.SavedEntity
  @deprecated("Use package scala.slick.additions.entity", "0.3.2")
  lazy val ModifiedEntity = slick.additions.entity.ModifiedEntity
}
