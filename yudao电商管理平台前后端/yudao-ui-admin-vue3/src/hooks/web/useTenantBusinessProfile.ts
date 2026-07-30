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
  const isB2B = computed(() => businessMode.value === 'B2B')
  const inventoryEnabled = computed(() => profile.value?.inventoryEnabled === true)
  const websiteProductFields = computed(() => profile.value?.websiteProductFields || [])
  const productFieldStates = computed(() => profile.value?.productFieldStates || {})
  const websiteProductFieldEnabled = (field: string) => websiteProductFields.value.includes(field)
  const productFieldState = (field: string): TenantApi.ProductFieldState =>
    productFieldStates.value[field] || 'INTERNAL'
  const productFieldIsWebsite = (field: string) => productFieldState(field) === 'WEBSITE'
  const productFieldIsInternal = (field: string) => productFieldState(field) === 'INTERNAL'
  const productFieldIsNotApplicable = (field: string) =>
    productFieldState(field) === 'NOT_APPLICABLE'

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
    isB2B,
    inventoryEnabled,
    websiteProductFields,
    productFieldStates,
    websiteProductFieldEnabled,
    productFieldState,
    productFieldIsWebsite,
    productFieldIsInternal,
    productFieldIsNotApplicable,
    loadTenantBusinessProfile
  }
}
