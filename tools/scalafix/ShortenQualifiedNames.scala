package fix

import scala.meta._
import scala.collection.mutable.ListBuffer
import scalafix.v1._

/** Shorten class/object references without changing literals, comments or import paths. */
class ShortenQualifiedNames extends SyntacticRule("ShortenQualifiedNames") {
  private val roots = Set(
    "java",
    "javax",
    "jakarta",
    "scala",
    "org",
    "com",
    "ui",
    "tools",
    "io",
    "net",
    "munit",
    "ember",
    "reflect"
  )
  private val aliases = Map(
    "java.math.BigDecimal"       -> "JavaBigDecimal",
    "java.lang.Boolean"          -> "JavaBoolean",
    "java.lang.Byte"             -> "JavaByte",
    "java.lang.Short"            -> "JavaShort",
    "java.lang.Integer"          -> "JavaInteger",
    "java.lang.Long"             -> "JavaLong",
    "java.lang.Float"            -> "JavaFloat",
    "java.lang.Double"           -> "JavaDouble",
    "java.lang.Character"        -> "JavaCharacter",
    "jakarta.ws.rs.core.Context" -> "RestContext"
  )

  private def path(tree: Tree): List[String] =
    tree match {
      case Term.Name(name) =>
        List(name)
      case Term.Select(qualifier, name) =>
        path(qualifier) :+ name.value
      case Type.Select(qualifier, name) =>
        path(qualifier) :+ name.value
      case _ =>
        Nil
    }

  private def inImportOrPackage(tree: Tree): Boolean = tree.parent
    .exists {
      case _: Import =>
        true
      case pkg: Pkg if pkg.ref.pos.start <= tree.pos.start && tree.pos.end <= pkg.ref.pos.end =>
        true
      case parent =>
        inImportOrPackage(parent)
    }

  override def fix(implicit doc: SyntacticDocument): Patch = {
    val utilReferences = qualifyJavaUtil(doc)
    val importAliases  =
      doc.tree.collect { case rename: Importee.Rename =>
        rename.tokens.collect { case arrow: Token.RightArrow =>
          Patch.replaceToken(arrow, "as")
        }.asPatch
      }.asPatch
    val references = doc.tree
      .collect {
        case tree: Term.Select if qualifiedClass(tree) && !directJavaUtil(tree) =>
          tree
        case tree: Type.Select if qualifiedClass(tree) && !directJavaUtil(tree) =>
          tree
      }
    if (references.isEmpty)
      return (importAliases + utilReferences).atomic

    // Only reuse imports at file/package scope, never an import local to a method.
    val imports = doc.tree
      .collect {
        case importer: Importer
            if importer.parent
              .flatMap(_.parent)
              .exists {
                case _: Source | _: Pkg.Body =>
                  true
                case _ =>
                  false
              } =>
          importer
      }
    val imported =
      imports.flatMap { importer =>
        val prefix = path(importer.ref).mkString(".")
        importer.importees
          .collect {
            case Importee.Name(name) =>
              (prefix + "." + name.value, name.value)
            case Importee.Rename(name, alias) =>
              (prefix + "." + name.value, alias.value)
          }
      }.toMap

    // An existing unqualified use may come from a wildcard import or an enclosing
    // package. An alias avoids changing its binding without requiring SemanticDB.
    val usedNames =
      doc.tree.collect {
        case name: Name
            if !inImportOrPackage(name) &&
              !references.exists { ref =>
                ref.pos.start <= name.pos.start && name.pos.end <= ref.pos.end
              } =>
          name.value
      }.toSet ++ imported.values
    val definedNames =
      doc.tree.collect {
        case name: Type.Name
            if name.parent
              .exists {
                case _: Defn.Class | _: Defn.Trait | _: Defn.Type | _: Type.Param =>
                  true
                case _ =>
                  false
              } =>
          name.value
        case name: Term.Name
            if name.parent
              .exists {
                case _: Defn.Object | _: Term.Param | _: Pat.Var =>
                  true
                case _ =>
                  false
              } =>
          name.value
      }.toSet
    var occupied = usedNames

    val additions    = ListBuffer.empty[String]
    val replacements =
      references
        .groupBy(tree => path(tree).filterNot(_ == "_root_").mkString("."))
        .toList
        .sortBy(_._1)
        .map { case (qualified, trees) =>
          val parts     = qualified.split("\\.").toList
          val simple    = parts.last
          val preferred = aliases.getOrElse(qualified, simple)
          val existing  = imported.get(qualified).filterNot(definedNames)
          val local     = existing.getOrElse {
            val base =
              if (occupied(preferred))
                parts(parts.length - 2).capitalize + simple
              else
                preferred
            val chosen =
              Iterator
                .from(0)
                .map(i =>
                  if (i == 0)
                    base
                  else
                    base + i
                )
                .find(!occupied(_))
                .get
            occupied += chosen
            chosen
          }
          if (existing.isEmpty) {
            val selector =
              if (local == simple)
                simple
              else
                "{" + simple + " as " + local + "}"
            additions += "import " + parts.dropRight(1).mkString(".") + "." + selector
          }
          trees.map(tree => Patch.replaceTree(tree, local)).asPatch
        }
        .asPatch
    (insertImports(doc, additions.toList) + replacements + importAliases + utilReferences).atomic
  }

  private def directJavaUtil(tree: Tree): Boolean =
    path(tree).filterNot(_ == "_root_") match {
      case List("java", "util", name) =>
        name.headOption.exists(_.isUpper) && !inImportOrPackage(tree)
      case _ =>
        false
    }

  private def qualifyJavaUtil(doc: SyntacticDocument): Patch = {
    def global(importer: Importer): Boolean = importer.parent
      .flatMap(_.parent)
      .exists {
        case _: Source | _: Pkg.Body =>
          true
        case _ =>
          false
      }
    val imports = doc.tree
      .collect { case importer: Importer =>
        importer
      }
    val classImports = imports.filter { importer =>
      path(importer.ref) == List("java", "util") &&
      importer.importees
        .forall {
          case Importee.Name(name) =>
            name.value.headOption.exists(_.isUpper)
          case Importee.Rename(name, _) =>
            name.value.headOption.exists(_.isUpper)
          case _ =>
            false
        }
    }
    val names = classImports.flatMap { importer =>
      importer.importees
        .collect {
          case Importee.Name(name) =>
            (name.value, name.value, importer)
          case Importee.Rename(name, alias) =>
            (alias.value, name.value, importer)
        }
    }
    val qualified = doc.tree
      .collect {
        case tree: Term.Select if directJavaUtil(tree) =>
          tree
        case tree: Type.Select if directJavaUtil(tree) =>
          tree
      }
    if (classImports.isEmpty && qualified.isEmpty)
      return Patch.empty
    val uses =
      doc.tree.collect {
        case name: Name
            if !inImportOrPackage(name) &&
              name.parent
                .forall {
                  case select: Term.Select =>
                    select.qual eq name
                  case select: Type.Select =>
                    select.qual eq name
                  case _: Pat.Var =>
                    false
                  case parameter: Term.Param =>
                    !(parameter.name eq name)
                  case parameter: Type.Param =>
                    !(parameter.name eq name)
                  case definition: Defn.Class =>
                    !(definition.name eq name)
                  case definition: Defn.Trait =>
                    !(definition.name eq name)
                  case definition: Defn.Object =>
                    !(definition.name eq name)
                  case definition: Defn.Type =>
                    !(definition.name eq name)
                  case _ =>
                    true
                } =>
          names
            .find { case (alias, _, importer) =>
              alias == name.value && (
                global(importer) ||
                  importer.parent
                    .flatMap(_.parent)
                    .exists { scope =>
                      importer.pos.end <= name.pos.start && name.pos.end <= scope.pos.end
                    }
              )
            }
            .map { case (_, original, _) =>
              Patch.replaceTree(name, "util." + original)
            }
            .getOrElse(Patch.empty)
      }.asPatch
    val hasUtil = imports.exists { importer =>
      global(importer) && path(importer.ref) == List("java") &&
      importer.importees
        .exists {
          case Importee.Name(name) =>
            name.value == "util"
          case _ =>
            false
        }
    }
    val anchor =
      if (hasUtil)
        None
      else
        classImports.find(global)
    val removeImports =
      classImports
        .filterNot(importer => anchor.contains(importer))
        .flatMap(_.importees)
        .map(Patch.removeImportee)
        .asPatch
    val packageImport =
      anchor match {
        case Some(importer) =>
          Patch.replaceTree(importer, "java.util")
        case None if !hasUtil =>
          insertImports(doc, List("import java.util"))
        case _ =>
          Patch.empty
      }
    packageImport + removeImports + uses +
      qualified.map(tree => Patch.replaceTree(tree, "util." + path(tree).last)).asPatch
  }

  private def insertImports(doc: SyntacticDocument, additions: List[String]): Patch = {
    def stats(tree: Tree): List[Stat] =
      tree match {
        case Source(List(pkg: Pkg)) =>
          stats(pkg)
        case Source(body) =>
          body
        case pkg: Pkg =>
          pkg.body.stats match {
            case List(inner: Pkg) =>
              stats(inner)
            case body =>
              body
          }
        case _ =>
          Nil
      }
    if (additions.isEmpty)
      return Patch.empty
    val body = stats(doc.tree)
    body.takeWhile(_.isInstanceOf[Import]).lastOption match {
      case Some(last) =>
        Patch.addRight(last, "\n" + additions.mkString("\n"))
      case None =>
        body.headOption
          .map { first =>
            // Insert before the definition's leading comments so its Scaladoc stays attached.
            val leading =
              doc.tokens
                .takeWhile(_.pos.end <= first.pos.start)
                .reverse
                .takeWhile {
                  case _: Token.Whitespace | _: Token.Comment =>
                    true
                  case _ =>
                    false
                }
                .reverse
            val anchor = leading
              .collectFirst { case comment: Token.Comment =>
                comment
              }
              .getOrElse(first.tokens.head)
            Patch.addLeft(anchor, additions.mkString("\n") + "\n\n")
          }
          .getOrElse(Patch.empty)
    }
  }

  private def qualifiedClass(tree: Tree): Boolean = {
    val parts = path(tree).filterNot(_ == "_root_")
    parts.length >= 2 && roots(parts.head) &&
    (tree.isInstanceOf[Type.Select] || parts.last.headOption.exists(_.isUpper)) &&
    parts.dropRight(1).forall(_.headOption.exists(_.isLower)) && !inImportOrPackage(tree)
  }
}
