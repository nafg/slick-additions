package slick.additions.codegen

import scala.annotation.nowarn
import scala.meta.Member.ParamClauseGroup
import scala.meta.{
  Ctor,
  Defn,
  Import,
  Importer,
  Init,
  Mod,
  Name,
  Pat,
  Self,
  Stat,
  Template,
  Term,
  Type,
  XtensionParseInputLike
}


object ScalaMetaDsl {
  // noinspection ScalaDeprecation
  @nowarn("cat=deprecation") // TODO needed for scala 2.12 support
  implicit class scalametaNonMacroInterpolators(private val sc: StringContext) extends AnyVal {
    def term(args: String*) = Term.Name(new StringContext(sc.parts: _*).standardInterpolator(identity, args))
    def typ(args: String*)  = Type.Name(new StringContext(sc.parts: _*).standardInterpolator(identity, args))
  }

  implicit class scalametaDefnClassExtensionMethods(private val self: Defn.Class) extends AnyVal {
    def withMod(mod: Mod) = self.copy(mods = mod +: self.mods)
  }

  implicit class scalametaTermExtensionMethods(private val self: Term)        extends AnyVal {
    def termApply(args: Term*): Term.Apply         = Term.Apply(self, Term.ArgClause(args.toList))
    def termApplyType(args: Type*): Term.ApplyType = Term.ApplyType(self, Type.ArgClause(args.toList))

    def termSelect(name: Term.Name): Term.Select = Term.Select(self, name)
    def termSelect(name: String): Term.Select    = termSelect(Term.Name(name))
  }
  implicit class scalametaTermRefExtensionMethods(private val self: Term.Ref) extends AnyVal {
    def typeSelect(name: Type.Name): Type.Select = Type.Select(self, name)
  }
  implicit class scalametaTypeExtensionMethods(private val self: Type)        extends AnyVal {
    def typeApply(args: Type*): Type.Apply = Type.Apply(self, Type.ArgClause(args.toList))
  }

  def defDef(
    name: String,
    declaredType: Option[Type] = None,
    modifiers: Seq[Mod] = Nil
  )(params: ParamClauseGroup*
  )(body: Term
  ) =
    Defn.Def(
      mods = modifiers.toList,
      name = Term.Name(name),
      paramClauseGroups = params.toList,
      decltpe = declaredType,
      body = body
    )

  def defVal(name: Term.Name, declaredType: Option[Type] = None, modifiers: Seq[Mod] = Nil)(rhs: Term) =
    Defn.Val(
      mods = modifiers.toList,
      pats = List(Pat.Var(name)),
      decltpe = declaredType,
      rhs = rhs
    )

  def termParam(name: Term.Name, typ: Type, default: Option[Term] = None) =
    Term.Param(
      mods = Nil,
      name = name,
      decltpe = Some(typ),
      default = default
    )

  def template(inits: Init*)(statements: Seq[Stat] = Nil) =
    Template(
      earlyClause = None,
      inits = inits.toList,
      body =
        Template.Body(
          selfOpt = Some(Self(Name.Anonymous(), None)),
          stats = statements.toList
        ),
      derives = Nil
    )

  def init(typ: Type, args: Seq[Seq[Term]] = Nil) =
    Init(
      tpe = typ,
      name = Name.Anonymous(),
      argClauses = args.map(a => Term.ArgClause(a.toList))
    )

  def defClass(
    name: String,
    modifiers: Seq[Mod] = Nil,
    params: Seq[Term.Param] = Nil,
    inits: Seq[Init] = Nil
  )(statements: Seq[Stat] = Nil
  ) =
    Defn.Class(
      mods = modifiers.toList,
      name = Type.Name(name),
      tparamClause = Type.ParamClause(Nil),
      ctor = Ctor.Primary(
        mods = Nil,
        name = Name.Anonymous(),
        paramClauses = List(Term.ParamClause(params.toList))
      ),
      templ = template(inits: _*)(statements)
    )

  def defObject(name: Term.Name, inits: Init*)(statements: Seq[Stat] = Nil) =
    Defn.Object(
      mods = Nil,
      name = name,
      templ = template(inits: _*)(statements)
    )

  private def recursePathTerm[A <: C, C](xs: List[String], last: A)(g: (Term.Ref, A) => C): C =
    xs match {
      case Nil     => last
      case x :: xs => g(toTermRef0(x, xs), last)
    }

  private def toTermRef0(last: String, revInit: List[String]): Term.Ref =
    recursePathTerm[Term.Name, Term.Ref](revInit, term"$last")(_.termSelect(_))

  def toTermRef(s: String): Term.Ref =
    s.split('.').toList.reverse match {
      case Nil             => term"$s"
      case last :: revInit => toTermRef0(last, revInit)
    }

  def toTypeRef(s: String): Type.Ref =
    s.split('.').toList.reverse match {
      case Nil             => typ"$s"
      case last :: revInit => recursePathTerm[Type.Name, Type.Ref](revInit, typ"$last")(_.typeSelect(_))
    }

  def makeImports(strings: List[String]): List[Stat] =
    if (strings.isEmpty)
      Nil
    else
      List(Import(strings.map(_.parse[Importer].get)))

}
