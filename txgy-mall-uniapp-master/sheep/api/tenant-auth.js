import { tenantTransport, ensureTenantReady } from '@/sheep/helper/tenant-runtime';
import { tenantId } from '@/sheep/config';
import { createTenantAuthClient } from '@/sheep/helper/tenant-auth-client.mjs';

// No user-store interceptor; token candidates are confirmed before any storage writes.
export const targetAuth = createTenantAuthClient(async ({ path, method, headers, data, params }) => {
  await ensureTenantReady();
  const query = params ? '?' + new URLSearchParams(params).toString() : '';
  return tenantTransport(path + query, { method, header: headers, data });
}, tenantId);
