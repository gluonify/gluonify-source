import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import App from './App.vue'
import { api } from './api.js'
import { auth } from './auth.js'
import { detectLanguage, setLanguage, t } from './i18n/index.js'

// Edge cases: the failures of each call, the shapes of the API errors, the language detection without storage, the entry point.
const CHARM = { signInRequired: true, clientId: 'source-ui', authorizationUrl: 'https://gluonify.lan/authorize', tokenUrl: 'https://gluonify.lan/token', logoutUrl: 'https://gluonify.lan/logout', scope: 'openid profile' }
const NOTE = { id: '1', title: 'First', body: 'text', createdAt: '2026-10-05T10:00:00Z', author: 'ada' }
const PLATFORM = { envName: '', envNode: '', store: 'memory', storeReady: false, services: null, webhooks: { accepted: 0, duplicates: 0 }, webhookOpen: false }

function respond(status, text) {
  return { ok: status < 400, status, text: async () => text, json: async () => JSON.parse(text), blob: async () => new Blob([text]) }
}

function mockFetch(handler) {
  globalThis.fetch = vi.fn(async (path, init = {}) => {
    if (path === '/api/auth-config') return respond(200, JSON.stringify(CHARM))
    return handler((init.method || 'GET') + ' ' + path, init)
  })
}

describe('API calls', () => {
  beforeEach(() => { sessionStorage.clear(); auth.forget(); auth.setToken('abc.def.ghi') })
  afterEach(() => vi.restoreAllMocks())

  it('a 204 has no body, and a JSON body is parsed', async () => {
    mockFetch((key) => (key.startsWith('DELETE') ? respond(204, '') : respond(200, '[1]')))
    expect(await api.remove('a/b')).toBeNull()
    expect(globalThis.fetch.mock.calls.at(-1)[0]).toBe('/api/notes/a%2Fb')
    expect(await api.notes()).toEqual([1])
  })

  it('an error carries the API message, the validation violations, or the HTTP status', async () => {
    mockFetch((key) => ({
      'GET /api/notes': respond(400, JSON.stringify({ violations: [{ message: 'title must not be blank' }, { message: 'body too long' }] })),
      'POST /api/notes': respond(502, JSON.stringify({ error: 'Crystallon is unreachable' }))
    })[key] ?? respond(500, 'not json'))
    await expect(api.notes()).rejects.toMatchObject({ message: 'title must not be blank, body too long', status: 400, apiMessage: true })
    await expect(api.create('t', 'b')).rejects.toMatchObject({ message: 'Crystallon is unreachable', apiMessage: true })
    await expect(api.platform()).rejects.toMatchObject({ message: 'HTTP 500', status: 500, apiMessage: false })
  })

  it('a download error without a JSON body is still an error with its status', async () => {
    mockFetch(() => respond(502, '<html>bad gateway</html>'))
    await expect(api.invoice()).rejects.toMatchObject({ message: 'HTTP 502', status: 502 })
  })
})

describe('notes screen failures', () => {
  beforeEach(() => { sessionStorage.clear(); auth.forget(); auth.setToken('abc.def.ghi'); setLanguage('en'); localStorage.clear() })
  afterEach(() => vi.restoreAllMocks())

  it('removes a note then refreshes; a failing removal shows the message of the API', async () => {
    let failing = false
    mockFetch((key) => {
      if (key === 'GET /api/notes') return respond(200, JSON.stringify([NOTE]))
      if (key === 'GET /api/platform') return respond(200, JSON.stringify(PLATFORM))
      if (key === 'DELETE /api/notes/1') return failing ? respond(404, JSON.stringify({ error: 'unknown note' })) : respond(204, '')
      return respond(404, '{}')
    })
    const w = mount(App)
    await flushPromises()
    expect(w.text()).toContain('local')
    await w.find('button.danger').trigger('click')
    await flushPromises()
    expect(globalThis.fetch.mock.calls.some(([p, i]) => p === '/api/notes/1' && i.method === 'DELETE')).toBe(true)
    failing = true
    await w.find('button.danger').trigger('click')
    await flushPromises()
    expect(w.find('[role=alert]').text()).toBe('unknown note')
  })

  it('a creation refused for another reason than the role shows the message as received, and a good one empties the form', async () => {
    let status = 400
    mockFetch((key) => {
      if (key === 'GET /api/notes') return respond(200, '[]')
      if (key === 'GET /api/platform') return respond(200, JSON.stringify(PLATFORM))
      if (key === 'POST /api/notes') return status === 201 ? respond(201, JSON.stringify(NOTE)) : respond(400, JSON.stringify({ error: 'title is too long' }))
      return respond(404, '{}')
    })
    const w = mount(App)
    await flushPromises()
    const title = w.find('input[aria-label="Title"]')
    await title.setValue('New')
    await w.find('form.stack').trigger('submit')
    await flushPromises()
    expect(w.find('[role=alert]').text()).toBe('title is too long')
    status = 201
    await w.find('form.stack').trigger('submit')
    await flushPromises()
    expect(title.element.value).toBe('')
    expect(w.find('[role=alert]').exists()).toBe(false)
  })
})

describe('loading failures', () => {
  beforeEach(() => { sessionStorage.clear(); auth.forget(); auth.setToken('abc.def.ghi'); setLanguage('en'); localStorage.clear() })
  afterEach(() => vi.restoreAllMocks())

  it('a read refused by the API explains the missing role, any other failure shows its message', async () => {
    let status = 403
    mockFetch((key) => (key === 'GET /api/notes' ? respond(status, JSON.stringify({ error: 'boom' })) : respond(200, JSON.stringify(PLATFORM))))
    const w = mount(App)
    await flushPromises()
    expect(w.find('[role=alert]').text()).toContain('source:read')
    status = 500
    await w.find('section button[type=button]:not(.danger)').trigger('click')
    await flushPromises()
    expect(w.find('[role=alert]').text()).toBe('boom')
  })

  it('without the sign-in configuration the page still opens', async () => {
    globalThis.fetch = vi.fn(async (path) => {
      if (path === '/api/auth-config') throw new Error('network down')
      return respond(401, '{}')
    })
    const w = mount(App)
    await flushPromises()
    expect(w.find('main').exists()).toBe(true)
  })

  it('an empty successful body is no data', async () => {
    mockFetch(() => respond(200, ''))
    expect(await api.notes()).toBeNull()
  })
})

describe('language', () => {
  afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals(); localStorage.clear() })

  it('works without storage and without navigator languages', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('blocked') })
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('blocked') })
    vi.spyOn(navigator, 'languages', 'get').mockReturnValue(undefined)
    vi.spyOn(navigator, 'language', 'get').mockReturnValue('fr-CA')
    expect(detectLanguage()).toBe('fr')
    setLanguage('de')
    expect(document.documentElement.lang).toBe('de')
  })

  it('an unsupported code falls back to English, and a missing navigator is English too', () => {
    setLanguage('xx')
    expect(document.documentElement.lang).toBe('en')
    localStorage.clear()
    vi.stubGlobal('navigator', undefined)
    expect(detectLanguage()).toBe('en')
    setLanguage(42)
    expect(document.documentElement.lang).toBe('en')
  })

  it('a text falls back to the key and keeps an unknown placeholder', () => {
    expect(t('no.such.key')).toBe('no.such.key')
    expect(t('removeLabel', {})).toContain('{title}')
  })
})

describe('entry point', () => {
  it('mounts the application on #app in the remembered language', async () => {
    document.body.innerHTML = '<div id="app"></div>'
    mockFetch(() => respond(404, '{}'))
    await import('./main.js')
    await flushPromises()
    expect(document.querySelector('#app main')).not.toBeNull()
  })
})
