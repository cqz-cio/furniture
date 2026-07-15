import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const envLocal = await readFile(new URL('../.env.local', import.meta.url), 'utf8')
const axiosConfig = await readFile(new URL('../src/config/axios/config.ts', import.meta.url), 'utf8')
const axiosService = await readFile(new URL('../src/config/axios/service.ts', import.meta.url), 'utf8')

assert.match(
  envLocal,
  /^VITE_AI_BASE_URL=['"]?http:\/\/localhost:48090['"]?$/m,
  'local development must point AI requests at the standalone AI service'
)
assert.match(
  axiosConfig,
  /ai_base_url:\s*import\.meta\.env\.VITE_AI_BASE_URL/,
  'axios config must expose the optional AI service base URL'
)
assert.match(
  axiosService,
  /config\.url\?\.startsWith\('\/ai\/'\)/,
  'axios must recognize AI module request paths'
)
assert.match(
  axiosService,
  /config\.baseURL\s*=\s*ai_base_url/,
  'AI module requests must override the default service base URL'
)

console.log('Local standalone AI routing contract passed.')
