import { ref } from 'vue'

let cache = null

/** Test helper: forgets the session cache. */
export function resetPortalCatalog() { cache = null }

/** Loads GET <origin>/portal/apps.json once per session. Any failure yields an empty catalogue, silently. */
export function usePortalCatalog() {
  const catalog = ref([])
  const loaded = ref(false)
  if (!cache) {
    cache = (async () => {
      try {
        const res = await fetch(`${window.location.origin}/portal/apps.json`, { headers: { Accept: 'application/json' } })
        if (!res.ok) return []
        const body = await res.json()
        return Array.isArray(body) ? body : Array.isArray(body && body.apps) ? body.apps : []
      } catch {
        return []
      }
    })()
  }
  cache.then((c) => { catalog.value = c; loaded.value = true })
  return { catalog, loaded }
}
