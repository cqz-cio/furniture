<script lang="ts" setup>
import { computed } from 'vue'
import { useAppStore } from '@/store/modules/app'
import { useDesign } from '@/hooks/web/useDesign'
import oakvedWordmark from '@/assets/svgs/oakved-wordmark.svg'

defineOptions({ name: 'Logo' })

const { getPrefixCls } = useDesign()

const prefixCls = getPrefixCls('logo')

const appStore = useAppStore()

const layout = computed(() => appStore.getLayout)
</script>

<template>
  <div>
    <router-link
      :class="[
        prefixCls,
        layout !== 'classic' ? `${prefixCls}__Top` : '',
        'flex !h-[var(--logo-height)] items-center justify-center cursor-pointer px-12px relative decoration-none overflow-hidden'
      ]"
      to="/"
    >
      <img
        :src="oakvedWordmark"
        alt="Oakved"
        class="h-[calc(var(--logo-height)-18px)] w-auto max-w-[calc(100%-24px)] shrink-0 object-contain"
      />
    </router-link>
  </div>
</template>
