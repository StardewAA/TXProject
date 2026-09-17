// No framework, storage or HTTP dependencies: server identity and browser choice stay separate.
export const normalizeTenantId = value => {
  const text = String(value ?? '').trim();
  return /^[1-9]\d*$/.test(text) && Number.isSafeInteger(Number(text)) ? text : null;
};

export function tenantHints(href = '') {
  const result = [];
  const collect = (query, depth = 0) => {
    const params = new URLSearchParams(query);
    for (const key of ['tenantId', 'tenant-id', 'tenant_id']) result.push(...params.getAll(key));
    if (depth < 2) for (const page of params.getAll('page')) {
      const index = page.indexOf('?');
      if (index >= 0) collect(page.slice(index + 1), depth + 1);
    }
  };
  const [beforeHash, hash = ''] = String(href).split('#');
  if (beforeHash.includes('?')) collect(beforeHash.slice(beforeHash.indexOf('?') + 1));
  if (hash.includes('?')) collect(hash.slice(hash.indexOf('?') + 1));
  return result;
}

export function createTenantContext(legacyTenantId = '1', entryTenantId = legacyTenantId) {
  let legacy = normalizeTenantId(legacyTenantId) || '1';
  let browse = normalizeTenantId(entryTenantId) || legacy, account = null, userId = null, epoch = 0, sessionEpoch = 0, blocked = '';
  let config = { enabled: false, legacyTenantId: legacy, publicTenantId: null, tenants: [],
    capabilities: { browseSwitch: false, registrationSelection: false } };
  const state = () => ({ browseTenantId: browse, accountTenantId: account, userId, epoch, sessionEpoch, blocked, config });
  return {
    state,
    blockEntry(message) { if (blocked !== message) epoch++; blocked = message; },
    configure(value) {
      if (!value || normalizeTenantId(value.legacyTenantId) !== legacy) return false;
      config = { enabled: value.enabled === true, legacyTenantId: legacy,
        publicTenantId: normalizeTenantId(value.publicTenantId),
        tenants: Array.isArray(value.tenants) ? value.tenants.filter(t => normalizeTenantId(t.tenantId)) : [],
        // This first batch deliberately has no switching/registration-selection implementation.
        capabilities: { browseSwitch: false, registrationSelection: false } };
      return true;
    },
    validateEntry(hints = []) {
      blocked = hints.some(id => normalizeTenantId(id) !== browse)
        ? '该商城入口尚未开放，请从当前商城首页重新进入' : '';
      if (blocked) epoch++;
      return !blocked;
    },
    setAccount(tenantId, id = null) {
      const next = normalizeTenantId(tenantId);
      if (account !== next || userId !== id) { account = next; userId = id; epoch++; }
    },
    invalidateAccount() { account = null; userId = null; epoch++; sessionEpoch++; },
    rotateAccount(tenantId, id) {
      account = normalizeTenantId(tenantId) || account;
      if (id !== undefined && id !== null) userId = id;
      epoch++;
    },
    capture(token = '', refreshToken = '') {
      return Object.freeze({ ...state(), token, refreshToken });
    },
    current(snapshot, token = '') {
      return Boolean(snapshot) && snapshot.epoch === epoch && snapshot.browseTenantId === browse
        && snapshot.token === token && !blocked;
    },
    headers(snapshot, withToken = true) {
      if (blocked || snapshot.epoch !== epoch || snapshot.browseTenantId !== browse) throw new Error(blocked || '商城上下文已变化，请重试');
      if (withToken && snapshot.token && snapshot.accountTenantId && snapshot.accountTenantId !== browse) {
        throw new Error('当前账号不属于此商城，请使用此商城账号登录');
      }
      return { 'tenant-id': browse, ...(withToken && snapshot.token ? { Authorization: snapshot.token } : {}) };
    },
  };
}
