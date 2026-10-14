<script setup>
import { onBeforeUnmount, watchEffect } from 'vue'
import GxBrandBlock from './GxBrandBlock.vue'
import GxButton from './GxButton.vue'
import GxFooter from './GxFooter.vue'
import GxHeader from './GxHeader.vue'
import { useGx, localized } from './context.js'
import { loginTitle } from './metrics.js'
import './components.css'

/**
 * THE sign-in page of every console (and, by the same classes, of Charm). Layout: header (brand, language, theme), then a panel
 * with h1, the console's introduction, the status and errors, then the « Sign in with Charm » button (icon signIn), then the footer.
 * Tab title: « Sign in - <product> ». Every human sign-in goes through Charm (authorization code + PKCE), so that Charm's MFA
 * (WebAuthn, TOTP) cannot be bypassed: there is deliberately no field to paste an access token. Without Charm configured, the page
 * says so instead of offering the button. The logic stays in the console: it listens to `charm` (start the redirect) and drives
 * `charmEnabled`, `denied` (account refused by the console), `failed`, `checking` (silent sign-in running).
 */
const props = defineProps({
  intro: { type: [String, Object], default: undefined },
  charmEnabled: { type: Boolean, default: false },
  denied: { type: Boolean, default: false },
  failed: { type: Boolean, default: false },
  checking: { type: Boolean, default: false },
  name: { type: [String, Object], default: undefined },
  locale: { type: String, default: undefined },
})
const emit = defineEmits(['charm'])
const gx = useGx(props)

const productName = () => localized(props.name ?? gx.brand?.name, gx.locale.value)
const previousTitle = typeof document !== 'undefined' ? document.title : ''
watchEffect(() => { document.title = loginTitle(gx.t('login.title'), productName()) })
onBeforeUnmount(() => { document.title = previousTitle })
</script>

<template>
  <div class="gx-login">
    <GxHeader :locale="locale"><template #brand><GxBrandBlock :locale="locale" /></template></GxHeader>
    <main id="main" class="gx-login__main">
      <div class="gx-login__panel">
        <h1>{{ gx.t('login.title') }}</h1>
        <p class="gx-muted gx-login__intro"><slot name="intro">{{ intro === undefined ? gx.t('login.intro') : localized(intro, gx.locale.value) }}</slot></p>
        <p v-if="checking" class="gx-muted" role="status">{{ gx.t('login.checking') }}</p>
        <p v-if="denied" class="gx-error" role="alert">{{ gx.t('login.rejected') }}</p>
        <p v-if="failed" class="gx-error" role="alert">{{ gx.t('login.failed') }}</p>
        <GxButton v-if="charmEnabled" :label="gx.t('login.charm')" icon="signIn" @click="emit('charm')" />
        <p v-else class="gx-muted" role="note" data-gx="unavailable">{{ gx.t('login.unavailable') }}</p>
      </div>
    </main>
    <GxFooter :locale="locale" />
  </div>
</template>
