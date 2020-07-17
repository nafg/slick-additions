# Slick Additions

## Helpers for Scala Slick (https://scala-slick.org)

Note: Artifacts are deployed to Bintray and synchronized to JCenter,
so you may need to add `ThisBuild / useJCenter := true` to your build.


### `slick-additions-entity`

This project actually does not depend on Slick.

```scala
libraryDependencies += "io.github.nafg" %% "slick-additions-entity" % "0.10.3"
libraryDependencies += "io.github.nafg" %%% "slick-additions-entity" % "0.10.3"  // for Scala.js
```

It defines abstractions to separate unsafe, low-level database implementation details from your models.

For instance, suppose you have a `Person` model which consists of a `name` text field and a numeric `age`.
You might store it in a `person` table that uses an auto-incremented `BIGINT` primary key. However, you might want to
work with a `Person` that has not been saved yet and therefore has no ID.

You might have other models that can contain references to a `Person`. For example, you might have a `Document` model
with an `owner` column. In the database it's just another `BIGINT`, except it has a referential integrity constraint
ensuring that it points to a valid `person` record. In code, you don't want to be dealing with raw `Long`s. You want
the `owner` field to have a type that ensures it refers to a `Person`.

Another way of looking at this is that sometimes we have data about a person (name and age) but not an ID,
sometimes we have the ID of a person but not the data, and sometimes we have both. Also, some parts of the
code need either the ID or the data and consider the other one optional.

We define an ADT / type hierarchy that allows you to be as concrete or abstract about this as appropriate, while
remaining fully typesafe. All types are parameterized by `K`, the key type, and `A`, the value type.

![EntityRef subclass hierarchy](http://www.plantuml.com/plantuml/svg/ZO-zJWCn48HxFuMLcaU8eg08cW05q26A9PvJhFZdjBj6vEqvOOwiT4NeFAkPRyPRKM9jEJaNYu87Ei3V5OrwViRWIVpgJxtjR_pj-yglryeKTRCnaOTQJstyyVVdmkpnfIX1BdHtI2IptkDisYOr0-POnovMcj3_AoTyWeWXs8kjDd5JFRaHJDheQoD-5ppM48S82x6dvOPUOH4lz0cRx_fgs1rZWC_87s3fGXICgkSHpiafbf6OiWyOg2MzhqMVACFlQkC8DWuPg-wEn9U_lZgtdHfRJkuR "Class hierarchy diagram")

 * `EntityRef`: the common base trait, not very useful on its own
 * `Lookup`: has a key, may or may not have a value.
   Useful for fields that represent a foreign key reference to another model.
 * `Entity`: has a value, may or may not have a key.
   Useful when working with values that may or may not have been saved already.
 * `EntityKey`: extends `Lookup`; has a key but no value.
   You can use this type statically to ensure equality is only comparing by key, for instance in a `Map`.
   When comparing explicitly you can just use the `sameKey` method.
 * `KeylessEntity`: extends `Entity`; has a value but no key.
 * `KeyedEntity`: extends `Lookup` and `Entity`; has a key and a value.
   This is a trait extended by two case classes, `SavedEntity` and `ModifiedEntity`.
   `SavedEntity` means it can be assumed that the entity is as it was in the database.
   `ModifiedEntity` means it was changed after it was retrieved.
   Usually you can use `KeyedEntity` as your static type (it has a `apply` and `unapply` methods) and if you care
   about whether it was modified you can check with the `isSaved` method. However, occasionally it may be
   useful to use one of the subtypes statically to communicate or to ensure if you're dealing with an entity that
   has changes that need to be saved.

To modify an entity use the `modify` method. Example:

```scala
import slick.additions.entity._
case class Person(name: String, age: Int)
def grow(personEnt: Entity[Long, Person]) = personEnt.modify(p => p.copy(age = p.age + 1))
```

`SavedEntity#modify` will return a `ModifiedEntity`.

If you need to make a change that shouldn't clear its "saved" status, use `transform` instead of `modify`.


### `slick-additions`
```scala
// In your build
libraryDependencies += "io.github.nafg" %% "slick-additions" % "0.10.3"

// In your codebase, use a custom Profile. For example:
import slick.jdbc._
import slick.additions.AdditionsProfile
trait SlickProfile 
    extends PostgresProfile // Replace with your database
      with AdditionsProfile {
  object myApi extends API with AdditionsApi
  override val api = myApi
}
object SlickProfile extends SlickProfile

// Then, wherever you import the Slick API, import it from there
import SlickProfile.api._
```


#### `AutoName`

Mix this into your `Table` to be able to infer the names of columns, indexes, and foreign keys
from the name of the `val` or `def` (uses https://github.com/lihaoyi/sourcecode).
You'll need an implicit `slick.additions.NameStyle`.

If your names in code are camelCased and your database names are snake_case, just use `AutoNameSnakify`. It will
supply `NameStyle.Snakify` as the implicit.

For a name to be inferred, use `column` instead of `col`, `foreignKey` instead of `foreign`,
and `index` instead of `idx`.


#### `EntityTableModule`

Saves boilerplate defining an `Entity`-based Slick table, especially when combined with `AutoName`.

Example:

```scala
object People extends EntityTableModule[Long, Person]("material") {
  class Row(tag: Tag) extends BaseEntRow(tag) with AutoNameSnakify {
    val name = col[String]
    val age = col[Int]
    def mapping = (name, age).mapTo[Person]
  }
}
```

The TableQuery is defined for you as `val Q`, so you'd use it as, e.g., `People.Q`

`EntityTableModule` is built on `EntityTable`.


#### `EntityTable` / `EntTableQuery`

Simplifies defining tables based on `slick-additions-entity`.

Example:

```scala
class People(tag: Tag) extends EntityTable[Long, Person](tag, "doctor_contact") with AutoNameSnakify {
  val name = col[String]
  val age = col[Int]

  override def mapping = (name, age).mapTo[Person]
  override def tableQuery = People
}

object People extends EntTableQuery[Long, Person, People](new People(_))
```

`EntityTable` builds on `KeyedTable`.


#### `KeyedTable` / `KeyedTableQuery`

Useful for abstracting over tables that have a separate primary key column.
