import { ref, watch } from 'vue'

let cache = null
let cacheKey = null

/** Test helper: forgets the session cache. */
export function resetPortalCatalog() { cache = null; cacheKey = null }

async function load(token) {
  const url = `${window.location.origin}/portal/apps.json`
  const headers = { Accept: 'application/json' }
  try {
    let res = await fetch(url, { headers: token ? { ...headers, Authorization: `Bearer ${token}` } : headers })
    // a token the catalogue does not accept (expired, another audience): the public catalogue rather than no menu
    if (!res.ok && token) res = await fetch(url, { headers })
    if (!res.ok) return []
    const body = await res.json()
    if (Array.isArray(body)) return body
    return Array.isArray(body?.apps) ? body.apps : []
  } catch {
    return []
  }
}

/**
 * Loads GET <origin>/portal/apps.json once per session and per token. Any failure yields an empty catalogue, silently.
 * Option `token`: a function returning the signed-in person's access token (or nothing); when it gives one, it is sent as a Bearer header so that a
 * catalogue computed per person (Prism's on the customer origin) answers for them. A refused token falls back to the anonymous catalogue. The function is
 * watched: when it reads a Vue ref (`auth.getAccessToken`), a sign-in after the menu was mounted loads the person's catalogue.
 */
export function usePortalCatalog(options = {}) {
  const catalog = ref([])
  const loaded = ref(false)
  const read = () => {
    try { return (typeof options.token === 'function' ? options.token() : '') || '' } catch { return '' }
  }
  watch(read, (token) => {
    if (!cache || cacheKey !== token) {
      cacheKey = token
      cache = load(token)
    }
    cache.then((c) => {
      if (cacheKey === token) catalog.value = c
      loaded.value = true
    })
  }, { immediate: true })
  return { catalog, loaded }
}
