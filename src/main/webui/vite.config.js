import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// Quinoa lance « npm run dev » (port 5173) en mode dev et « npm run build » (dossier dist/) au package ; les appels /api vont à Quarkus (même origine grâce à Quinoa).
export default defineConfig({
  plugins: [vue()],
  base: './',
  build: { outDir: 'dist', emptyOutDir: true },
  test: { environment: 'jsdom', include: ['src/**/*.test.js'] }
})
