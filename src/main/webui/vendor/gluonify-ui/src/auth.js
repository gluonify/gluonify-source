import { computed, ref } from 'vue'
import { rolesFromToken, roleMatches } from './roles.js'

/**
 * Browser sign-in with Charm, shared by the consoles: authorization code + PKCE (S256) for a public client, silent attempt
 * (`prompt=none`) with a retry window, `offline_access` refresh, sign-out with `id_token_hint` and a broadcast to the other tabs.
 * No interface here: the console drives it from its router and its login page. Tokens only live in sessionStorage.
 *
 *   const auth = createAuth({ storagePrefix: 'fermion' })
 *
 * Options: storagePrefix (sessionStorage keys `<prefix>-token`, `-pkce`, `-idtoken`, `-refresh`, `-expiry`, `-silent`),
 * getConfig (async () => OidcConfig | null; by default GET <base>/public/auth/config), baseUrl (string or function; by default
 * Vite's BASE_URL, read at call time), clientId and scope (override what the server config says), silentRetryMs (30 000),
 * logoutChannel ('gluonify-logout'), appName (announced on the channel; the prefix by default).
 */
export const LOGOUT_CHANNEL = 'gluonify-logout'
export const SILENT_RETRY_MS = 30_000
const SILENT_ERRORS = ['login_required', 'consent_required', 'interaction_required', 'account_selection_required']

export function decodeJwt(token) {
  try {
    const part = String(token).split('.')[1] ?? ''
    const base64 = part.replace(/-/g, '+').replace(/_/g, '/')
    const bytes = Uint8Array.from(atob(base64), (c) => c.charCodeAt(0))
    return JSON.parse(new TextDecoder().decode(bytes)) ?? {}
  } catch {
    return {}
  }
}

const b64url = (buf) =>
  btoa(String.fromCharCode(...new Uint8Array(buf))).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
const random = (n = 32) => b64url(crypto.getRandomValues(new Uint8Array(n)))

/** S256 challenge of a verifier (RFC 7636). */
export async function challenge(verifier) {
  return b64url(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier)))
}

export function oidcConfigured(cfg) {
  return !!(cfg && cfg.clientId && cfg.authorizationUrl && cfg.tokenUrl)
}

/** The configured scope plus `offline_access`, which gives the refresh token. */
export function scopeOf(cfg, scope) {
  const scopes = (scope || cfg.scope || 'openid').split(/\s+/).filter(Boolean)
  return (scopes.includes('offline_access') ? scopes : [...scopes, 'offline_access']).join(' ')
}

function defaultBase() {
  return import.meta.env?.BASE_URL || '/'
}

export function createAuth(options = {}) {
  const prefix = options.storagePrefix
  if (!prefix) throw new Error('createAuth: storagePrefix is required')
  const K = {
    token: `${prefix}-token`, pkce: `${prefix}-pkce`, id: `${prefix}-idtoken`,
    refresh: `${prefix}-refresh`, expiry: `${prefix}-expiry`, silent: `${prefix}-silent`, out: `${prefix}-signedout`,
  }
  const retryMs = options.silentRetryMs ?? SILENT_RETRY_MS
  const channelName = options.logoutChannel ?? LOGOUT_CHANNEL
  const appName = options.appName ?? prefix

  // --- application base path (Vite `base`: `/` or `/fermion/`), read at call time so a test can change it
  const baseUrl = () => {
    const raw = typeof options.baseUrl === 'function' ? options.baseUrl() : (options.baseUrl ?? defaultBase())
    const b = raw || '/'
    return (b.startsWith('/') ? b : `/${b}`).replace(/\/?$/, '/')
  }
  const withBase = (path) => baseUrl().slice(0, -1) + (path.startsWith('/') ? path : `/${path}`)
  const stripBase = (pathname) => {
    const prefixPath = baseUrl().slice(0, -1)
    if (prefixPath && (pathname === prefixPath || pathname.startsWith(`${prefixPath}/`))) return pathname.slice(prefixPath.length) || '/'
    return pathname || '/'
  }
  const appRoot = (loc = window.location) => `${loc.origin}${baseUrl()}`
  const redirectUri = (loc = window.location) => appRoot(loc)
  // the page to come back to, hash included (consoles with a hash router: `/#/overview`)
  const currentPage = (loc) => stripBase(loc.pathname) + loc.search + (loc.hash || '')

  // --- storage
  const readSession = (key) => { try { return sessionStorage.getItem(key) ?? '' } catch { return '' } }
  const writeSession = (key, value) => {
    try { value ? sessionStorage.setItem(key, value) : sessionStorage.removeItem(key) } catch { /* kept in memory only */ }
  }

  // --- state
  const token = ref(readSession(K.token))
  const config = ref(null)
  const claims = computed(() => (token.value ? decodeJwt(token.value) : {}))
  const username = computed(() => claims.value.name ?? claims.value.preferred_username ?? claims.value.sub ?? '')
  const roles = computed(() => (token.value ? rolesFromToken(token.value) : []))
  const hasRole = (role) => roles.value.some((r) => roleMatches(role, r) || r === role)

  const isValid = (t = token.value) => {
    if (!t) return false
    const exp = decodeJwt(t).exp
    return !exp || exp * 1000 > Date.now()
  }
  const authenticated = computed(() => isValid(token.value))
  // explicit sign-out (this tab, or another tab or console): the sign-in page says so instead of signing straight back in
  const signedOut = ref(readSession(K.out) === '1')
  const markSignedOut = (v) => { signedOut.value = v; writeSession(K.out, v ? '1' : '') }
  // the last return from Charm carried an error (or the code exchange failed): no automatic redirect, the page explains
  const loginError = ref(false)
  const setToken = (v) => { token.value = String(v || '').trim(); writeSession(K.token, token.value) }
  const getAccessToken = () => (isValid() ? token.value : undefined)

  async function loadConfig(fetchFn = fetch) {
    try {
      if (options.getConfig) {
        const j = await options.getConfig()
        config.value = oidcConfigured(j) ? j : null
        return config.value
      }
      const r = await fetchFn(withBase('/public/auth/config'), { headers: { Accept: 'application/json' } })
      if (!r.ok) return null
      const j = await r.json()
      config.value = oidcConfigured(j) ? j : null
    } catch {
      config.value = null
    }
    return config.value
  }

  const clientIdOf = (cfg) => options.clientId ?? cfg.clientId

  async function buildLoginUrl(cfg, redirect, returnTo = '/', silent = false) {
    const verifier = random(48)
    const state = random(16)
    writeSession(K.pkce, JSON.stringify({ verifier, state, returnTo, silent }))
    const q = new URLSearchParams({
      response_type: 'code', client_id: clientIdOf(cfg), redirect_uri: redirect, scope: scopeOf(cfg, options.scope),
      state, code_challenge: await challenge(verifier), code_challenge_method: 'S256',
    })
    if (silent) q.set('prompt', 'none')
    return `${cfg.authorizationUrl}${cfg.authorizationUrl.includes('?') ? '&' : '?'}${q}`
  }

  /** Sends the browser to Charm to sign in (resolves to false when Charm is not configured). */
  async function startLogin(loc = window.location) {
    const cfg = config.value ?? (await loadConfig())
    if (!oidcConfigured(cfg)) return false
    writeSession(K.silent, String(Date.now())) // a deliberate sign-in also arms the loop guard
    markSignedOut(false)
    loginError.value = false
    loc.assign(await buildLoginUrl(cfg, redirectUri(loc), currentPage(loc)))
    return true
  }

  /**
   * Automatic sign-in: without a valid token, sends the browser straight to Charm's sign-in page (code + PKCE, no `prompt`: an open
   * Charm session comes back at once, otherwise Charm shows its form: password or access key), keeping the requested page so that the
   * person comes back to it. Resolves to true when the browser is being sent away; false means the console shows its sign-in page:
   * already signed in (possibly refused by the console's roles), Charm not configured, the last return from Charm was an error, an
   * explicit sign-out (until the next sign-in), or the loop guard (a redirect less than `silentRetryMs` ago that did not come back
   * signed in). `returnTo` overrides the page to come back to (a hash route, for instance).
   */
  async function autoLogin(loc = window.location, { returnTo } = {}) {
    signedOut.value = readSession(K.out) === '1' // the session is the reference (another instance or a test may have changed it)
    if (authenticated.value || signedOut.value || loginError.value) return false
    const last = Number(readSession(K.silent)) || 0
    if (Date.now() - last < retryMs) return false
    const cfg = config.value
    if (!oidcConfigured(cfg)) return false
    // a token kept in the tab but no longer usable (expired, refresh refused) must not stop the sign-in: Charm's session may be open
    if (token.value) forget({ blockSilent: false })
    writeSession(K.silent, String(Date.now()))
    loc.assign(await buildLoginUrl(cfg, redirectUri(loc), returnTo ?? currentPage(loc)))
    return true
  }
  /** Former name of `autoLogin` (it used to ask with `prompt=none`); kept so that every console follows the automatic redirect. */
  const trySilentLogin = (loc = window.location, opts) => autoLogin(loc, opts)

  function keep(j) {
    setToken(j.access_token ?? '')
    writeSession(K.id, j.id_token ?? readSession(K.id))
    if (j.refresh_token) writeSession(K.refresh, j.refresh_token)
    writeSession(K.expiry, j.expires_in ? String(Date.now() + j.expires_in * 1000) : '')
  }

  /**
   * Finishes the login when the page was opened with ?code=&state=. Resolves to the path to go back to, or null when there was
   * no callback (or a refused silent attempt: the sign-in page takes over). Throws 'login-failed' on any error.
   */
  async function completeLogin(cfg = config.value, loc = window.location, fetchFn = fetch) {
    const p = new URLSearchParams(loc.search)
    const code = p.get('code'), state = p.get('state'), error = p.get('error')
    if (!code && !error) return null
    loginError.value = true // until the exchange succeeds
    let saved = null
    try { saved = JSON.parse(readSession(K.pkce) || 'null') } catch { /* no saved login */ }
    writeSession(K.pkce, '')
    try { history.replaceState(null, '', loc.pathname + loc.hash) } catch { /* no history */ }
    if (!code && error && SILENT_ERRORS.includes(error) && saved?.silent && saved.state === state) { loginError.value = false; return null }
    if (!code || !saved || saved.state !== state || !oidcConfigured(cfg)) throw new Error('login-failed')
    const r = await fetchFn(cfg.tokenUrl, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded', Accept: 'application/json' },
      body: new URLSearchParams({
        grant_type: 'authorization_code', code, redirect_uri: redirectUri(loc), client_id: clientIdOf(cfg), code_verifier: saved.verifier,
      }),
    })
    if (!r.ok) throw new Error('login-failed')
    const j = await r.json()
    if (!j.access_token) throw new Error('login-failed')
    keep(j)
    loginError.value = false
    markSignedOut(false)
    writeSession(K.silent, '') // came back signed in: the loop guard is released
    const back = saved.returnTo ?? '/'
    return back.startsWith('/') && !back.startsWith('//') ? back : '/'
  }

  /** Where "Sign out" sends the browser to end the Charm session too, or null for a local sign-out. */
  function endSessionUrl(cfg, returnUri) {
    const idToken = readSession(K.id)
    const base = cfg?.logoutUrl || (cfg?.authorizationUrl ? cfg.authorizationUrl.replace(/\/authorize(\?.*)?$/, '/logout') : '')
    if (!cfg || !idToken || !base || !/^https?:\/\//.test(base)) return null
    const q = new URLSearchParams({ id_token_hint: idToken, post_logout_redirect_uri: returnUri, client_id: clientIdOf(cfg) })
    return `${base}${base.includes('?') ? '&' : '?'}${q}`
  }

  let refreshing
  /** Gets a new access token with the refresh token when the current one expires within a minute. A refusal ends the local session. */
  function refreshIfNeeded(fetchFn = fetch) {
    refreshing ??= doRefresh(fetchFn).finally(() => { refreshing = undefined })
    return refreshing
  }
  async function doRefresh(fetchFn) {
    const refresh = readSession(K.refresh)
    const cfg = config.value
    const expiry = Number(readSession(K.expiry) || 0) || (decodeJwt(token.value).exp ?? 0) * 1000
    if (!refresh || !token.value || !expiry || expiry - Date.now() > 60_000 || !oidcConfigured(cfg)) return
    try {
      const r = await fetchFn(cfg.tokenUrl, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded', Accept: 'application/json' },
        body: new URLSearchParams({ grant_type: 'refresh_token', refresh_token: refresh, client_id: clientIdOf(cfg) }),
      })
      const j = await r.json().catch(() => ({}))
      if (r.status >= 500) return // Charm failing is not a refusal: keep the token until it really expires
      if (!r.ok || j.error || !j.access_token) { forget({ blockSilent: false }); return } // refused: Charm's session may still let a silent sign-in through
      keep(j)
    } catch { /* Charm unreachable: keep the token until it really expires */ }
  }

  /** Forgets the local session. A sign-out (the default) also stamps the silent attempt, so that it does not sign straight back in for a while. */
  function forget({ blockSilent = true } = {}) {
    setToken('')
    for (const k of [K.pkce, K.id, K.refresh, K.expiry]) writeSession(k, '')
    if (blockSilent) writeSession(K.silent, String(Date.now())) // signed out: no silent sign-in straight back (for a while)
  }

  /**
   * The server refused the token (401: expired early, signed by a key that changed, roles renamed): forget it and ask Charm once
   * more, silently. Resolves to true when the browser is being sent away; false means the sign-in page should be shown.
   */
  async function recover(loc = window.location) {
    forget({ blockSilent: false })
    return trySilentLogin(loc)
  }

  let listener = null
  let listenerCtor
  /** Listens for sign-outs of the other tabs and of the other Gluonify interfaces (same origin): the token is emptied at once. */
  function initLogoutBroadcast(onLogout) {
    if (typeof BroadcastChannel === 'undefined' || (listener && listenerCtor === BroadcastChannel)) return
    listener?.close()
    listenerCtor = BroadcastChannel
    listener = new BroadcastChannel(channelName)
    listener.onmessage = () => { forget(); markSignedOut(true); onLogout?.() }
  }
  function announceLogout() {
    if (typeof BroadcastChannel === 'undefined') return
    const c = new BroadcastChannel(channelName) // a channel does not hear itself: a short-lived one to speak
    c.postMessage({ app: appName })
    c.close()
  }

  /** Signs out: forgets the token, tells the other tabs and, when Charm gave an id token, ends the Charm session as well. */
  function logout(loc = window.location) {
    const target = endSessionUrl(config.value, redirectUri(loc))
    forget()
    markSignedOut(true)
    announceLogout()
    if (target) loc.assign(target)
  }

  return {
    token, config, claims, username, roles, authenticated, hasRole, signedOut, loginError,
    isValid, setToken, getAccessToken, loadConfig, buildLoginUrl, startLogin, autoLogin, trySilentLogin, completeLogin,
    endSessionUrl, refreshIfNeeded, logout, forget, recover, initLogoutBroadcast,
    baseUrl, withBase, stripBase, appRoot, redirectUri,
  }
}
