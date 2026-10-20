// Calls to the service's REST API (same origin: Quinoa serves the UI and Quarkus the API on the same port).
// The Charm token comes from the sign-in (auth.js): kept for the tab only (sessionStorage), never written in the code, never in a cookie.
import { auth } from './auth.js'

// An Error carrying the HTTP status; apiMessage is true when the text comes from the API itself ({"error": ...} or validation violations) rather than being "HTTP <status>".
function failure(status, json) {
  const apiMessage = json?.error || json?.violations?.map((v) => v.message).join(', ') || ''
  const e = new Error(apiMessage || `HTTP ${status}`)
  e.status = status
  e.apiMessage = Boolean(apiMessage)
  return e
}

function parse(text) {
  try { return text ? JSON.parse(text) : null } catch { return null /* non-JSON body */ }
}

// Renews the token first when it expires within a minute (refresh token of offline_access), then adds it.
async function authorization(headers) {
  await auth.refreshIfNeeded()
  const t = auth.getAccessToken()
  if (t) headers.Authorization = 'Bearer ' + t
  return headers
}

async function call(method, path, body) {
  const headers = await authorization({ Accept: 'application/json' })
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  const r = await fetch(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) })
  if (r.status === 204) return null
  const json = parse(await r.text())
  if (!r.ok) throw failure(r.status, json)
  return json
}

// POST that answers a file (the PDF of a document): the body is a Blob; an error is still JSON ({"error": ...}).
async function download(path, body) {
  const headers = await authorization({ Accept: 'application/pdf, application/json', 'Content-Type': 'application/json' })
  const r = await fetch(path, { method: 'POST', headers, body: JSON.stringify(body) })
  if (!r.ok) throw failure(r.status, parse(await r.text()))
  return r.blob()
}

// The invoice the "Download sample invoice" button sends to POST /api/documents/invoice (sample data, in English).
export const SAMPLE_INVOICE = {
  number: '2026-0042',
  subject: 'Website relaunch',
  customer: { name: 'Example Customer SA', street: 'Rue du Lac', houseNumber: '7', postalCode: '1003', city: 'Lausanne', country: 'CH' },
  lines: [
    { description: 'Website redesign (fixed price)', quantity: 1, unit: 'pce', unitPrice: 4800 },
    { description: 'Consulting, 12 hours', quantity: 12, unit: 'h', unitPrice: 160 },
    { description: 'Hosting, annual plan', quantity: 1, unit: 'pce', unitPrice: 480 }
  ]
}

export const api = {
  authConfig: () => call('GET', '/api/auth-config'), // public: Charm client id and endpoints, and whether the API asks for a token at all
  notes: () => call('GET', '/api/notes'),
  create: (title, body) => call('POST', '/api/notes', { title, body }),
  remove: (id) => call('DELETE', '/api/notes/' + encodeURIComponent(id)),
  platform: () => call('GET', '/api/platform'),
  invoice: (request = SAMPLE_INVOICE) => download('/api/documents/invoice', request)
}
