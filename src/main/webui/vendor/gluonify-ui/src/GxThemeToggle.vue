<script setup>
import { computed } from 'vue'
import GxButton from './GxButton.vue'
import { useGx } from './context.js'

// Two states. The button shows the theme it switches to (sun in the dark theme, moon in the light one). v-model optional ('light'|'dark').
const model = defineModel({ type: String, default: undefined })
const props = defineProps({ locale: { type: String, default: undefined } })
const gx = useGx(props)
const current = computed(() => model.value ?? gx.theme.value)
function toggle() {
  const next = current.value === 'dark' ? 'light' : 'dark'
  model.value = next
  gx.setTheme(next)
}
</script>

<template>
  <GxButton class="gx-theme" variant="ghost" round :icon="current === 'dark' ? 'sun' : 'moon'" :title="gx.t('theme.' + current)" :aria-label="gx.t('theme.toggle')" @click="toggle" />
</template>
