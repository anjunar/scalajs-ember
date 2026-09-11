package ember.editor.browser

/** Reads an `inputType` as an [[InputIntent]].
  *
  * ==Why a table and not a `when` in the controller==
  *
  * Because this is the one part of the input pipeline that is a pure function of two strings, and
  * because the table is long. §15.2 warns that the Input Events Level 2 specification "ist im
  * geprueften Stand ein Working Draft und kein Beleg fuer einheitliches Browserverhalten" -- so
  * this table will grow as real traces arrive, and it should grow in a place a test can reach
  * without an engine.
  *
  * ==What is deliberately not here==
  *
  * The target ranges. `getTargetRanges()` returns live DOM ranges and needs [[DomPositionMap]] to
  * mean anything; mixing it in would make the whole adapter need a browser. The controller reads
  * them where it needs them, which is only for the intents that name a range they did not select.
  */
object BeforeInputAdapter:

  /** @param inputType
    *   the event's `inputType`.
    * @param data
    *   the event's `data`, when it carried text.
    * @param transferText
    *   `dataTransfer.getData("text/plain")`, when there was one. Paste and drop carry their
    *   payload there rather than in `data`.
    */
  def intentOf(
      inputType: String,
      data: Option[String] = None,
      transferText: Option[String] = None
  ): InputIntent =
    inputType match
      // -----------------------------------------------------------------------------------
      // Text
      // -----------------------------------------------------------------------------------
      case "insertText" => InputIntent.InsertText(data.getOrElse(""))

      // Autocorrect, spell-check replacement, and the "smart" substitutions. §15.2 lists
      // "Autokorrektur-Replacement" as its own test case because the range it replaces is not
      // the selection -- it comes from `getTargetRanges`.
      case "insertReplacementText" => InputIntent.ReplaceText(data.getOrElse(""))

      // Dictation and similar. The composition types belong to P23; here they are text, and a
      // controller in `Composing` never asks this adapter anyway.
      case "insertFromComposition" | "insertCompositionText" =>
        InputIntent.InsertText(data.getOrElse(""))

      case "insertParagraph"       => InputIntent.InsertParagraph
      case "insertLineBreak"       => InputIntent.InsertLineBreak

      // -----------------------------------------------------------------------------------
      // Loeschen
      // -----------------------------------------------------------------------------------
      case "deleteContentBackward" =>
        InputIntent.Delete(Direction.Backward, Granularity.Character)
      case "deleteContentForward" =>
        InputIntent.Delete(Direction.Forward, Granularity.Character)
      case "deleteWordBackward" => InputIntent.Delete(Direction.Backward, Granularity.Word)
      case "deleteWordForward"  => InputIntent.Delete(Direction.Forward, Granularity.Word)
      case "deleteSoftLineBackward" | "deleteHardLineBackward" =>
        InputIntent.Delete(Direction.Backward, Granularity.Line)
      case "deleteSoftLineForward" | "deleteHardLineForward" =>
        InputIntent.Delete(Direction.Forward, Granularity.Line)

      // A plain `deleteContent` has no direction: the selection is what goes. Reporting it as
      // backward would delete one character too many at a caret that never existed.
      case "deleteContent" => InputIntent.Delete(Direction.Backward, Granularity.Selection)

      case "deleteByCut"  => InputIntent.Transfer(TransferKind.Cut, None)
      case "deleteByDrag" => InputIntent.Delete(Direction.Forward, Granularity.Selection)

      // -----------------------------------------------------------------------------------
      // Format
      // -----------------------------------------------------------------------------------
      case "formatBold"          => InputIntent.Format(NativeFormat.Bold)
      case "formatItalic"        => InputIntent.Format(NativeFormat.Italic)
      case "formatUnderline"     => InputIntent.Format(NativeFormat.Underline)
      case "formatStrikeThrough" => InputIntent.Format(NativeFormat.StrikeThrough)
      case "formatSuperscript"   => InputIntent.Format(NativeFormat.Superscript)
      case "formatSubscript"     => InputIntent.Format(NativeFormat.Subscript)

      // -----------------------------------------------------------------------------------
      // History und Transfer
      // -----------------------------------------------------------------------------------
      case "historyUndo" => InputIntent.History(HistoryDirection.Undo)
      case "historyRedo" => InputIntent.History(HistoryDirection.Redo)

      // §15.2: "dieselbe Aktion aus Clipboard-Event und beforeinput genau einmal verarbeiten."
      // `data` is empty for a paste; the payload sits in the data transfer.
      case "insertFromPaste" | "insertFromPasteAsQuotation" =>
        InputIntent.Transfer(TransferKind.Paste, transferText.orElse(data))
      case "insertFromDrop" => InputIntent.Transfer(TransferKind.Drop, transferText.orElse(data))

      case other => InputIntent.Unknown(other)

  /** Whether an `inputType` describes something that is about to change the document.
    *
    * Used before the ownership and readonly checks, so that a readonly editor can refuse -- with
    * a `preventDefault` -- exactly the events that would otherwise edit its DOM, and leave
    * everything else alone.
    */
  def editsDocument(inputType: String): Boolean = intentOf(inputType).editsDocument
