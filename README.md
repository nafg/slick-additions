# `slick-additions` 

## Helpers for Scala Slick (slick.typesafe.com)



| Slick version | Slick-additions version | SBT dependency                                       | Supported scala versions
|---------------|-------------------------|------------------------------------------------------|--------------------------
| 3.3.2         | 0.10.2                  | `"io.github.nafg" %% "slick-additions" % "0.10.1"`   | 2.12, 2.13
| 3.3.0         | 0.8.0                   | `"io.github.nafg" %% "slick-additions" % "0.8.0"`    | 2.11, 2.12
| 3.2.1         | 0.7.2                   | `"io.github.nafg" %% "slick-additions" % "0.7.2"`    | 2.11, 2.12
| 3.2.0         | 0.7.0                   | `"io.github.nafg" %% "slick-additions" % "0.7.0"`    | 2.11, 2.12
| 3.1.1         | 0.6.1                   | `"io.github.nafg" %% "slick-additions" % "0.6.1"`    | 2.10, 2.11
| 3.0.3         | 0.5.2                   | `"io.github.nafg" %% "slick-additions" % "0.5.2"`    | 2.10, 2.11

Artifacts are deployed to Bintray and synchronized to JCenter, so you may need to add `resolvers += Resolver.jcenterRepo` to your build.



See https://github.com/nafg/slick-additions/blob/master/src/test/scala/slick/additions/KeyedTableTests.scala for an example.

Including:

 - KeyedTable / Entity: your domain classes don't need a PK field, and can contain child objects.
 - Type mapper for any kind of enum
 
