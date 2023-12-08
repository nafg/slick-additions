package slick.additions.codegen

import scala.meta.Member.ParamClauseGroup
import scala.meta.{Ctor, Defn, Init, Mod, Name, Pat, Self, Stat, Template, Term, Type}


object ScalaMetaDsl {
  implicit class scalametaNonMacroInterpolators(private val sc: StringContext) extends AnyVal {
    def term(args: String*) = Term.Name(StringContext.standardInterpolator(identity, args, sc.parts))
    def typ(args: String*)  = Type.Name(StringContext.standardInterpolator(identity, args, sc.parts))
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
      early = Nil,
      inits = inits.toList,
      self = Self(Name.Anonymous(), None),
      stats = statements.toList,
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
}
