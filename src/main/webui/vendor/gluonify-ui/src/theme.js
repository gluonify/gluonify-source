import { ref } from 'vue'

/**
 * Light/dark theme. The choice is kept in localStorage; without one the system preference decides (light when the
 * browser cannot tell). The page gets `data-theme` on <html> (the tokens' switch) and the legacy `app-dark` class, plus `color-scheme`.
 */
export const THEMES = ['light', 'dark']
const instances = new Map()

function read(key) {
  try { const v = localStorage.getItem(key); if (THEMES.includes(v)) return v } catch { /* no storage */ }
  return null
}
function system() {
  try { return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light' } catch { return 'light' }
}

export function applyTheme(theme) {
  try {
    const el = document.documentElement
    el.dataset.theme = theme
    el.classList.toggle('app-dark', theme === 'dark')
    el.style.colorScheme = theme
  } catch { /* no document */ }
}

/** One state per storage key (`gluonify.theme` by default): { theme: Ref, setTheme, toggle }. */
export function useTheme(storageKey = 'gluonify.theme') {
  let s = instances.get(storageKey)
  if (!s) {
    const theme = ref(read(storageKey) ?? system())
    const setTheme = (v) => {
      if (!THEMES.includes(v)) return
      theme.value = v
      try { localStorage.setItem(storageKey, v) } catch { /* the choice lasts for this page only */ }
      applyTheme(v)
    }
    s = { theme, setTheme, toggle: () => setTheme(theme.value === 'dark' ? 'light' : 'dark') }
    instances.set(storageKey, s)
    applyTheme(theme.value)
  }
  return s
}

export function resetThemes() { instances.clear() }
