<script lang="ts" setup>
import { computed } from 'vue'
import { useAppStore } from '@/store/modules/app'
import { useDesign } from '@/hooks/web/useDesign'
import { useTenantBranding } from '@/hooks/web/useTenantBranding'

defineOptions({ name: 'Logo' })

const { getPrefixCls } = useDesign()

const prefixCls = getPrefixCls('logo')

const appStore = useAppStore()
const { tenantName } = useTenantBranding()

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
      <span
        :title="tenantName"
        class="min-w-0 truncate text-20px font-bold text-[var(--logo-title-text-color)]"
      >{{ tenantName }}</span>
    </router-link>
  </div>
</template>
