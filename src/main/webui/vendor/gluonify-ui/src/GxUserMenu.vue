<script setup>
import { nextTick, onBeforeUnmount, ref } from 'vue'
import GxIcon from './GxIcon.vue'
import { useGx } from './context.js'
import './components.css'

// The signed-in person's menu in the header (`user` slot of GxHeader): a button (user icon, name, chevron) that opens a small menu.
// Optional `items` come first, then « Account security » when `accountUrl` (Charm's `/account`, see `charmAccountUrl`) is set;
// « Sign out » is always the last entry. Charm's `/account` has no `return` parameter yet, so the page opens in a new tab. Link items have an `href`; the others emit `select` with their id.
const props = defineProps({
  name: { type: String, default: '' },
  items: { type: Array, default: () => [] },
  accountUrl: { type: String, default: '' },
  locale: { type: String, default: undefined },
})
const emit = defineEmits(['select', 'sign-out'])
const gx = useGx(props)
const open = ref(false)
const root = ref(null)
const button = ref(null)
const menu = ref(null)

const entries = () => [...(menu.value?.querySelectorAll('[role="menuitem"]') || [])]
function onOutside(e) { if (root.value && !root.value.contains(e.target)) close(false) }
function close(refocus = true) {
  open.value = false
  document.removeEventListener('mousedown', onOutside)
  if (refocus) button.value?.focus()
}
async function show() {
  open.value = true
  document.addEventListener('mousedown', onOutside)
  await nextTick()
  entries()[0]?.focus()
}
function toggle() { if (open.value) close(); else show() }
function move(step) {
  const list = entries()
  if (!list.length) return
  const i = list.indexOf(document.activeElement)
  list[(i + step + list.length) % list.length].focus()
}
function onKey(e) {
  if (e.key === 'Escape') { if (open.value) { e.preventDefault(); close() } }
  else if (e.key === 'ArrowDown') { e.preventDefault(); if (open.value) move(1); else show() }
  else if (e.key === 'ArrowUp') { e.preventDefault(); if (open.value) move(-1); else show() }
  else if (e.key === 'Tab' && open.value) close(false)
}
function pick(item) { close(false); emit('select', item.id) }
function signOut() { close(false); emit('sign-out') }
onBeforeUnmount(() => document.removeEventListener('mousedown', onOutside))
</script>

<template>
  <div ref="root" class="gx-usermenu" @keydown="onKey">
    <button ref="button" type="button" class="gx-usermenu__btn" aria-haspopup="menu" :aria-expanded="open" :aria-label="gx.t('header.user')" @click="toggle">
      <GxIcon name="user" :size="16" /><span class="gx-usermenu__name">{{ name }}</span><GxIcon name="chevronDown" :size="13" />
    </button>
    <div v-if="open" ref="menu" class="gx-usermenu__menu" role="menu" :aria-label="gx.t('header.user')">
      <template v-for="item in items" :key="item.id">
        <a v-if="item.href" class="gx-usermenu__item" role="menuitem" tabindex="-1" :href="item.href" @click="close(false)"><GxIcon v-if="item.icon" :name="item.icon" :size="15" /><span>{{ item.label }}</span></a>
        <button v-else class="gx-usermenu__item" role="menuitem" tabindex="-1" type="button" @click="pick(item)"><GxIcon v-if="item.icon" :name="item.icon" :size="15" /><span>{{ item.label }}</span></button>
      </template>
      <a v-if="accountUrl" class="gx-usermenu__item gx-usermenu__account" role="menuitem" tabindex="-1" :href="accountUrl" target="_blank" rel="noopener noreferrer"
         :aria-label="`${gx.t('header.accountSecurity')} (${gx.t('header.newTab')})`" @click="close(false)"><GxIcon name="userCog" :size="15" /><span>{{ gx.t('header.accountSecurity') }}</span></a>
      <button class="gx-usermenu__item" role="menuitem" tabindex="-1" type="button" @click="signOut"><GxIcon name="signOut" :size="15" /><span>{{ gx.t('header.signOut') }}</span></button>
    </div>
  </div>
</template>
