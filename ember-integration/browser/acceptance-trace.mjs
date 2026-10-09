// Opt-in operator trace for physical device checks; only the test page is observed.
const editor = document.getElementById('toolbar-editor')
const panel = document.createElement('section')
const title = document.createElement('h2'); title.textContent = 'Geräteabnahme'
const help = document.createElement('p'); help.textContent = 'Pro Prüfschritt einen Trace aufnehmen. Nur den vorgegebenen Testtext verwenden. Eingabe prüfen, stoppen, Ergebnis und Beobachtung eintragen und herunterladen. Ein Trace allein ist keine Gerätefreigabe.'
const status = document.createElement('p'); status.setAttribute('role', 'status')
const start = document.createElement('button'); start.textContent = 'Trace starten'
const stop = document.createElement('button'); stop.textContent = 'Trace stoppen'
const download = document.createElement('button'); download.textContent = 'Trace herunterladen'
const exportPanel = document.createElement('section'); exportPanel.hidden = true
const exportHelp = document.createElement('p')
exportHelp.textContent = 'Falls kein Download erscheint: den Dateilink verwenden oder das vollständige JSON aus dem Textfeld kopieren und als .json speichern.'
const exportLink = document.createElement('a'); exportLink.textContent = 'Trace-Datei speichern'
const exportLabel = document.createElement('label'); exportLabel.textContent = 'Trace-JSON zum Kopieren'
const exportText = document.createElement('textarea'); exportText.id = 'acceptance-export-json'
exportText.readOnly = true; exportText.rows = 12; exportText.style.width = '100%'
exportLabel.htmlFor = exportText.id
exportPanel.append(exportHelp, exportLink, exportLabel, exportText)
let exportUrl = null
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
panel.append(title, help, fields, start, stop, download, status, exportPanel); document.querySelector('main').append(panel)
let events = [], active = false, startedAt = null, stoppedAt = null, truncated = false
let build = null
let buildError = null
const buildAbort = new AbortController()
const buildTimeout = setTimeout(() => buildAbort.abort(), 5000)
const buildReady = fetch('/acceptance-build.json', { cache: 'no-store', signal: buildAbort.signal })
  .then(response => { if (!response.ok) throw new Error('Build evidence unavailable'); return response.json() })
  .then(value => { build = value })
  .catch(() => { buildError = 'Build-Nachweis nicht verfügbar (Anfrage fehlgeschlagen oder nach 5 Sekunden abgebrochen).' })
  .finally(() => clearTimeout(buildTimeout))
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
  if (active || download.disabled) return
  exportPanel.hidden = true; exportText.value = ''; exportLink.removeAttribute('href')
  if (exportUrl) { URL.revokeObjectURL(exportUrl); exportUrl = null }
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
  if (download.disabled) return
  stop.click()
  download.disabled = true; start.disabled = true
  status.textContent = 'Export wird vorbereitet …'
  try {
    const data = { formatVersion: 1, kind: 'operator-device-trace', reviewStatus: 'requires-human-review',
      build, operator: { tester: tester.value.trim(), environment: environment.value.trim(),
        inputMethod: inputMethod.value.trim(), caseId: caseId.value, outcome: outcome.value, notes: notes.value.trim() },
      startedAt, stoppedAt, userAgent: navigator.userAgent, language: navigator.language, truncated, events: structuredClone(events) }
    await buildReady
    data.build = build
    if (buildError) data.buildError = buildError
    // Keep a visible, copyable snapshot even when the browser blocks downloads.
    exportText.value = JSON.stringify(data, null, 2)
    exportPanel.hidden = false
    if (exportUrl) URL.revokeObjectURL(exportUrl)
    exportLink.removeAttribute('href')
    exportUrl = URL.createObjectURL(new Blob([exportText.value], { type: 'application/json' }))
    exportLink.href = exportUrl
    exportLink.download = `ember-device-${data.operator.caseId}-${(data.startedAt ?? 'not-started').replaceAll(':', '-')}.json`
    status.textContent = buildError
      ? 'Trace bereit; Build-Nachweis fehlt, Geräteabnahme bleibt offen. JSON kann gesichert werden.'
      : 'Trace bereit. Falls kein Download erscheint, Dateilink oder JSON-Textfeld verwenden.'
    // Retain the URL until the next export/run; downloads need not consume it synchronously.
    exportLink.click()
  } catch {
    status.textContent = 'Download konnte nicht vorbereitet werden. Vorhandenes JSON aus dem Textfeld sichern; die Seite nicht neu laden.'
  } finally {
    download.disabled = false; start.disabled = false
  }
}
window.addEventListener('pagehide', () => stop.click(), { once: true })
