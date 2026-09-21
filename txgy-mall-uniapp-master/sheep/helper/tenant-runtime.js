import { baseUrl, apiPath, tenantId } from '@/sheep/config';
import { createTenantContext, tenantHints } from './tenant-context.mjs';
import { createTenantAuthClient, SessionRestoreError } from './tenant-auth-client.mjs';
import { entryHint, requireEntry, scopedTokenKey } from './tenant-entry.mjs';

const entry = entryHint(typeof sessionStorage === 'undefined' ? undefined : sessionStorage, tenantId, typeof window === 'undefined' ? '' : window.location?.href);
export const tenantContext = createTenantContext(tenantId, entry.tenantId);
export const tenantTokenKey = key => scopedTokenKey(key, entry.tenantId, tenantId);
export const readTenantToken = key => uni.getStorageSync(tenantTokenKey(key)) || '';
export const writeTenantToken = (key, value) => value ? uni.setStorageSync(tenantTokenKey(key), value) : uni.removeStorageSync(tenantTokenKey(key));
let ready;
let sessionCheck;
const accessToken = () => readTenantToken('token');
let observedToken;
let bootstrapStatus = { status: 'loading', confirmed: false };
export const getTenantBootstrapStatus = () => ({ ...bootstrapStatus });
const href = () => typeof window === 'undefined' ? '' : window.location.href;
export function stampTenantUrl() {
  if (typeof window === 'undefined' || !window.history?.replaceState || entry.invalid) return;
  if (tenantHints(href()).some(id => id !== entry.tenantId)) return;
  const url = new URL(href()); url.searchParams.set('tenantId', entry.tenantId);
  if (url.href !== href()) window.history.replaceState(window.history.state, '', url.href);
}

// Bootstrap never calls the business client (which itself waits for bootstrap).
export function tenantTransport(path, { method = 'GET', header = {}, data } = {}) {
  return new Promise((resolve, reject) => uni.request({
    url: baseUrl + apiPath + path, method, header, data, timeout: 8000,
    success: response => {
      if (response.statusCode && (response.statusCode < 200 || response.statusCode >= 300)) {
        reject(Object.assign(new Error('商城服务暂不可用'), { statusCode: response.statusCode }));
      } else resolve(response.data);
    }, fail: reject,
  }));
}

export function ensureTenantReady() {
  if (entry.invalid) { tenantContext.blockEntry('商城入口参数无效，请返回原商城'); return Promise.reject(new Error(tenantContext.state().blocked)); }
  const foreign = entry.tenantId !== String(tenantId);
  if (!ready) ready = (async () => {
    try {
      const response = await tenantTransport('/member/mall-tenant/config');
      if (foreign) {
        if (response?.code !== 0) throw new Error('商城入口尚未开放或暂无法核验');
        requireEntry(response.data, entry.tenantId, tenantId);
      }
      if (response?.code === 0 && tenantContext.configure(response.data)) bootstrapStatus = { status: 'ready', confirmed: true };
      else bootstrapStatus = { status: response?.code === 404 ? 'legacy-server' : 'unavailable', confirmed: false };
    } catch (error) {
      bootstrapStatus = { status: error?.statusCode === 404 ? 'legacy-server' : 'unavailable', confirmed: false };
      if (foreign) {
        tenantContext.blockEntry('商城入口尚未开放或暂无法核验，请返回原商城；不会读取原商城同编号数据');
        throw new Error(tenantContext.state().blocked);
      }
    }
    tenantContext.validateEntry(tenantHints(href()));
  })();
  const pending = ready;
  return pending.then(() => {
    // Recheck URL changes and another tab's navigation before each business request.
    tenantContext.validateEntry(tenantHints(href()));
    if (tenantContext.state().blocked) throw new Error(tenantContext.state().blocked);
    stampTenantUrl();
  }).finally(() => { if (foreign && ready === pending) ready = undefined; });
}

export const captureTenantRequest = () => tenantContext.capture(accessToken(), readTenantToken('refresh-token'));
export const isTenantRequestCurrent = snapshot => tenantContext.current(snapshot, accessToken());
export const getControlledTenantId = () => tenantContext.state().browseTenantId;
export const getAccountTenantId = () => tenantContext.state().accountTenantId;

// Capture before any await: verification may update epoch, but cannot replace the initiating session.
export function captureTenantOperation() {
  const token = accessToken();
  if (observedToken !== token) {
    const replaced = observedToken !== undefined;
    recordTenantSession(token);
    if (replaced) uni.$emit?.('mall:session-changed');
  }
  return { token, sessionEpoch: tenantContext.state().sessionEpoch };
}
export const isTenantOperationCurrent = operation => accessToken() === observedToken
  && operation.sessionEpoch === tenantContext.state().sessionEpoch;

export function recordTenantSession(token, identity, refreshing = false) {
  observedToken = token || '';
  sessionCheck = undefined;
  if (refreshing) tenantContext.rotateAccount(identity?.tenantId, identity?.userId);
  else {
    tenantContext.invalidateAccount();
    if (token && identity?.tenantId) tenantContext.setAccount(identity.tenantId, identity.userId);
  }
}

// H5 storage events never authorize a session: discard old view data and verify on next request.
if (typeof window !== 'undefined' && window.addEventListener) window.addEventListener('storage', () => {
  if (observedToken !== accessToken()) {
    recordTenantSession(accessToken());
    uni.$emit?.('mall:session-changed');
  }
});

export async function ensureAccountSession() {
  const operation = captureTenantOperation();
  await ensureTenantReady();
  if (!isTenantOperationCurrent(operation)) throw new Error('登录状态已变化');
  const token = accessToken();
  if (!token || tenantContext.state().accountTenantId) return;
  const snapshot = captureTenantRequest();
  if (!sessionCheck || sessionCheck.token !== token || sessionCheck.epoch !== snapshot.epoch) {
    const promise = (async () => {
      const verifier = createTenantAuthClient(({ path, method, headers, data, params }) => {
        const query = params ? '?' + new URLSearchParams(params).toString() : '';
        return tenantTransport(path + query, { method, header: headers, data });
      }, tenantId);
      try {
        const identity = await verifier.restoreSession({ tenantId: snapshot.browseTenantId,
          accessToken: snapshot.token, refreshToken: snapshot.refreshToken }, () => isTenantRequestCurrent(snapshot));
        if (!isTenantRequestCurrent(snapshot)) throw new Error('登录状态已变化');
        if (identity.accessToken !== snapshot.token || identity.refreshToken !== snapshot.refreshToken) {
          writeTenantToken('token', identity.accessToken);
          writeTenantToken('refresh-token', identity.refreshToken);
          observedToken = identity.accessToken;
        }
        tenantContext.setAccount(identity.tenantId, identity.userId);
        uni.$emit?.('mall:session-status', '');
      } catch (error) {
        const current = isTenantRequestCurrent(snapshot);
        if (current && error instanceof SessionRestoreError && error.kind === 'invalid') {
          writeTenantToken('token', '');
          writeTenantToken('refresh-token', '');
          recordTenantSession('');
          uni.$emit?.('mall:session-changed');
        }
        if (current) uni.$emit?.('mall:session-status', error.message || '登录状态暂无法核验，请重试');
        throw error;
      }
    })();
    sessionCheck = { token, epoch: snapshot.epoch, promise };
  }
  const pending = sessionCheck;
  try { await pending.promise; }
  finally { if (sessionCheck === pending) sessionCheck = undefined; }
}

export async function prepareTenantUpload() {
  const operation = captureTenantOperation();
  await ensureAccountSession();
  if (!isTenantOperationCurrent(operation)) throw new Error('登录状态已变化');
  const snapshot = captureTenantRequest();
  if (!snapshot.token) throw new Error('请先登录');
  return { snapshot, header: tenantContext.headers(snapshot) };
}

export function assertAccountProfile(profile) {
  const state = tenantContext.state();
  if (state.accountTenantId && String(profile?.id) === String(state.userId)
      && (profile.tenantId === undefined || String(profile.tenantId) === state.accountTenantId)) return;
  writeTenantToken('token', '');
  writeTenantToken('refresh-token', '');
  recordTenantSession('');
  uni.$emit?.('mall:session-changed');
  uni.$emit?.('mall:session-status', '会员资料与当前会话不一致，请重新登录');
  throw new Error('会员资料与当前会话不一致，请重新登录');
}
