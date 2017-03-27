# `slick-additions` 

## Helpers for Scala Slick (slick.typesafe.com)



| Slick version | SBT dependency                                       |
|---------------|------------------------------------------------------|
| 3.1.1         | `"io.github.nafg" %% "slick-additions" % "0.6.0"`    |
| 3.0.3         | `"io.github.nafg" %% "slick-additions" % "0.5.2"`    |

Artifacts are deployed to Bintray and synchronized to JCenter, so you may need to add `resolvers += Resolver.jcenterRepo` to your build.



See https://github.com/nafg/slick-additions/blob/master/src/test/scala/slick/additions/KeyedTableTests.scala for an example.

Including:

 - KeyedTable / Entity: your domain classes don't need a PK field, and can contain child objects.
 - Type mapper for any kind of enum
 
