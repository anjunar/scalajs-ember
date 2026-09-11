import { createServer } from 'node:http'
import { readFile } from 'node:fs/promises'

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
import { emberFixtures, runtimeFixtures, projectionFixtures, formFixtures } from '/main.js'
window.fixtures = emberFixtures
window.runtime = runtimeFixtures
window.projection = projectionFixtures
window.form = formFixtures
window.ready = true
</script>
</body></html>`

// Das Modul im Serverprozess laden, um damit zu rendern. Genau das, was §15.2 zusichert -- ein
// Import darf weder `window` noch `document` lesen --, und ohne diese Zusicherung gaebe es
// kein serverseitig gerendertes Feld zum Absenden.
const { formFixtures } = await import(new URL('main.js', output).href)

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
