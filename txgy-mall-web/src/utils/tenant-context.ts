import { entryHint, requireEntry } from './tenant-entry.mjs'
/** Entry changes require a validated directory and a full reload, not an in-place setter. */
export interface MallTenant { tenantId: string | number; name: string; registrationEnabled: boolean }
export interface MallTenantConfig {
  enabled: boolean
  legacyTenantId: string | number
  publicTenantId: string | number | null
  tenants: MallTenant[]
  capabilities: { browseSwitch: boolean; registrationSelection: boolean; scopedEntry?: boolean }
}
export interface AccountContext { tenantId: string | number; userId: string | number }
export const TENANT_CONTEXT_KEY = 'mall-pc-tenant-context'
export class AccountContextError extends Error {}
function tenantId(value: unknown): string {
  const text = String(value ?? '')
  if (!/^[1-9]\d*$/.test(text)) throw new Error('商城租户编号无效')
  return text
}
export const legacyTenantId = tenantId(import.meta.env.VITE_TENANT_ID || '1')
const hint = entryHint(typeof sessionStorage === 'undefined' ? undefined : sessionStorage, legacyTenantId, typeof window === 'undefined' ? '' : window.location?.href)
export const entryTenantId = hint.tenantId
export const invalidEntryHint = hint.invalid
let epoch = 0
let account: { tenantId: string; userId: string } | null = null
let catalog: Readonly<MallTenantConfig> | null = null
let catalogStatus: 'pending' | 'ready' | 'legacy' | 'unavailable' = 'pending'
function notify() {
  // Stored hints are never read as authority; verify old tokens on every boot.
  if (typeof localStorage !== 'undefined') localStorage.setItem(TENANT_CONTEXT_KEY, JSON.stringify({
    browseTenantId: entryTenantId, accountTenantId: account?.tenantId ?? null,
  }))
  if (typeof window !== 'undefined') window.dispatchEvent(new Event('mall:tenant-context-updated'))
}
export function getTenantContext() {
  return Object.freeze({ browseTenantId: entryTenantId, accountTenantId: account?.tenantId ?? null,
    accountUserId: account?.userId ?? null, epoch, catalogStatus, catalog,
    canSwitchBrowse: false, canSelectRegistrationTenant: false })
}
export function getAccountRequestTenant() { return account?.tenantId ?? entryTenantId }
export function isCurrentTenantEpoch(expected: number) { return epoch === expected }
export function invalidateAccountContext() { epoch += 1; account = null; notify(); return epoch }
export function assertResponseTenant(value: unknown, expected = entryTenantId) {
  // An absent field is accepted only for the unchanged legacy backend contract.
  if (value === undefined && expected === legacyTenantId) return
  if (String(value) !== expected || expected !== entryTenantId) throw new AccountContextError('账号租户与当前商城不一致，请重新登录')
}
export function confirmAccountContext(value: AccountContext, expectedEpoch: number) {
  if (!isCurrentTenantEpoch(expectedEpoch)) return false
  assertResponseTenant(value.tenantId)
  const userId = String(value.userId ?? '')
  if (!/^[1-9]\d*$/.test(userId)) throw new AccountContextError('服务器未返回有效会员身份')
  account = { tenantId: tenantId(value.tenantId), userId }
  notify()
  return true
}
export function acceptTenantCatalog(value: MallTenantConfig) {
  if (invalidEntryHint) throw new Error('商城入口参数无效，请返回原商城')
  requireEntry(value, entryTenantId, legacyTenantId)
  if (typeof value?.enabled !== 'boolean' || !Array.isArray(value.tenants)
    || tenantId(value.legacyTenantId) !== legacyTenantId || !value.capabilities
    || typeof value.capabilities.browseSwitch !== 'boolean' || typeof value.capabilities.registrationSelection !== 'boolean') throw new Error('商城入口配置无效')
  const ids = new Set<string>()
  const tenants = value.tenants.map(item => {
    const id = tenantId(item.tenantId)
    if (!item.name?.trim() || typeof item.registrationEnabled !== 'boolean' || ids.has(id)) throw new Error('公开商城目录无效')
    ids.add(id)
    return Object.freeze({ tenantId: id, name: item.name, registrationEnabled: item.registrationEnabled })
  })
  catalog = Object.freeze({ enabled: value.enabled, legacyTenantId,
    publicTenantId: value.publicTenantId === null ? null : tenantId(value.publicTenantId),
    tenants: Object.freeze(tenants) as unknown as MallTenant[],
    capabilities: Object.freeze({ browseSwitch: false, registrationSelection: false, ...(value.capabilities.scopedEntry === true ? { scopedEntry: true } : {}) }) })
  catalogStatus = value.enabled ? 'ready' : 'legacy'
  notify()
}
export function tenantCatalogUnavailable(legacyBackend = false) {
  catalog = null; catalogStatus = legacyBackend ? 'legacy' : 'unavailable'; notify()
}
export function acceptsLegacyTenantLink(query: Record<string, unknown>) {
  return ['tenantId', 'tenant-id', 'tenant_id'].every(key => query[key] === undefined || query[key] === entryTenantId)
}
