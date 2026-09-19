const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const vm = require('node:vm')
const script = fs.readFileSync('app/src/main/assets/web-compat.js', 'utf8')

function setup(theme = 'dark') {
  const calls = { themes: [], signedOut: 0, links: [], opens: [], fetches: [], observers: [] }
  const root = { dataset: { theme } }
  const response = { ok: true, clone: () => ({ json: async () => ({ authenticated: false }) }) }
  const context = vm.createContext({
    URL,
    location: new URL('https://lumic.test/'),
    document: { documentElement: root },
    MutationObserver: class {
      constructor(callback) { this.callback = callback; calls.observers.push(this) }
      observe(target, options) { this.target = target; this.options = options }
    },
    window: {
      Lumir: {
        setTheme: dark => calls.themes.push(dark),
        onSignedOut: () => calls.signedOut++,
        copyLink: url => calls.links.push(url)
      },
      fetch: async (...args) => { calls.fetches.push(args); return response },
      open: (...args) => { calls.opens.push(args); return 'opened' }
    }
  })
  vm.runInContext(script, context)
  return { context, calls, root, response }
}

test('theme synchronization is deduplicated and does not inject styles or gestures', () => {
  const { context, calls, root } = setup()
  assert.deepEqual(calls.themes, [true])
  root.dataset.theme = 'light'
  calls.observers[0].callback()
  calls.observers[0].callback()
  assert.deepEqual(calls.themes, [true, false])
  vm.runInContext(script, context)
  assert.equal(calls.observers.length, 1)
  assert.deepEqual(Array.from(calls.observers[0].options.attributeFilter), ['data-theme'])
  const blank = setup('')
  assert.deepEqual(blank.calls.themes, [])
})

test('legacy and versioned session/logout requests preserve response and notify logout', async () => {
  const { context, calls, response } = setup()
  for (const input of ['/api/logout', '/api/v1/auth/logout', { url: '/api/session' }, new URL('https://lumic.test/api/v1/auth/session')]) {
    assert.equal(await context.window.fetch(input), response)
  }
  assert.equal(calls.signedOut, 4)
  response.ok = false
  await context.window.fetch('/api/logout')
  assert.equal(calls.signedOut, 4)
  response.ok = true
  await context.window.fetch('https://external.test/api/logout')
  assert.equal(calls.signedOut, 4)
})

test('gallery and original image fetches are transparent and external links retain behavior', async () => {
  const { context, calls, response } = setup()
  for (const path of ['/api/v1/gallery?author=Artist', '/flow/original.jpg', '/api/v1/posts?id=123']) {
    assert.equal(await context.window.fetch(path), response)
  }
  assert.equal(calls.signedOut, 0)
  assert.equal(context.window.open('https://external.test/post/123'), null)
  assert.deepEqual(calls.links, ['https://external.test/post/123'])
  assert.equal(context.window.open('/post/123'), 'opened')
  assert.equal(calls.opens.length, 1)
})
