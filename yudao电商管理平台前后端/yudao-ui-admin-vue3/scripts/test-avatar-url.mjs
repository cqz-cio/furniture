import assert from 'node:assert/strict'
import { resolveAvatarUrl } from '../src/utils/avatar.ts'

const file = '/admin-api/infra/file/4/get/20260916/avatar.png'
for (const host of ['127.0.0.1:48080', 'localhost:48080', '[::1]:48080']) {
  assert.equal(
    resolveAvatarUrl(`http://${host}${file}`, 'https://api.vanzhome.com'),
    `https://api.vanzhome.com${file}`
  )
  assert.equal(
    resolveAvatarUrl(`http://${host}${file}`, 'http://124.220.2.69'),
    `http://124.220.2.69${file}`
  )
}
assert.equal(
  resolveAvatarUrl(`http://127.0.0.1:48080${file}?v=2`, 'https://api.vanzhome.com'),
  `https://api.vanzhome.com${file}?v=2`
)
for (const unchanged of [
  '',
  '/assets/avatar.png',
  'data:image/png;base64,AAAA',
  'blob:https://example.com/id',
  'https://cdn.example.com/avatar.png',
  `https://api.vanzhome.com${file}`,
  'http://localhost:3000/avatar.png'
]) {
  assert.equal(resolveAvatarUrl(unchanged, 'https://api.vanzhome.com'), unchanged)
}
console.log('Avatar URL regression checks passed (production, test, legacy and external sources).')
