type TenantChangeGuard = () => boolean | Promise<boolean>
const guards = new Set<TenantChangeGuard>()
export const registerTenantChangeGuard = (guard: TenantChangeGuard) => {
  guards.add(guard)
  return () => guards.delete(guard)
}
export const canChangeTenant = async () => {
  for (const guard of guards) if (!(await guard())) return false
  return true
}
