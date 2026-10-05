<script setup>
import { onMounted, ref } from 'vue'
import { api, token } from './api.js'

// État de la page : les notes, le formulaire, l'info plateforme, les erreurs (affichées, jamais avalées).
const notes = ref([])
const platform = ref(null)
const title = ref('')
const body = ref('')
const jwt = ref(token.get())
const error = ref('')
const loading = ref(false)

async function refresh() {
  loading.value = true
  error.value = ''
  try {
    notes.value = await api.notes()
    platform.value = await api.platform()
  } catch (e) {
    notes.value = []
    error.value = e.status === 401 ? 'Non authentifié : collez un jeton Charm ci-dessous.' : e.status === 403 ? 'Jeton valide, mais sans le rôle source:read.' : e.message
  } finally {
    loading.value = false
  }
}

async function add() {
  error.value = ''
  try {
    await api.create(title.value, body.value)
    title.value = ''
    body.value = ''
    await refresh()
  } catch (e) {
    error.value = e.status === 403 ? 'Il faut le rôle source:write pour créer une note.' : e.message
  }
}

async function remove(id) {
  try {
    await api.remove(id)
    await refresh()
  } catch (e) {
    error.value = e.message
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
    <h1>gluonify-source</h1>
    <p class="lead">Application exemple pour Gluonify : un service REST, cette interface, et les services de la plateforme.</p>

    <section>
      <h2>Authentification</h2>
      <p class="hint">En production, l'API demande un jeton émis par Charm (rôles <code>source:read</code> et <code>source:write</code>). En développement, aucun jeton n'est nécessaire.</p>
      <form class="row" @submit.prevent="saveToken">
        <input v-model="jwt" type="password" autocomplete="off" placeholder="Jeton Charm (Bearer)" aria-label="Jeton Charm" />
        <button type="submit">Utiliser ce jeton</button>
      </form>
    </section>

    <p v-if="error" class="error" role="alert">{{ error }}</p>

    <section>
      <h2>Notes</h2>
      <form class="stack" @submit.prevent="add">
        <input v-model="title" required maxlength="120" placeholder="Titre" aria-label="Titre" />
        <textarea v-model="body" maxlength="4000" rows="3" placeholder="Texte" aria-label="Texte"></textarea>
        <button type="submit">Ajouter</button>
      </form>
      <p v-if="loading" class="hint">Chargement…</p>
      <ul class="notes">
        <li v-for="n in notes" :key="n.id">
          <div>
            <strong>{{ n.title }}</strong>
            <span class="meta"> — {{ n.author || 'anonyme' }}, {{ new Date(n.createdAt).toLocaleString() }}</span>
            <p>{{ n.body }}</p>
          </div>
          <button class="danger" type="button" :aria-label="'Supprimer ' + n.title" @click="remove(n.id)">Supprimer</button>
        </li>
      </ul>
      <p v-if="!loading && !notes.length && !error" class="hint">Aucune note. Avec <code>source.store=files</code>, une note créée sur une autre réplique peut mettre ~3 secondes à apparaître : rafraîchissez.</p>
      <button type="button" @click="refresh">Rafraîchir</button>
    </section>

    <section v-if="platform">
      <h2>Ce que la plateforme fournit</h2>
      <dl class="grid">
        <dt>Environnement</dt><dd>{{ platform.envName || '(local)' }}</dd>
        <dt>Réplique</dt><dd>{{ platform.envNode || '—' }}</dd>
        <dt>Stockage</dt><dd>{{ platform.store }} ({{ platform.storeReady ? 'prêt' : 'indisponible' }})</dd>
        <dt>Services déclarés</dt><dd>{{ Object.keys(platform.services || {}).join(', ') || 'aucun' }}</dd>
        <dt>Webhooks</dt><dd>{{ platform.webhookOpen ? 'ouverts' : 'fermés' }} — reçus : {{ platform.webhooks.accepted }}, doublons : {{ platform.webhooks.duplicates }}</dd>
      </dl>
    </section>
  </main>
</template>
