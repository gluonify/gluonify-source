<script setup>
import { computed } from 'vue'
import { DropdownMenuRoot, DropdownMenuTrigger, DropdownMenuPortal, DropdownMenuContent, DropdownMenuItem } from 'reka-ui'
import { LayoutGrid } from 'lucide-vue-next'
import { visibleApps } from './roles.js'
import { t, normalizeLocale } from './i18n/index.js'

const props = defineProps({
  catalog: { type: Array, default: () => [] },
  roles: { type: Array, default: () => [] },
  currentId: { type: String, default: '' },
  locale: { type: String, default: 'en' },
  dark: { type: Boolean, default: false }
})

const lang = computed(() => normalizeLocale(props.locale))
const apps = computed(() => visibleApps(props.catalog, props.roles))
const label = computed(() => t(lang.value, 'switcher.label'))
const roleText = (app) => (app.role && (app.role[lang.value] || app.role.en)) || ''
const color = (app) => (props.dark && app.colorLight ? app.colorLight : app.color)
// colorLight is meant for the dark theme; fall back on the CSS theme attribute when the host does not pass `dark`.
function go(app) { window.location.assign(app.url) }
</script>

<template>
  <DropdownMenuRoot v-if="apps.length">
    <DropdownMenuTrigger class="gx-switch-btn" :aria-label="label" :title="label">
      <LayoutGrid :size="18" aria-hidden="true" />
    </DropdownMenuTrigger>
    <DropdownMenuPortal>
      <DropdownMenuContent class="gx-switch-menu" align="end" :side-offset="6" :aria-label="t(lang, 'switcher.menu')">
        <DropdownMenuItem
          v-for="app in apps" :key="app.id" class="gx-switch-item" :data-app="app.id"
          :aria-current="app.id === currentId ? 'page' : undefined"
          @select="go(app)"
        >
          <span class="gx-switch-particle" :style="{ color: color(app) }" aria-hidden="true">{{ app.particle }}</span>
          <span class="gx-switch-text">
            <span class="gx-switch-name">{{ app.name }}</span>
            <span class="gx-switch-role">{{ roleText(app) }}</span>
          </span>
          <span v-if="app.id === currentId" class="sr-only">({{ t(lang, 'switcher.current') }})</span>
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenuPortal>
  </DropdownMenuRoot>
</template>

<style>
.gx-switch-btn { display: inline-flex; align-items: center; justify-content: center; min-width: 36px; min-height: 36px; border: 0; border-radius: var(--radius-sm, 5px); background: transparent; color: var(--text, inherit); cursor: pointer; }
.gx-switch-btn:hover { background: var(--surface-hover, #f1f5f9); }
.gx-switch-menu { z-index: 60; min-width: 14rem; background: var(--surface, #fff); color: var(--text, #0f172a); border: 1px solid var(--border, #d5deea); border-radius: var(--radius-sm, 5px); box-shadow: var(--shadow, 0 4px 14px rgb(0 0 0 / .14)); padding: .25rem; display: grid; grid-template-columns: max-content 1fr; }
/* two aligned columns (particle | name + role): every item spans both columns of the menu grid */
.gx-switch-item { display: grid; grid-template-columns: subgrid; grid-column: 1 / -1; align-items: center; column-gap: .65rem; padding: .45rem .65rem; border-radius: var(--radius-sm, 5px); cursor: pointer; outline: none; }
.gx-switch-item[data-highlighted] { background: var(--surface-hover, #f1f5f9); }
.gx-switch-item[aria-current='page'] { background: var(--primary-soft, #eef2ff); }
.gx-switch-particle { font-weight: 700; font-size: 1.1rem; text-align: start; }
.gx-switch-text { display: flex; flex-direction: column; line-height: 1.25; }
.gx-switch-name { font-weight: 700; }
.gx-switch-role { font-size: .85rem; color: var(--text-muted, #526173); }
</style>
