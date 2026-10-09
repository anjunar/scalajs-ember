import { test } from 'node:test'
import assert from 'node:assert/strict'
import { largeMoveEvidence } from '../large-move-evidence.mjs'

const attachment = (name, value) => ({ name, body: Buffer.from(JSON.stringify(value)).toString('base64') })
function fixture() {
  return { errors: [], suites: [{ specs: [{ tests: ['chromium', 'firefox', 'webkit'].map(browser => ({
    projectName: browser, expectedStatus: 'passed', status: 'expected', results: [{ status: 'passed', attachments: [
      attachment('mount', { nodes: 100001 }),
      attachment('move', { browser, version: 'test-version', elapsedMs: 100,
        identity: true, firstIsLast: true, mounts: 0, unmounts: 0, added: 1, removed: 1 }),
    ] }],
  })) }] }] }
}
const cases = report => report.suites[0].specs[0].tests
const first = report => cases(report)[0]
function alterAttachment(report, name, changes) {
  const attachments = first(report).results[0].attachments
  const index = attachments.findIndex(a => a.name === name)
  const value = JSON.parse(Buffer.from(attachments[index].body, 'base64').toString('utf8'))
  attachments[index] = attachment(name, { ...value, ...changes })
}

test('retains measured times and engine versions for all three browsers', () => {
  const result = largeMoveEvidence(fixture())
  assert.equal(result.passed, 3)
  assert.equal(result.nodes, 100001)
  assert.deepEqual(result.measurements.map(m => [m.browser, m.version, m.elapsedMs]),
    ['chromium', 'firefox', 'webkit'].map(browser => [browser, 'test-version', 100]))
})
test('rejects missing, duplicate and unrecognized browser projects', () => {
  for (const change of [report => cases(report).pop(), report => { first(report).projectName = 'firefox' },
    report => { first(report).projectName = 'other' }]) {
    const report = fixture(); change(report)
    assert.throws(() => largeMoveEvidence(report))
  }
  assert.throws(() => largeMoveEvidence({ suites: [] }))
})
test('rejects expected failures, skipped tests, flaky retries and timeouts', () => {
  for (const change of [test => { test.expectedStatus = 'failed' }, test => { test.status = 'skipped' },
    test => { test.status = 'flaky' }, test => { test.results[0].status = 'timedOut' },
    test => { test.results.push(structuredClone(test.results[0])) }]) {
    const report = fixture(); change(first(report))
    assert.throws(() => largeMoveEvidence(report))
  }
})
test('rejects runner errors even if individual tests passed', () => {
  const report = fixture(); report.errors.push({ message: 'global teardown failed' })
  assert.throws(() => largeMoveEvidence(report))
})
test('rejects missing or ambiguous attachments', () => {
  for (const change of [attachments => attachments.pop(), attachments => attachments.shift(),
    attachments => attachments.push(attachments[0])]) {
    const report = fixture(); change(first(report).results[0].attachments)
    assert.throws(() => largeMoveEvidence(report))
  }
})
test('rejects evidence for a smaller document or a different engine', () => {
  const small = fixture(); alterAttachment(small, 'mount', { nodes: 10001 })
  assert.throws(() => largeMoveEvidence(small))
  const foreign = fixture(); alterAttachment(foreign, 'move', { browser: 'firefox' })
  assert.throws(() => largeMoveEvidence(foreign))
})
test('rejects remounts, identity loss, incorrect order and extra DOM mutations', () => {
  for (const change of [{ mounts: 1 }, { unmounts: 1 }, { identity: false },
    { firstIsLast: false }, { added: 2 }, { removed: 2 }]) {
    const report = fixture(); alterAttachment(report, 'move', change)
    assert.throws(() => largeMoveEvidence(report))
  }
})
test('rejects absent or invalid timing and version data', () => {
  for (const change of [{ elapsedMs: null }, { elapsedMs: -1 }, { elapsedMs: '100' }, { version: '' }]) {
    const report = fixture(); alterAttachment(report, 'move', change)
    assert.throws(() => largeMoveEvidence(report))
  }
})
