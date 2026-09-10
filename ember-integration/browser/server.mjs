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
import { emberFixtures, runtimeFixtures, projectionFixtures } from '/main.js'
window.fixtures = emberFixtures
window.runtime = runtimeFixtures
window.projection = projectionFixtures
window.ready = true
</script>
</body></html>`

const server = createServer(async (request, response) => {
  const path = new URL(request.url, 'http://127.0.0.1').pathname
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
