import type { Component, ComputedRef, Plugin, Ref } from 'vue'

export type Locale = 'en' | 'fr' | 'es' | 'it' | 'de'
export type Theme = 'light' | 'dark'
export type Catalogue = Record<Locale, Record<string, string>>
export type Localized = string | Partial<Record<Locale, string>>

export interface PortalApp { id: string; name: string; role: Localized; particle: string; color: string; colorLight?: string; url: string; requiredRoles: string[]; mark?: string }

export const AppSwitcher: Component
export function usePortalCatalog(): { catalog: Ref<PortalApp[]> }
export function resetPortalCatalog(): void
export function rolesFromToken(jwt: string): string[]
export function roleMatches(pattern: string, role: string): boolean
export function visibleApps(catalog: PortalApp[], roles: string[]): PortalApp[]

export const messages: Catalogue
export const locales: Locale[]
export const LANGUAGES: Record<Locale, string>
export function normalizeLocale(locale?: string | null): Locale
export function interpolate(text: string, params?: Record<string, unknown>): string
export function t(locale: string, key: string, params?: Record<string, unknown>, extra?: Partial<Catalogue>): string
/** Merges the console's own keys over the package catalogue; throws when a language lacks a key of English. */
export function extendMessages(extra: Catalogue): Catalogue
export function parityGaps(catalogue?: Record<string, Record<string, string>>): Record<string, string[]>

export const GxIcon: Component
export const GxButton: Component
export const GxPasswordInput: Component
export const GxBrandBlock: Component
export const GxLanguageSelect: Component
export const GxThemeToggle: Component
export const GxHeader: Component
export interface UserMenuItem { id: string; label: string; icon?: string; href?: string }
export const GxUserMenu: Component
export const GxFooter: Component
export const GxLoginPage: Component
export const ICONS: Record<string, Component>
export function resolveIcon(ref?: string | null): string | undefined

export interface Brand { name: Localized; role: Localized; particle: Localized; color?: string; colorLight?: string; logo?: Component }
export interface GluonifyUiOptions {
  locale?: Ref<string> | string
  setLocale?: (l: string) => void
  theme?: Ref<Theme> | Theme
  setTheme?: (t: Theme) => void
  messages?: Partial<Catalogue>
  brand?: Brand
}
export function createGluonifyUi(options?: GluonifyUiOptions): Plugin
export function useGx(props?: { locale?: string }): {
  locale: ComputedRef<Locale>; theme: ComputedRef<Theme>; setLocale(l: string): void; setTheme(t: Theme): void
  t(key: string, params?: Record<string, unknown>): string; brand?: Brand
}
export function localized(value: Localized | undefined, locale: string): string
export const THEMES: Theme[]
export function applyTheme(theme: Theme): void
export function useTheme(storageKey?: string): { theme: Ref<Theme>; setTheme(t: Theme): void; toggle(): void }
export function useLocale(storageKey?: string): { locale: Ref<Locale>; setLocale(l: string): void }

export const metrics: Readonly<Record<string, number>>
export const HEADER_ORDER: readonly string[]
export function loginTitle(signIn: string, product: string): string

export interface OidcConfig { clientId: string; authorizationUrl: string; tokenUrl: string; logoutUrl?: string; scope?: string }
export interface Claims { sub?: string; name?: string; preferred_username?: string; exp?: number; roles?: string[]; [key: string]: unknown }
export interface AuthOptions {
  storagePrefix: string
  getConfig?: () => Promise<OidcConfig | null>
  baseUrl?: string | (() => string)
  clientId?: string
  scope?: string
  silentRetryMs?: number
  logoutChannel?: string
  appName?: string
}
export interface Auth {
  token: Ref<string>; config: Ref<OidcConfig | null>; claims: ComputedRef<Claims>; username: ComputedRef<string>
  roles: ComputedRef<string[]>; authenticated: ComputedRef<boolean>; hasRole(role: string): boolean
  isValid(token?: string): boolean; setToken(v: string): void; getAccessToken(): string | undefined
  loadConfig(fetchFn?: typeof fetch): Promise<OidcConfig | null>
  buildLoginUrl(cfg: OidcConfig, redirectUri: string, returnTo?: string, silent?: boolean): Promise<string>
  startLogin(loc?: Location): Promise<boolean>
  trySilentLogin(loc?: Location): Promise<boolean>
  completeLogin(cfg?: OidcConfig | null, loc?: Location, fetchFn?: typeof fetch): Promise<string | null>
  endSessionUrl(cfg: OidcConfig | null, returnUri: string): string | null
  refreshIfNeeded(fetchFn?: typeof fetch): Promise<void>
  logout(loc?: Location): void
  forget(opts?: { blockSilent?: boolean }): void
  /** The server refused the token: forget it and try one silent sign-in (true: the browser is leaving for Charm). */
  recover(loc?: Location): Promise<boolean>
  initLogoutBroadcast(onLogout?: () => void): void
  baseUrl(): string; withBase(path: string): string; stripBase(pathname: string): string
  appRoot(loc?: Pick<Location, 'origin'>): string; redirectUri(loc?: Pick<Location, 'origin'>): string
}
export function createAuth(options: AuthOptions): Auth
export function decodeJwt(token: string): Claims
export function challenge(verifier: string): Promise<string>
export function scopeOf(cfg: OidcConfig, scope?: string): string
export function oidcConfigured(cfg: unknown): cfg is OidcConfig
export const LOGOUT_CHANNEL: string
export const SILENT_RETRY_MS: number
