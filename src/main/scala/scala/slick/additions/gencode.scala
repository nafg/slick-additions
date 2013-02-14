package scala.slick.additions

/**
 * For now I just copy-paste from here
 */
private object GenCode extends App {
  def copy(s: String) = {
    java.awt.Toolkit.getDefaultToolkit.getSystemClipboard.setContents(new java.awt.datatransfer.StringSelection(s), null); println(s)
  }
  def p(i2: Int)(i1: Int)(s: String) = (i1 to i2).map(s + _).mkString(", ")

  def tmpl(i: Int) = {
    val pi = p(i) _
    val p1 = pi(1)
    val p2 = pi(2)
    val pT1 = p1("T")
    s"""
    implicit class EntityMapping$i[$pT1](val p: Projection$i[$pT1]) extends EntityMapping[($pT1) => A, A => Option[($pT1)]] {
      def <->(ap: Option[K] => _Ap, unap: _Unap) = Mapping(
        p <> (ap(None), unap),
        (key ~: p).<>[KEnt](
          (t: (K, $pT1)) => SavedEntity(t._1, ap(Some(t._1))(${p(i+1)(2)("t._")})),
          { ke: KEnt => unap(ke.value) map (t => (ke.key, ${p1("t._")})) }
        )
      )
    }
"""
  }
  copy(2 to 21 map tmpl mkString "")
}
