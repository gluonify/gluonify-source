import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// Quinoa runs "npm run dev" (port 5173) in dev mode and "npm run build" (dist/ folder) at package time; /api calls go to Quarkus (same origin thanks to Quinoa).
export default defineConfig({
  plugins: [vue()],
  base: './',
  build: { outDir: 'dist', emptyOutDir: true },
  // the kit's .vue files live in node_modules (file:./vendor/gluonify-ui): Vitest must transform them (node 20 otherwise refuses the .vue extension)
  test: {
    coverage: { provider: 'v8', include: ['src/**/*.{js,ts,vue}'], exclude: ['src/**/*.test.{js,ts}', 'src/test-setup.{js,ts}'], thresholds: { lines: 100, branches: 100, functions: 100 } }, // ratchet (gluonify-plasma README, "Quality gates"): current level rounded down, never lowered
    environment: 'jsdom', include: ['src/**/*.test.js'], server: { deps: { inline: ['@gluonify/ui'] } } }
})
