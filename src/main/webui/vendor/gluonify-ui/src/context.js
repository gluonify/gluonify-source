import { computed, inject, unref } from 'vue'
import { normalizeLocale, t as translate } from './i18n/index.js'
import { useLocale } from './locale.js'
import { useTheme } from './theme.js'

const KEY = Symbol('gluonify-ui')

/**
 * Plugin: `app.use(createGluonifyUi({ locale, setLocale, theme, setTheme, messages, brand }))`. Everything is optional: without
 * it the components use the package's own language and theme state (localStorage) and English.
 *  - locale / setLocale: a Ref (or value) and a setter, to follow the console's own i18n (vue-i18n...)
 *  - theme / setTheme: same for 'light'|'dark'
 *  - messages: the console's own catalogue { en: {...}, fr: {...}, ... }, looked up before the package's (so a console can reword login.intro)
 *  - brand: { name, role, particle, color, colorLight, logo } (each text a string or a { en, fr, ... } map; `logo` is a component, wrap it in markRaw)
 */
export function createGluonifyUi(options = {}) {
  return {
    install(app) {
      app.provide(KEY, options)
    },
  }
}

/** The context of a component: translator, language, theme, brand. A `locale` prop wins over the plugin. */
export function useGx(props = {}) {
  const opts = inject(KEY, {}) || {}
  const own = useLocale()
  const ownTheme = useTheme()
  const locale = computed(() => normalizeLocale(props.locale ?? unref(opts.locale) ?? own.locale.value))
  const theme = computed(() => unref(opts.theme) ?? ownTheme.theme.value)
  return {
    locale,
    theme,
    setLocale: (l) => (opts.setLocale ?? own.setLocale)(l),
    setTheme: (v) => (opts.setTheme ?? ownTheme.setTheme)(v),
    t: (key, params) => translate(locale.value, key, params, opts.messages),
    brand: opts.brand,
  }
}

/** A text given as a string or as a { en, fr, ... } map. */
export function localized(value, locale) {
  if (value && typeof value === 'object') return value[locale] ?? value.en ?? ''
  return value ?? ''
}
