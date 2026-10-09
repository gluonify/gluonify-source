<script setup>
import { onMounted, ref } from 'vue'
import { api, token } from './api.js'
import { useI18n } from './i18n/index.js'
import BrandLogo from './BrandLogo.vue'

const { t, locale, setLanguage, languages } = useI18n()

// Page state: the notes, the form, the platform info, the errors (displayed, never swallowed).
const notes = ref([])
const platform = ref(null)
const title = ref('')
const body = ref('')
const jwt = ref(token.get())
// Error shown: { key, params } (translated at display time, so a language change applies) or { text } (API message shown as returned).
const error = ref(null)
const loading = ref(false)

async function refresh() {
  loading.value = true
  error.value = null
  try {
    notes.value = await api.notes()
    platform.value = await api.platform()
  } catch (e) {
    notes.value = []
    error.value = e.status === 401 ? { key: 'errUnauthenticated' } : e.status === 403 ? { key: 'errForbiddenRead', params: { role: 'source:read' } } : { text: e.message }
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

function saveToken() {
  token.set(jwt.value.trim())
  refresh()
}

onMounted(refresh)
</script>

<template>
  <main>
    <header class="top">
      <h1><BrandLogo :label="t('logo')" /> {{ t('pageTitle') }}</h1>
      <label class="lang">
        <span class="sr-only">{{ t('language') }}</span>
        <select :value="locale" :aria-label="t('language')" @change="setLanguage($event.target.value)">
          <option v-for="l in languages" :key="l.code" :value="l.code" :lang="l.code">{{ l.name }}</option>
        </select>
      </label>
    </header>
    <p class="lead">{{ t('lead') }}</p>

    <section>
      <h2>{{ t('authTitle') }}</h2>
      <p class="hint">{{ t('authHint', { read: 'source:read', write: 'source:write' }) }}</p>
      <form class="row" @submit.prevent="saveToken">
        <input v-model="jwt" type="password" autocomplete="off" :placeholder="t('tokenPlaceholder')" :aria-label="t('tokenLabel')" />
        <button type="submit">{{ t('useToken') }}</button>
      </form>
    </section>

    <p v-if="error" class="error" role="alert">{{ error.key ? t(error.key, error.params) : error.text }}</p>

    <section>
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

    <section v-if="platform">
      <h2>{{ t('platformTitle') }}</h2>
      <dl class="grid">
        <dt>{{ t('environment') }}</dt><dd>{{ platform.envName || t('local') }}</dd>
        <dt>{{ t('replica') }}</dt><dd>{{ platform.envNode || '—' }}</dd>
        <dt>{{ t('storage') }}</dt><dd>{{ platform.store }} ({{ platform.storeReady ? t('storeReady') : t('storeDown') }})</dd>
        <dt>{{ t('services') }}</dt><dd>{{ Object.keys(platform.services || {}).join(', ') || t('none') }}</dd>
        <dt>{{ t('webhooks') }}</dt><dd>{{ platform.webhookOpen ? t('open') : t('closed') }} — {{ t('received') }}: {{ platform.webhooks.accepted }}, {{ t('duplicates') }}: {{ platform.webhooks.duplicates }}</dd>
      </dl>
    </section>
  </main>
</template>
