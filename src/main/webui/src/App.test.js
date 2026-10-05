import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import App from './App.vue'
import { LANGUAGES, setLanguage, detectLanguage } from './i18n/index.js'
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
    return { ok: r.status < 400, status: r.status, text: async () => (r.body === undefined ? '' : JSON.stringify(r.body)) }
  })
}
const platform = { envName: 'SBX', envNode: '1', store: 'memory', storeReady: true, services: { graphdb: 'http://x' }, webhooks: { accepted: 2, duplicates: 1 }, webhookOpen: true }

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
    expect(w.text()).toContain('graphdb')
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
})
