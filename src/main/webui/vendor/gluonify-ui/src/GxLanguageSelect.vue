<script setup>
import { LANGUAGES } from './i18n/index.js'
import { useGx } from './context.js'
import './components.css'

// The five languages under their native names. v-model optional: without it the choice goes through the plugin / the package's
// own state (localStorage, <html lang>).
const props = defineProps({ modelValue: { type: String, default: undefined }, locale: { type: String, default: undefined } })
const emit = defineEmits(['update:modelValue'])
const gx = useGx(props)
function change(e) {
  const v = e.target.value
  emit('update:modelValue', v)
  gx.setLocale(v)
}
</script>

<template>
  <select class="gx-select" :aria-label="gx.t('language.label')" :value="modelValue ?? gx.locale.value" @change="change">
    <option v-for="(label, code) in LANGUAGES" :key="code" :lang="code" :value="code">{{ label }}</option>
  </select>
</template>
