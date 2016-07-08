package scala.slick

package object additions {
  @deprecated("Use package scala.slick.additions.entity", "0.3.2")
  type Entity[K, +A] = scala.slick.additions.entity.Entity[K, A]
  @deprecated("Use package scala.slick.additions.entity", "0.3.2")
  type KeylessEntity[K, +A] = scala.slick.additions.entity.KeylessEntity[K, A]
  @deprecated("Use package scala.slick.additions.entity", "0.3.2")
  type KeyedEntity[K, +A] = scala.slick.additions.entity.KeyedEntity[K, A]
  @deprecated("Use package scala.slick.additions.entity", "0.3.2")
  type SavedEntity[K, +A] = scala.slick.additions.entity.SavedEntity[K, A]
  @deprecated("Use package scala.slick.additions.entity", "0.3.2")
  type ModifiedEntity[K, +A] = scala.slick.additions.entity.ModifiedEntity[K, A]

  @deprecated("Use package scala.slick.additions.entity", "0.3.2")
  lazy val KeylessEntity = scala.slick.additions.entity.KeylessEntity
  @deprecated("Use package scala.slick.additions.entity", "0.3.2")
  lazy val KeyedEntity = scala.slick.additions.entity.KeyedEntity
  @deprecated("Use package scala.slick.additions.entity", "0.3.2")
  lazy val SavedEntity = scala.slick.additions.entity.SavedEntity
  @deprecated("Use package scala.slick.additions.entity", "0.3.2")
  lazy val ModifiedEntity = scala.slick.additions.entity.ModifiedEntity
}
