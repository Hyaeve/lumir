const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const vm = require('node:vm')
const script = fs.readFileSync('app/src/main/assets/web-compat.js', 'utf8')

test('feed and gallery cache policy preserves random queries, pagination and caller options', async () => {
  const { context, calls } = setup()
  const request = { url: 'https://lumic.test/api/v1/posts?sort=random&seed=one&cursor=two', method: 'GET' }
  const options = { credentials: 'include', headers: { Accept: 'application/json' }, cache: 'default' }
  await context.window.fetch(request, options)
  assert.equal(calls.fetches[0][0], request)
  assert.equal(calls.fetches[0][1].cache, 'no-store')
  assert.equal(calls.fetches[0][1].headers, options.headers)
  assert.equal(calls.fetches[0][1].credentials, 'include')
  assert.equal(options.cache, 'default')
  await context.window.fetch('/api/v1/gallery?author=Artist')
  assert.equal(calls.fetches[1][1].cache, 'no-store')
  for (const url of ['/flow/original.jpg', '/preview/mobile.jpg', 'https://other.test/api/v1/posts']) {
    await context.window.fetch(url, options)
    assert.equal(calls.fetches.at(-1)[1], options)
  }
  await context.window.fetch('/api/v1/posts', { method: 'POST', body: '{}' })
  assert.equal(calls.fetches.at(-1)[1].cache, undefined)
})

test('native upgrade clears only page cache and revalidates entry without installing touch handlers', () => {
  const source = fs.readFileSync('app/src/main/java/com/hyaeve/lumir/MainActivity.kt', 'utf8')
  assert.match(source, /getInt\(WEB_CACHE_VERSION_KEY, 0\) != BuildConfig.VERSION_CODE/)
  assert.match(source, /clearCache\(true\)/)
  assert.match(source, /view\.loadUrl\(server, mapOf\("Cache-Control" to "no-cache"/)
  assert.doesNotMatch(source, /setOnTouchListener|LOAD_CACHE_ONLY|LOAD_CACHE_ELSE_NETWORK/)
})

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
