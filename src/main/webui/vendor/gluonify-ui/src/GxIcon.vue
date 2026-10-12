<script setup>
import { computed } from 'vue'
import { ICONS, resolveIcon } from './icons.js'

// `name`: a name of the common set (see icons.js). Decorative: hidden from assistive technology, the label carries the meaning.
const props = defineProps({ name: { type: String, default: '' }, size: { type: Number, default: 16 }, filled: { type: Boolean, default: false } })
const resolved = computed(() => resolveIcon(props.name))
const component = computed(() => (resolved.value ? ICONS[resolved.value] : undefined))
</script>

<template>
  <component :is="component" v-if="component" class="gx-icon" :class="{ 'gx-spin': resolved === 'spinner' }" :size="size" :fill="filled || resolved === 'dot' ? 'currentColor' : 'none'" aria-hidden="true" focusable="false" />
</template>
