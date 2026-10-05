// Tiny dependency-free i18n. To add a language: create <code>.js with the keys of en.js, import it here and add it to MESSAGES and LANGUAGES.
import { ref } from 'vue'
import en from './en.js'
import fr from './fr.js'
import es from './es.js'
import it from './it.js'
import de from './de.js'

const MESSAGES = { en, fr, es, it, de }
export const LANGUAGES = [
  { code: 'en', name: 'English' },
  { code: 'fr', name: 'Français' },
  { code: 'es', name: 'Español' },
  { code: 'it', name: 'Italiano' },
  { code: 'de', name: 'Deutsch' }
]
export const DEFAULT_LANGUAGE = 'en'
const KEY = 'gluonify-source-lang'

const supported = (c) => (typeof c === 'string' && c.slice(0, 2).toLowerCase() in MESSAGES ? c.slice(0, 2).toLowerCase() : null)

// Remembered choice (localStorage), else the browser language if it is one of the five, else English.
export function detectLanguage() {
  try { const s = supported(localStorage.getItem(KEY)); if (s) return s } catch { /* storage unavailable */ }
  const list = typeof navigator !== 'undefined' ? navigator.languages || [navigator.language] : []
  for (const l of list) { const s = supported(l); if (s) return s }
  return DEFAULT_LANGUAGE
}

export const locale = ref(DEFAULT_LANGUAGE)

export function setLanguage(code) {
  const c = supported(code) || DEFAULT_LANGUAGE
  locale.value = c
  try { localStorage.setItem(KEY, c) } catch { /* storage unavailable: the choice lasts for this page only */ }
  if (typeof document !== 'undefined') {
    document.documentElement.lang = c
    document.title = MESSAGES[c].pageTitle
  }
}

export function initLanguage() { setLanguage(detectLanguage()) }

// t('key', { name: value }): falls back to English, then to the key itself.
export function t(key, params = {}) {
  const s = MESSAGES[locale.value][key] ?? en[key] ?? key
  return s.replace(/\{(\w+)\}/g, (m, k) => (k in params ? String(params[k]) : m))
}

export const useI18n = () => ({ t, locale, setLanguage, languages: LANGUAGES })
