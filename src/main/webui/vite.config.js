import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// Quinoa runs "npm run dev" (port 5173) in dev mode and "npm run build" (dist/ folder) at package time; /api calls go to Quarkus (same origin thanks to Quinoa).
export default defineConfig({
  plugins: [vue()],
  base: './',
  build: { outDir: 'dist', emptyOutDir: true },
  // the kit's .vue files live in node_modules (file:./vendor/gluonify-ui): Vitest must transform them (node 20 otherwise refuses the .vue extension)
  test: { environment: 'jsdom', include: ['src/**/*.test.js'], server: { deps: { inline: ['@gluonify/ui'] } } }
})
