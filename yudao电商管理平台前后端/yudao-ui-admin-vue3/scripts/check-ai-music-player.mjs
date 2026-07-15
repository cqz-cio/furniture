import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const player = await readFile(
  new URL('../src/views/ai/music/index/list/audioBar/index.vue', import.meta.url),
  'utf8'
)

assert.doesNotMatch(player, /v-bind="audioProps"/)
assert.doesNotMatch(player, /formatPast|timeStamp/)
assert.doesNotMatch(player, /https?:\/\//)
assert.match(player, /ref<HTMLAudioElement \| null>/)
assert.match(player, /const currentTime = ref\(0\)/)
assert.match(player, /const duration = ref\(0\)/)
assert.match(player, /const volumePercent = ref\(50\)/)
assert.match(player, /Math\.min\(1, Math\.max\(0, value \/ 100\)\)/)
assert.match(player, /@loadedmetadata="syncDuration"/)
assert.match(player, /@timeupdate="syncCurrentTime"/)
assert.match(player, /@error="useFallbackCover"/)
assert.match(player, /@\/assets\/imgs\/logo\.png/)

console.log('AI music player contract passed.')
