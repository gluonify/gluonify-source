import en from './en.json'
import fr from './fr.json'
import es from './es.json'
import it from './it.json'
import de from './de.json'

export const messages = { en, fr, es, it, de }
export const locales = Object.keys(messages)
/** Native names, shown by the language selector. */
export const LANGUAGES = { en: 'English', fr: 'Français', es: 'Español', it: 'Italiano', de: 'Deutsch' }

/** Normalises 'fr-CH' to 'fr'; unknown locales fall back to English. */
export function normalizeLocale(locale) {
  const base = String(locale || 'en').toLowerCase().split(/[-_]/)[0]
  return messages[base] ? base : 'en'
}

/** Replaces `{name}` placeholders. */
export function interpolate(text, params) {
  return params ? text.replace(/\{(\w+)\}/g, (m, k) => (params[k] !== undefined ? String(params[k]) : m)) : text
}

/** Minimal translator: t(locale, key, params?). `extra` is the console's own catalogue ({ en: {...}, fr: {...} }), looked up first. */
export function t(locale, key, params, extra) {
  const l = normalizeLocale(locale)
  const text = extra?.[l]?.[key] ?? extra?.en?.[key] ?? messages[l][key] ?? messages.en[key] ?? key
  return interpolate(text, params)
}

/**
 * Helper for a console that adds its own keys: merges them over the package catalogue, language by language.
 * A language that lacks one of the console's keys is an error (thrown), as the platform rules require five complete languages.
 */
export function extendMessages(extra) {
  const out = {}
  const english = Object.keys(extra?.en ?? {})
  for (const l of locales) {
    const own = extra?.[l] ?? {}
    const missing = english.filter((k) => !(k in own))
    if (missing.length) throw new Error(`gluonify-ui: language "${l}" lacks keys: ${missing.join(', ')}`)
    out[l] = { ...messages[l], ...own }
  }
  return out
}

/** Keys that differ between languages (used by the parity tests of the package and of the consoles). */
export function parityGaps(catalogue = messages) {
  const all = new Set(Object.values(catalogue).flatMap((m) => Object.keys(m)))
  const gaps = {}
  for (const l of Object.keys(catalogue)) {
    const missing = [...all].filter((k) => !(k in catalogue[l]))
    if (missing.length) gaps[l] = missing
  }
  return gaps
}
