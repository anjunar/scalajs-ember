import { readdir, readFile, stat, mkdir, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'
import { gzipSync, brotliCompressSync } from 'node:zlib'
import { createHash } from 'node:crypto'
import { execFileSync } from 'node:child_process'

const root = fileURLToPath(new URL('../', import.meta.url))
const results = []
for (const name of ['text', 'markdown', 'standard']) {
  const directory = resolve(root, 'target/editor-profiles', name)
  const files = (await readdir(directory)).filter(file => file.endsWith('.js')).sort()
  if (!files.includes('main.js')) throw new Error(`Missing fullLinkJS output for ${name}`)
  const sizes = []
  for (const file of files) {
    // Byte statistics/compression only. Never inspect or search generated JavaScript source.
    const bytes = await readFile(resolve(directory, file))
    sizes.push({ file, bytes: (await stat(resolve(directory, file))).size,
      gzipBytes: gzipSync(bytes, { level: 9 }).length, brotliBytes: brotliCompressSync(bytes).length,
      sha256: createHash('sha256').update(bytes).digest('hex') })
  }
  const { profile } = await import(pathToFileURL(resolve(directory, 'main.js')).href)
  const registrations = [...profile.registrations()]
  if (name !== 'standard' && JSON.stringify(registrations) !== '["ember.rich-text"]') throw new Error(`${name}: unexpected optional registrations`)
  if (!profile.render('Hello').includes('Hello')) throw new Error(`${name}: SSR failed`)
  results.push({ profile: name, registrations, files: sizes,
    bytes: sizes.reduce((n, f) => n + f.bytes, 0), gzipBytes: sizes.reduce((n, f) => n + f.gzipBytes, 0),
    brotliBytes: sizes.reduce((n, f) => n + f.brotliBytes, 0) })
}
await mkdir(resolve(root, 'target/p28'), { recursive: true })
const report = { measuredAt: new Date().toISOString(), node: process.version,
  commit: execFileSync('git', ['rev-parse', 'HEAD'], { cwd: root, encoding: 'utf8' }).trim(),
  dirty: !!execFileSync('git', ['status', '--porcelain'], { cwd: root, encoding: 'utf8' }).trim(),
  build: 'Scala.js fullLinkJS, ES2021, ESModule, source maps excluded', profiles: results }
await writeFile(resolve(root, 'target/p28/bundles.json'), JSON.stringify(report, null, 2))
console.table(results.map(({ profile, bytes, gzipBytes, brotliBytes }) => ({ profile, bytes, gzipBytes, brotliBytes })))
