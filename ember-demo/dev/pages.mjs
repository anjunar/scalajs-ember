import { cp, mkdir, readFile, readdir, rm, writeFile } from 'node:fs/promises'
import { dirname, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const demoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const repoRoot = resolve(demoRoot, '..')
const source = resolve(demoRoot, 'dev')
const linker = resolve(repoRoot, 'target', 'ember-demo-full')
const output = resolve(repoRoot, 'target', 'ember-pages')
const basePath = '/scalajs-ember/'

await rm(output, { recursive: true, force: true })
await mkdir(output, { recursive: true })

for (const file of ['index.html', 'style.css', 'ember.svg', 'landscape.svg']) {
  await cp(resolve(source, file), resolve(output, file))
}
for (const file of ['main.js', 'main.js.map']) {
  await cp(resolve(linker, file), resolve(output, file))
}
await writeFile(resolve(output, '.nojekyll'), '', 'utf8')
await writeFile(resolve(output, '404.html'), notFoundPage(), 'utf8')

await validate()
console.log(`GitHub Pages artifact: ${relative(repoRoot, output)}`)
for (const file of (await readdir(output)).sort()) console.log(`  ${file}`)

async function validate() {
  const required = ['.nojekyll', '404.html', 'ember.svg', 'index.html', 'landscape.svg', 'main.js', 'main.js.map', 'style.css']
  const existing = new Set(await readdir(output))
  const missing = required.filter(file => !existing.has(file))
  if (missing.length) throw new Error(`Pages artifact is incomplete: ${missing.join(', ')}`)

  const html = await readFile(resolve(output, 'index.html'), 'utf8')
  if (!html.includes("from './main.js'")) throw new Error('index.html does not import the basis-relative Scala.js bundle.')
  if (/(?:href|src)=["']\//.test(html)) throw new Error('index.html contains a root-relative asset URL.')

  const bundle = await readFile(resolve(output, 'main.js'), 'utf8')
  if (!bundle.includes('./landscape.svg')) throw new Error('The production bundle does not contain the basis-relative demo image URL.')
  if (bundle.includes('scalajs-lexical')) throw new Error('The Pages bundle unexpectedly contains the removed Lexical prototype.')
}

function notFoundPage() {
  return `<!doctype html>
<html lang="de">
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <meta name="robots" content="noindex" />
    <meta http-equiv="refresh" content="0; url=${basePath}" />
    <title>Seite nicht gefunden · Ember Editor</title>
    <link rel="canonical" href="https://anjunar.github.io${basePath}" />
  </head>
  <body>
    <p>Diese Seite gibt es nicht. <a href="${basePath}">Zur Ember-Showcase</a></p>
  </body>
</html>
`
}
