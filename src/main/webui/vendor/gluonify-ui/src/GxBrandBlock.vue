<script setup>
import { computed } from 'vue'
import { useGx, localized } from './context.js'
import './components.css'

// Logo, then « Name · role » and the particle line. Texts: strings or { en, fr, ... } maps; default from the plugin's `brand`.
// Logo: the `logo` slot, else the `brand.logo` component of the plugin (so the sign-in page and the header show the console's logo), else a generic mark.
// Colours: `color` (light theme) and `colorLight` (dark theme) set --gx-brand / --gx-brand-dark; the logo goes in the `logo` slot.
const props = defineProps({
  name: { type: [String, Object], default: undefined },
  role: { type: [String, Object], default: undefined },
  particle: { type: [String, Object], default: undefined },
  color: { type: String, default: undefined },
  colorLight: { type: String, default: undefined },
  locale: { type: String, default: undefined },
})
const gx = useGx(props)
const pick = (own, key) => localized(own ?? gx.brand?.[key], gx.locale.value)
const text = computed(() => ({ name: pick(props.name, 'name'), role: pick(props.role, 'role'), particle: pick(props.particle, 'particle') }))
const style = computed(() => {
  const c = props.color ?? gx.brand?.color
  const d = props.colorLight ?? gx.brand?.colorLight
  return { ...(c ? { '--gx-brand': c } : {}), ...(d ? { '--gx-brand-dark': d } : {}) }
})
</script>

<template>
  <span class="gx-brand" :style="style">
    <span class="gx-brand__logo"><slot name="logo"><component :is="gx.brand.logo" v-if="gx.brand?.logo" /><svg v-else viewBox="0 0 24 24" width="26" height="26" role="img" :aria-label="text.name" focusable="false"><circle cx="12" cy="12" r="9" fill="none" stroke="currentColor" stroke-width="1.8" /><circle cx="12" cy="12" r="3" fill="var(--gx-particle)" /></svg></slot></span>
    <span class="gx-brand__text"><span class="gx-brand__line"><span class="gx-brand__name">{{ text.name }} ·</span> <span class="gx-brand__role">{{ text.role }}</span></span><span class="gx-brand__particle">{{ text.particle }}</span></span>
  </span>
</template>
