package slick.additions.test

import slick.additions.AdditionsProfile
import slick.jdbc.H2Profile


trait TestProfile extends H2Profile with AdditionsProfile {
  object _api extends AdditionsApi with API
  override val api = _api
}

object TestProfile extends TestProfile
