import { readFile, writeFile, mkdir } from 'node:fs/promises'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { parseArgs } from 'node:util'
import { largeMoveEvidence } from './large-move-evidence.mjs'

const root = fileURLToPath(new URL('../', import.meta.url))
const { values: options } = parseArgs({ options: { record: { type: 'boolean' }, 'scala-log': { type: 'string' } } })
const load = async name => JSON.parse(await readFile(resolve(root, 'target/p28', name), 'utf8'))
function tests(suite) {
  return [...(suite.specs ?? []).flatMap(spec => spec.tests.map(test => ({ title: spec.title, ...test }))),
    ...(suite.suites ?? []).flatMap(tests)]
}
function counts(report) {
  const all = tests(report)
  return { total: all.length,
    passed: all.filter(t => t.expectedStatus === 'passed' && t.status === 'expected').length,
    expectedFailures: all.filter(t => t.expectedStatus === 'failed' && t.status === 'expected').length,
    unexpected: all.filter(t => t.status === 'unexpected').length,
    flaky: all.filter(t => t.status === 'flaky').length, skipped: all.filter(t => t.status === 'skipped').length }
}
const [node, bundles, corpus, boundaries, browserReport, functionalReport, stressReport] = await Promise.all(
  ['node.json', 'bundles.json', 'corpora.json', 'boundaries.json', 'browser-report.json', 'full-browser-report.json', 'large-move-report.json'].map(load))
const functional = counts(functionalReport), benchmark = counts(browserReport)
const largeMove = largeMoveEvidence(stressReport)
if (browserReport.errors?.length || functionalReport.errors?.length) throw new Error('Browser runner reported global errors')
for (const [name, result] of Object.entries({ functional, benchmark })) {
  if (!result.total || result.unexpected || result.flaky || result.skipped || result.passed + result.expectedFailures !== result.total)
    throw new Error(`${name}: incomplete/failed evidence ${JSON.stringify(result)}`)
}
const browser = tests(browserReport).map(test => {
  const attachment = test.results.at(-1).attachments.find(a => a.name === 'measurements')
  if (!attachment?.body) throw new Error(`Missing measurement: ${test.title}`)
  return JSON.parse(Buffer.from(attachment.body, 'base64').toString('utf8'))
})
// Opt in to a specific Scala run. Never silently mix in an old local release log.
let scala = null
if (options['scala-log']) {
  const log = await readFile(resolve(root, options['scala-log']), 'utf8')
  const passed = [...log.matchAll(/Total number of tests run: (\d+)/g)].reduce((n, match) => n + Number(match[1]), 0)
  if (!passed || /\[error\]|\*\*\*.*FAILED|OutOfMemoryError/.test(log)) throw new Error('Scala gate log is incomplete or failed')
  scala = { passed, log: options['scala-log'], command: 'sbt --server "Test/testOnly *"',
    runtime: /welcome to sbt ([^\r\n]+)/.exec(log)?.[1] ?? null }
}
const summary = { node, bundles, corpus, boundaries, browser, functional, benchmark, scala,
  recordedAt: new Date().toISOString(), largeMove,
  devices: 'No physical device, IME or screenreader acceptance recorded' }
const ms = value => value == null ? '—' : value.toFixed(3)
const mib = value => value == null ? '—' : (value / 1048576).toFixed(2)
const table = (header, rows) => `| ${header.join(' | ')} |\n| ${header.map(() => '---').join(' | ')} |\n${rows.map(row => `| ${row.join(' | ')} |`).join('\n')}\n`
const markdown = `# P28: gemessene Werte

Erzeugt aus den JSON-Belegen am ${summary.recordedAt}. Methodik und offene
Abnahmegrenzen: [Messbericht](../report.md). Daten: [acceptance.json](acceptance.json).

Umgebung: ${node.platform}; ${node.cpu}; ${node.logicalCpus} logische CPUs;
${mib(node.memoryBytes)} MiB RAM; Node ${node.node}; Basis ${node.commit};
Arbeitsbaum geändert: ${node.dirty}. Full-Link, ES2021, ESModule.

## Core, History und Formstring

Commitzeiten in ms, inklusive der jeweiligen synchronen Reducer und Listener.
Die separate Vollserialisierung wird fünfmal gemessen; ihre p95 ist damit das Maximum.

${table(['Form / Nodes', 'Modus', 'Commit p50', 'Commit p95', 'Serialisierung p50', 'Serialisierung p95', 'Undo-Stufen', 'geschätzte History-Bytes'], node.results.map(r => [r.shape + ' / ' + r.actualNodes, r.mode, ms(r.commit.p50Ms), ms(r.commit.p95Ms), ms(r.serialize.p50Ms), ms(r.serialize.p95Ms), r.undoEntries, r.estimatedHistoryBytes]))}
## Node-Heap nach Freigabe

Einmalige Prozessmessung pro Fall, keine p50/p95 und kein Beweis für vollständige
Leakfreiheit. Modul-/JIT-Caches bleiben im Prozess; vor und nach dem Fall wird GC angefordert.

${table(['Form / Nodes', 'Modus', 'vorher MiB', 'vor GC MiB', 'nach Dispose + GC MiB'], node.results.map(r => [r.shape + ' / ' + r.actualNodes, r.mode, mib(r.heapBeforeBytes), mib(r.heapBeforeGcBytes), mib(r.heapAfterDisposeGcBytes)]))}
## Browserprojektion

Je 50 Warmups und 1000 gemessene Änderungen, ein Worker. Commit enthält die synchrone
Projektion; Projected misst vom Start desselben Commits bis zum Callback, keinen Paint.
Mount, Move und Dispose sind Einzelmessungen in ms. „—“ bei Move bedeutet nicht gemessen:
100001 Nodes haben einen separaten Stressfall; Leaf und Liste keinen Wurzel-Block-Move.
0.000 ms liegt unter der jeweiligen Uhr-Auflösung, besonders bei Firefox.

${table(['Engine / Version', 'Form / Nodes', 'Mount', 'Commit p50 / p95', 'Projected p50 / p95', 'Move', 'Dispose'], browser.map(r => [r.browser + ' ' + r.version, r.shape + ' / ' + r.mount.nodes, ms(r.mount.mountMs), ms(r.edits.commitIncludingProjection.p50Ms) + ' / ' + ms(r.edits.commitIncludingProjection.p95Ms), ms(r.edits.commitToProjected.p50Ms) + ' / ' + ms(r.edits.commitToProjected.p95Ms), ms(r.move?.elapsedMs), ms(r.release.disposeMs)]))}
Alle lokalen Fälle: ein geänderter Modellknoten, **0 Mounts/Unmounts**, 1050
Textmutationen einschließlich Warmup, **0 ChildList-/Attributmutationen**.
Die gemessenen Moves behalten die Host-Identität ohne Mount/Unmount.

## Großer Block-Move als Pflichtprüfung

${largeMove.nodes} Modellknoten, ${largeMove.paragraphSiblings} Absätze: erstes Kind ans Ende.
Jede Engine muss im ersten Versuch bestehen. Genau ein Host wird entfernt und wieder
eingefügt; Identität und Reihenfolge stimmen, Mounts/Unmounts bleiben null.
Die Zeit ist eine Einzelmessung; die CI setzt keine hardwareabhängige Millisekundengrenze.

${table(['Engine / Version', 'Move ms'], largeMove.measurements.map(r => [r.browser + ' ' + r.version, ms(r.elapsedMs)]))}

## Chromium-Heap

CDP mit angefordertem GC. Firefox/WebKit bieten hier keine vergleichbare Heap-API;
ihre Werte bleiben null. Alle Browser prüfen zusätzlich einen leeren Host nach Dispose.

${table(['Form / Nodes', 'vorher MiB', 'montiert nach GC MiB', 'nach Dispose + GC MiB'], browser.filter(r => r.browser === 'chromium').map(r => [r.shape + ' / ' + r.mount.nodes, mib(r.heap.beforeGcBytes), mib(r.heap.mountedGcBytes), mib(r.heap.afterDisposeGcBytes)]))}
## Profilgrößen

Tatsächliche JavaScript-Dateibytes, gzip Level 9 und Brotli-Default; keine Sourcemaps.
SHA-256 je Datei und ausgeführte Registrierungslisten stehen im JSON.

${table(['Profil', 'JS Bytes', 'gzip Bytes', 'Brotli Bytes'], bundles.profiles.map(r => [r.profile, r.bytes, r.gzipBytes, r.brotliBytes]))}
## Automatisierte Gates

${scala ? `${scala.passed} bestandene Scala-Tests; ${scala.runtime}.` : 'Scala läuft in einem getrennten CI-Job; in diesem Artefakt liegt kein Scala-Log vor.'}
${functional.passed} tatsächliche Browserpässe, ${functional.expectedFailures} erwartete Fehler,
${functional.unexpected} unerwartete Fehler, ${functional.flaky} instabile und ${functional.skipped} übersprungene Fälle.
Separat ${benchmark.passed} bestandene Browser-Messfälle, ${largeMove.passed} große Move-Stressfälle, ${corpus.results.length} Korpusfälle,
${boundaries.projects} geprüfte Projekte und ${boundaries.errors.length} Modulgrenzenfehler.
Die zwei erwarteten Clipboard-Fehler zählen nicht als bestanden.
`
await mkdir(resolve(root, 'target/p28'), { recursive: true })
await writeFile(resolve(root, 'target/p28/summary.json'), JSON.stringify(summary, null, 2))
await writeFile(resolve(root, 'target/p28/measurements.md'), markdown)
if (options.record) {
  await mkdir(resolve(root, 'benchmarks/results'), { recursive: true })
  await writeFile(resolve(root, 'benchmarks/results/acceptance.json'), JSON.stringify(summary, null, 2))
  await writeFile(resolve(root, 'benchmarks/results/measurements.md'), markdown)
}
console.log(JSON.stringify({ functional, benchmark, largeMove, corpora: corpus.results.length, projects: boundaries.projects }))
