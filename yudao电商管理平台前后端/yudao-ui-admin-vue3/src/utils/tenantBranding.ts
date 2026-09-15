export const SYSTEM_BRAND_NAME = '全品轩'

/** 展示名称与登录使用的租户原名分离，兼容已有租户和记住我缓存。 */
export const getTenantDisplayName = (name?: string | null): string => {
  const tenantName = name?.trim() || ''
  return tenantName === '芋道源码' ? '超级管理员' : tenantName
}
