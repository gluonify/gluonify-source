<script setup>
import { useGx } from './context.js'
import './components.css'

// « © <year> Gluonify », centred under a top border, across the whole width. The default slot adds console-specific text (version...).
const props = defineProps({ year: { type: Number, default: () => new Date().getFullYear() }, locale: { type: String, default: undefined } })
const gx = useGx(props)
</script>

<template>
  <footer class="gx-footer"><small>{{ gx.t('footer.copyright', { year }) }}</small><slot /></footer>
</template>
