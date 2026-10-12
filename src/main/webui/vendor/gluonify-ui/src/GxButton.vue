<script setup>
import { computed } from 'vue'
import GxIcon from './GxIcon.vue'
import { metrics } from './metrics.js'
import './components.css'

// Variants: primary (filled accent), ghost (transparent). Sizes: sm, md (default), lg. An icon-only button needs an aria-label (passed as attribute).
const props = defineProps({
  variant: { type: String, default: 'primary', validator: (v) => ['primary', 'ghost'].includes(v) },
  // tone: colour family of the button. `secondary` (neutral, bordered), `danger` and `warn` re-colour either variant (filled or ghost).
  tone: { type: String, default: 'default', validator: (v) => ['default', 'secondary', 'danger', 'warn'].includes(v) },
  size: { type: String, default: 'md', validator: (v) => ['sm', 'md', 'lg'].includes(v) },
  icon: { type: String, default: '' },
  label: { type: String, default: '' },
  loading: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false },
  round: { type: Boolean, default: false },
  type: { type: String, default: 'button' },
})
const iconSize = computed(() => (props.size === 'sm' ? metrics.iconSm : metrics.iconMd))
</script>

<template>
  <button class="gx-btn" :class="[`gx-btn--${variant}`, `gx-btn--${size}`, { [`gx-btn--tone-${tone}`]: tone !== 'default', 'gx-btn--round': round, 'gx-btn--icon': !label && !$slots.default }]" :type="type" :disabled="disabled || loading" :aria-busy="loading || undefined">
    <GxIcon v-if="loading || icon" :name="loading ? 'spinner' : icon" :size="iconSize" />
    <span v-if="label" class="gx-btn__label">{{ label }}</span>
    <slot />
  </button>
</template>
