import { createServer } from 'node:http'
import { readFile } from 'node:fs/promises'
import { createHash } from 'node:crypto'
import { execFileSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import { mediaRequest } from './media-server.mjs'

const output = new URL('../../target/ember-browser-tests/', import.meta.url)

// Laut scheitern, wenn die Scala.js-Anwendung nicht gelinkt wurde. Ein Harness, der
// stillschweigend gegen eine alte oder fehlende Ausgabe laeuft, ist schlimmer als keiner.
await readFile(new URL('main.js', output)).catch(() => {
  console.error(
    'Kein Linkeroutput. Zuerst:\n' +
      '  sbt --server "scalajs-ember-integration/fullLinkJS"'
  )
  process.exit(1)
})

const html = `<!doctype html><html><body>
<div id="root"></div>
<script type="module">
import { emberFixtures, runtimeFixtures, projectionFixtures, formFixtures, selectionFixtures, editingFixtures, clipboardFixtures, mediaFixtures, highlightFixtures, tableFixtures, editorBench } from '/main.js'
window.fixtures = emberFixtures
window.runtime = runtimeFixtures
window.projection = projectionFixtures
window.form = formFixtures
window.selection = selectionFixtures
window.editing = editingFixtures
window.clipboard = clipboardFixtures
window.media = mediaFixtures
window.highlight = highlightFixtures
window.table = tableFixtures
window.bench = editorBench
window.ready = true
</script>
</body></html>`

// Das Modul im Serverprozess laden, um damit zu rendern. Genau das, was §15.2 zusichert -- ein
// Import darf weder `window` noch `document` lesen --, und ohne diese Zusicherung gaebe es
// kein serverseitig gerendertes Feld zum Absenden.
const { formFixtures, mediaFixtures } = await import(new URL('main.js', output).href)

/** Die Seite fuer den No-JS-Test: ein echtes Formular um das gerenderte Feld.
 *
 * §16 laesst Action, Methode, CSRF, Validierung und Persistenz ausdruecklich der Anwendung.
 * Genau das ist hier die Anwendung -- der Editor erfindet keinen Endpunkt, dieser Testserver
 * stellt einen bereit.
 */
function formPage(source) {
  return `<!doctype html><html><head><meta charset="utf-8"></head><body>
<form method="post" action="/submitted" id="editor-form">
<label for="body-source">Inhalt</label>
<div id="editor-host">${formFixtures.renderForNoScript(source)}</div>
<button type="submit" id="save">Speichern</button>
<button type="reset" id="revert">Zuruecksetzen</button>
</form>
</body></html>`
}

/** Was der Server bekommen hat, als lesbare Seite.
 *
 * Bewusst kein JSON-Body, sondern eine Seite: der No-JS-Weg ist POST/Redirect/GET (§16), und
 * ein Test, der eine Seite liest, prueft denselben Weg wie ein Benutzer.
 */
function submittedPage(fields) {
  const rows = [...fields.entries()]
    // Ein zusaetzlicher Umbruch nach `<pre>`: der HTML-Parser wirft genau einen direkt danach
    // weg -- dieselbe Regel, die §16 fuer `textarea` nennt. Ohne ihn verloere diese Seite den
    // fuehrenden Umbruch, den der Test gerade nachweisen soll.
    .map(([name, value]) => `<li data-field="${name}"><pre>
${escapeHtml(value)}</pre></li>`)
    .join('')
  return `<!doctype html><html><head><meta charset="utf-8"></head><body>
<p id="count">${fields.size}</p>
<ul id="received">${rows}</ul>
</body></html>`
}

function escapeHtml(text) {
  return text
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
}

async function readBody(request) {
  const chunks = []
  for await (const chunk of request) chunks.push(chunk)
  return Buffer.concat(chunks).toString('utf-8')
}

const server = createServer(async (request, response) => {
  const url = new URL(request.url, 'http://127.0.0.1')
  const path = url.pathname
  const profileAsset = /^\/profiles\/(text|markdown|standard)\/([A-Za-z0-9_.-]+\.js)$/.exec(path)
  if (profileAsset) {
    response.setHeader('Content-Type', 'text/javascript; charset=utf-8')
    response.end(await readFile(new URL(`../../target/editor-profiles/${profileAsset[1]}/${profileAsset[2]}`, import.meta.url)))
    return
  }
  if (path === '/profile') {
    const name = url.searchParams.get('name') ?? 'text'
    if (!['text', 'markdown', 'standard'].includes(name)) { response.writeHead(404).end(); return }
    response.setHeader('Content-Type', 'text/html; charset=utf-8')
    response.end(`<!doctype html><html lang="de"><head><meta charset="utf-8"><title>Ember Profil ${name}</title></head><body>
      <h1>Profil ${name}</h1><div id="root" aria-label="Dokument"></div><button id="value">Wert lesen</button><pre id="output"></pre>
      <script type="module">import { profile } from '/profiles/${name}/main.js'; window.profile = profile;
      profile.mount(document.getElementById('root'), 'Hello');
      document.getElementById('value').onclick = () => document.getElementById('output').textContent = profile.value();
      window.ready = true;</script></body></html>`)
    return
  }
  if (await mediaRequest(request, response, url, mediaFixtures)) return
  if (path === '/acceptance-build.json') {
    const root = fileURLToPath(new URL('../../', import.meta.url))
    let metadata = null
    try { metadata = JSON.parse(await readFile(new URL('../../target/editor-metadata/scalajs-ember-integration.json', import.meta.url), 'utf8')) }
    catch (error) { if (error.code !== 'ENOENT') throw error }
    // Hash linked bytes for provenance only; never inspect generated source.
    const integrationSha256 = createHash('sha256').update(await readFile(new URL('main.js', output))).digest('hex')
    response.setHeader('Content-Type', 'application/json; charset=utf-8')
    response.setHeader('Cache-Control', 'no-store')
    response.end(JSON.stringify({ capturedAt: new Date().toISOString(), integrationSha256,
      revision: execFileSync('git', ['rev-parse', 'HEAD'], { cwd: root, encoding: 'utf8' }).trim(),
      dirty: !!execFileSync('git', ['status', '--porcelain'], { cwd: root, encoding: 'utf8' }).trim(),
      uiCore: metadata?.compileModules.find(module => module.startsWith('com.anjunar:scalajs-ui-core_')) ?? null }))
    return
  }
  if (path === '/acceptance-trace.mjs') {
    response.setHeader('Content-Type', 'text/javascript; charset=utf-8')
    response.end(await readFile(new URL('acceptance-trace.mjs', import.meta.url)))
    return
  }

  if (path === '/toolbar.css') {
    response.setHeader('Content-Type', 'text/css; charset=utf-8')
    response.end(await readFile(new URL('../../ember-toolbar/src/main/resources/ember-toolbar.css', import.meta.url)))
    return
  }
  if (path === '/toolbar') {
    response.setHeader('Content-Type', 'text/html; charset=utf-8')
    response.end(`<!doctype html><html lang="de"><head><meta charset="utf-8">
      <meta name="viewport" content="width=device-width, initial-scale=1">
      <title>Ember – Toolbar und Dialoge</title><link rel="stylesheet" href="/toolbar.css">
      <style>body{font:1rem/1.6 system-ui;max-width:52rem;margin:3rem auto;padding:0 1rem}#toolbar-editor{min-height:12rem;padding:1rem;border:1px solid currentColor;margin:1rem 0}</style>
      </head><body><main><h1>Text bearbeiten</h1>
      <p>Tab wechselt zwischen Text und Werkzeugleiste. In der Leiste führen die Pfeiltasten zu den Aktionen.</p>
      <div id="toolbar-editor" aria-label="Dokument"></div><div id="toolbar-host"></div>
      <label><input type="checkbox" id="readonly"> Schreibgeschützt</label>
      <p><label>Notizen außerhalb des Editors <input id="outside"></label></p>
      <div id="dialogs"></div><input id="toolbar-file" type="file" hidden accept="image/png,image/jpeg,image/gif,image/webp">
      <p>Der Demo-Upload nimmt ausschließlich die PNG-Testdatei des Medien-Harness an.</p></main>
      <script type="module">import { toolbarFixtures } from '/main.js';
      window.toolbar = toolbarFixtures;
      toolbarFixtures.mount(document.getElementById('toolbar-editor'),document.getElementById('toolbar-host'),document.getElementById('dialogs'),document.getElementById('toolbar-file'));
      document.getElementById('readonly').addEventListener('change', event => toolbarFixtures.readonly(event.target.checked));
      window.ready = true;</script>${url.searchParams.get('trace') === '1' ? '<script type="module" src="/acceptance-trace.mjs"></script>' : ''}</body></html>`)
    return
  }

  // P19b: das serverseitig gerenderte Feld, in einem echten Formular.
  if (path === '/form') {
    response.setHeader('Content-Type', 'text/html; charset=utf-8')
    response.end(formPage(url.searchParams.get('source') ?? '# Titel\n\nEin Absatz.'))
    return
  }

  if (path === '/submitted' && request.method === 'POST') {
    const fields = new URLSearchParams(await readBody(request))
    response.setHeader('Content-Type', 'text/html; charset=utf-8')
    response.end(submittedPage(fields))
    return
  }

  if (path === '/') {
    response.setHeader('Content-Type', 'text/html; charset=utf-8')
    response.end(html)
    return
  }
  if (!/^\/[A-Za-z0-9_.-]+\.(js|map)$/.test(path)) {
    response.writeHead(404).end()
    return
  }
  try {
    response.setHeader('Content-Type', 'text/javascript')
    response.end(await readFile(new URL(path.slice(1), output)))
  } catch {
    response.writeHead(404).end()
  }
})

server.listen(4188, '127.0.0.1')
