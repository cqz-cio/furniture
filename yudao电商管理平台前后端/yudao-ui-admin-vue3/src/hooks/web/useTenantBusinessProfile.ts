import * as TenantApi from '@/api/system/tenant'

/**
 * 加载当前页面对应的有效租户业务配置。
 *
 * 每个商品顶层页面创建独立实例，不跨页面或跨租户缓存。
 */
export const useTenantBusinessProfile = () => {
  const profile = ref<TenantApi.TenantBusinessProfile>()
  const profileLoading = ref(true)
  const profileError = ref<unknown>()

  const profileLoaded = computed(() => profile.value !== undefined)
  const businessMode = computed(() => profile.value?.businessMode)
  const inventoryEnabled = computed(() => profile.value?.inventoryEnabled === true)
  const websiteProductFields = computed(() => profile.value?.websiteProductFields || [])
  const websiteProductFieldEnabled = (field: string) =>
    websiteProductFields.value.includes(field)

  const loadTenantBusinessProfile = async () => {
    profile.value = undefined
    profileError.value = undefined
    profileLoading.value = true
    try {
      profile.value = await TenantApi.getCurrentTenantBusinessProfile()
      return profile.value
    } catch (error) {
      profileError.value = error
      throw error
    } finally {
      profileLoading.value = false
    }
  }

  return {
    profile,
    profileLoading,
    profileLoaded,
    profileError,
    businessMode,
    inventoryEnabled,
    websiteProductFields,
    websiteProductFieldEnabled,
    loadTenantBusinessProfile
  }
}
