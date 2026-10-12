import { ref } from 'vue'
import { locales, normalizeLocale } from './i18n/index.js'

/** Language state: stored in localStorage, <html lang> follows. One state per key (`gluonify.locale` by default). */
const instances = new Map()

function read(key) {
  try { const v = localStorage.getItem(key); if (v && locales.includes(v)) return v } catch { /* no storage */ }
  try { return normalizeLocale(navigator.language) } catch { return 'en' }
}
export function applyLocale(l) { try { document.documentElement.lang = l } catch { /* no document */ } }

export function useLocale(storageKey = 'gluonify.locale') {
  let s = instances.get(storageKey)
  if (!s) {
    const locale = ref(read(storageKey))
    const setLocale = (v) => {
      const l = normalizeLocale(v)
      locale.value = l
      try { localStorage.setItem(storageKey, l) } catch { /* page only */ }
      applyLocale(l)
    }
    s = { locale, setLocale }
    instances.set(storageKey, s)
    applyLocale(locale.value)
  }
  return s
}
export function resetLocales() { instances.clear() }
