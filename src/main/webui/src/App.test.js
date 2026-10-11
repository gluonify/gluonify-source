import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import App from './App.vue'
import { LANGUAGES, setLanguage, detectLanguage } from './i18n/index.js'
import { SAMPLE_INVOICE } from './api.js'
import en from './i18n/en.js'
import fr from './i18n/fr.js'
import es from './i18n/es.js'
import it_ from './i18n/it.js'
import de from './i18n/de.js'

// UI tests ("mvn package" runs them through Quinoa): fetch is replaced, no server is needed.
function mockApi(routes) {
  globalThis.fetch = vi.fn(async (path, init = {}) => {
    const key = (init.method || 'GET') + ' ' + path
    const r = routes[key]
    if (!r) return { ok: false, status: 404, text: async () => '{"error":"unknown"}' }
    return { ok: r.status < 400, status: r.status, text: async () => (r.body === undefined ? '' : JSON.stringify(r.body)), blob: async () => new Blob([r.pdf || '']) }
  })
}
const platform = { envName: 'SBX', envNode: '1', store: 'memory', storeReady: true, services: { gdown: 'http://x' }, webhooks: { accepted: 2, duplicates: 1 }, webhookOpen: true }

describe('gluonify-source UI', () => {
  beforeEach(() => { sessionStorage.clear(); setLanguage('en'); localStorage.clear() })
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

  it('401: prompts to paste a token, then sends it as Authorization: Bearer', async () => {
    mockApi({ 'GET /api/notes': { status: 401, body: {} } })
    const w = mount(App)
    await flushPromises()
    expect(w.find('[role=alert]').text()).toContain('Charm token')
    mockApi({ 'GET /api/notes': { status: 200, body: [] }, 'GET /api/platform': { status: 200, body: platform } })
    await w.find('input[aria-label="Charm token"]').setValue('  abc.def.ghi  ')
    await w.find('form.row').trigger('submit')
    await flushPromises()
    const call = globalThis.fetch.mock.calls.find(([p]) => p === '/api/notes')
    expect(call[1].headers.Authorization).toBe('Bearer abc.def.ghi')
    expect(w.find('[role=alert]').exists()).toBe(false)
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
    mockApi({ 'GET /api/notes': { status: 401, body: {} }, 'GET /api/platform': { status: 200, body: platform } })
    const w = mount(App)
    await flushPromises()
    await w.find('select').setValue(code)
    expect(document.documentElement.lang).toBe(code)
    expect(localStorage.getItem('gluonify-source-lang')).toBe(code)
    expect(w.find('select').attributes('aria-label')).toBe(m.language)
    expect(w.find('h2').text()).toBe(m.authTitle)
    expect(w.find('input[type=password]').attributes('placeholder')).toBe(m.tokenPlaceholder)
    expect(w.find('input[type=password]').attributes('aria-label')).toBe(m.tokenLabel)
    expect(w.find('[role=alert]').text()).toBe(m.errUnauthenticated)
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
    sessionStorage.clear(); localStorage.clear(); setLanguage('en')
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
    sessionStorage.setItem('gluonify-source-token', 'abc.def.ghi')
    mockApi({ ...notes, 'POST /api/documents/invoice': { status: 200, pdf: '%PDF-1.4 fake' } })
    const w = mount(App)
    await flushPromises()
    expect(w.findAll('h2').map((h) => h.text())).toContain('Documents')
    expect(w.text()).toContain('gluonify-crystallon')
    expect(w.text()).toContain('gluonify:crystallon:render')
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
    release()
    await flushPromises()
    expect(button(w).text()).toBe('Download sample invoice')
    expect(button(w).attributes('disabled')).toBeUndefined()
  })

  it.each([
    [401, {}, 'Not authenticated: paste a Charm token below.'],
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

  it.each([['en', en], ['fr', fr], ['es', es], ['it', it_], ['de', de]])('%s: title, hint and button are translated', async (code, m) => {
    mockApi(notes)
    setLanguage(code)
    const w = mount(App)
    await flushPromises()
    const section = w.findAll('section').find((s) => s.find('h2').text() === m.documentsTitle)
    expect(section).toBeDefined()
    expect(section.find('.hint').text()).toBe(m.documentsHint.replace('{audience}', 'gluonify-crystallon').replace('{role}', 'gluonify:crystallon:render'))
    expect(section.find('button').text()).toBe(m.documentsDownload)
    setLanguage('en')
  })
})
