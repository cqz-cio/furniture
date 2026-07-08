<template>
  <div class="relative flex h-full w-full flex-row justify-center">
    <div class="flex flex-col justify-center">
      <div class="text-center text-28px font-bold">Oakved AI</div>
      <div class="mt-20px flex w-460px flex-row flex-wrap items-center justify-center">
        <div
          v-for="prompt in promptList"
          :key="prompt.prompt"
          class="m-10px flex w-180px cursor-pointer justify-center rounded-10px border border-solid border-[#e4e4e4] leading-50px hover:bg-[rgba(243,243,243,0.73)]"
          @click="handlerPromptClick(prompt)"
        >
          {{ prompt.prompt }}
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
const promptList = [
  { prompt: '今天的重点运营事项有哪些？' },
  { prompt: '帮我写一段商品推广文案' }
]

const emits = defineEmits(['onPrompt'])

const handlerPromptClick = async ({ prompt }) => {
  emits('onPrompt', prompt)
}
</script>
