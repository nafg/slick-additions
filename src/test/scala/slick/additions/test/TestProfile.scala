package slick.additions.test

import slick.additions.KeyedTableProfile
import slick.jdbc.H2Profile


trait TestProfile extends H2Profile with KeyedTableProfile {
  object _api extends KeyedTableApi with API
  override val api = _api
}

object TestProfile extends TestProfile
