import { entryId } from './tenant-entry.mjs';

const aliases = ['tenantId', 'tenant-id', 'tenant_id'];
const base = 'https://mall.invalid';
function portalRoute(url) {
  if (url.hash.startsWith('#/')) {
    const hashUrl = new URL(url.hash.slice(1), base);
    return { pathname: hashUrl.pathname, query: hashUrl.searchParams };
  }
  return { pathname: url.pathname, query: url.searchParams };
}
function isPlatformRoute(route) {
  return route.pathname === '/' || route.pathname === '/pages/index/index'
    || ['/pages/platform/cart', '/pages/platform/checkout'].includes(route.pathname)
    || (['/pages/goods/list', '/pages/goods/index'].includes(route.pathname)
      && (route.query.get('portal') === '1' || route.query.has('tenantSlug')));
}
function isAccountRoute(route) {
  return route.pathname === '/pages/index/user'
    || /^\/pages\/(?:order|user|pay|chat)(?:\/|$)/.test(route.pathname)
    || route.pathname === '/pages/app/sign';
}
export function isPlatformPortalAddress(address) {
  try { return isPlatformRoute(portalRoute(new URL(address, base))); }
  catch { return false; }
}
export function isPlatformAccountAddress(address, legacyTenant) {
  try {
    const route = portalRoute(new URL(address, base));
    if (!isAccountRoute(route)) return false;
    const legacy = entryId(legacyTenant);
    const hints = aliases.flatMap(key => route.query.getAll(key));
    return hints.length === 0 || hints.every(value => entryId(value) === legacy);
  } catch { return false; }
}
export function platformNavigationTarget(path, address) {
  let platformShell = false;
  try {
    const route = portalRoute(new URL(address, base));
    platformShell = isPlatformRoute(route) || isAccountRoute(route);
  } catch { platformShell = false; }
  if (!platformShell) return path;
  if (path === '/pages/index/cart') return '/pages/platform/cart';
  if (path === '/pages/index/category' || path === '/pages/index/search') return '/pages/goods/list?portal=1';
  return path;
}
export function stripPlatformTenantHints(address) {
  const url = new URL(address, base);
  if (!isPlatformRoute(portalRoute(url))) return url.href;
  for (const key of aliases) url.searchParams.delete(key);
  if (url.hash.startsWith('#/')) {
    const hashUrl = new URL(url.hash.slice(1), base);
    for (const key of aliases) hashUrl.searchParams.delete(key);
    url.hash = '#' + hashUrl.pathname + hashUrl.search + hashUrl.hash;
  }
  return url.href;
}
export function platformPublicPath(path) {
  if (typeof path !== 'string' || !path.startsWith('/') || path.startsWith('//')
      || /[\\\\\u0000-\u0020\u007f]/.test(path)) throw new Error('商城链接无效');
  const url = new URL(path, base);
  if (url.origin !== base || !isPlatformRoute(portalRoute(url))) throw new Error('平台公开链接无效');
  for (const key of aliases) url.searchParams.delete(key);
  if (url.hash.startsWith('#/')) {
    const hashUrl = new URL(url.hash.slice(1), base);
    for (const key of aliases) hashUrl.searchParams.delete(key);
    url.hash = '#' + hashUrl.pathname + hashUrl.search + hashUrl.hash;
  }
  return url.pathname + url.search + url.hash;
}
function validateQuery(query, tenant) {
  for (const key of aliases) for (const value of query.getAll(key)) {
    if (entryId(value) !== tenant) throw new Error('链接属于其他商城，请先通过商城入口切换');
  }
}
export function scopeLocalPath(path, tenant) {
  if (typeof path !== 'string' || !path.startsWith('/') || path.startsWith('//')
      || /[\\\\\u0000-\u0020\u007f]/.test(path)) throw new Error('商城链接无效');
  const url = new URL(path, base);
  if (url.origin !== base) throw new Error('商城链接无效');
  if (isPlatformRoute(portalRoute(url))) return platformPublicPath(path);
  const id = entryId(tenant);
  validateQuery(url.searchParams, id);
  // Hash routes can also carry a tenant. Never hide a contradictory hint there.
  const hashQuery = url.hash.indexOf('?');
  if (hashQuery >= 0) validateQuery(new URLSearchParams(url.hash.slice(hashQuery + 1)), id);
  for (const key of aliases) url.searchParams.delete(key);
  url.searchParams.set('tenantId', id);
  return url.pathname + url.search + url.hash;
}
export function safeReturnPath(path, tenant, fallback = '/member') {
  try { return scopeLocalPath(path, tenant); } catch { return scopeLocalPath(fallback, tenant); }
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
