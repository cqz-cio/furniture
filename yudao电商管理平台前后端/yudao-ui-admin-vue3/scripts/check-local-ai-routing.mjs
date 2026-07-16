import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const envLocal = await readFile(new URL('../.env.local', import.meta.url), 'utf8')
const axiosConfig = await readFile(new URL('../src/config/axios/config.ts', import.meta.url), 'utf8')
const axiosService = await readFile(new URL('../src/config/axios/service.ts', import.meta.url), 'utf8')

assert.match(
  envLocal,
  /^VITE_BASE_URL=['"]?http:\/\/localhost:48080['"]?$/m,
  'local development must point requests at the unified backend'
)
assert.doesNotMatch(
  envLocal,
  /^VITE_AI_BASE_URL=/m,
  'local development must not configure a separate AI backend'
)
assert.doesNotMatch(
  axiosConfig,
  /ai_base_url|VITE_AI_BASE_URL/,
  'axios config must use the unified backend only'
)
assert.doesNotMatch(
  axiosService,
  /config\.url\?\.startsWith\('\/ai\/'\)/,
  'axios must not special-case AI request paths'
)
assert.doesNotMatch(
  axiosService,
  /config\.baseURL\s*=\s*ai_base_url|\bai_base_url\b/,
  'AI requests must not override the unified base URL'
)

console.log('Local unified AI routing contract passed.')
