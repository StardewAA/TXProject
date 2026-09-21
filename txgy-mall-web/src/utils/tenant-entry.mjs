// A tab's entry is only a navigation hint. The public server directory must authorize it.
export const ENTRY_KEY = 'mall-scoped-entry';
export function entryId(value) {
  if (!['string', 'number'].includes(typeof value)) throw new Error('商城编号无效');
  const text = String(value);
  if (!/^[1-9]\d*$/.test(text) || !Number.isSafeInteger(Number(text))) throw new Error('商城编号无效');
  return text;
}
export function entryHint(storage, legacy, href = '') {
  try {
    const ids = [];
    for (const part of String(href).split('#')) {
      const index = part.indexOf('?');
      if (index >= 0) {
        const query = new URLSearchParams(part.slice(index + 1));
        for (const key of ['tenantId', 'tenant-id', 'tenant_id']) ids.push(...query.getAll(key).map(entryId));
      }
    }
    if (ids.length) {
      if (ids.some(id => id !== ids[0])) throw new Error('入口参数冲突');
      storage?.setItem(ENTRY_KEY, ids[0]);
      return { tenantId: ids[0], invalid: false };
    }
    const value = storage?.getItem(ENTRY_KEY);
    return { tenantId: value == null ? entryId(legacy) : entryId(value), invalid: false };
  } catch { return { tenantId: entryId(legacy), invalid: true }; }
}
export function parseEntryDirectory(value, legacy) {
  if (!value || String(value.legacyTenantId) !== entryId(legacy) || typeof value.enabled !== 'boolean') throw new Error('商城目录暂不可用');
  if (!value.enabled || value.capabilities?.scopedEntry !== true) return { enabled: false, tenants: [] };
  if (!Array.isArray(value.tenants)) throw new Error('商城目录暂不可用');
  const seen = new Set();
  const tenants = value.tenants.map(item => {
    const tenantId = entryId(item?.tenantId);
    if (seen.has(tenantId) || typeof item.name !== 'string' || !item.name.trim()) throw new Error('商城目录无效');
    seen.add(tenantId); return Object.freeze({ tenantId, name: item.name.trim() });
  });
  return Object.freeze({ enabled: true, tenants: Object.freeze(tenants) });
}
export function requireEntry(value, target, legacy) {
  const id = entryId(target), original = entryId(legacy);
  const directory = parseEntryDirectory(value, original);
  if (id !== original && (!directory.enabled || !directory.tenants.some(item => item.tenantId === id))) {
    throw new Error('所选商城入口未开放或已停用，请返回原商城；不会切回原商城读取同编号数据');
  }
  return id;
}
export function scopedTokenKey(key, target, legacy) {
  const tenant = entryId(target);
  return tenant === entryId(legacy) ? key : key + ':tenant:' + tenant;
}
export function entryDestination(kind, purpose, tenant) {
  const id = entryId(tenant);
  if (!['pc', 'h5'].includes(kind) || !['home', 'register'].includes(purpose)) throw new Error('商城入口动作无效');
  if (purpose === 'home') return (kind === 'pc' ? '/' : '/pages/index/index') + '?tenantId=' + id;
  return kind === 'pc' ? '/register?tenantId=' + id + '&registrationTenant=' + id
    : '/pages/index/user?tenantId=' + id + '&mallRegistration=1&registrationTenant=' + id;
}
export function selectEntry(storage, directory, target, legacy, kind, purpose = 'home') {
  const tenant = requireEntry(directory, target, legacy);
  const path = entryDestination(kind, purpose, tenant);
  storage.setItem(ENTRY_KEY, tenant);
  return path; // Fixed local destinations only; tokens never enter URLs.
}
