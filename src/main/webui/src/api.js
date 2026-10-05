// Calls to the service's REST API (same origin: Quinoa serves the UI and Quarkus the API on the same port).
// The Gluonify (Charm) token is pasted by the user and kept for the tab only (sessionStorage): never written in the code, never in a cookie.
const KEY = 'gluonify-source-token'

export const token = {
  get: () => { try { return sessionStorage.getItem(KEY) || '' } catch { return '' } },
  set: (v) => { try { v ? sessionStorage.setItem(KEY, v) : sessionStorage.removeItem(KEY) } catch { /* storage unavailable: the token stays in the page's memory */ } }
}

async function call(method, path, body) {
  const headers = { Accept: 'application/json' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  const t = token.get()
  if (t) headers.Authorization = 'Bearer ' + t
  const r = await fetch(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) })
  if (r.status === 204) return null
  const text = await r.text()
  let json = null
  try { json = text ? JSON.parse(text) : null } catch { /* non-JSON body */ }
  if (!r.ok) {
    const e = new Error((json && (json.error || (json.violations && json.violations.map((v) => v.message).join(', ')))) || `HTTP ${r.status}`)
    e.status = r.status
    throw e
  }
  return json
}

export const api = {
  notes: () => call('GET', '/api/notes'),
  create: (title, body) => call('POST', '/api/notes', { title, body }),
  remove: (id) => call('DELETE', '/api/notes/' + encodeURIComponent(id)),
  platform: () => call('GET', '/api/platform')
}
