// Gluonify ESLint configuration, version 1 (2026-10-19): the same file in every Vue interface (gluonify-plasma README, "Quality gates").
// `npm test` runs it before Vitest. Existing debt is in eslint-suppressions.json (ESLint bulk suppressions): it only shrinks, and ESLint fails
// when a suppression is no longer needed (`npx eslint . --prune-suppressions` then removes it). New code is checked at once.
import js from '@eslint/js'
import { defineConfig } from 'eslint/config'
import sonarjs from 'eslint-plugin-sonarjs'
import pluginVue from 'eslint-plugin-vue'
import globals from 'globals'
import tseslint from 'typescript-eslint'

export default defineConfig(
  { ignores: ['dist/**', 'coverage/**', 'node_modules/**', 'vendor/**', 'public/**', '**/*.d.ts'] },
  js.configs.recommended,
  tseslint.configs.strict,
  pluginVue.configs['flat/essential'],
  sonarjs.configs.recommended,
  {
    files: ['**/*.vue'],
    languageOptions: { parserOptions: { parser: tseslint.parser, extraFileExtensions: ['.vue'] } },
  },
  {
    languageOptions: { ecmaVersion: 'latest', sourceType: 'module', globals: { ...globals.browser, ...globals.node } },
  },
)
