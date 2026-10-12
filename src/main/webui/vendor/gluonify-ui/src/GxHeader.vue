<script setup>
import GxBrandBlock from './GxBrandBlock.vue'
import GxButton from './GxButton.vue'
import GxLanguageSelect from './GxLanguageSelect.vue'
import GxThemeToggle from './GxThemeToggle.vue'
import { useGx } from './context.js'
import './components.css'

// Order, left to right (HEADER_ORDER): menu button, brand, title, spacer, actions, language, theme, user, switcher.
// The tools menu (AppSwitcher, `switcher` slot) is ALWAYS the last element, at the far right. Without `brand` and `title` slots the brand block is shown.
const props = defineProps({
  menuButton: { type: Boolean, default: false },
  // state and target of the menu button, for assistive technology (aria-expanded, aria-controls); omitted when not given
  menuOpen: { type: Boolean, default: undefined },
  menuControls: { type: String, default: undefined },
  language: { type: Boolean, default: true },
  theme: { type: Boolean, default: true },
  locale: { type: String, default: undefined },
})
defineEmits(['menu'])
const gx = useGx(props)
</script>

<template>
  <header class="gx-header">
    <GxButton v-if="menuButton" class="gx-header__menu" variant="ghost" round icon="menu" :aria-label="gx.t('header.menu')" :aria-expanded="menuOpen" :aria-controls="menuControls" @click="$emit('menu')" />
    <div v-if="$slots.brand || !$slots.title" class="gx-header__brand"><slot name="brand"><GxBrandBlock :locale="locale" /></slot></div>
    <div v-if="$slots.title" class="gx-header__title"><slot name="title" /></div>
    <span class="gx-header__spacer"></span>
    <div v-if="$slots.actions" class="gx-header__actions"><slot name="actions" /></div>
    <GxLanguageSelect v-if="language" :locale="locale" />
    <GxThemeToggle v-if="theme" :locale="locale" />
    <div v-if="$slots.user" class="gx-header__user"><slot name="user" /></div>
    <div v-if="$slots.switcher" class="gx-header__switcher"><slot name="switcher" /></div>
  </header>
</template>
