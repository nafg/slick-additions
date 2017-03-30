package slick.additions

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

import slick.additions.entity.Lookup
import slick.ast.{Library, LiteralNode, ProductNode, TypedType}
import slick.lifted.{LiteralColumn, OptionLift, Rep, OptionMapperDSL => O}
import slick.util.ConstArray


/**
  * Phantom type to support custom extension methods
  */
sealed trait LookupRep[K, A] {
  def ? : Rep[Option[Lookup[K, A]]] = macro LookupRepMacros.lookupOptMacro[K, A]
  def inSet[R](seq: Traversable[Lookup[K, A]])
              (implicit om: O.arg[Lookup[K, A], Lookup[K, A]]#to[Boolean, R]): Rep[R] = macro LookupRepMacros.lookupInSetMacro[K, A, R]
}

object LookupRep {
  def lookupOpt[K, A](rep: Rep[Lookup[K, A]])
                     (implicit l: OptionLift[Rep[Lookup[K, A]], Rep[Option[Lookup[K, A]]]]): Rep[Option[Lookup[K, A]]] =
    Rep.Some(rep)

  def lookupInSet[K, A, R](rep: Rep[Lookup[K, A]])
                          (seq: Traversable[Lookup[K, A]])
                          (implicit om: O.arg[Lookup[K, A], Lookup[K, A]]#to[Boolean, R],
                           boolType: TypedType[Boolean],
                           lookupType: TypedType[Lookup[K, A]]): Rep[R] =
    if (seq.isEmpty) om(LiteralColumn(false))
    else om.column(Library.In, rep.toNode, ProductNode(ConstArray.from(seq.map(v => LiteralNode(lookupType, v)))))

  def apply[K, A](rep: Rep[Lookup[K, A]]): Rep[Lookup[K, A]] with LookupRep[K, A] =
    rep.asInstanceOf[Rep[Lookup[K, A]] with LookupRep[K, A]]
}

object LookupRepMacros {
  def lookupOptMacro[K, A](c: blackbox.Context): c.Expr[Rep[Option[Lookup[K, A]]]] = {
    import c.universe._
    val Select((rep, TermName("$qmark"))) = c.macroApplication
    c.Expr(q"slick.additions.LookupRep.lookupOpt($rep)")
  }
  def lookupInSetMacro[K, A, R](c: blackbox.Context)
                               (seq: c.Expr[Traversable[Lookup[K, A]]])
                               (om: c.Expr[O.arg[Lookup[K, A], Lookup[K, A]]#to[Boolean, R]]): c.Expr[Rep[R]] = {
    import c.universe._
    val Apply(Apply(TypeApply(Select((rep, TermName("inSet"))), _), List(seq)), _) = c.macroApplication
    c.Expr(q"slick.additions.LookupRep.lookupInSet($rep)($seq)")
  }
}
