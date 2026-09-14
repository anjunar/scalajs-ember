// Opt-in operator trace for physical device checks; only the test page is observed.
const editor = document.getElementById('toolbar-editor')
const panel = document.createElement('section')
const title = document.createElement('h2'); title.textContent = 'Geräteabnahme'
const help = document.createElement('p'); help.textContent = 'Pro Prüfschritt einen Trace aufnehmen. Nur den vorgegebenen Testtext verwenden. Eingabe prüfen, stoppen, Ergebnis und Beobachtung eintragen und herunterladen. Ein Trace allein ist keine Gerätefreigabe.'
const status = document.createElement('p'); status.setAttribute('role', 'status')
const start = document.createElement('button'); start.textContent = 'Trace starten'
const stop = document.createElement('button'); stop.textContent = 'Trace stoppen'
const download = document.createElement('button'); download.textContent = 'Trace herunterladen'
const fields = document.createElement('fieldset')
const legend = document.createElement('legend'); legend.textContent = 'Manuelles Ergebnisprotokoll'
fields.append(legend)
function field(name, caption, options) {
  const label = document.createElement('label'); label.textContent = caption + ' '
  const input = document.createElement(options ? 'select' : name === 'notes' ? 'textarea' : 'input')
  input.id = `acceptance-${name}`
  label.htmlFor = input.id
  if (options) for (const [value, text] of options) input.add(new Option(text, value))
  else { input.maxLength = name === 'notes' ? 4000 : 200; input.autocomplete = 'off' }
  const row = document.createElement('p'); row.append(label, input); fields.append(row)
  return input
}
const tester = field('tester', 'Testperson (Kürzel)')
const environment = field('environment', 'Gerät, Windows- und Browser-Version')
const inputMethod = field('input-method', 'Eingabemethode und Sprache')
const caseId = field('case', 'Prüfschritt', [
  ['keyboard', 'W01 – Tastatur, Auswahl und Unicode'],
  ['ime-confirm', 'W02 – IME bestätigen und Undo'],
  ['ime-cancel', 'W03 – IME abbrechen'],
  ['ime-blur', 'W04 – Fokuswechsel während IME'],
  ['ime-replace', 'W05 – Auswahl ersetzen'],
  ['toolbar', 'W06 – Toolbar und Linkdialog'],
])
const outcome = field('outcome', 'Ergebnis', [['open', 'Offen / noch nicht geprüft'],
  ['passed', 'Bestanden (Beobachtung eintragen)'], ['failed', 'Fehlgeschlagen'], ['blocked', 'Nicht durchführbar']])
const notes = field('notes', 'Beobachtung / Abweichung')
panel.append(title, help, fields, start, stop, download, status); document.querySelector('main').append(panel)
let events = [], active = false, startedAt = null, stoppedAt = null, truncated = false
let build = null
const buildReady = fetch('/acceptance-build.json', { cache: 'no-store' })
  .then(response => { if (!response.ok) throw new Error('Build evidence unavailable'); return response.json() })
  .then(value => { build = value })
  .catch(() => { status.textContent = 'Build-Nachweis fehlt; vor einer Abnahme den Testserver prüfen.' })
const pending = new WeakMap()
const types = ['beforeinput', 'input', 'compositionstart', 'compositionupdate', 'compositionend', 'keydown', 'selectionchange', 'focusin', 'focusout']
function record(event) {
  if (!active || (event.type !== 'selectionchange' && !editor.contains(event.target))) return
  if (events.length >= 2000) { truncated = true; stop.click(); status.textContent = 'Grenze von 2000 Ereignissen erreicht.'; return }
  const row = { time: performance.now(), type: event.type, trusted: event.isTrusted,
    inputType: event.inputType ?? null, data: event.data ?? null, key: event.key ?? null, isComposing: event.isComposing ?? null }
  events.push(row); pending.set(event, row)
  row.before = { domText: editor.textContent, modelText: window.toolbar.text() }
}
function afterDispatch(event) {
  const row = pending.get(event)
  if (row) {
    row.domText = editor.textContent
    row.modelText = window.toolbar.text()
    const selection = document.getSelection()
    row.selection = selection ? { anchorOffset: selection.anchorOffset, focusOffset: selection.focusOffset, collapsed: selection.isCollapsed } : null
    row.defaultPrevented = event.defaultPrevented
    pending.delete(event)
  }
}
start.onclick = () => {
  if (active) return
  events = []; truncated = false; startedAt = new Date().toISOString(); stoppedAt = null; active = true
  outcome.value = 'open'; notes.value = ''
  // Bubble runs after the editor's handlers. A capture-listener microtask can run
  // between native callbacks, before the editor has committed the same event.
  for (const type of types) {
    document.addEventListener(type, record, true)
    document.addEventListener(type, afterDispatch)
  }
  status.textContent = 'Aufzeichnung läuft.'
  editor.focus()
}
stop.onclick = () => {
  if (active) stoppedAt = new Date().toISOString()
  active = false
  for (const type of types) {
    document.removeEventListener(type, record, true)
    document.removeEventListener(type, afterDispatch)
  }
  status.textContent = `${events.length} Ereignisse aufgezeichnet.`
}
download.onclick = async () => {
  stop.click()
  const data = { formatVersion: 1, kind: 'operator-device-trace', reviewStatus: 'requires-human-review',
    build, operator: { tester: tester.value.trim(), environment: environment.value.trim(),
      inputMethod: inputMethod.value.trim(), caseId: caseId.value, outcome: outcome.value, notes: notes.value.trim() },
    startedAt, stoppedAt, userAgent: navigator.userAgent, language: navigator.language, truncated, events: structuredClone(events) }
  await buildReady
  data.build = build
  const url = URL.createObjectURL(new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' }))
  const anchor = document.createElement('a'); anchor.href = url
  anchor.download = `ember-device-${caseId.value}-${(startedAt ?? 'not-started').replaceAll(':', '-')}.json`; anchor.click()
  setTimeout(() => URL.revokeObjectURL(url), 0)
}
window.addEventListener('pagehide', () => stop.click(), { once: true })
