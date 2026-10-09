import { readFile, writeFile } from 'node:fs/promises'
import { createHash } from 'node:crypto'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = fileURLToPath(new URL('../', import.meta.url))
const read = path => readFile(resolve(root, path), 'utf8')
const json = async path => JSON.parse(await read(path))
const hash = async path => createHash('sha256').update(await readFile(resolve(root, path))).digest('hex')
const log = async path => (await read(path)).replace(/\u001b\[[0-9;]*m/g, '')
function tests(suite) {
  return [...(suite.specs ?? []).flatMap(spec => spec.tests), ...(suite.suites ?? []).flatMap(tests)]
}
function browserGate(report) {
  const all = tests(report)
  if (!all.length || all.some(test => test.status !== 'expected')) throw new Error('Incomplete browser gate')
  return { passed: all.filter(test => test.expectedStatus === 'passed').length,
    expectedFailures: all.filter(test => test.expectedStatus === 'failed').length }
}
function scalaGate(text) {
  const summaries = [...text.matchAll(/Tests: succeeded (\d+), failed (\d+), canceled (\d+), ignored (\d+), pending (\d+)/g)]
  if (/\[error\]/.test(text) || !summaries.length || summaries.some(m => m.slice(2).some(n => Number(n))))
    throw new Error('Scala/link/format gate failed')
  return summaries.reduce((sum, m) => sum + Number(m[1]), 0)
}

const publicationLog = await read('target/ui-release-1.0.1-publish.log')
const publication = JSON.parse(publicationLog.slice(publicationLog.indexOf('{')))
if (publication.deploymentState !== 'PUBLISHED' || Object.keys(publication.errors).length)
  throw new Error('Release not published')
const artifacts = await json('target/ui-release-1.0.1-central.json')
const staged = await json('target/ui-release-1.0.1-artifacts.json')
if (artifacts.length !== 9 || artifacts.some(a => a.version !== '1.0.1' ||
    staged.find(s => s.artifact === a.artifact)?.jarSha256 !== a.jarSha256))
  throw new Error('Published artifacts differ from verified staging')
const metadata = await json('target/editor-metadata/scalajs-ember-integration.json')
const runtime = metadata.compileModules.filter(m => m.includes(':scalajs-ui-core_'))
if (runtime.length !== 1 || !runtime[0].endsWith(':1.0.1')) throw new Error('Editor uses wrong runtime')
const candidate = await json('benchmarks/results/runtime-reorder.json')
for (const source of candidate.sourceHashes) {
  if (await hash(`../scalajs-ui/${source.file}`) !== source.sha256) throw new Error(`Core source changed: ${source.file}`)
}
const browser = await json('target/ui-release-1.0.1-editor-browser.json')
const stress = await json('target/p28/large-move-report.json')
const stressGate = browserGate(stress)
const moves = tests(stress).map(test => {
  const attachment = test.results.at(-1).attachments.find(a => a.name === 'move')
  return JSON.parse(Buffer.from(attachment.body, 'base64').toString('utf8'))
})
const npmLog = await log('target/ui-release-1.0.1-npm-verify.log')
if (/npm error|Tests\s+\d+ failed/.test(npmLog) || !npmLog.includes('57 passed') || !npmLog.includes('all checks passed'))
  throw new Error('npm verification incomplete')
const report = {
  recordedAt: new Date().toISOString(), publication, artifacts, runtime: runtime[0],
  sourceHashes: candidate.sourceHashes, integrationSha256: await hash('target/ember-browser-tests/main.js'),
  gates: {
    uiScala: scalaGate(await log('target/ui-release-1.0.1-scala.log')),
    editorScala: scalaGate(await log('target/ui-release-1.0.1-editor-scala.log')),
    npmWorkspaces: 'passed', npmVitestCases: [...npmLog.matchAll(/Tests\s+(\d+) passed/g)].reduce((sum, m) => sum + Number(m[1]), 0),
    uiBrowser: 57, editorBrowser: browserGate(browser), stress: stressGate,
    uiAndEditorFormat: 'passed',
  },
  stressCase: { nodes: 100001, paragraphSiblings: 50000, operation: 'first paragraph to end' }, moves,
}
await writeFile(resolve(root, 'benchmarks/results/ui-core-1.0.1-release.json'), JSON.stringify(report, null, 2))
console.log(JSON.stringify({ gates: report.gates, moves }, null, 2))
