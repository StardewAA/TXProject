import { ApiError, request } from './http'
import { acceptTenantCatalog, tenantCatalogUnavailable, type AccountContext, type MallTenantConfig } from '@/utils/tenant-context'
export const tenantApi = {
  config: () => request<MallTenantConfig>({ url: '/member/mall-tenant/config', scope: 'entry', headers: { 'x-silent-error': true } }),
  session: () => request<AccountContext>({ url: '/member/mall-tenant/session', headers: { 'x-silent-error': true } }),
}
export async function loadTenantCatalog() {
  try { acceptTenantCatalog(await tenantApi.config()) }
  catch (error) { tenantCatalogUnavailable(error instanceof ApiError && (error.status === 404 || error.code === 404)) }
}
