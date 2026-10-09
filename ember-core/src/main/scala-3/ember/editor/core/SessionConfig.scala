package ember.editor.core

import scala.collection.mutable

/** Was eine Sitzung mitbekommt.
  *
  * [[ResolvedExtensions.sessionConfig]] baut sie aus Extension-Beitraegen zusammen; direkt
  * uebergeben werden kann sie weiterhin, etwa in Tests.
  *
  * @param mappingRetention
  *   wie viele Commit-Abbildungen aufgehoben werden. Begrenzt, weil §11 die Retention ausdruecklich
  *   beschraenkt: ein Bookmark, dessen Abbildungen herausgefallen sind, laeuft ab -- und das ist
  *   richtig so, denn die Alternative waere eine unbegrenzt wachsende Kette oder eine geratene
  *   Position.
  * @param errorSink
  *   nimmt Fehler entgegen, die niemand zurueckgeben kann -- vor allem geworfene Listener.
  *   Voreinstellung ist ein Nichtstuer; eine Anwendung sollte hier protokollieren.
  * @param strictCommands
  *   ob geprueft wird, dass ein Handler mit `Pass` den Entwurf nicht veraendert hat (§12). In der
  *   Entwicklung an, in Produktion abschaltbar -- der Vertrag gilt dann unveraendert, nur
  *   unbeobachtet.
  */
final case class SessionConfig(
    fields: Vector[StateField[?]] = Vector.empty,
    preCommitRules: Vector[PreCommitRule] = Vector.empty,
    selectionSupport: SelectionSupport = SelectionSupport.core,
    transforms: Vector[Transform[?]] = Vector.empty,
    commands: Vector[CommandRegistration[?]] = Vector.empty,
    transformBudget: TransformBudget = TransformBudget.default,
    mappingRetention: Int = 64,
    strictCommands: Boolean = true,
    errorSink: EditorError => Unit = _ => ()
)
