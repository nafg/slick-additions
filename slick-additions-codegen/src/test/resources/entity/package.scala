import slick.additions.entity.{EntityKey, Lookup}

import cats.implicits.toInvariantOps
import io.circe.{Codec, Decoder, Encoder}


package object entity {
  implicit def codecLookup[K : Encoder: Decoder,A]: Codec[Lookup[K, A]] =
    Codec.from(Decoder[K], Encoder[K]).imap[Lookup[K, A]](EntityKey[K, A])(_.key)
}
