import { baseUrl, apiPath, tenantId } from '@/sheep/config';
import { createTenantContext, tenantHints } from './tenant-context.mjs';
import { createTenantAuthClient, SessionRestoreError } from './tenant-auth-client.mjs';
import { entryHint, requireEntry, scopedTokenKey } from './tenant-entry.mjs';
import { isPlatformPortalAddress, stripPlatformTenantHints } from './tenant-navigation.mjs';

const platformId = value => {
  const text = String(value ?? '').trim();
  return /^[1-9]\d*$/.test(text) && Number.isSafeInteger(Number(text)) ? text : '';
};
const initialHref = typeof window === 'undefined' ? '' : window.location?.href || '';
const platformEntry = isPlatformPortalAddress(initialHref);
// Platform routes are addressed by stable slugs (or the global portal) and must
// never inherit an old tenant selector from sessionStorage or a stale query.
const entry = entryHint(platformEntry || typeof sessionStorage === 'undefined' ? undefined : sessionStorage,
  tenantId, platformEntry ? stripPlatformTenantHints(initialHref) : initialHref);
export const tenantContext = createTenantContext(tenantId, entry.tenantId);
// A proved platform login always owns the legacy/platform credential slot. The
// current tenant URL remains browse-only and must not select another token slot.
const platformCredentialHint = platformId(uni.getStorageSync('platform-source-tenant'))
  && platformId(uni.getStorageSync('platform-member-id')) && uni.getStorageSync('token');
const credentialEntryTenant = platformEntry || platformCredentialHint ? tenantId : entry.tenantId;
export const tenantTokenKey = key => scopedTokenKey(key, credentialEntryTenant, tenantId);
export const readTenantToken = key => uni.getStorageSync(tenantTokenKey(key)) || '';
export const writeTenantToken = (key, value) => value ? uni.setStorageSync(tenantTokenKey(key), value) : uni.removeStorageSync(tenantTokenKey(key));
export const readPlatformCarrier = () => platformId(readTenantToken('platform-source-tenant'));
export const readPlatformMemberId = () => platformId(readTenantToken('platform-member-id'));
const writePlatformBinding = (carrier = '', memberId = '') => {
  const source = platformId(carrier), member = platformId(memberId);
  writeTenantToken('platform-source-tenant', source);
  writeTenantToken('platform-member-id', source && member ? member : '');
};
let ready;
let sessionCheck;
const accessToken = () => readTenantToken('token');
let observedToken;
let bootstrapStatus = { status: 'loading', confirmed: false };
export const getTenantBootstrapStatus = () => ({ ...bootstrapStatus });
const href = () => typeof window === 'undefined' ? '' : window.location.href;
export function stampTenantUrl() {
  if (typeof window === 'undefined' || !window.history?.replaceState || entry.invalid) return;
  if (isPlatformPortalAddress(href())) {
    const clean = stripPlatformTenantHints(href());
    if (clean !== href()) window.history.replaceState(window.history.state, '', clean);
    return;
  }
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

export const captureTenantRequest = () => Object.freeze({
  ...tenantContext.capture(accessToken(), readTenantToken('refresh-token')),
  platformCarrierTenantId: readPlatformCarrier(),
  platformMemberId: readPlatformMemberId(),
});
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
  const previousObserved = observedToken;
  observedToken = token || '';
  sessionCheck = undefined;
  if (refreshing) tenantContext.rotateAccount(identity?.tenantId, identity?.userId);
  else {
    tenantContext.invalidateAccount();
    if (token && identity?.tenantId) tenantContext.setAccount(identity.tenantId, identity.userId);
  }
  // A normal tenant login/logout or a runtime token replacement cannot inherit an
  // older platform carrier. Initial startup keeps the persisted hint until the
  // signed platform session endpoint proves it below.
  if (!refreshing && (!token || identity || previousObserved !== undefined)) {
    writePlatformBinding();
  }
}

export function recordPlatformSession(token, identity, refreshing = false) {
  const carrier = platformId(identity?.sourceTenantId ?? identity?.tenantId);
  const memberId = platformId(identity?.canonicalMemberId ?? identity?.userId);
  if (!token || !carrier || !memberId) throw new Error('平台登录身份无效');
  recordTenantSession(token, { tenantId: carrier, userId: memberId }, refreshing);
  writePlatformBinding(carrier, memberId);
}

export class PlatformSessionError extends Error {
  constructor(kind, message) { super(message); this.kind = kind; }
}

const platformToken = value => typeof value === 'string' && value.length > 0
  && value.length <= 4096 && !/\s/.test(value.replace(/^Bearer /, ''));
const platformError = (kind = 'invalid') => new PlatformSessionError(kind,
  kind === 'unavailable' ? '平台登录状态暂时无法核验，请稍后重试' : '平台登录已失效，请重新登录');

export async function verifyPlatformSession(identity, current = () => true) {
  const carrier = platformId(identity?.tenantId ?? identity?.sourceTenantId);
  const memberId = platformId(identity?.userId ?? identity?.canonicalMemberId);
  const access = String(identity?.accessToken || '').replace(/^Bearer /, '');
  const refresh = identity?.refreshToken || '';
  if (!carrier || !memberId || !platformToken(access) || (refresh && !platformToken(refresh))) {
    throw platformError();
  }
  if (!current()) throw new PlatformSessionError('stale', '登录状态已变化');
  let response;
  try {
    response = await tenantTransport('/member/platform-account/session', {
      header: { Authorization: 'Bearer ' + access, terminal: '20', Accept: 'application/json' },
    });
  } catch (error) {
    if (!current()) throw new PlatformSessionError('stale', '登录状态已变化');
    if ([401, 403].includes(Number(error?.statusCode))) throw platformError();
    throw platformError('unavailable');
  }
  if (!current()) throw new PlatformSessionError('stale', '登录状态已变化');
  const proof = response?.code === 0 ? response.data : null;
  if (!proof || platformId(proof.sourceTenantId) !== carrier
      || platformId(proof.canonicalMemberId) !== memberId
      || platformId(proof.legacyMemberId) !== memberId
      || !platformId(proof.platformAccountId)
      || typeof proof.platformNo !== 'string' || !proof.platformNo.trim()) {
    throw platformError();
  }
  return Object.freeze({
    accessToken: access,
    refreshToken: refresh,
    tenantId: carrier,
    userId: memberId,
    sourceTenantId: carrier,
    canonicalMemberId: memberId,
    legacyMemberId: memberId,
    platformAccountId: platformId(proof.platformAccountId),
    platformNo: proof.platformNo.trim(),
    memberships: Object.freeze(Array.isArray(proof.memberships) ? proof.memberships.slice() : []),
  });
}

// H5 storage events never authorize a session: discard old view data and verify on next request.
if (typeof window !== 'undefined' && window.addEventListener) window.addEventListener('storage', () => {
  if (observedToken !== accessToken()) {
    recordTenantSession(accessToken());
    uni.$emit?.('mall:session-changed');
  }
});

async function restorePlatformSession(snapshot) {
  const current = () => isTenantRequestCurrent(snapshot);
  const carrier = platformId(snapshot.platformCarrierTenantId);
  const memberId = platformId(snapshot.platformMemberId);
  if (!carrier || !memberId) throw platformError();
  const stored = { tenantId: carrier, userId: memberId,
    accessToken: snapshot.token, refreshToken: snapshot.refreshToken };
  try {
    return await verifyPlatformSession(stored, current);
  } catch (error) {
    if (!(error instanceof PlatformSessionError) || error.kind !== 'invalid'
        || !platformToken(snapshot.refreshToken) || !current()) throw error;
  }
  let response;
  try {
    response = await tenantTransport('/member/auth/refresh-token?refreshToken=' + encodeURIComponent(snapshot.refreshToken), {
      method: 'POST',
      header: { 'tenant-id': carrier, terminal: '20', Accept: 'application/json' },
    });
  } catch (error) {
    if (!current()) throw new PlatformSessionError('stale', '登录状态已变化');
    if ([401, 403].includes(Number(error?.statusCode))) throw platformError();
    throw platformError('unavailable');
  }
  if (!current()) throw new PlatformSessionError('stale', '登录状态已变化');
  const renewed = response?.code === 0 ? response.data : null;
  if (!renewed || platformId(renewed.tenantId) !== carrier
      || platformId(renewed.userId) !== memberId
      || !platformToken(renewed.accessToken) || !platformToken(renewed.refreshToken)) {
    throw platformError();
  }
  return verifyPlatformSession(renewed, current);
}

export async function ensureAccountSession() {
  const operation = captureTenantOperation();
  const platformRoute = isPlatformPortalAddress(href());
  if (!platformRoute) await ensureTenantReady();
  if (!isTenantOperationCurrent(operation)) throw new Error('登录状态已变化');
  const token = accessToken();
  if (!token) return;
  const platformMode = platformRoute || Boolean(readPlatformCarrier() && readPlatformMemberId());
  const known = tenantContext.state();
  if (known.accountTenantId && (!platformMode
      || (known.accountTenantId === readPlatformCarrier()
        && String(known.userId) === readPlatformMemberId()))) return;
  const snapshot = captureTenantRequest();
  if (!sessionCheck || sessionCheck.token !== token || sessionCheck.epoch !== snapshot.epoch) {
    const promise = (async () => {
      const verifier = createTenantAuthClient(({ path, method, headers, data, params }) => {
        const query = params ? '?' + new URLSearchParams(params).toString() : '';
        return tenantTransport(path + query, { method, header: headers, data });
      }, tenantId);
      try {
        const identity = platformMode
          ? await restorePlatformSession(snapshot)
          : await verifier.restoreSession({ tenantId: snapshot.browseTenantId,
              accessToken: snapshot.token, refreshToken: snapshot.refreshToken }, () => isTenantRequestCurrent(snapshot));
        if (!isTenantRequestCurrent(snapshot)) throw new Error('登录状态已变化');
        if (identity.accessToken !== snapshot.token || identity.refreshToken !== snapshot.refreshToken) {
          writeTenantToken('token', identity.accessToken);
          writeTenantToken('refresh-token', identity.refreshToken);
          observedToken = identity.accessToken;
        }
        tenantContext.setAccount(identity.tenantId, identity.userId);
        if (platformMode) writePlatformBinding(identity.tenantId, identity.userId);
        uni.$emit?.('mall:session-status', '');
      } catch (error) {
        const current = isTenantRequestCurrent(snapshot);
        if (current && ((error instanceof SessionRestoreError && error.kind === 'invalid')
            || (platformMode && error instanceof PlatformSessionError && error.kind !== 'stale'))) {
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
  return { snapshot, header: tenantContext.accountHeaders(snapshot) };
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
