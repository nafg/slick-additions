package slick.additions

import slick.additions.entity.{EntityKey, KeyedEntity, SavedEntity}
import slick.lifted.Query


trait Lookups[K, V, A, T] {
  def lookupQuery(lookup: Lookup): Query[T, A, Seq]
  def lookupValue(a: A): V
  type Lookup = entity.Lookup[K, V]
  object Lookup {
    def apply(key: K): Lookup                = EntityKey(key)
    def apply(key: K, precache: V): Lookup   = SavedEntity(key, precache)
    def apply(ke: KeyedEntity[K, V]): Lookup = ke
  }
}
