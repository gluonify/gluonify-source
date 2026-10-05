import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// Quinoa runs "npm run dev" (port 5173) in dev mode and "npm run build" (dist/ folder) at package time; /api calls go to Quarkus (same origin thanks to Quinoa).
export default defineConfig({
  plugins: [vue()],
  base: './',
  build: { outDir: 'dist', emptyOutDir: true },
  test: { environment: 'jsdom', include: ['src/**/*.test.js'] }
})
