// Sign-in with Charm, the platform's identity server, through the shared kit @gluonify/ui (vendored in ../vendor/gluonify-ui: a Git clone builds on its own).
// The UI is a PUBLIC OIDC client: authorization code + PKCE (S256), no secret in the browser. "Sign in" sends the browser to Charm's own login page
// (username and password are typed THERE, never in this application); Charm sends it back here with a code, exchanged for a token whose audience is
// gluonify-source and whose "roles" claim carries source:read / source:write. The client id and Charm's public endpoints come from GET /api/auth-config
// (never hard-coded). Tokens only live in sessionStorage (gone when the tab closes) and are sent as "Authorization: Bearer" to this application's /api.
import { createAuth } from '@gluonify/ui'

export { oidcConfigured } from '@gluonify/ui'

export const auth = createAuth({
  storagePrefix: 'gluonify-source', // sessionStorage keys gluonify-source-token, -pkce, -idtoken, -refresh, -expiry
  baseUrl: '/', // the UI is served at the root of the application's address: the redirect URI registered in Charm is https://<app address>/
  getConfig: async () => null // the configuration comes from GET /api/auth-config, loaded by App.vue into auth.config
})

// The page's location, replaceable in tests (jsdom cannot navigate).
export const browser = { location: () => window.location }
