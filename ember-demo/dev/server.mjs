// Der Dev-Server der Demo. Node und sonst nichts.
//
// Kein vite, kein Bundler, keine npm-Abhaengigkeit: die Demo besteht aus einer HTML-Huelle, einem
// Stylesheet und der Linkerausgabe. Ein Build-Schritt dafuer waere mehr Werkzeug als Editor --
// und ein Werkzeug, das jemand pflegen muss, bevor er den Editor sehen kann.
//
// Gebraucht wird er trotzdem: `main.js` ist ein ES-Modul, und ein Modulimport ueber `file://`
// scheitert an der Origin-Pruefung des Browsers.

import { createServer } from 'node:http'
import { readFile } from 'node:fs/promises'

const port = Number(process.env.EMBER_DEMO_PORT ?? 4200)
const here = new URL('./', import.meta.url)
const output = new URL(
  process.env.EMBER_DEMO_FULL ? '../../target/ember-demo-full/' : '../../target/ember-demo/',
  import.meta.url
)

const linkCommand = process.env.EMBER_DEMO_FULL
  ? 'sbt --server "scalajs-ember-demo/fullLinkJS"'
  : 'sbt --server "scalajs-ember-demo/fastLinkJS"'

// Laut scheitern, wenn nicht gelinkt wurde. Ein Server, der stillschweigend eine alte oder
// fehlende Ausgabe ausliefert, ist schlimmer als keiner.
await readFile(new URL('main.js', output)).catch(() => {
  console.error(`Kein Linkeroutput. Zuerst:\n  ${linkCommand}`)
  process.exit(1)
})

const types = {
  '.js': 'text/javascript; charset=utf-8',
  '.map': 'application/json; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.html': 'text/html; charset=utf-8',
  '.svg': 'image/svg+xml; charset=utf-8',
}

async function send(response, url, contentType) {
  try {
    response.setHeader('Content-Type', contentType)
    // Ohne Cache: wer neu linkt, will neu laden und nicht erst den Browser ueberreden.
    response.setHeader('Cache-Control', 'no-store')
    response.end(await readFile(url))
  } catch {
    response.writeHead(404).end()
  }
}

const server = createServer(async (request, response) => {
  const path = new URL(request.url, `http://127.0.0.1:${port}`).pathname

  if (path === '/') {
    await send(response, new URL('index.html', here), types['.html'])
    return
  }

  if (path === '/style.css') {
    await send(response, new URL('style.css', here), types['.css'])
    return
  }

  // Das Bild der Demo (P16). Es liegt hier und nicht im Linkerverzeichnis, weil es kein
  // Linkeroutput ist -- und es ist eine echte Datei unter einem relativen Pfad, weil genau das
  // die Quelle ist, die `MediaUrlPolicy.default` zulaesst.
  if (path === '/ember.svg') {
    await send(response, new URL('ember.svg', here), types['.svg'])
    return
  }

  // Nur flache Dateinamen aus dem Linkerverzeichnis. Kein Pfad, kein `..`, keine Ueberraschung.
  const match = /^\/([A-Za-z0-9_.-]+\.(js|map))$/.exec(path)
  if (match) {
    await send(response, new URL(match[1], output), types[`.${match[2]}`])
    return
  }

  response.writeHead(404).end()
})

server.listen(port, '127.0.0.1', () => {
  console.log(`Ember-Demo auf http://127.0.0.1:${port}`)
})
