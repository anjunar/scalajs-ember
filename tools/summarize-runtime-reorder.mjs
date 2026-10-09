import { readFile, writeFile } from 'node:fs/promises'
import { createHash } from 'node:crypto'
import { execFileSync } from 'node:child_process'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { cpus, platform, release } from 'node:os'

const root = fileURLToPath(new URL('../', import.meta.url))
const ui = resolve(root, '../scalajs-ui')
const json = async path => JSON.parse(await readFile(resolve(root, path), 'utf8'))
const log = async path => (await readFile(resolve(root, path), 'utf8')).replace(/\u001b\[[0-9;]*m/g, '')
const sha = async path => createHash('sha256').update(await readFile(path)).digest('hex')
function tests(suite) {
  return [...(suite.specs ?? []).flatMap(spec => spec.tests), ...(suite.suites ?? []).flatMap(tests)]
}
function browserGate(report) {
  const all = tests(report)
  if (!all.length || all.some(t => t.status !== 'expected')) throw new Error('Browser gate incomplete or failed')
  return { passed: all.filter(t => t.expectedStatus === 'passed').length,
    expectedFailures: all.filter(t => t.expectedStatus === 'failed').length }
}
function attachments(report, name) {
  return tests(report).map(test => {
    const data = test.results.at(-1).attachments.find(a => a.name === name)
    if (!data?.body) throw new Error(`Missing ${name} measurement`)
    return JSON.parse(Buffer.from(data.body, 'base64').toString('utf8'))
  })
}
function scalaGate(text) {
  const suites = [...text.matchAll(/Tests: succeeded (\d+), failed (\d+), canceled (\d+), ignored (\d+), pending (\d+)/g)]
  if (!suites.length || suites.some(m => m.slice(2).some(n => Number(n) !== 0))) throw new Error('Scala tests incomplete or failed')
  return { passed: suites.reduce((n, m) => n + Number(m[1]), 0) }
}
const metadata = await json('target/editor-metadata/scalajs-ember-integration.json')
const modules = metadata.compileModules.filter(m => m.includes(':scalajs-ui-core_'))
if (modules.length !== 1 || !modules[0].endsWith(':1.0.1-p28-SNAPSHOT')) throw new Error('Wrong runtime candidate')
const baseline = await json('benchmarks/results/acceptance.json')
const stress = await json('target/p28/large-move-report.json')
const regular = await json('target/p28/browser-report.json')
const editor = await json('target/p28-runtime-editor-browser.json')
const uiBrowser = await json('target/p28-runtime-browser.json')
const editorScala = await log('target/p28-runtime-editor-scala.log')
if (/\[error\]/.test(editorScala)) throw new Error('Editor Scala/link/format gate failed')
const coreFormat = await log('target/p28-runtime-core-format.log')
if (/\[error\]/.test(coreFormat)) throw new Error('Core formatting failed')
const uiFormat = await log('target/p28-runtime-format.log')
const globalFormatFailed = /\[error\]/.test(uiFormat)
if (globalFormatFailed && !/TableView\.scala isn't formatted properly/.test(uiFormat))
  throw new Error('Unrecognized UI format failure; inspect the log before recording evidence')
const uiScala = await log('target/p28-runtime-scala.log')
const npmCore = await log('target/p28-runtime-npm-core.log')
const npmDemo = await log('target/p28-runtime-npm-demo.log')
if (!npmDemo.includes('all checks passed')) throw new Error('Demo verification missing')
const files = ['scala/scalajs-ui-core/src/main/scala-3/ui/core/component/Runtime.scala',
  'scala/scalajs-ui-core/src/main/scala-3/ui/core/statement/KeyedChildren.scala',
  'scala/scalajs-ui-core/src/test/scala-3/ui/core/render/KeyedReorderSpec.scala']
const sourceHashes = await Promise.all(files.map(async file => ({ file, sha256: await sha(resolve(ui, file)) })))
const measurements = attachments(regular, 'measurements')
const largeMoves = attachments(stress, 'move')
const report = {
  recordedAt: new Date().toISOString(), runtime: modules[0], defaultRuntime: '1.0.0 (published, unchanged)',
  environment: { platform: `${platform()} ${release()}`, cpu: cpus()[0].model, node: process.version },
  editorCommit: execFileSync('git', ['rev-parse', 'HEAD'], { cwd: root, encoding: 'utf8' }).trim(),
  uiCommit: execFileSync('git', ['-c', `safe.directory=${ui.replaceAll('\\', '/')}`, 'rev-parse', 'HEAD'], { cwd: ui, encoding: 'utf8' }).trim(),
  workingTreeCandidate: true, sourceHashes,
  baselineFile: 'benchmarks/results/acceptance.json',
  stressCase: { nodes: 100001, paragraphSiblings: 50000, operation: 'first paragraph to end' },
  integrationSha256: await sha(resolve(root, 'target/ember-browser-tests/main.js')),
  gates: { uiScala: scalaGate(uiScala), editorScala: scalaGate(editorScala),
    uiBrowser: browserGate(uiBrowser), editorBrowser: browserGate(editor),
    benchmark: browserGate(regular), stress: browserGate(stress), coreFormat: 'passed',
    uiGlobalFormat: globalFormatFailed ? 'failed: parallel TableView.scala changes are not formatted' : 'passed',
    npmCore: [...npmCore.matchAll(/Tests\s+(\d+) passed/g)].map(m => Number(m[1])), npmDemo: 'passed: typecheck, client, SSR, pages, one runtime' },
  largeMoves, measurements,
  comparison: measurements.filter(m => m.move).map(m => ({ browser: m.browser, nodes: m.mount.nodes,
    beforeMs: baseline.browser.find(b => b.browser === m.browser && b.nodes === m.nodes && b.shape === m.shape)?.move?.elapsedMs,
    afterMs: m.move.elapsedMs }))
}
if (report.gates.npmCore.length !== 2) throw new Error('Incomplete npm core evidence')
if (report.comparison.some(row => row.beforeMs === undefined)) throw new Error('Missing baseline comparison')
await writeFile(resolve(root, 'benchmarks/results/runtime-reorder.json'), JSON.stringify(report, null, 2))
console.log(JSON.stringify({ gates: report.gates, comparison: report.comparison, largeMoves }, null, 2))
