import { createApp } from 'vue'
import App from './App.vue'
import { initLanguage } from './i18n/index.js'
import './style.css'

initLanguage()
createApp(App).mount('#app')
