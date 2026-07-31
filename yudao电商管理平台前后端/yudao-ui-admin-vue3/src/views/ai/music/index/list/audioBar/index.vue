<template>
  <div
    class="flex items-center justify-between px-2 h-72px bg-[var(--el-bg-color-overlay)] b-solid b-1 b-[var(--el-border-color)] b-l-none"
  >
    <div class="flex gap-[10px]">
      <el-image :src="coverUrl" class="w-[45px] h-[45px]" fit="cover" @error="useFallbackCover" />
      <div>
        <div>{{ currentSong.title || currentSong.name || '示例音乐' }}</div>
        <div class="text-[12px] text-gray-400">{{ currentSong.singer || 'AI 音乐' }}</div>
      </div>
    </div>

    <div class="flex gap-[12px] items-center">
      <Icon icon="majesticons:back-circle" :size="20" class="text-gray-300" />
      <Icon
        :icon="isPaused ? 'mdi:arrow-right-drop-circle' : 'solar:pause-circle-bold'"
        :size="30"
        class="cursor-pointer"
        @click="togglePlayback"
      />
      <Icon icon="majesticons:next-circle" :size="20" class="text-gray-300" />
      <div class="flex gap-[16px] items-center">
        <span>{{ formatAudioTime(currentTime) }}</span>
        <el-slider
          v-model="currentTime"
          :max="duration"
          :disabled="duration <= 0"
          class="w-[160px!important]"
          @change="seek"
        />
        <span>{{ formatAudioTime(duration) }}</span>
      </div>
      <audio
        ref="audioRef"
        :src="currentAudioUrl"
        :muted="muted"
        class="hidden"
        preload="metadata"
        @loadedmetadata="syncDuration"
        @durationchange="syncDuration"
        @timeupdate="syncCurrentTime"
        @play="isPaused = false"
        @pause="isPaused = true"
        @ended="handleEnded"
      ></audio>
    </div>

    <div class="flex gap-[16px] items-center">
      <Icon
        :icon="muted ? 'tabler:volume-off' : 'tabler:volume'"
        :size="20"
        class="cursor-pointer"
        @click="toggleMuted"
      />
      <el-slider
        v-model="volumePercent"
        :min="0"
        :max="100"
        class="w-[160px!important]"
        @input="updateVolume"
      />
    </div>
  </div>
</template>

<script lang="ts" setup>
import audioUrl from '@/assets/audio/response.mp3'
import fallbackCover from '@/assets/imgs/logo.png'

defineOptions({ name: 'AiMusicAudioBar' })

const currentSong = inject<Ref<Recordable>>('currentSong', ref({}))
const audioRef = ref<HTMLAudioElement | null>(null)
const currentTime = ref(0)
const duration = ref(0)
const volumePercent = ref(50)
const isPaused = ref(true)
const muted = ref(false)
const coverUrl = ref(fallbackCover)
const currentAudioUrl = computed(() => currentSong.value.audioUrl || audioUrl)

watch(
  () => currentSong.value.imageUrl,
  (value) => {
    coverUrl.value = value || fallbackCover
  },
  { immediate: true }
)

watch(currentAudioUrl, () => {
  currentTime.value = 0
  duration.value = 0
  isPaused.value = true
})

const useFallbackCover = () => {
  coverUrl.value = fallbackCover
}

const togglePlayback = async () => {
  if (!audioRef.value) return
  if (audioRef.value.paused) {
    await audioRef.value.play()
  } else {
    audioRef.value.pause()
  }
}

const toggleMuted = () => {
  muted.value = !muted.value
  if (audioRef.value) audioRef.value.muted = muted.value
}

const syncDuration = () => {
  const value = audioRef.value?.duration ?? 0
  duration.value = Number.isFinite(value) ? value : 0
}

const syncCurrentTime = () => {
  currentTime.value = audioRef.value?.currentTime ?? 0
}

const seek = (value: number) => {
  if (audioRef.value) audioRef.value.currentTime = value
}

const updateVolume = (value: number) => {
  if (audioRef.value) {
    audioRef.value.volume = Math.min(1, Math.max(0, value / 100))
  }
}

const handleEnded = () => {
  isPaused.value = true
  currentTime.value = 0
}

const formatAudioTime = (seconds: number) => {
  if (!Number.isFinite(seconds) || seconds < 0) return '00:00'
  const minutes = Math.floor(seconds / 60)
  const remainingSeconds = Math.floor(seconds % 60)
  return `${minutes.toString().padStart(2, '0')}:${remainingSeconds.toString().padStart(2, '0')}`
}
</script>
