<script setup>
import { onBeforeUnmount, ref, watchEffect } from 'vue'
import GxBrandBlock from './GxBrandBlock.vue'
import GxButton from './GxButton.vue'
import GxFooter from './GxFooter.vue'
import GxHeader from './GxHeader.vue'
import GxPasswordInput from './GxPasswordInput.vue'
import { useGx, localized } from './context.js'
import { loginTitle } from './metrics.js'
import './components.css'

/**
 * THE sign-in page of every console (and, by the same classes, of Charm). Layout: header (brand, language, theme), then a panel
 * with h1, the console's introduction, the « Sign in with Charm » button (icon signIn), a rule, the masked token field with its eye,
 * the hint, the errors and the submit button (icon check), then the footer. Tab title: « Sign in - <product> ».
 * The logic stays in the console: it listens to `charm` (start the redirect) and `submit` (a token with three parts) and drives
 * `charmEnabled`, `denied` (token rejected), `failed`, `checking` (silent sign-in running).
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
const emit = defineEmits(['charm', 'submit'])
const gx = useGx(props)
const pasted = ref('')
const err = ref('')

const productName = () => localized(props.name ?? gx.brand?.name, gx.locale.value)
const previousTitle = typeof document !== 'undefined' ? document.title : ''
watchEffect(() => { document.title = loginTitle(gx.t('login.title'), productName()) })
onBeforeUnmount(() => { document.title = previousTitle })

function submit() {
  err.value = ''
  const v = pasted.value.trim()
  if (v.split('.').length !== 3) { err.value = gx.t('login.invalidToken'); return }
  pasted.value = ''
  emit('submit', v)
}
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
        <template v-if="charmEnabled">
          <GxButton :label="gx.t('login.charm')" icon="signIn" @click="emit('charm')" />
          <hr />
        </template>
        <form novalidate @submit.prevent="submit">
          <label class="gx-label" for="login-token">{{ gx.t('login.tokenLabel') }}</label>
          <GxPasswordInput id="login-token" v-model="pasted" describedby="login-hint" :locale="locale" @enter="submit" />
          <p id="login-hint" class="gx-muted gx-hint">{{ gx.t('login.tokenHint') }}</p>
          <p v-if="err" class="gx-error" role="alert">{{ err }}</p>
          <GxButton :label="gx.t('login.submit')" icon="check" type="submit" />
        </form>
      </div>
    </main>
    <GxFooter :locale="locale" />
  </div>
</template>
