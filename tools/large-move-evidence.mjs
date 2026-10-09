const engines = ['chromium', 'firefox', 'webkit']
const tests = suite => [...(suite.specs ?? []).flatMap(spec => spec.tests),
  ...(suite.suites ?? []).flatMap(tests)]

// This is a release gate: a missing engine or an expected failure is not a pass.
export function largeMoveEvidence(report) {
  const all = tests(report)
  if (report.errors?.length || all.length !== engines.length ||
      new Set(all.map(test => test.projectName)).size !== engines.length)
    throw new Error('Large move: require one successful test per browser engine')
  const measurements = engines.map(engine => {
    const test = all.find(test => test.projectName === engine)
    if (!test || test.expectedStatus !== 'passed' || test.status !== 'expected' ||
        test.results?.length !== 1 || test.results[0].status !== 'passed')
      throw new Error(`Large move: ${engine} did not pass on its first attempt`)
    const attachment = name => {
      const matches = (test.results[0].attachments ?? []).filter(a => a.name === name)
      if (matches.length !== 1 || !matches[0].body) throw new Error(`Large move: missing ${engine}/${name}`)
      return JSON.parse(Buffer.from(matches[0].body, 'base64').toString('utf8'))
    }
    const mount = attachment('mount'), move = attachment('move')
    if (mount.nodes !== 100001 || move.browser !== engine ||
        typeof move.version !== 'string' || !move.version.trim() ||
        !Number.isFinite(move.elapsedMs) || move.elapsedMs < 0 ||
        move.identity !== true || move.firstIsLast !== true ||
        move.mounts !== 0 || move.unmounts !== 0 || move.added !== 1 || move.removed !== 1)
      throw new Error(`Large move: invalid size, timing, identity or mutation evidence for ${engine}`)
    return move
  })
  return { passed: engines.length, nodes: 100001, paragraphSiblings: 50000, measurements }
}
