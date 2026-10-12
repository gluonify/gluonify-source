<script setup>
import { ref } from 'vue'
import GxIcon from './GxIcon.vue'
import { useGx } from './context.js'
import './components.css'

// Masked secret field with a show/hide eye. v-model; `enter` on Enter; attributes (id, autocomplete...) go to the input.
defineOptions({ inheritAttrs: false })
const model = defineModel({ type: String, default: '' })
const props = defineProps({ id: { type: String, default: undefined }, placeholder: { type: String, default: '' }, describedby: { type: String, default: undefined }, locale: { type: String, default: undefined } })
defineEmits(['enter'])
const { t } = useGx(props)
const shown = ref(false)
</script>

<template>
  <div class="gx-pw">
    <input :id="id" v-model="model" v-bind="$attrs" class="gx-input" :type="shown ? 'text' : 'password'" :placeholder="placeholder" :aria-describedby="describedby" autocomplete="off" spellcheck="false" @keyup.enter="$emit('enter')" />
    <button type="button" class="gx-pw__eye" :aria-label="shown ? t('password.hide') : t('password.show')" :aria-pressed="shown" @click="shown = !shown">
      <GxIcon :name="shown ? 'eyeOff' : 'eye'" :size="16" />
    </button>
  </div>
</template>
