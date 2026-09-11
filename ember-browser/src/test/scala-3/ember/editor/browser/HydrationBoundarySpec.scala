package ember.editor.browser

import ember.editor.core.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** The activation rules of §17, as rules (P20).
  *
  * ==What is here and what is in the browser gate==
  *
  * §17 has two kinds of statement. Some are about a live DOM -- does a capture read the typed value
  * rather than the attribute, does a failed claim leave the fallback standing, is a second
  * activation a no-op. Those need an engine and live in `editor-hydration.spec.mjs`.
  *
  * The rest are decisions: given what was captured and what succeeded, may the editor become
  * editable, and may it write a selection. Those are a function of their inputs, and a function is
  * better tested as one than through a browser.
  *
  * The split is the same one `EditorFieldSpec` makes for §16, and for the same reason: a rule
  * tested through one of its renderings is a rule tested once.
  */
final class HydrationBoundarySpec extends AnyFlatSpec with Matchers {

  private def snapshot(
      value: String = "# Titel",
      focused: Boolean = false,
      composing: Boolean = false
  ): HydrationSnapshot =
    HydrationSnapshot(value, 0, 0, SelectionDirection.Collapsed, focused, composing)

  private def decide(
      claimed: Boolean = true,
      pageHydrated: Boolean = true,
      captured: Option[HydrationSnapshot] = Some(snapshot()),
      served: String = "# Titel",
      imported: Boolean = false
  ): ActivationState =
    EditorActivation.decide(claimed, pageHydrated, captured, served, imported)

  // ---------------------------------------------------------------------------------------
  // Aktivierung
  // ---------------------------------------------------------------------------------------

  "Activation" should "happen when the claim and the page both succeeded" in {
    decide() shouldBe ActivationState.Active
    decide().isEditable shouldBe true
  }

  it should "need the outer hydration too, not only the local claim" in {
    // §17.6: "Nach lokal vollstaendig erfolgreichem Claim '''und''' aeusserem
    // Hydration-Abschluss". Zwei Bedingungen, nicht eine -- eine Boundary kann ihren Teilbaum
    // uebernommen haben, waehrend die Seite um sie herum noch adoptiert wird.
    decide(pageHydrated = false) shouldBe
      ActivationState.Deferred(DeferralReason.PageHydrating)
  }

  it should "fail when the claim did" in {
    // §17: "Editierbarkeit erst nach erfolgreichem Abschluss." Was bleibt, ist der Fallback --
    // und der liegt ausserhalb der Boundary, also ueberlebt er (§17.3).
    decide(claimed = false) should matchPattern { case ActivationState.Failed(_) => }
    decide(claimed = false).isEditable shouldBe false
  }

  it should "wait while a composition is running" in {
    // §17.6: "Waehrend bekannter Textarea-Composition […] wird der Wechsel aufgeschoben."
    decide(captured = Some(snapshot(composing = true))) shouldBe
      ActivationState.Deferred(DeferralReason.InputSession)
  }

  it should "wait for a focused field even without a known composition" in {
    // Der konservative Teil von §17.2: "Eine bereits vor Attach begonnene Composition laesst
    // sich nicht zuverlaessig nachtraeglich abfragen. Deshalb wird ein bereits fokussiertes
    // Source-Feld konservativ erst nach Blur oder einer ausdruecklichen Wechselaktion
    // erweitert."
    //
    // Fokus heisst nicht Composition. Aber ein fokussiertes Feld ist der einzige Ort, an dem
    // eine laufen koennte, und es gibt keinen Weg zu fragen -- "nein" zu raten hiesse, Text
    // mitten in einer Composition zu ersetzen.
    decide(captured = Some(snapshot(focused = true))) shouldBe
      ActivationState.Deferred(DeferralReason.InputSession)
  }

  it should "wait for a source the user changed before the script ran" in {
    // §17.4: "Falls Source seit SSR geaendert wurde, bleibt sie zunaechst unangetastet."
    decide(captured = Some(snapshot(value = "Selbst getippt")), served = "# Titel") shouldBe
      ActivationState.Deferred(DeferralReason.UnimportedSource)
  }

  it should "proceed once that source has been imported" in {
    decide(
      captured = Some(snapshot(value = "Selbst getippt")),
      served = "# Titel",
      imported = true
    ) shouldBe ActivationState.Active
  }

  it should "proceed when there is no source control at all" in {
    // Eine readonly Ansicht braucht keine editierbare Textarea (§16), und dann gibt es auch
    // nichts zu erfassen.
    decide(captured = None) shouldBe ActivationState.Active
  }

  it should "be idempotent" in {
    // §17s Abweichungstabelle: "Wiederholtes Enhancement: Idempotent; genau ein Controller,
    // keine doppelten Handler oder Observer." Eine Entscheidungsfunktion ist das von selbst --
    // zweimal dieselbe Frage gibt dieselbe Antwort, und der Aufrufer handelt nur bei einer
    // Aenderung.
    decide() shouldBe decide()
    decide(claimed = false) shouldBe decide(claimed = false)
  }

  it should "put the failed claim before every deferral" in {
    // Reihenfolge zaehlt: ein fehlgeschlagener Claim ist kein Warten, sondern ein Ende. Ihn als
    // "aufgeschoben" zu melden liesse einen Aufrufer auf etwas warten, das nicht kommt.
    decide(claimed = false, pageHydrated = false, captured = Some(snapshot(focused = true))) should
      matchPattern { case ActivationState.Failed(_) => }
  }

  // ---------------------------------------------------------------------------------------
  // Selection
  // ---------------------------------------------------------------------------------------

  "A selection" should "be restorable only for a field that had focus" in {
    // §17.7: "'''Nur wenn''' der Nutzer das Feld tatsaechlich fokussiert hatte […]. Ansonsten
    // keine Fokus-/Selection-Schreibaktion." Die negative Haelfte kostet etwas: ein Editor, der
    // sich beim Laden selbst fokussiert, nimmt den Fokus dort weg, wo der Benutzer war.
    EditorActivation.mayRestoreSelection(Some(snapshot(focused = true))) shouldBe true
    EditorActivation.mayRestoreSelection(Some(snapshot(focused = false))) shouldBe false
    EditorActivation.mayRestoreSelection(None) shouldBe false
  }

  it should "be absent from a snapshot without focus" in {
    snapshot(focused = false).selection shouldBe None
  }

  it should "carry its range when there was focus" in {
    val focused = HydrationSnapshot("abc", 1, 3, SelectionDirection.Backward, true, false)

    focused.selection shouldBe Some((1, 3))
    focused.selectionDirection shouldBe SelectionDirection.Backward
  }

  "The selection direction" should "survive, so that the next arrow key moves the right end" in {
    // P20 nennt `SelectionDirection` ausdruecklich in der Testliste. Sie wegzulassen machte
    // jede wiederhergestellte Auswahl vorwaerts gerichtet.
    // Und zwar als der Begriff des Kerns (§11), nicht als zweiter daneben: ein eigenes Enum
    // braeuchte an jeder Verwendung eine Umrechnung, und genau dort liefen die beiden
    // irgendwann auseinander.
    TextSelectionDirection.parse("forward") shouldBe SelectionDirection.Forward
    TextSelectionDirection.parse("backward") shouldBe SelectionDirection.Backward
    TextSelectionDirection.parse("none") shouldBe SelectionDirection.Collapsed
    TextSelectionDirection.parse("") shouldBe SelectionDirection.Collapsed
  }

  // ---------------------------------------------------------------------------------------
  // Die Diagnosen
  // ---------------------------------------------------------------------------------------

  "A mismatch" should "say which node and what differed" in {
    // §17s Abweichungstabelle verlangt "klare Diagnose". Eine Meldung ohne Knoten-ID liesse den
    // Leser das ganze Dokument absuchen.
    val problem = HydrationProblem.TextMismatch(NodeId("t1"), "Erwartet", "Gefunden")

    problem.render should include("t1")
    problem.render should include("Erwartet")
    problem.render should include("Gefunden")
  }

  it should "collect several, and say so in one message" in {
    val failure = HydrationMismatch(
      Vector(
        HydrationProblem.TagMismatch(NodeId("a"), "p", "div"),
        HydrationProblem.ChildCount(NodeId("b"), 2, 3)
      )
    )

    failure.getMessage should include("a")
    failure.getMessage should include("b")
  }

  it should "name a missing attribute as missing, not as wrong" in {
    val problem = HydrationProblem.AttributeMismatch(NodeId("n"), "data-ember-node", "n", None)

    problem.render should include("nicht gesetzt")
  }
}
