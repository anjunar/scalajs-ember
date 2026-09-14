import { test, expect } from '@playwright/test'
import { fixturePng } from '../media-server.mjs'

const file = { name: 'picture.png', mimeType: 'image/png', buffer: fixturePng }
async function mount(page, controlled = true) {
  await page.goto('/')
  await page.waitForFunction(() => window.ready)
  await page.evaluate(controlled => {
    const input = document.createElement('input')
    input.type = 'file'; input.id = 'media-file'; input.multiple = true
    const alt = document.createElement('input')
    alt.id = 'media-alt'; alt.value = 'A description'
    const choose = document.createElement('button')
    choose.id = 'media-choose'; choose.textContent = 'Bild wählen'
    choose.addEventListener('click', () => { window.mediaLastOpen = window.media.open() })
    document.body.append(input, alt, choose)
    window.media.mount(document.getElementById('root'), input, controlled)
  }, controlled)
  await page.locator('#root').focus()
  await page.evaluate(() => window.media.select(2, 2))
}
async function choose(page, files = file) {
  const chooser = page.waitForEvent('filechooser')
  await page.locator('#media-choose').click()
  expect(await page.evaluate(() => window.mediaLastOpen)).toBe(true)
  await (await chooser).setFiles(files)
}
async function phase(page, text, index = 0) {
  await expect.poll(() => page.evaluate(i => window.media.statuses()[i]?.phase, index)).toContain(text)
}
test.afterEach(async ({ page }) => page.evaluate(() => window.media?.dispose()).catch(() => {}))

test('picker waits, replaces only after success, and Undo restores selected text', async ({ page }) => {
  await mount(page)
  await page.evaluate(() => window.media.select(0, 5))
  await choose(page)
  expect(await page.evaluate(() => window.media.text())).toBe('Hello world')
  expect(await page.evaluate(() => window.media.imageCount())).toBe(0)
  await page.evaluate(() => window.media.progress(0, .5))
  expect(await page.evaluate(() => window.media.statuses()[0].progress)).toBe(.5)
  expect(await page.evaluate(() => window.media.complete(0))).toBe(true)
  await phase(page, 'Inserted')
  expect(await page.evaluate(() => window.media.text())).toBe(' world')
  await expect(page.locator('#root img')).toHaveAttribute('alt', 'A description')
  expect(await page.evaluate(() => window.media.revoked())).toBe(1)
  expect(await page.evaluate(() => window.media.undo())).toBe(true)
  expect(await page.evaluate(() => window.media.text())).toBe('Hello world')
})

for (const kind of ['paste', 'drop']) {
  test(`${kind} files use the same media coordinator`, async ({ page }) => {
    await mount(page)
    await page.evaluate(kind => {
      const host = document.getElementById('root')
      const data = new DataTransfer()
      data.items.add(new File(['fixture'], 'picture.png', { type: 'image/png' }))
      let event
      if (kind === 'paste') {
        event = new ClipboardEvent('paste', { bubbles: true, cancelable: true })
        Object.defineProperty(event, 'clipboardData', { value: data })
      } else {
        const node = document.createTreeWalker(host.querySelector('[data-ember-node="t"]'), NodeFilter.SHOW_TEXT).nextNode()
        const range = document.createRange()
        range.setStart(node, node.length); range.collapse(true)
        const rect = range.getBoundingClientRect()
        event = new DragEvent('drop', { bubbles: true, cancelable: true, dataTransfer: data, clientX: rect.x, clientY: rect.y + rect.height / 2 })
      }
      host.dispatchEvent(event)
    }, kind)
    expect(await page.evaluate(() => window.media.count())).toBe(1)
    await page.evaluate(() => window.media.complete(0))
    await phase(page, 'Inserted')
    expect(await page.evaluate(() => window.media.imageCount())).toBe(1)
    const json = await page.evaluate(() => window.media.json())
    expect(json).not.toMatch(/blob:|data:|base64|picture\.png|progress/)
    expect(await page.evaluate(() => window.media.value())).toContain('/media/assets/fixture.png')
  })
}

test('cancel releases the actual object URL and ignores late completion', async ({ page }) => {
  await mount(page)
  await choose(page)
  const preview = await page.evaluate(() => window.media.previews()[0])
  expect(await page.evaluate(async url => (await fetch(url)).ok, preview)).toBe(true)
  await page.evaluate(() => window.media.cancel(1))
  await page.evaluate(() => window.media.complete(0))
  await phase(page, 'Cancelled')
  expect(await page.evaluate(async url => { try { await fetch(url); return false } catch { return true } }, preview)).toBe(true)
  expect(await page.evaluate(() => window.media.imageCount())).toBe(0)
  expect(await page.evaluate(() => window.media.aborts())).toBe(1)
})

test('failure and forbidden result URLs never delete a selection', async ({ page }) => {
  await mount(page)
  await page.evaluate(() => window.media.select(0, 5))
  await choose(page)
  await page.evaluate(() => window.media.fail(0))
  await phase(page, 'Failed')
  expect(await page.evaluate(() => window.media.text())).toBe('Hello world')
  await page.locator('#root').focus()
  await choose(page)
  await page.evaluate(() => window.media.complete(1, 'http://untrusted.test/image.png'))
  await phase(page, 'Failed', 1)
  expect(await page.evaluate(() => window.media.text())).toBe('Hello world')
})

test('parallel uploads complete out of order and each completion is consumed once', async ({ page }) => {
  await mount(page)
  await choose(page, [file, { ...file, name: 'second.png' }])
  expect(await page.evaluate(() => window.media.count())).toBe(2)
  await page.evaluate(() => window.media.complete(1))
  await expect.poll(() => page.evaluate(() => window.media.imageCount())).toBe(1)
  await page.evaluate(() => window.media.complete(0))
  await expect.poll(() => page.evaluate(() => window.media.imageCount())).toBe(2)
  expect(await page.evaluate(() => window.media.complete(0))).toBe(false)
  expect(await page.evaluate(() => window.media.revoked())).toBe(2)
})

test('multiple files replace a selected range once and keep every image', async ({ page }) => {
  await mount(page)
  await page.evaluate(() => window.media.select(5, 0))
  await choose(page, [file, { ...file, name: 'second.png' }])
  await page.evaluate(() => window.media.complete(1))
  await expect.poll(() => page.evaluate(() => window.media.imageCount())).toBe(1)
  await page.evaluate(() => window.media.complete(0))
  await expect.poll(() => page.evaluate(() => window.media.imageCount())).toBe(2)
  expect(await page.evaluate(() => window.media.text())).toBe(' world')
})

test('Strict Markdown rejects unrepresentable media metadata atomically', async ({ page }) => {
  await mount(page)
  await page.evaluate(() => window.media.select(0, 5))
  await choose(page)
  await page.evaluate(() => window.media.complete(0, '/media/assets/fixture.png', true))
  await phase(page, 'Failed')
  expect(await page.evaluate(() => window.media.statuses()[0].phase)).toContain('MediaId')
  expect(await page.evaluate(() => window.media.text())).toBe('Hello world')
  expect(await page.evaluate(() => window.media.imageCount())).toBe(0)
})

for (const action of ['removeTarget', 'undo', 'dispose']) {
  test(`${action} invalidates an outstanding upload`, async ({ page }) => {
    await mount(page)
    if (action === 'undo') await page.evaluate(() => window.media.edit())
    await choose(page)
    await page.evaluate(action => window.media[action](), action)
    await page.evaluate(() => window.media.complete(0))
    await phase(page, action === 'dispose' ? 'Cancelled' : 'Discarded')
    expect(await page.evaluate(() => window.media.imageCount())).toBe(0)
    expect(await page.evaluate(() => window.media.revoked())).toBe(1)
  })
}

test('SourceBusy waits on discard but a source import invalidates the old target', async ({ page }) => {
  await mount(page)
  await choose(page)
  await page.evaluate(() => { window.media.source('draft'); window.media.complete(0) })
  await phase(page, 'SourceBusy')
  expect(await page.evaluate(() => window.media.value())).toBe('draft')
  await page.evaluate(() => window.media.leaveSource(false))
  await phase(page, 'Inserted')
  await mount(page)
  await choose(page)
  await page.evaluate(() => { window.media.source('replacement'); window.media.complete(0) })
  await phase(page, 'SourceBusy')
  await page.evaluate(() => window.media.leaveSource(true))
  await phase(page, 'Discarded')
  expect(await page.evaluate(() => window.media.text())).toBe('replacement')
})

test('CompositionBusy resumes through the real controller completion notification', async ({ page }) => {
  await mount(page)
  await choose(page)
  await page.locator('#root').focus()
  await page.evaluate(() => {
    document.getElementById('root').dispatchEvent(new CompositionEvent('compositionstart', { bubbles: true }))
    window.media.complete(0)
  })
  await phase(page, 'CompositionBusy')
  await page.evaluate(() => document.getElementById('root').dispatchEvent(new CompositionEvent('compositionend', { bubbles: true })))
  await phase(page, 'Inserted')
})

test('file validation precedes upload and external URLs do not upload', async ({ page }) => {
  await mount(page)
  await choose(page, { name: 'bad.txt', mimeType: 'text/plain', buffer: Buffer.from('bad') })
  expect(await page.evaluate(() => window.media.count())).toBe(0)
  expect(await page.evaluate(() => window.media.errors())).toContain('nicht erlaubt')
  expect(await page.evaluate(() => window.media.external('/media/assets/fixture.png', ''))).toBe(true)
  expect(await page.evaluate(() => window.media.count())).toBe(0)
  await expect(page.locator('#root img')).toHaveAttribute('alt', '')
})

test('real multipart upload inserts a durable reference and keeps storage after Undo', async ({ page, request }) => {
  await mount(page, false)
  await choose(page)
  await phase(page, 'Inserted')
  const src = await page.locator('#root img').getAttribute('src')
  expect(src).toMatch(/^\/media\/assets\/[a-f0-9]{64}\.png$/)
  expect(await (await request.get(src)).body()).toEqual(fixturePng)
  expect(await page.evaluate(() => window.media.json())).not.toMatch(/blob:|base64|data:/)
  await page.evaluate(() => window.media.undo())
  expect((await request.get(src)).ok()).toBe(true)
})

test('server validation failure preserves the text and releases its preview', async ({ page }) => {
  await mount(page, false)
  await page.evaluate(() => window.media.select(0, 5))
  await choose(page, { ...file, buffer: Buffer.from('not a PNG') })
  await phase(page, 'Failed')
  expect(await page.evaluate(() => window.media.text())).toBe('Hello world')
  expect(await page.evaluate(() => window.media.revoked())).toBe(1)
})
