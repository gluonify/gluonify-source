/**
 * Measurements of the shared look, the single numeric source of docs/UI-SPEC.md. tokens.css carries the same values as
 * CSS variables and a test keeps both in step.
 */
export const metrics = {
  rootFontPx: 16,
  baseFontPx: 14,
  headerHeightPx: 46,
  footerFontRem: 0.8,
  h1Rem: 1.4,
  smallRem: 0.85,
  iconSm: 15,
  iconMd: 17,
  iconLg: 20,
  loginMaxWidthPx: 460,
  radiusPx: 8,
  radiusSmPx: 5,
}
/** Order of the elements of the header, from left to right; the tools menu is always last. */
export const HEADER_ORDER = ['menu', 'brand', 'title', 'spacer', 'actions', 'language', 'theme', 'user', 'switcher']
/** Tab title of the sign-in page: « Sign in - <product> » (the first part is translated). */
export const loginTitle = (signIn, product) => `${signIn} - ${product}`
