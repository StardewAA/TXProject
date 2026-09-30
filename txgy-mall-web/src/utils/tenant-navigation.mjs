import { entryId } from './tenant-entry.mjs';

const aliases = ['tenantId', 'tenant-id', 'tenant_id'];
const base = 'https://mall.invalid';
export function isPlatformPath(path) {
  if (typeof path !== 'string') return false;
  try {
    const url = new URL(path, base);
    return url.origin === base && (url.pathname === '/' || /^\/(?:tenants|products|companies|portal|markets|cart|checkout|platform-login|member|purchase|enterprise|sales|pay|download)(?:\/|$)/.test(url.pathname));
  } catch { return false; }
}
export function scopePlatformPath(path) {
  if (typeof path !== 'string' || !path.startsWith('/') || path.startsWith('//')
      || /[\\\u0000-\u0020\u007f]/.test(path)) throw new Error('平台链接无效');
  const url = new URL(path, base);
  if (url.origin !== base || !isPlatformPath(url.pathname)) throw new Error('平台链接无效');
  for (const key of aliases) url.searchParams.delete(key);
  return url.pathname + url.search + url.hash;
}
function validateQuery(query, tenant) {
  for (const key of aliases) for (const value of query.getAll(key)) {
    if (entryId(value) !== tenant) throw new Error('链接属于其他商城，请先通过商城入口切换');
  }
}
export function scopeLocalPath(path, tenant) {
  const id = entryId(tenant);
  if (typeof path !== 'string' || !path.startsWith('/') || path.startsWith('//')
      || /[\\\\\u0000-\u0020\u007f]/.test(path)) throw new Error('商城链接无效');
  const url = new URL(path, base);
  if (url.origin !== base) throw new Error('商城链接无效');
  validateQuery(url.searchParams, id);
  // Hash routes can also carry a tenant. Never hide a contradictory hint there.
  const hashQuery = url.hash.indexOf('?');
  if (hashQuery >= 0) validateQuery(new URLSearchParams(url.hash.slice(hashQuery + 1)), id);
  for (const key of aliases) url.searchParams.delete(key);
  url.searchParams.set('tenantId', id);
  return url.pathname + url.search + url.hash;
}
export function safeReturnPath(path, tenant, fallback = '/member') {
  try { return isPlatformPath(path) ? scopePlatformPath(path) : scopeLocalPath(path, tenant); }
  catch { return isPlatformPath(fallback) ? scopePlatformPath(fallback) : scopeLocalPath(fallback, tenant); }
}
// Public share URLs are rebuilt from public routing data, never from the current
// page's token, login code, order ID, address or arbitrary redirect parameters.
export function tenantShareLink(address, spm, tenant, fallbackOrigin) {
  const id = entryId(tenant);
  if (typeof spm !== 'string' || !/^\d+\.\d+\.\d+\.\d+\.\d+$/.test(spm)) throw new Error('分享参数无效');
  let url;
  try {
    url = new URL(address || '/pages/index/index', fallbackOrigin);
    if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password) throw new Error('分享地址无效');
    validateQuery(url.searchParams, id);
    if (url.hash.includes('?')) validateQuery(new URLSearchParams(url.hash.slice(url.hash.indexOf('?') + 1)), id);
  } catch {
    url = new URL('/pages/index/index', fallbackOrigin);
  }
  const query = new URLSearchParams({ spm, tenantId: id });
  const hashPath = url.hash.split('?')[0];
  url.search = '';
  url.hash = '';
  if (hashPath.startsWith('#/')) url.hash = hashPath + '?' + query;
  else url.search = query.toString();
  return url.href;
}
