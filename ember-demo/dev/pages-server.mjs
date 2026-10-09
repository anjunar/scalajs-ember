import { createServer } from 'node:http'
import { readFile } from 'node:fs/promises'
import { dirname, extname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../..')
const pages = resolve(repoRoot, 'target', 'ember-pages')
const port = Number(process.env.EMBER_PAGES_PORT ?? 4202)
const prefix = '/scalajs-ember/'
const types = new Map([
  ['.html', 'text/html; charset=utf-8'], ['.css', 'text/css; charset=utf-8'],
  ['.js', 'text/javascript; charset=utf-8'], ['.map', 'application/json; charset=utf-8'],
  ['.svg', 'image/svg+xml; charset=utf-8'],
])

await readFile(resolve(pages, 'index.html'))

createServer(async (request, response) => {
  const pathname = new URL(request.url, `http://127.0.0.1:${port}`).pathname
  if (pathname === '/') {
    response.writeHead(302, { Location: prefix }).end()
    return
  }
  if (!pathname.startsWith(prefix)) {
    await send(response, resolve(pages, '404.html'), 404)
    return
  }
  const relativePath = pathname.slice(prefix.length) || 'index.html'
  if (!/^[A-Za-z0-9_.-]+$/.test(relativePath)) {
    await send(response, resolve(pages, '404.html'), 404)
    return
  }
  await send(response, resolve(pages, relativePath), 200)
}).listen(port, '127.0.0.1', () => console.log(`http://127.0.0.1:${port}${prefix}`))

async function send(response, file, successStatus) {
  try {
    const content = await readFile(file)
    response.writeHead(successStatus, {
      'Content-Type': types.get(extname(file)) ?? 'application/octet-stream',
      'Cache-Control': 'no-store',
    }).end(content)
  } catch {
    response.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' }).end('Not found')
  }
}
