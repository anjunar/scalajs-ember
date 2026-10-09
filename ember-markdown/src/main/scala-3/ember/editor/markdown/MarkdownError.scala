package ember.editor.markdown

import ember.editor.core.*

import scala.collection.mutable

/** A conversion that produced nothing. */
sealed trait MarkdownError extends EditorError

object MarkdownError:

  final case class Unparseable(cause: ParseError) extends MarkdownError:
    def message: String = s"Der Quelltext liess sich nicht parsen: ${cause.render}"

  final case class DuplicateRules(ids: Vector[String]) extends MarkdownError:
    def message: String =
      ids.mkString("Mehrfach registrierte Regeln: ", ", ", ". Eine haette still gewonnen.")

  final case class NoRule(syntax: String, at: SourceSpan) extends MarkdownError:
    def message: String =
      s"Fuer `$syntax` bei ${at.render} ist keine MarkdownRule registriert. " +
        "Ein stilles Weglassen waere ein Dokument, das der Quelltext nicht meint."

  final case class NoNodeRule(node: NodeId, nodeClass: String) extends MarkdownError:
    def message: String =
      s"Fuer `${node.value}` ($nodeClass) ist keine MarkdownRule registriert."

  final case class WouldLose(losses: Vector[MarkdownDiagnostic]) extends MarkdownError:
    def message: String =
      losses
        .map(_.render)
        .mkString(
          "Der Export verloere Information (LossPolicy.Strict): ",
          "; ",
          ". Mit `LossPolicy.AllowLossy` ist es eine Diagnose statt eines Fehlers."
        )

  final case class InvalidDocument(violations: Vector[Violation]) extends MarkdownError:
    def message: String =
      violations.map(_.render).mkString("Das dekodierte Dokument ist ungueltig: ", "; ", "")
