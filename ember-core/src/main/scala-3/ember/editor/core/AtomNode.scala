package ember.editor.core

/** Ein atomarer Knoten: sein Inneres ist kein normaler Textbereich.
  *
  * Entspricht Lexicals `DecoratorNode`, aber bewusst enger (§3.4): keine Slots, keine editierbaren
  * Teilbereiche. Eine Bildunterschrift ist ein fachlicher Container, nicht ein impliziter UI-Slot
  * eines atomaren Blatts.
  */
trait AtomNode extends EditorNode
