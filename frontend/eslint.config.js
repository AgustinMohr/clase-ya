import js from '@eslint/js';
import globals from 'globals';
import reactHooks from 'eslint-plugin-react-hooks';
import tseslint from 'typescript-eslint';

/**
 * Flat ESLint config (QUAL-01): TypeScript recommended + React hooks rules.
 * Type-checking stays in `tsc --noEmit` (strict); ESLint covers hooks, dead code and unsafe
 * patterns that the compiler cannot see.
 */
export default tseslint.config(
  { ignores: ['dist', 'node_modules', 'playwright-report', 'test-results'] },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    files: ['**/*.{ts,tsx}'],
    languageOptions: {
      ecmaVersion: 2020,
      globals: { ...globals.browser, ...globals.es2020 },
    },
    plugins: { 'react-hooks': reactHooks },
    rules: {
      ...reactHooks.configs.recommended.rules,
      // The codebase uses effect-based data loading and refs for URL-state initialization.
      // These React Compiler rules (v7) flag that pattern; migrating to the compiler is a
      // separate change. rules-of-hooks and exhaustive-deps stay enabled.
      'react-hooks/set-state-in-effect': 'off',
      'react-hooks/refs': 'off',
      // TypeScript already resolves identifiers; `no-undef` produces false positives on types.
      'no-undef': 'off',
      'no-empty': ['error', { allowEmptyCatch: true }],
      '@typescript-eslint/no-unused-vars': [
        'error',
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_' },
      ],
    },
  },
  {
    files: ['public/**/*.js'],
    languageOptions: { ecmaVersion: 2020, globals: { ...globals.browser } },
    rules: {
      'no-empty': ['error', { allowEmptyCatch: true }],
      '@typescript-eslint/no-unused-vars': ['error', { caughtErrors: 'none' }],
    },
  },
  {
    files: ['tests/**/*.ts', 'playwright.config.ts', 'vite.config.ts', 'scripts/**/*.mjs'],
    languageOptions: { globals: { ...globals.node } },
  },
);
