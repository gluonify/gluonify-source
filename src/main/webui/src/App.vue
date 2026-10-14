<script setup>
import { computed, onMounted, ref } from 'vue'
import { api, SAMPLE_INVOICE } from './api.js'
import { auth, browser, oidcConfigured } from './auth.js'
import { useI18n } from './i18n/index.js'
import BrandLogo from './BrandLogo.vue'

const { t, locale, setLanguage, languages } = useI18n()

// Page state: the notes, the form, the platform info, the errors (displayed, never swallowed).
const notes = ref([])
const platform = ref(null)
const title = ref('')
const body = ref('')
// Sign-in: ready once GET /api/auth-config answered; open = the API asks for no token (mvn quarkus:dev); otherwise only a signed-in person sees the notes.
const ready = ref(false)
const open = ref(false)
const signedIn = computed(() => open.value || auth.authenticated.value)
const canSignIn = computed(() => oidcConfigured(auth.config.value))
// Error shown: { key, params } (translated at display time, so a language change applies) or { text } (API message shown as returned).
const error = ref(null)
const loading = ref(false)
const documentWorking = ref(false)
const SEP = '\u00b7' // separator between the name and the role of the header
const year = new Date().getFullYear()

async function refresh() {
  loading.value = true
  error.value = null
  try {
    notes.value = await api.notes()
    platform.value = await api.platform()
  } catch (e) {
    notes.value = []
    if (e.status === 401) return expired()
    error.value = e.status === 403 ? { key: 'errForbiddenRead', params: { role: 'source:read' } } : { text: e.message }
  } finally {
    loading.value = false
  }
}

async function add() {
  error.value = null
  try {
    await api.create(title.value, body.value)
    title.value = ''
    body.value = ''
    await refresh()
  } catch (e) {
    error.value = e.status === 403 ? { key: 'errForbiddenWrite', params: { role: 'source:write' } } : { text: e.message }
  }
}

async function remove(id) {
  try {
    await api.remove(id)
    await refresh()
  } catch (e) {
    error.value = { text: e.message }
  }
}

// Hands a downloaded file to the browser (a temporary link to the blob).
function saveFile(blob, name) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = name
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 10000)
}

// The sample invoice: POST /api/documents/invoice (the API calls Crystallon with the application's own token; the signed-in person's token only has to open this API).
// A 403 without a message is the API's own role check; with a message it is Crystallon's refusal, shown as received like every API message.
async function downloadInvoice() {
  error.value = null
  documentWorking.value = true
  try {
    saveFile(await api.invoice(), `invoice-${SAMPLE_INVOICE.number}.pdf`)
  } catch (e) {
    if (e.status === 401) return expired()
    error.value = e.status === 403 && !e.apiMessage ? { key: 'errForbiddenRead', params: { role: 'source:read' } } : { text: e.message }
  } finally {
    documentWorking.value = false
  }
}

// The API refused the token (expired, revoked): forget it and show the sign-in again.
function expired() {
  auth.forget({ blockSilent: false })
  platform.value = null
  error.value = { key: 'errSessionExpired' }
}

// "Sign in": the browser goes to Charm's login page and comes back here with ?code=&state= (handled in onMounted).
function signIn() {
  error.value = null
  auth.startLogin(browser.location())
}

// "Sign out": forgets the token in this tab, tells the other tabs, and ends the Charm session too when Charm gave an id token.
function signOut() {
  notes.value = []
  platform.value = null
  error.value = null
  auth.logout(browser.location())
}

onMounted(async () => {
  let cfg = null
  try { cfg = await api.authConfig() } catch { /* no configuration: sign-in unavailable */ }
  open.value = cfg?.signInRequired === false
  if (!open.value) {
    auth.config.value = oidcConfigured(cfg) ? cfg : null
    try {
      await auth.completeLogin(auth.config.value, browser.location()) // back from Charm: exchange the code for the token (PKCE verifier kept in sessionStorage)
    } catch {
      error.value = { key: 'errLoginFailed' }
    }
    // not signed in: straight to Charm's sign-in page, no intermediate step (the kit's autoLogin keeps the page for the return); the
    // sign-in section only shows after a sign-out, an error coming back from Charm, without Charm, or inside the kit's loop guard
    if (!auth.authenticated.value && !error.value && (await auth.autoLogin(browser.location()))) return
  }
  auth.initLogoutBroadcast(() => { notes.value = []; platform.value = null }) // signed out in another tab: this one follows
  ready.value = true
  if (signedIn.value) await refresh()
})
</script>

<template>
  <main>
    <header class="top">
      <h1><BrandLogo :label="t('logo')" /> <span class="brandtext"><span class="line"><span class="name">{{ t('pageTitle') }} {{ SEP }}</span> <span class="role">{{ t('appRole') }}</span></span><span class="particle">{{ t('appParticle') }}</span></span></h1>
      <div class="who">
      <span v-if="ready && !open && signedIn" class="user">{{ t('signedInAs', { name: auth.username.value || '—' }) }}</span>
      <button v-if="ready && !open && signedIn" type="button" class="ghost" @click="signOut">{{ t('signOut') }}</button>
      <label class="lang">
        <span class="sr-only">{{ t('language') }}</span>
        <select :value="locale" :aria-label="t('language')" @change="setLanguage($event.target.value)">
          <option v-for="l in languages" :key="l.code" :value="l.code" :lang="l.code">{{ l.name }}</option>
        </select>
      </label>
      </div>
    </header>
    <p class="lead">{{ t('lead') }}</p>

    <p v-if="ready && open" class="hint dev">{{ t('devOpen') }}</p>

    <section v-if="ready && !signedIn" class="signin">
      <h2>{{ t('authTitle') }}</h2>
      <p v-if="auth.signedOut.value" class="hint" role="status">{{ t('signedOutNotice') }}</p>
      <p class="hint">{{ t('signedOutHint', { read: 'source:read', write: 'source:write' }) }}</p>
      <button v-if="canSignIn" type="button" @click="signIn">{{ t(auth.signedOut.value ? 'signInAgain' : 'signIn') }}</button>
      <p v-else class="hint">{{ t('signInUnavailable') }}</p>
    </section>

    <p v-if="error" class="error" role="alert">{{ error.key ? t(error.key, error.params) : error.text }}</p>

    <section v-if="signedIn">
      <h2>{{ t('notesTitle') }}</h2>
      <form class="stack" @submit.prevent="add">
        <input v-model="title" required maxlength="120" :placeholder="t('titlePlaceholder')" :aria-label="t('titleLabel')" />
        <textarea v-model="body" maxlength="4000" rows="3" :placeholder="t('bodyPlaceholder')" :aria-label="t('bodyLabel')"></textarea>
        <button type="submit">{{ t('add') }}</button>
      </form>
      <p v-if="loading" class="hint">{{ t('loading') }}</p>
      <ul class="notes">
        <li v-for="n in notes" :key="n.id">
          <div>
            <strong>{{ n.title }}</strong>
            <span class="meta"> — {{ n.author || t('anonymous') }}, {{ new Date(n.createdAt).toLocaleString(locale) }}</span>
            <p>{{ n.body }}</p>
          </div>
          <button class="danger" type="button" :aria-label="t('removeLabel', { title: n.title })" @click="remove(n.id)">{{ t('remove') }}</button>
        </li>
      </ul>
      <p v-if="!loading && !notes.length && !error" class="hint">{{ t('empty', { store: 'source.store=files' }) }}</p>
      <button type="button" @click="refresh">{{ t('refresh') }}</button>
    </section>

    <section v-if="signedIn && platform && platform.documents">
      <h2>{{ t('documentsTitle') }}</h2>
      <p class="hint">{{ t('documentsHint') }}</p>
      <button type="button" :disabled="documentWorking" @click="downloadInvoice">{{ documentWorking ? t('documentsWorking') : t('documentsDownload') }}</button>
    </section>

    <section v-if="signedIn && platform">
      <h2>{{ t('platformTitle') }}</h2>
      <dl class="grid">
        <dt>{{ t('environment') }}</dt><dd>{{ platform.envName || t('local') }}</dd>
        <dt>{{ t('replica') }}</dt><dd>{{ platform.envNode || '—' }}</dd>
        <dt>{{ t('storage') }}</dt><dd>{{ platform.store }} ({{ platform.storeReady ? t('storeReady') : t('storeDown') }})</dd>
        <dt>{{ t('services') }}</dt><dd>{{ Object.keys(platform.services || {}).join(', ') || t('none') }}</dd>
        <dt>{{ t('webhooks') }}</dt><dd>{{ platform.webhookOpen ? t('open') : t('closed') }} — {{ t('received') }}: {{ platform.webhooks.accepted }}, {{ t('duplicates') }}: {{ platform.webhooks.duplicates }}</dd>
      </dl>
    </section>
    <footer class="foot">{{ t('copyright', { year }) }}</footer>
  </main>
</template>
