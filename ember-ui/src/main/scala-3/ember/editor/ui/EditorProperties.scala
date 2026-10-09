package ember.editor.ui

import ember.editor.core.*
import ui.core.state.{Disposable, Property, ReadOnlyProperty}

/** Adapter zwischen Sitzungszustand und UI-Properties.
  *
  * ==Warum nur lesend==
  *
  * §4 haelt zu `Property.scala` fest: "Eine Property ist weder Transaktion noch History." Sie
  * benachrichtigt synchron und einzeln; ein schreibender Adapter wuerde eine Aenderung an der
  * Commit-Grenze vorbeifuehren und damit Atomaritaet, ChangeSet und Positionsabbildung umgehen. Wer
  * aendern will, nimmt `session.update` oder einen Command.
  *
  * Diese Properties sind also eine Einbahnstrasse: Commit rein, Anzeige raus. Genau richtig fuer
  * eine Toolbar, die "kann rueckgaengig machen" anzeigt, oder eine Statuszeile mit der Wortzahl --
  * die Faelle aus §22, fuer die es einen UI-nahen Zugang braucht.
  */
object EditorProperties:

  /** Eine Property, die bei jedem Commit den abgeleiteten Wert nachfuehrt.
    *
    * Der Rueckgabewert enthaelt die Aufraeumaktion: ohne sie ueberlebte die Registrierung die
    * Komponente, die sie angelegt hat.
    */
  def derived[A](session: EditorSession)(
      read: EditorState => A
  ): (ReadOnlyProperty[A], Disposable) =
    val property = Property(read(session.state))
    val commits  = session.onCommit(commit => property.set(read(commit.current)))
    (property, Disposable(() => commits.dispose()))

  /** Der aktuelle Dokumentstand als Property. */
  def document(session: EditorSession): (ReadOnlyProperty[Document], Disposable) =
    derived(session)(_.document)

  /** Die aktuelle Auswahl als Property. */
  def selection(session: EditorSession): (ReadOnlyProperty[Option[Selection]], Disposable) =
    derived(session)(_.selection)

  /** Die Sitzungsrevision als Property. Steigt auch bei reinen Auswahlaenderungen (§9). */
  def revision(session: EditorSession): (ReadOnlyProperty[Long], Disposable) =
    derived(session)(_.revision.value)
