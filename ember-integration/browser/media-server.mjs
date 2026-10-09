import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { createHash, randomUUID } from 'node:crypto'

// This harness accepts one known, valid PNG. It is deliberately not an upload backend.
export const fixturePng = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j3ioAAAAASUVORK5CYII=', 'base64')
const storage = new URL('../../target/p26-media/', import.meta.url)
const submissions = new Map()
const escape = value => String(value).replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;')

async function multipart(request) {
  let bytes = 0
  const chunks = []
  for await (const chunk of request) {
    bytes += chunk.length
    if (bytes > 2 * 1024 * 1024) throw new Error('Multipart request too large')
    chunks.push(chunk)
  }
  return new Request('http://localhost/', { method: 'POST', headers: request.headers, body: Buffer.concat(chunks) }).formData()
}

async function store(file) {
  if (!file || typeof file.arrayBuffer !== 'function' || file.type !== 'image/png' || file.size > 65536) throw new Error('Nur die PNG-Testdatei ist erlaubt.')
  const bytes = Buffer.from(await file.arrayBuffer())
  if (!bytes.equals(fixturePng)) throw new Error('Ungültiger PNG-Testinhalt.')
  const mediaId = createHash('sha256').update(bytes).digest('hex')
  await mkdir(storage, { recursive: true })
  await writeFile(new URL(`${mediaId}.png`, storage), bytes)
  return { src: `/media/assets/${mediaId}.png`, mediaId }
}

export function mediaPage(fixtures, source = 'Hello world', alt = '', title = '', error = '') {
  return `<!doctype html><html><head><meta charset="utf-8"></head><body>
<p id="media-error" role="status">${escape(error)}</p>
<form action="/media/submit" method="post" enctype="multipart/form-data">
${fixtures.renderSource(source)}
<label>Beschreibung<input id="media-alt" name="alt" value="${escape(alt)}"></label>
<label>Titel<input name="title" value="${escape(title)}"></label>
<label>Bild<input id="media-file" type="file" name="file" accept="image/png"></label>
<input type="hidden" name="position" value="append">
<button id="media-save" type="submit">Speichern</button>
</form></body></html>`
}

export async function mediaRequest(request, response, url, fixtures) {
  if (!url.pathname.startsWith('/media/')) return false
  const path = url.pathname
  if (path === '/media/form') {
    response.setHeader('Content-Type', 'text/html; charset=utf-8')
    response.end(mediaPage(fixtures, url.searchParams.get('source') ?? 'Hello world'))
  } else if (path === '/media/upload' && request.method === 'POST') {
    try {
      const data = await multipart(request)
      const reference = await store(data.get('file'))
      response.setHeader('Content-Type', 'application/json')
      response.end(JSON.stringify(reference))
    } catch (error) {
      response.writeHead(422, { 'Content-Type': 'application/json' }).end(JSON.stringify({ error: error.message }))
    }
  } else if (path === '/media/submit' && request.method === 'POST') {
    let source = '', alt = '', title = ''
    try {
      const data = await multipart(request)
      source = String(data.get('body') ?? '')
      alt = String(data.get('alt') ?? '')
      title = String(data.get('title') ?? '')
      if (data.getAll('body').length !== 1 || data.getAll('file').length !== 1 || data.get('position') !== 'append') throw new Error('Ungültige Einfügeposition oder Formularfelder.')
      if (source.length > 1024 * 1024 || alt.length > 2000 || title.length > 2000) throw new Error('Formularwerte zu groß.')
      const reference = await store(data.get('file'))
      const value = fixtures.append(source, reference.src, alt)
      const id = randomUUID()
      submissions.set(id, { value, title, reference })
      if (submissions.size > 100) submissions.delete(submissions.keys().next().value)
      response.writeHead(303, { Location: `/media/result/${id}` }).end()
    } catch (error) {
      response.writeHead(422, { 'Content-Type': 'text/html; charset=utf-8' })
        .end(mediaPage(fixtures, source, alt, title, error.message))
    }
  } else if (path.startsWith('/media/result/')) {
    const result = submissions.get(path.slice('/media/result/'.length))
    if (!result) response.writeHead(404).end()
    else response.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' }).end(
      `<!doctype html><html><head><meta charset="utf-8"></head><body><pre id="media-value">\n${escape(result.value)}</pre><p id="received-title">${escape(result.title)}</p><img id="stored-image" src="${result.reference.src}" alt="Testbild"></body></html>`)
  } else if (/^\/media\/assets\/[a-f0-9]{64}\.png$/.test(path)) {
    try {
      const bytes = await readFile(new URL(path.split('/').at(-1), storage))
      response.writeHead(200, { 'Content-Type': 'image/png', 'X-Content-Type-Options': 'nosniff' })
        .end(bytes)
    } catch { response.writeHead(404).end() }
  } else if (path === '/media/assets/fixture.png') {
    response.writeHead(200, { 'Content-Type': 'image/png' }).end(fixturePng)
  } else response.writeHead(404).end()
  return true
}
