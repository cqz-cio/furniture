import { getTenantVisitList } from '@/api/system/tenant'
import { authContextVersion, clearVisitTenantId, getTenantId, getVisitTenantId } from '@/utils/auth'
import { deleteUserCache } from '@/hooks/web/useCache'

// 在加载菜单、字典和页面之前验证持久化的访问租户。旧缓存不能延续已收回的访问权。
export const validateTenantVisit = async () => {
  const visitId = getVisitTenantId()
  if (!visitId || String(visitId) === String(getTenantId())) return
  const contextVersion = authContextVersion.value
  const tenants = await getTenantVisitList()
  if (contextVersion !== authContextVersion.value) return
  if (!tenants.some((tenant) => String(tenant.id) === String(visitId))) {
    clearVisitTenantId()
    deleteUserCache()
  }
}
