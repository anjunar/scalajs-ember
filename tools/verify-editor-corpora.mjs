import { readFile, writeFile, mkdir } from 'node:fs/promises'
import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { corpusFixtures } from '../target/ember-browser-tests/main.js'

const bytes = await readFile(new URL('../benchmarks/corpora/roundtrips.json', import.meta.url))
const corpus = JSON.parse(bytes)
const results = []
for (const entry of corpus) {
  const result = corpusFixtures[entry.format](entry.source)
  if (entry.format === 'markdown') {
    assert.equal(result.stable, true, `${entry.name}: unstable Markdown serialization`)
    assert.equal(result.semanticStable, true, `${entry.name}: changed HTML semantics`)
    assert.equal(result.jsonStable, true, `${entry.name}: JSON changed document identity/content`)
  }
  for (const text of entry.includes ?? []) assert.ok(result.html.includes(text), `${entry.name}: missing ${text}`)
  for (const text of entry.excludes ?? []) assert.ok(!result.html.includes(text), `${entry.name}: retained ${text}`)
  if (entry.loss) assert.ok(result.losses.length, `${entry.name}: missing loss diagnostic`)
  results.push({ name: entry.name, passed: true })
}
await mkdir(new URL('../target/p28/', import.meta.url), { recursive: true })
await writeFile(new URL('../target/p28/corpora.json', import.meta.url), JSON.stringify({ sha256: createHash('sha256').update(bytes).digest('hex'), results }, null, 2))
console.log(`${results.length} versioned corpus cases passed (in addition to the Scala format/conformance suites).`)
