import { readFile, writeFile, mkdir } from 'node:fs/promises'
import { createHash } from 'node:crypto'
import { cpus, totalmem, platform, release } from 'node:os'
import { execFileSync } from 'node:child_process'
import assert from 'node:assert/strict'
import { editorBench } from '../target/ember-browser-tests/main.js'

if (!global.gc) throw new Error('Run node --expose-gc benchmarks/run.mjs')
const results = []
for (const [requested, shape] of [[1000, 'paragraphs'], [10000, 'paragraphs'], [100000, 'paragraphs'], [3, 'long-leaf'], [129, 'deep-list']]) {
  for (const mode of ['core', 'history', 'form']) {
    global.gc()
    const before = process.memoryUsage().heapUsed
    const result = editorBench.core(requested, shape, mode, mode === 'form' ? 30 : 1000)
    assert.equal(result.maxChangedNodes, 1, `${shape}/${mode}: local edit touched extra nodes`)
    if (mode === 'history') assert.ok(result.undoEntries > 0 && result.undoEntries <= 32, `${shape}: history did not trim`)
    else assert.equal(result.undoEntries, 0)
    const allocated = process.memoryUsage().heapUsed
    global.gc()
    const released = process.memoryUsage().heapUsed
    results.push({ ...result, heapBeforeBytes: before, heapBeforeGcBytes: allocated, heapAfterDisposeGcBytes: released })
    console.log(`${shape} ${result.actualNodes} ${mode}: p50=${result.commit.p50Ms.toFixed(3)}ms p95=${result.commit.p95Ms.toFixed(3)}ms`)
  }
}
await mkdir(new URL('../target/p28/', import.meta.url), { recursive: true })
await writeFile(new URL('../target/p28/node.json', import.meta.url), JSON.stringify({
  measuredAt: new Date().toISOString(), node: process.version, platform: `${platform()} ${release()}`,
  cpu: cpus()[0].model, logicalCpus: cpus().length, memoryBytes: totalmem(),
  commit: execFileSync('git', ['rev-parse', 'HEAD'], { encoding: 'utf8' }).trim(),
  dirty: !!execFileSync('git', ['status', '--porcelain'], { encoding: 'utf8' }).trim(),
  // Hash linked bytes for provenance only; never inspect generated source.
  integrationSha256: createHash('sha256').update(await readFile(new URL('../target/ember-browser-tests/main.js', import.meta.url))).digest('hex'),
  warmup: 50, iterations: { core: 1000, history: 1000, form: 30 }, results
}, null, 2))
