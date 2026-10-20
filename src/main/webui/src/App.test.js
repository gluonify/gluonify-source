import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import App from './App.vue'
import { LANGUAGES, setLanguage, detectLanguage } from './i18n/index.js'
import { SAMPLE_INVOICE } from './api.js'
import { auth, browser } from './auth.js'
import en from './i18n/en.json'
import fr from './i18n/fr.json'
import es from './i18n/es.json'
import it_ from './i18n/it.json'
import de from './i18n/de.json'

// UI tests ("mvn package" runs them through Quinoa): fetch is replaced, no server is needed.
// Sign-in configuration served by GET /api/auth-config on the platform (Charm client of this UI); every test is signed in unless it says otherwise.
const CHARM = { signInRequired: true, clientId: 'source-ui', authorizationUrl: 'https://gluonify.lan/authorize', tokenUrl: 'https://gluonify.lan/token', logoutUrl: 'https://gluonify.lan/logout', scope: 'openid profile' }
function mockApi(routes) {
  const all = { 'GET /api/auth-config': { status: 200, body: CHARM }, ...routes }
  globalThis.fetch = vi.fn(async (path, init = {}) => {
    const key = (init.method || 'GET') + ' ' + path
    const r = all[key]
    if (!r) return { ok: false, status: 404, text: async () => '{"error":"unknown"}', json: async () => ({ error: 'unknown' }) }
    return { ok: r.status < 400, status: r.status, text: async () => (r.body === undefined ? '' : JSON.stringify(r.body)), json: async () => r.body, blob: async () => new Blob([r.pdf || '']) }
  })
}
const signIn = () => auth.setToken('abc.def.ghi')
const platform = { envName: 'SBX', envNode: '1', store: 'memory', storeReady: true, services: { gdown: 'http://x' }, webhooks: { accepted: 2, duplicates: 1 }, webhookOpen: true }

describe('gluonify-source UI', () => {
  beforeEach(() => { sessionStorage.clear(); auth.forget(); signIn(); setLanguage('en'); localStorage.clear() })
  afterEach(() => vi.restoreAllMocks())

  it('shows the notes and what the platform provides', async () => {
    mockApi({ 'GET /api/notes': { status: 200, body: [{ id: '1', title: 'First', body: 'text', createdAt: '2026-10-05T10:00:00Z', author: 'ada' }] }, 'GET /api/platform': { status: 200, body: platform } })
    const w = mount(App)
    await flushPromises()
    expect(w.text()).toContain('First')
    expect(w.text()).toContain('ada')
    expect(w.text()).toContain('SBX')
    expect(w.text()).toContain('gdown')
    expect(w.text()).toContain('duplicates: 1')
  })

  it('401: the token is forgotten and the sign-in is shown again', async () => {
    mockApi({ 'GET /api/notes': { status: 401, body: {} } })
    const w = mount(App)
    await flushPromises()
    expect(w.find('[role=alert]').text()).toBe(en.errSessionExpired)
    expect(auth.token.value).toBe('')
    expect(w.findAll('h2').map((h) => h.text())).toEqual([en.authTitle])
    expect(w.find('.signin button').text()).toBe('Sign in')
  })

  it('sends the token of the sign-in as Authorization: Bearer', async () => {
    mockApi({ 'GET /api/notes': { status: 200, body: [] }, 'GET /api/platform': { status: 200, body: platform } })
    mount(App)
    await flushPromises()
    const call = globalThis.fetch.mock.calls.find(([p]) => p === '/api/notes')
    expect(call[1].headers.Authorization).toBe('Bearer abc.def.ghi')
    expect(globalThis.fetch.mock.calls.find(([p]) => p === '/api/auth-config')[1].headers.Authorization).toBe('Bearer abc.def.ghi')
  })

  it('creates a note (POST) then refreshes; 403: message about the missing role', async () => {
    mockApi({ 'GET /api/notes': { status: 200, body: [] }, 'GET /api/platform': { status: 200, body: platform }, 'POST /api/notes': { status: 403, body: {} } })
    const w = mount(App)
    await flushPromises()
    await w.find('input[aria-label="Title"]').setValue('New')
    await w.find('form.stack').trigger('submit')
    await flushPromises()
    const post = globalThis.fetch.mock.calls.find(([p, i]) => p === '/api/notes' && i.method === 'POST')
    expect(JSON.parse(post[1].body)).toEqual({ title: 'New', body: '' })
    expect(w.find('[role=alert]').text()).toContain('source:write')
  })

  it('renders in English by default with lang="en"', async () => {
    mockApi({ 'GET /api/notes': { status: 200, body: [] }, 'GET /api/platform': { status: 200, body: platform } })
    const w = mount(App)
    await flushPromises()
    expect(document.documentElement.lang).toBe('en')
    expect(w.text()).toContain('No notes.')
    expect(w.find('select').element.value).toBe('en')
  })

  it.each([['en', en], ['fr', fr], ['es', es], ['it', it_], ['de', de]])('%s: selector switches texts, aria-labels, html lang and is remembered', async (code, m) => {
    auth.forget()
    mockApi({})
    const w = mount(App)
    await flushPromises()
    await w.find('select').setValue(code)
    expect(document.documentElement.lang).toBe(code)
    expect(localStorage.getItem('gluonify-source-lang')).toBe(code)
    expect(w.find('select').attributes('aria-label')).toBe(m.language)
    expect(w.find('h2').text()).toBe(m.authTitle)
    expect(w.find('.signin .hint').text()).toBe(m.signedOutHint.replace('{read}', 'source:read').replace('{write}', 'source:write'))
    expect(w.find('.signin button').text()).toBe(m.signIn)
    expect(w.find('input').exists()).toBe(false)
  })

  it.each(['fr', 'es', 'it', 'de'])('%s: 403 on create shows the translated role message and the delete label', async (code) => {
    const m = { fr, es, it: it_, de }[code]
    setLanguage(code)
    mockApi({ 'GET /api/notes': { status: 200, body: [{ id: '1', title: 'First', body: 'x', createdAt: '2026-10-05T10:00:00Z' }] }, 'GET /api/platform': { status: 200, body: platform }, 'POST /api/notes': { status: 403, body: {} } })
    const w = mount(App)
    await flushPromises()
    expect(w.find('button.danger').attributes('aria-label')).toBe(m.removeLabel.replace('{title}', 'First'))
    expect(w.text()).toContain(m.anonymous)
    await w.find('input[required]').setValue('T')
    await w.find('form.stack').trigger('submit')
    await flushPromises()
    expect(w.find('[role=alert]').text()).toBe(m.errForbiddenWrite.replace('{role}', 'source:write'))
  })

  it('language files have exactly the keys of English, with no empty value', () => {
    for (const m of [fr, es, it_, de]) {
      expect(Object.keys(m).sort()).toEqual(Object.keys(en).sort())
      for (const v of Object.values(m)) expect(v.length).toBeGreaterThan(0)
    }
    expect(LANGUAGES.map((l) => l.code)).toEqual(['en', 'fr', 'es', 'it', 'de'])
  })

  it('detects the browser language when supported, else English; a stored choice wins', () => {
    const nav = vi.spyOn(navigator, 'languages', 'get')
    nav.mockReturnValue(['de-CH', 'en'])
    expect(detectLanguage()).toBe('de')
    nav.mockReturnValue(['ja-JP'])
    expect(detectLanguage()).toBe('en')
    localStorage.setItem('gluonify-source-lang', 'it')
    expect(detectLanguage()).toBe('it')
  })

  it('stylesheet: long words in notes wrap instead of widening the page on a phone', async () => {
    const { readFileSync } = await import('node:fs')
    const css = readFileSync('src/style.css', 'utf8')
    expect(css).toMatch(/\.notes li > div\s*\{[^}]*min-width: 0[^}]*overflow-wrap: anywhere/)
  })
})

describe('brand logo and header', () => {
  beforeEach(() => { auth.forget(); signIn() })
  it('shows the transparent logo next to the title, translated, and the favicon is the same mark', async () => {
    mockApi({ 'GET /api/notes': { status: 200, body: [] }, 'GET /api/platform': { status: 200, body: platform } })
    const w = mount(App)
    await flushPromises()
    const svg = w.find('h1 svg')
    expect(svg.attributes('role')).toBe('img')
    expect(svg.attributes('aria-label')).toBe('Gluonify Source logo')
    expect(svg.attributes('focusable')).toBe('false')
    expect(svg.find('rect').exists()).toBe(false)
    const shapes = (s) => (s.match(/<(rect|path|circle)\b[^>]*>/g) || []).map((x) => x.replace(/\s+/g, ' ').replace(/ ?\/?>$/, '')).join('\n')
    expect(shapes(svg.html())).toBe(shapes(readFileSync(join(process.cwd(), 'public/favicon.svg'), 'utf8')))
    expect(readFileSync(join(process.cwd(), 'public/favicon.svg'), 'utf8')).not.toContain('<rect')
    expect(readFileSync(join(process.cwd(), 'index.html'), 'utf8')).toContain('href="/favicon.svg"')
    expect(readFileSync(join(process.cwd(), 'index.html'), 'utf8')).toContain('<title>Source</title>')
  })
  it('header: name, role and particle in every language, and the Gluonify copyright at the bottom', async () => {
    mockApi({ 'GET /api/notes': { status: 200, body: [] }, 'GET /api/platform': { status: 200, body: platform } })
    for (const [code, m] of Object.entries({ en, fr, es, it: it_, de })) {
      setLanguage(code)
      const w = mount(App)
      await flushPromises()
      const h = w.find('h1')
      expect(h.find('.name').text()).toBe('Source \u00b7')
      expect(h.find('.role').text()).toBe(m.appRole)
      expect(h.find('.particle').text()).toBe(m.appParticle)
      expect(w.find('footer.foot').text()).toBe('\u00a9 ' + new Date().getFullYear() + ' Gluonify')
    }
    setLanguage('en')
  })
})

describe('documents (Crystallon)', () => {
  const on = { ...platform, documents: true, services: { charm: 'http://c', crystallon: 'http://k' } }
  const notes = { 'GET /api/notes': { status: 200, body: [] }, 'GET /api/platform': { status: 200, body: on } }
  let saved, urls

  beforeEach(() => {
    sessionStorage.clear(); localStorage.clear(); setLanguage('en'); auth.forget(); signIn()
    saved = []
    urls = []
    // jsdom has neither object URLs nor navigation: the download is observed through the anchor that is clicked
    globalThis.URL.createObjectURL = vi.fn((blob) => { urls.push(blob); return 'blob:sample' })
    globalThis.URL.revokeObjectURL = vi.fn()
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function () { saved.push({ href: this.getAttribute('href'), download: this.download }) })
  })
  afterEach(() => vi.restoreAllMocks())

  const button = (w) => w.findAll('button').find((b) => b.text().includes('invoice') || b.text().includes('Generating'))

  it('has no Documents section when the API says Crystallon is not configured', async () => {
    mockApi({ 'GET /api/notes': { status: 200, body: [] }, 'GET /api/platform': { status: 200, body: platform } })
    const w = mount(App)
    await flushPromises()
    expect(w.text()).not.toContain('Download sample invoice')
    expect(w.findAll('h2').map((h) => h.text())).not.toContain('Documents')
  })

  it('downloads the sample invoice: POST with the Bearer token, then hands the PDF to the browser', async () => {
    mockApi({ ...notes, 'POST /api/documents/invoice': { status: 200, pdf: '%PDF-1.4 fake' } })
    const w = mount(App)
    await flushPromises()
    expect(w.findAll('h2').map((h) => h.text())).toContain('Documents')
    expect(w.text()).toContain('Crystallon')
    await button(w).trigger('click')
    await flushPromises()
    const post = globalThis.fetch.mock.calls.find(([p, i]) => p === '/api/documents/invoice' && i.method === 'POST')
    expect(post[1].headers.Authorization).toBe('Bearer abc.def.ghi')
    expect(post[1].headers.Accept).toContain('application/pdf')
    expect(JSON.parse(post[1].body)).toEqual(SAMPLE_INVOICE)
    expect(urls).toHaveLength(1)
    expect(urls[0].size).toBe('%PDF-1.4 fake'.length)
    expect(saved).toEqual([{ href: 'blob:sample', download: 'invoice-2026-0042.pdf' }])
    expect(w.find('[role=alert]').exists()).toBe(false)
  })

  it('shows that the PDF is being generated, then enables the button again', async () => {
    let release
    mockApi(notes)
    const base = globalThis.fetch
    globalThis.fetch = vi.fn((path, init = {}) => (init.method === 'POST' && path === '/api/documents/invoice'
      ? new Promise((resolve) => { release = () => resolve({ ok: true, status: 200, text: async () => '', blob: async () => new Blob(['%PDF']) }) })
      : base(path, init)))
    const w = mount(App)
    await flushPromises()
    await button(w).trigger('click')
    expect(button(w).text()).toBe('Generating the PDF…')
    expect(button(w).attributes('disabled')).toBeDefined()
    await flushPromises() // the token is checked (and renewed if needed) before the request leaves
    release()
    await flushPromises()
    expect(button(w).text()).toBe('Download sample invoice')
    expect(button(w).attributes('disabled')).toBeUndefined()
  })

  it.each([
    [403, {}, 'Valid token, but without the source:read role.'],
    [403, { error: 'Crystallon did not accept your token (HTTP 403): it must carry the audience gluonify-crystallon and the role gluonify:crystallon:render' }, 'Crystallon did not accept your token (HTTP 403): it must carry the audience gluonify-crystallon and the role gluonify:crystallon:render'],
    [422, { error: 'Invalid template: unknown block type', code: 'CRYSTALLON-0015', path: 'body[2]' }, 'Invalid template: unknown block type'],
    [502, { error: 'Crystallon is unreachable' }, 'Crystallon is unreachable'],
    [404, { error: 'documents are off: deploy the application with "uses": ["crystallon"]' }, 'documents are off: deploy the application with "uses": ["crystallon"]']
  ])('%i: the error is shown (API messages as received, 401 and role-less 403 translated) and nothing is downloaded', async (status, body, text) => {
    mockApi({ ...notes, 'POST /api/documents/invoice': { status, body } })
    const w = mount(App)
    await flushPromises()
    await button(w).trigger('click')
    await flushPromises()
    expect(w.find('[role=alert]').text()).toBe(text)
    expect(saved).toEqual([])
    expect(button(w).attributes('disabled')).toBeUndefined()
  })

  it('401: the session has ended, the sign-in is shown again and nothing is downloaded', async () => {
    mockApi({ ...notes, 'POST /api/documents/invoice': { status: 401, body: {} } })
    const w = mount(App)
    await flushPromises()
    await button(w).trigger('click')
    await flushPromises()
    expect(w.find('[role=alert]').text()).toBe('Your session has ended: sign in again.')
    expect(saved).toEqual([])
    expect(w.findAll('h2').map((h) => h.text())).toEqual(['Authentication'])
  })

  it.each([['en', en], ['fr', fr], ['es', es], ['it', it_], ['de', de]])('%s: title, hint and button are translated', async (code, m) => {
    mockApi(notes)
    setLanguage(code)
    const w = mount(App)
    await flushPromises()
    const section = w.findAll('section').find((s) => s.find('h2').text() === m.documentsTitle)
    expect(section).toBeDefined()
    expect(section.find('.hint').text()).toBe(m.documentsHint)
    expect(section.find('button').text()).toBe(m.documentsDownload)
    setLanguage('en')
  })
})

describe('sign-in with Charm (authorization code + PKCE)', () => {
  let loc
  beforeEach(() => {
    sessionStorage.clear(); localStorage.clear(); setLanguage('en'); auth.forget()
    loc = { origin: 'https://source.gluonify.lan', pathname: '/', search: '', hash: '', assign: vi.fn() }
    browser.location = () => loc
  })
  afterEach(() => { browser.location = () => window.location; history.replaceState(null, '', '/'); vi.restoreAllMocks() })

  it('signed out: no notes, documents or platform; an explanation and a Sign in button; the API is not called', async () => {
    mockApi({ 'GET /api/notes': { status: 200, body: [{ id: '1', title: 'Secret', body: 'x', createdAt: '2026-10-05T10:00:00Z' }] }, 'GET /api/platform': { status: 200, body: { ...platform, documents: true } } })
    const w = mount(App)
    await flushPromises()
    expect(w.findAll('h2').map((h) => h.text())).toEqual(['Authentication'])
    expect(w.text()).not.toContain('Secret')
    expect(w.text()).toContain("Charm's sign-in page")
    expect(globalThis.fetch.mock.calls.map(([p]) => p)).toEqual(['/api/auth-config'])
    expect(w.find('input').exists()).toBe(false) // no pasted token, no password field: the password is typed on Charm's page
    expect(w.findAll('button').map((b) => b.text())).not.toContain('Sign out')
  })

  it('not signed in: goes straight to Charm\'s sign-in page, keeping the page, with no sign-in section in between', async () => {
    sessionStorage.removeItem('gluonify-source-silent') // beforeEach signed out locally, which arms the loop guard
    loc.search = '?note=1'
    mockApi({})
    const w = mount(App)
    await vi.waitFor(() => expect(loc.assign).toHaveBeenCalledTimes(1))
    expect(new URL(loc.assign.mock.calls[0][0]).searchParams.get('prompt')).toBeNull()
    expect(JSON.parse(sessionStorage.getItem('gluonify-source-pkce')).returnTo).toBe('/?note=1')
    expect(w.find('.signin').exists()).toBe(false)
    // came back without signing in (inside the loop guard): the sign-in section, no second redirect
    const again = mount(App)
    await flushPromises()
    expect(again.find('.signin button').text()).toBe('Sign in')
    expect(loc.assign).toHaveBeenCalledTimes(1)
  })

  it('Sign in sends the browser to Charm with the client id, the redirect URI of the UI and a S256 challenge', async () => {
    mockApi({})
    const w = mount(App)
    await flushPromises()
    await w.find('.signin button').trigger('click')
    // the login URL needs the S256 challenge (crypto.subtle, asynchronous): wait for the redirect rather than one microtask flush (flaky on slower hosts)
    await vi.waitFor(() => expect(loc.assign).toHaveBeenCalledTimes(1))
    const u = new URL(loc.assign.mock.calls[0][0])
    expect(u.origin + u.pathname).toBe('https://gluonify.lan/authorize')
    expect(u.searchParams.get('client_id')).toBe('source-ui')
    expect(u.searchParams.get('redirect_uri')).toBe('https://source.gluonify.lan/')
    expect(u.searchParams.get('response_type')).toBe('code')
    expect(u.searchParams.get('code_challenge_method')).toBe('S256')
    expect(u.searchParams.get('scope')).toBe('openid profile offline_access')
    expect(JSON.parse(sessionStorage.getItem('gluonify-source-pkce')).state).toBe(u.searchParams.get('state'))
  })

  it('back from Charm: the code is exchanged at the token endpoint, then the notes are shown with the name and Sign out', async () => {
    sessionStorage.setItem('gluonify-source-pkce', JSON.stringify({ verifier: 'v'.repeat(64), state: 's1', returnTo: '/' }))
    loc.search = '?code=c1&state=s1'
    const jwt = 'x.' + btoa(JSON.stringify({ name: 'Ada (acme)', roles: ['source:read', 'source:write'], exp: Math.floor(Date.now() / 1000) + 3600 })).replace(/=+$/, '') + '.y'
    mockApi({
      'POST https://gluonify.lan/token': { status: 200, body: { access_token: jwt, id_token: 'id.t.k', refresh_token: 'r1', expires_in: 3600 } },
      'GET /api/notes': { status: 200, body: [{ id: '1', title: 'First', body: 'x', createdAt: '2026-10-05T10:00:00Z', author: 'ada' }] },
      'GET /api/platform': { status: 200, body: platform }
    })
    const w = mount(App)
    await flushPromises()
    const post = globalThis.fetch.mock.calls.find(([p]) => p === 'https://gluonify.lan/token')
    const form = new URLSearchParams(post[1].body)
    expect(form.get('grant_type')).toBe('authorization_code')
    expect(form.get('code')).toBe('c1')
    expect(form.get('client_id')).toBe('source-ui')
    expect(form.get('code_verifier')).toBe('v'.repeat(64))
    expect(form.get('redirect_uri')).toBe('https://source.gluonify.lan/')
    expect(w.text()).toContain('First')
    expect(w.text()).toContain('Signed in as Ada (acme)')
    expect(globalThis.fetch.mock.calls.find(([p]) => p === '/api/notes')[1].headers.Authorization).toBe('Bearer ' + jwt)
    // Sign out: the token is forgotten, the notes disappear and Charm's session is ended too (id_token_hint, back to the UI)
    await w.findAll('button').find((b) => b.text() === 'Sign out').trigger('click')
    await flushPromises()
    expect(auth.token.value).toBe('')
    expect(w.text()).not.toContain('First')
    expect(w.find('.signin button').text()).toBe('Sign in again') // right after a sign-out: no automatic redirect, the person chooses
    expect(w.find('.signin output').text()).toBe('You are signed out.')
    const out = new URL(loc.assign.mock.calls.at(-1)[0])
    expect(out.origin + out.pathname).toBe('https://gluonify.lan/logout')
    expect(out.searchParams.get('id_token_hint')).toBe('id.t.k')
    expect(out.searchParams.get('post_logout_redirect_uri')).toBe('https://source.gluonify.lan/')
  })

  it('a callback whose state does not match is refused: no token, an error, still signed out', async () => {
    sessionStorage.setItem('gluonify-source-pkce', JSON.stringify({ verifier: 'v', state: 'expected' }))
    loc.search = '?code=c1&state=forged'
    mockApi({})
    const w = mount(App)
    await flushPromises()
    expect(globalThis.fetch.mock.calls.map(([p]) => p)).toEqual(['/api/auth-config'])
    expect(w.find('[role=alert]').text()).toBe(en.errLoginFailed)
    expect(w.find('.signin').exists()).toBe(true)
  })

  it('without a Charm client (GLUONIFY_SOURCE_OIDC_CLIENT_ID unset): no button, the missing variables are named', async () => {
    mockApi({ 'GET /api/auth-config': { status: 200, body: { signInRequired: true, clientId: null, scope: 'openid profile' } } })
    const w = mount(App)
    await flushPromises()
    expect(w.find('.signin button').exists()).toBe(false)
    expect(w.find('.signin').text()).toContain('GLUONIFY_SOURCE_OIDC_CLIENT_ID')
  })

  it('development (OIDC off, signInRequired false): everything is shown without a sign-in, and no Sign out', async () => {
    mockApi({ 'GET /api/auth-config': { status: 200, body: { signInRequired: false } }, 'GET /api/notes': { status: 200, body: [] }, 'GET /api/platform': { status: 200, body: platform } })
    const w = mount(App)
    await flushPromises()
    expect(w.find('.signin').exists()).toBe(false)
    expect(w.text()).toContain(en.devOpen)
    expect(w.findAll('h2').map((h) => h.text())).toContain('Notes')
    expect(w.findAll('button').map((b) => b.text())).not.toContain('Sign out')
  })
})

describe('vendored kit', () => {
  it('the UI depends on the embedded copy of @gluonify/ui, and the copy says where it comes from', () => {
    const pkg = JSON.parse(readFileSync(join(process.cwd(), 'package.json'), 'utf8'))
    expect(pkg.dependencies['@gluonify/ui']).toBe('file:./vendor/gluonify-ui')
    expect(readFileSync(join(process.cwd(), 'vendor/gluonify-ui/.vendored-from'), 'utf8')).toContain('source=gluonify-ui')
  })
})
