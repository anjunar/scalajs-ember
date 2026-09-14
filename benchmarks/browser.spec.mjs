import { test, expect } from '../ember-integration/browser/node_modules/@playwright/test/index.mjs'

for (const [nodes, shape] of [[1000, 'paragraphs'], [10000, 'paragraphs'], [100000, 'paragraphs'], [3, 'long-leaf'], [129, 'deep-list']]) {
  test(`${shape} ${nodes}: local projection and release${shape === 'paragraphs' && nodes < 100000 ? ', block move' : ''}`, async ({ page, browserName, browser }, testInfo) => {
    await page.goto('/')
    await page.waitForFunction(() => window.ready)
    const cdp = browserName === 'chromium' ? await page.context().newCDPSession(page) : null
    async function heap() {
      if (!cdp) return null
      await cdp.send('HeapProfiler.collectGarbage')
      return (await cdp.send('Runtime.getHeapUsage')).usedSize
    }
    const beforeHeap = await heap()
    const mount = await page.evaluate(([nodes, shape]) => window.bench.mount(document.getElementById('root'), nodes, shape), [nodes, shape])
    console.log(`${browserName} ${shape} ${nodes}: mount ${mount.mountMs.toFixed(1)}ms`)
    await testInfo.attach('mount', { body: JSON.stringify(mount), contentType: 'application/json' })
    const result = await page.evaluate(() => {
      const host = document.getElementById('root')
      const observer = new MutationObserver(() => {})
      observer.observe(host, { subtree: true, childList: true, characterData: true, attributes: true })
      const edits = window.bench.edits(1000)
      const records = observer.takeRecords()
      const writes = { text: records.filter(r => r.type === 'characterData').length,
        children: records.filter(r => r.type === 'childList').length,
        attributes: records.filter(r => r.type === 'attributes').length }
      observer.disconnect()
      return { edits, writes }
    })
    result.mount = mount
    console.log(`${browserName} ${shape} ${nodes}: edits p95 ${result.edits.commitIncludingProjection.p95Ms.toFixed(3)}ms`)
    await testInfo.attach('edits', { body: JSON.stringify(result), contentType: 'application/json' })
    // The 50,000-sibling move is a separate stress reproduction for each runtime version.
    // Keep the large local-edit/release evidence even when that structural case times out.
    result.move = shape === 'paragraphs' && nodes < 100000 ? await page.evaluate(() => window.bench.move()) : null
    const mountedHeap = await heap()
    const release = await page.evaluate(() => {
      const start = performance.now()
      window.bench.dispose()
      return { disposeMs: performance.now() - start, remainingNodes: document.getElementById('root').childNodes.length }
    })
    const afterHeap = await heap()
    expect(result.edits.mounts).toBe(0)
    expect(result.edits.unmounts).toBe(0)
    expect(result.edits.maxChangedNodes).toBe(1)
    expect(result.writes).toEqual({ text: 1050, children: 0, attributes: 0 })
    if (result.move) {
      expect(result.move.identity).toBe(true)
      expect(result.move.mounts).toBe(0)
      expect(result.move.unmounts).toBe(0)
    }
    expect(release.remainingNodes).toBe(0)
    await testInfo.attach('measurements', { body: JSON.stringify({ browser: browserName, version: browser.version(), nodes, shape,
      ...result, release, heap: { beforeGcBytes: beforeHeap, mountedGcBytes: mountedHeap, afterDisposeGcBytes: afterHeap } }), contentType: 'application/json' })
  })
}
