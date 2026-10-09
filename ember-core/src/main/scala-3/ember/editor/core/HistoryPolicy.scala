package ember.editor.core

/** Was eine Aenderung fuer die History bedeuten soll.
  *
  * §14 fuehrt sie zusammen mit [[Origin]] als "typisierte Metadaten" auf. Der Kern wertet sie nicht
  * aus -- er traegt sie, wie er [[TransactionMeta.label]] traegt. Ausgewertet wird sie in
  * `ember-history`, und ohne dieses Modul ist sie folgenlos.
  *
  * Sie ist eine Ausnahme, kein Regelfall: fehlt sie, entscheiden die Gruppierungsregeln aus §14
  * anhand dessen, was tatsaechlich passiert ist. Wer sie setzt, weiss etwas, das sich am ChangeSet
  * nicht ablesen laesst.
  */
enum HistoryPolicy:

  /** Eine eigene Undo-Stufe, auch wenn die Regeln verschmelzen wuerden. */
  case Push

  /** Mit der laufenden Gruppe verschmelzen, wenn es eine gibt. */
  case Merge

  /** Nicht aufzeichnen. Fuer Aenderungen, die niemand rueckgaengig machen koennen soll. */
  case Ignore
