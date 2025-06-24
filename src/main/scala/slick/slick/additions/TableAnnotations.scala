package slick.additions

import scala.annotation.StaticAnnotation
import scala.language.experimental.macros
import scala.reflect.ClassTag
import scala.reflect.macros.Context

object TableAnnotations {
  def impl[T](c: Context)(annottees: c.Expr[Any]*): c.Expr[Any] = {
    import c.universe._

    val ret: Tree = c.macroApplication match {
      case q"""new ExpandTable($clz1).macroTransform($tree)""" =>
        val q"""classOf[${tq"$clz"}]""" = clz1
        val cc: Tree = clz
        val cc2 = cc.duplicate
        val t = c.typeCheck(q"(??? : $cc2)").tpe

        val declarations = t.declarations
        val ctor = declarations.collectFirst { case m: MethodSymbol if m.isPrimaryConstructor => m }.getOrElse(c.abort(c.macroApplication.pos, s"$t has no constructor"))
        val params = ctor.paramss.head

        val fields = params.map(p => (p.name.toTermName, p.typeSignature))

        val columnDefs = fields map { case (f, t) =>
          q"""def $f = column[$t](${f.toString}) """
        }

        val tuple = q"(..${fields map (_._1)})"

        val inputs = annottees.map(_.tree)

        val origClass = inputs collectFirst { case cd: ClassDef => cd }
        val origModule = inputs collectFirst { case md: ModuleDef => md }

//        println(inputs.length)
//        println(inputs.map(_.getClass))

        val tableClass = origClass.map(_.name) orElse origModule.map(_.name.toTypeName) getOrElse c.abort(c.macroApplication.pos, "No class or module in " + inputs)

        val modParent = q"${tq"TableQuery[$tableClass]"}(tag => new $tableClass(tag))"
        val modParents = origModule.toList.flatMap(_.impl.parents) match {
          case tq"scala.AnyRef" :: tail => modParent :: tail
          case Nil                      => modParent :: Nil
          case other :: _               => c.abort(c.macroApplication.pos, "Module ${ origModule.get.name } already extends class $other and cannot extend class TableQuery too")
        }

//          val moduleTemplate = origModule.map(_.impl) getOrElse Template()
        val moduleOut1 =
          ModuleDef(
            origModule.map(_.mods) getOrElse NoMods,
            origModule.map(_.name) getOrElse tableClass.toTermName,
            Template(modParents, origModule.map(_.impl.self) getOrElse emptyValDef, origModule.toList.flatMap(_.impl.body))
          )
        val moduleOut = origModule match {
          case Some(md @ q"""$mods object $name extends { ..$earlydefns } with ..$parents { $self => ..$stats } """) =>
            parents.headOption match {
              case None | Some(tq"scala.AnyRef") =>
              case other                         => c.abort(md.pos, s"Module $name already extends class $other and cannot extend TableQuery too")
            }
            q"""
                $mods object $name extends { ..$earlydefns } with TableQuery[$tableClass](tag => new $tableClass(tag)) with ..${ parents.drop(1) } { $self =>
                  ..$stats
                }
            """
          case None =>
            q"""object ${ tableClass.toTermName } extends ..$modParents"""

        }
//        println("moduleOut: " + show(moduleOut))


        val classOut = origClass match {
          case Some(cd @ q""" $mods class $name[..$tparams] $ctmods(...$paramss) extends { ..$earlydefns } with ..$parents { $self => ..$stats } """) =>
            parents.headOption match {
              case None | Some(tq"scala.AnyRef") =>
              case other                         => c.abort(cd.pos, s"Class $name already extends class $other and cannot extends Table too")
            }
            q"""
              $mods class $name[..$tparams] $ctmods(tag: Tag)(...$paramss) extends Table[$cc](tag, ${ cc.toString }) with ..${ parents.drop(1) } { $self =>
                ..$stats
                ..$columnDefs
                def * = $tuple <> (${t.typeSymbol.name.toTermName}.tupled, ${t.typeSymbol.name.toTermName}.unapply)
              }
            """
          case None =>
            q"""
              class ${tableClass}(tag: Tag) extends Table[$cc](tag, ${cc.toString()}) {
                ..$columnDefs
                def * = $tuple <> (${t.typeSymbol.name.toTermName}.tupled, ${t.typeSymbol.name.toTermName}.unapply)
              }
            """
        }
//        println("classOut: " + classOut)
        val classOut1 =
          ClassDef.apply(
            origClass.map(_.mods) getOrElse NoMods,
            tableClass,
            origClass.toList.flatMap(_.tparams),
          Template(origClass.toList.flatMap(_.impl.parents), origClass.map(_.impl.self) getOrElse emptyValDef, origClass.toList.flatMap(_.impl.body) )
          )

        /*tree match {
          case q""" $mods class $name[..$tparams] $ctmods(...$paramss) extends { ..$earlydefns } with ..$parents { $self => ..$stats } """ =>

            q"""
             $mods class $name[..$tparams] $ctmods(...$paramss) extends { ..$earlydefns } with ..$parents { $self =>
                ..$stats
                ..$columnDefs
                def * = $tuple <> (${t.typeSymbol.name.toTermName}.tupled, ${t.typeSymbol.name.toTermName}.unapply)
             }
            """

          case q"$mods object $tname extends { ..$earlydefns } with ..$parents { $self => ..$body }" =>
            val tableClass = tname.toTypeName
            val p = q"${tq"TableQuery[$tableClass]"}(tag => new $tableClass(tag))"
            val ps = parents.head match {
              case tq"scala.AnyRef" => p :: parents.tail
            }
            q"""
              $mods object $tname extends { ..$earlydefns } with ..$ps { $self => ..$body }

              class $tableClass(tag: Tag) extends Table[$cc](tag, ${cc.toString()}) {
                ..$columnDefs
                def * = $tuple <> (${t.typeSymbol.name.toTermName}.tupled, ${t.typeSymbol.name.toTermName}.unapply)
              }
            """
        }*/

        q"""
          $moduleOut
          $classOut
        """
    }

    println(ret)

    c.Expr[Any](ret)
  }
}

class ExpandTable[T](clazz: Class[T]) extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro TableAnnotations.impl[T]
}
