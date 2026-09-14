// Opt-in operator trace for physical device checks; only the test page is observed.
const editor = document.getElementById('toolbar-editor')
const panel = document.createElement('section')
const title = document.createElement('h2'); title.textContent = 'Geräteabnahme'
const help = document.createElement('p'); help.textContent = 'Nur Testtext verwenden. Aufzeichnung starten, Eingabe prüfen, stoppen und den Trace herunterladen.'
const status = document.createElement('p'); status.setAttribute('role', 'status')
const start = document.createElement('button'); start.textContent = 'Trace starten'
const stop = document.createElement('button'); stop.textContent = 'Trace stoppen'
const download = document.createElement('button'); download.textContent = 'Trace herunterladen'
panel.append(title, help, start, stop, download, status); document.querySelector('main').append(panel)
let events = [], active = false, startedAt = null, truncated = false
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
  events = []; truncated = false; startedAt = new Date().toISOString(); active = true
  // Bubble runs after the editor's handlers. A capture-listener microtask can run
  // between native callbacks, before the editor has committed the same event.
  for (const type of types) {
    document.addEventListener(type, record, true)
    document.addEventListener(type, afterDispatch)
  }
  status.textContent = 'Aufzeichnung läuft.'
}
stop.onclick = () => {
  active = false
  for (const type of types) {
    document.removeEventListener(type, record, true)
    document.removeEventListener(type, afterDispatch)
  }
  status.textContent = `${events.length} Ereignisse aufgezeichnet.`
}
download.onclick = () => {
  stop.click()
  const data = { startedAt, userAgent: navigator.userAgent, language: navigator.language, truncated, events }
  const url = URL.createObjectURL(new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' }))
  const anchor = document.createElement('a'); anchor.href = url; anchor.download = 'ember-device-trace.json'; anchor.click()
  setTimeout(() => URL.revokeObjectURL(url), 0)
}
window.addEventListener('pagehide', () => stop.click(), { once: true })
