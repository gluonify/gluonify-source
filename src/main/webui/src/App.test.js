import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import App from './App.vue'

// UI tests ("mvn test" runs them through Quinoa): fetch is replaced, no server is needed.
function mockApi(routes) {
  globalThis.fetch = vi.fn(async (path, init = {}) => {
    const key = (init.method || 'GET') + ' ' + path
    const r = routes[key]
    if (!r) return { ok: false, status: 404, text: async () => '{"error":"inconnu"}' }
    return { ok: r.status < 400, status: r.status, text: async () => (r.body === undefined ? '' : JSON.stringify(r.body)) }
  })
}
const platform = { envName: 'SBX', envNode: '1', store: 'memory', storeReady: true, services: { graphdb: 'http://x' }, webhooks: { accepted: 2, duplicates: 1 }, webhookOpen: true }

describe('gluonify-source UI', () => {
  beforeEach(() => sessionStorage.clear())
  afterEach(() => vi.restoreAllMocks())

  it('shows the notes and what the platform provides', async () => {
    mockApi({ 'GET /api/notes': { status: 200, body: [{ id: '1', title: 'Première', body: 'texte', createdAt: '2026-10-05T10:00:00Z', author: 'ada' }] }, 'GET /api/platform': { status: 200, body: platform } })
    const w = mount(App)
    await flushPromises()
    expect(w.text()).toContain('Première')
    expect(w.text()).toContain('ada')
    expect(w.text()).toContain('SBX')
    expect(w.text()).toContain('graphdb')
    expect(w.text()).toContain('doublons : 1')
  })

  it('401: prompts to paste a token, then sends it as Authorization: Bearer', async () => {
    mockApi({ 'GET /api/notes': { status: 401, body: {} } })
    const w = mount(App)
    await flushPromises()
    expect(w.find('[role=alert]').text()).toContain('jeton Charm')
    mockApi({ 'GET /api/notes': { status: 200, body: [] }, 'GET /api/platform': { status: 200, body: platform } })
    await w.find('input[aria-label="Jeton Charm"]').setValue('  abc.def.ghi  ')
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
    await w.find('input[aria-label="Titre"]').setValue('Nouvelle')
    await w.find('form.stack').trigger('submit')
    await flushPromises()
    const post = globalThis.fetch.mock.calls.find(([p, i]) => p === '/api/notes' && i.method === 'POST')
    expect(JSON.parse(post[1].body)).toEqual({ title: 'Nouvelle', body: '' })
    expect(w.find('[role=alert]').text()).toContain('source:write')
  })
})
