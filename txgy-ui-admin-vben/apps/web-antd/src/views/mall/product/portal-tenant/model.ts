/** 历史公共租户只保留兼容读取，不能重新开放到平台门户。 */
export const LEGACY_PUBLIC_TENANT_ID = 3000;

export const PORTAL_TENANT_SLUG_PATTERN =
  /^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$/;

export function normalizePortalTenantSlug(value: string) {
  return value.trim().toLowerCase();
}

export function canOpenPortalTenant(tenantId?: number) {
  return tenantId !== LEGACY_PUBLIC_TENANT_ID;
}
