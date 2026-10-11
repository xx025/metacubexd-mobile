// Android's injected browser script uses plain JavaScript, independent of the
// Nuxt/TypeScript lint configuration and its TypeScript parser version.
export default [
  {
    files: ['**/*.js'],
    languageOptions: {
      globals: {
        window: 'readonly',
        document: 'readonly',
        console: 'readonly',
        getComputedStyle: 'readonly',
        requestAnimationFrame: 'readonly',
        MutationObserver: 'readonly',
      },
    },
    rules: {
      'no-undef': 'error',
      'no-unused-vars': 'error',
      'no-unreachable': 'error',
      'no-constant-condition': 'error',
    },
  },
]
