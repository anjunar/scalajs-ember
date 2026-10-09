import { test, expect } from '@playwright/test'

async function open(page) {
  await page.goto('/')
  await page.waitForFunction(() => window.ready === true)
  await page.evaluate(() => {
    // Firefox ignores clipboardData in synthetic ClipboardEvent construction;
    // WebKit does the same for InputEvent.dataTransfer. Explicitly attach the
    // protocol payload; the separate keyboard case exercises native events.
    window.clipboardEvent = (type, init) => {
      const event = new ClipboardEvent(type, init)
      Object.defineProperty(event, 'clipboardData', { value: init.clipboardData })
      return event
    }
    window.transferInputEvent = (type, init) => {
      const event = new InputEvent(type, init)
      if (init.dataTransfer) Object.defineProperty(event, 'dataTransfer', { value: init.dataTransfer })
      return event
    }
  })
  await page.evaluate(() => window.clipboard.mount(document.getElementById('root')))
  await page.locator('#root').focus()
}
async function select(page, a, from, b = a, until = from, key = 'a') {
  await page.evaluate(args => window.clipboard.select(...args), [a, from, b, until, key])
}
async function paste(page, formats, echo = false) {
  return page.evaluate(({ formats, echo }) => {
    const host = document.getElementById('root')
    const data = new DataTransfer()
    for (const [mime, value] of Object.entries(formats)) data.setData(mime, value)
    const event = window.clipboardEvent('paste', { bubbles: true, cancelable: true, clipboardData: data })
    host.dispatchEvent(event)
    if (echo) {
      host.dispatchEvent(window.transferInputEvent('beforeinput', { bubbles: true, cancelable: true, inputType: 'insertFromPaste', dataTransfer: data }))
      host.dispatchEvent(window.transferInputEvent('input', { bubbles: true, inputType: 'insertFromPaste' }))
    }
    return { prevented: event.defaultPrevented, text: window.clipboard.text(), log: window.clipboard.log() }
  }, { formats, echo })
}

test.beforeEach(async ({ page }) => open(page))
test.afterEach(async ({ page }) => page.evaluate(() => window.clipboard?.dispose()).catch(() => {}))

test('copy writes all supported formats and preserves marks', async ({ page }) => {
  await select(page, 't1', 5, 't0', 2)
  const result = await page.evaluate(() => {
    const data = new DataTransfer()
    const event = window.clipboardEvent('copy', { bubbles: true, cancelable: true, clipboardData: data })
    document.getElementById('root').dispatchEvent(event)
    return { prevented: event.defaultPrevented, plain: data.getData('text/plain'), html: data.getData('text/html'), internal: data.getData('application/x-ember-editor+json') }
  })
  expect(result.prevented).toBe(true)
  expect(result.plain).toBe('llo world')
  expect(result.html).toContain('<strong>world</strong>')
  expect(result.html).not.toContain('data-ember')
  expect(JSON.parse(result.internal).profile).toBe('standard/1')
})

test('paste and beforeinput echo insert only once; a later paste still works', async ({ page }) => {
  await select(page, 't0', 3)
  const first = await paste(page, { 'text/plain': 'X' }, true)
  expect(first.prevented).toBe(true)
  expect(first.text).toBe('HelXlo world\nSecond line\n\uFFFC')
  const second = await paste(page, { 'text/plain': 'Y' })
  expect(second.text).toBe('HelXYlo world\nSecond line\n\uFFFC')
})

test('a beforeinput-only paste uses the same validated path', async ({ page }) => {
  await select(page, 't0', 0)
  const value = await page.evaluate(() => {
    const data = new DataTransfer()
    data.setData('text/plain', 'ABC')
    document.getElementById('root').dispatchEvent(window.transferInputEvent('beforeinput', {
      bubbles: true, cancelable: true, inputType: 'insertFromPaste', dataTransfer: data,
    }))
    return window.clipboard.text()
  })
  expect(value).toBe('ABCHello world\nSecond line\n\uFFFC')
})

test('multiline paste at a block start keeps the old text after its final line', async ({ page }) => {
  await select(page, 't0', 0)
  const result = await paste(page, { 'text/plain': 'A\nB' }, true)
  expect(result.text).toBe('A\nBHello world\nSecond line\n\uFFFC')
  expect(await page.evaluate(() => window.clipboard.undo())).toBe(true)
  expect(await page.evaluate(() => window.clipboard.text())).toBe('Hello world\nSecond line\n\uFFFC')
})

test('invalid internal content falls back to sanitized HTML', async ({ page }) => {
  await select(page, 't0', 6)
  const result = await paste(page, {
    'application/x-ember-editor+json': '{invalid',
    'text/html': '<p><script>bad()</script><em>safe</em><img src="javascript:bad()"></p>',
    'text/plain': 'wrong',
  })
  expect(result.text).toBe('Hello safeworld\nSecond line\n\uFFFC')
  expect(result.log).toContain('application/x-ember-editor+json')
  expect(await page.locator('#root script, #root img').count()).toBe(0)
})

test('cut confirms its write before deleting and ignores the deletion echo', async ({ page }) => {
  await select(page, 't0', 0, 't0', 5)
  const value = await page.evaluate(() => {
    const host = document.getElementById('root')
    const data = new DataTransfer()
    host.dispatchEvent(window.clipboardEvent('cut', { bubbles: true, cancelable: true, clipboardData: data }))
    host.dispatchEvent(window.transferInputEvent('beforeinput', { bubbles: true, cancelable: true, inputType: 'deleteByCut' }))
    return [data.getData('text/plain'), window.clipboard.text()]
  })
  expect(value).toEqual(['Hello', ' world\nSecond line\n\uFFFC'])
  expect(await page.evaluate(() => window.clipboard.undo())).toBe(true)
  expect(await page.evaluate(() => window.clipboard.text())).toBe('Hello world\nSecond line\n\uFFFC')
})

test('a clipboard write exception never deletes selected text', async ({ page }) => {
  await select(page, 't0', 0, 't0', 5)
  const value = await page.evaluate(() => {
    const data = new DataTransfer()
    Object.defineProperty(data, 'setData', { value() { throw new Error('denied') } })
    document.getElementById('root').dispatchEvent(window.clipboardEvent('cut', { bubbles: true, cancelable: true, clipboardData: data }))
    return [window.clipboard.text(), window.clipboard.log()]
  })
  expect(value[0]).toBe('Hello world\nSecond line\n\uFFFC')
  expect(value[1]).toContain('denied')
})

test('clipboard events in a native atom textarea remain its own', async ({ page }) => {
  await page.locator('[data-widget-input]').focus()
  const prevented = await page.evaluate(() => {
    return ['copy', 'cut', 'paste'].map(type => {
      const event = window.clipboardEvent(type, { bubbles: true, cancelable: true, clipboardData: new DataTransfer() })
      document.querySelector('[data-widget-input]').dispatchEvent(event)
      return event.defaultPrevented
    })
  })
  expect(prevented).toEqual([false, false, false])
  expect(await page.evaluate(() => window.clipboard.log())).toBe('')
})

for (const mode of ['readonly', 'composition']) {
  test(`${mode} refuses paste without native replacement`, async ({ page }) => {
    await select(page, 't0', 3)
    await page.evaluate(mode => {
      if (mode === 'readonly') window.clipboard.readonly(true)
      else document.getElementById('root').dispatchEvent(new CompositionEvent('compositionstart', { bubbles: true }))
    }, mode)
    const result = await paste(page, { 'text/plain': 'X' }, true)
    expect(result.prevented).toBe(true)
    expect(result.text).toBe('Hello world\nSecond line\n\uFFFC')
  })
}

test('files become a media intent without entering the document', async ({ page }) => {
  await select(page, 't0', 3)
  const result = await page.evaluate(() => {
    const data = new DataTransfer()
    data.items.add(new File(['image fixture'], 'test.png', { type: 'image/png' }))
    document.getElementById('root').dispatchEvent(window.clipboardEvent('paste', { bubbles: true, cancelable: true, clipboardData: data }))
    return [window.clipboard.fileCount(), window.clipboard.text()]
  })
  expect(result).toEqual([1, 'Hello world\nSecond line\n\uFFFC'])
})

test('real keyboard copy and paste goes through the event adapters', async ({ page, browserName }) => {
  test.fail(browserName === 'webkit' && process.platform === 'win32',
    'Windows WebKit loses event-written clipboard data; independent native-textarea repro below.')
  const modifier = process.platform === 'darwin' ? 'Meta' : 'Control'
  await select(page, 't1', 0, 't1', 5)
  await page.keyboard.press(`${modifier}+c`)
  await select(page, 't2', 11)
  await page.keyboard.press(`${modifier}+v`)
  await expect.poll(() => page.evaluate(() => window.clipboard.text())).toBe('Hello world\nSecond lineworld\n\uFFFC')
  expect(await page.locator('[data-ember-node="p1"] strong').textContent()).toBe('world')
})

test('native textarea transfers event-written text without any editor adapter', async ({ page, browserName }) => {
  test.fail(browserName === 'webkit' && process.platform === 'win32',
    'Reproduced engine/harness limitation: setData reads back successfully but the following native paste is empty.')
  await page.evaluate(() => {
    const area = document.createElement('textarea')
    area.id = 'clipboard-native-probe'
    area.value = 'original'
    document.body.append(area)
    area.focus()
    area.select()
    area.addEventListener('copy', event => {
      event.clipboardData.setData('text/plain', 'event-written')
      window.clipboardProbeReadback = event.clipboardData.getData('text/plain')
      event.preventDefault()
    }, { once: true })
  })
  const modifier = process.platform === 'darwin' ? 'Meta' : 'Control'
  await page.keyboard.press(`${modifier}+c`)
  expect(await page.evaluate(() => window.clipboardProbeReadback)).toBe('event-written')
  await page.locator('#clipboard-native-probe').fill('')
  await page.keyboard.press(`${modifier}+v`)
  await expect(page.locator('#clipboard-native-probe')).toHaveValue('event-written')
})
