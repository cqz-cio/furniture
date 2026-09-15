import { ref, watch } from 'vue'
import { getTenantSimpleList } from '@/api/login'
import {
  authContextVersion,
  getAccessToken,
  getTenantId,
  getVisitTenantId
} from '@/utils/auth'
import { getTenantDisplayName, SYSTEM_BRAND_NAME } from '@/utils/tenantBranding'

export const useTenantBranding = () => {
  const tenantName = ref(SYSTEM_BRAND_NAME)

  watch(
    authContextVersion,
    async (_, __, onCleanup) => {
      // 立即清除上一个账号/访问租户的名称，旧请求返回时也不能覆盖新租户。
      tenantName.value = SYSTEM_BRAND_NAME
      let cancelled = false
      onCleanup(() => { cancelled = true })
      if (!getAccessToken()) return

      const tenantId = getVisitTenantId() || getTenantId()
      if (tenantId === null || tenantId === undefined || tenantId === '') return
      try {
        const tenants = await getTenantSimpleList()
        if (cancelled) return
        const tenant = tenants.find((item) => String(item.id) === String(tenantId))
        tenantName.value = getTenantDisplayName(tenant?.name) || SYSTEM_BRAND_NAME
      } catch {
        // 名称加载失败不影响正常使用，也不沿用其他租户的品牌。
      }
    },
    { immediate: true, flush: 'sync' }
  )

  return { tenantName }
}
