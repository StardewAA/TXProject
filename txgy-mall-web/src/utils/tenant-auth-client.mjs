// Shared PC/H5 challenge transport. No storage, global tenant mutation or legacy fallback on writes.
import { parseAuthEntry } from './auth-challenge.mjs';
import { imageAuthPayload, validateImageChallenge } from './image-registration.mjs';

const id = value => {
  if (!['string', 'number'].includes(typeof value)) throw new Error('认证租户或会员编号无效');
  const text = String(value);
  if (!/^[1-9]\d*$/.test(text) || !Number.isSafeInteger(Number(text))) throw new Error('认证租户或会员编号无效');
  return text;
};
const token = value => typeof value === 'string' && value.length > 0 && value.length <= 4096 && !/\s/.test(value);
const operations = Object.freeze({
  'send-code': ['tenantId', 'mobile', 'purpose'],
  login: ['tenantId', 'mobile', 'password'],
  'sms-login': ['tenantId', 'mobile', 'challengeId', 'code'],
  register: ['tenantId', 'mobile', 'challengeId', 'code', 'password', 'nickname', 'agree', 'memberIdentity'],
  'reset-password': ['tenantId', 'mobile', 'challengeId', 'code', 'password'],
});
const imageOperations = new Set(['image-challenge', 'register-image', 'account-login']);
const authenticated = new Set(['login', 'sms-login', 'register', 'register-image', 'account-login']);
const checkCurrent = current => { if (!current()) throw new Error('认证上下文已变化，请重新操作'); };
export class SessionRestoreError extends Error {
  constructor(kind, message) { super(message); this.kind = kind; }
}
const statusOf = error => error?.response?.status ?? error?.statusCode ?? error?.status ?? error?.code;
// Only reviewed domain codes have user-facing messages; never display arbitrary server text.
const authErrorMessages = Object.freeze({
  1004003000: '账号或密码不正确',
  1004003001: '账号已被禁用，请联系管理员',
  1004003008: '账号与当前商城不匹配，请返回账号所属商城',
  1004003009: '商城暂不可用，请联系管理员检查商城配置和数据库初始化',
  1004003010: '旧版登录入口已停用，请从商城首页重新登录',
  1004003011: '验证码无效或已过期，请重新获取',
  1004003012: '操作过于频繁，请稍后重试',
  1004003013: '验证码发送未确认，请稍后重新获取',
  1004003014: '平台账号尚未完成安全映射，请联系平台处理',
  1004003015: '收货地址不存在或不属于当前账号',
  1004003016: '登录标识已存在或有冲突，请使用原账号登录或联系平台处理',
  1004003017: '手机号修改失败，请核对平台账号映射或新手机号',
  1004003018: '当前账号无权处理平台账号身份归并',
  1004003019: '身份归并信息已变化，请刷新后重试',
  1004003020: '身份归并存在冲突，操作未完成',
  1004008190: '登录名需为 6-20 位字母、数字或下划线，且以字母开头',
  1004008191: '该登录名已被使用，请更换登录名',
  1004008192: '请使用登录名和图片验证码注册，已有账号请登录',
  1004008193: '请同意协议，并确认两次输入的密码一致',
  1004008194: '身份手机号须经过验证绑定，不能通过资料编辑设置',
  1004008200: '图片验证码无效或已过期，请刷新后重试',
  1004008201: '操作过于频繁，请稍后重试',
  1004008202: '图片验证暂不可用，请稍后重试',
});
const envelope = response => {
  if (!response || response.code !== 0) {
    const code = typeof response?.code === 'number' ? response.code : -1;
    const error = new Error(authErrorMessages[code] || '商城认证未完成，请检查输入或稍后重试');
    error.code = code;
    throw error;
  }
  return response.data;
};

export function validateTenantIdentity(value, tenantId, userId) {
  const target = id(tenantId);
  if (!value || id(value.tenantId) !== target || !token(value.accessToken) || !token(value.refreshToken)) {
    throw new Error('认证返回的租户或令牌不匹配');
  }
  const member = id(value.userId);
  if (userId !== undefined && member !== id(userId)) throw new Error('认证返回的会员不匹配');
  return Object.freeze({ ...value, tenantId: target, userId: member });
}

export function platformLoginPayload(input) {
  const account = typeof input?.account === 'string' ? input.account.trim().toLowerCase() : '';
  if (!/^1[3-9]\d{9}$/.test(account) && !/^[a-z][a-z0-9_]{5,19}$/.test(account)) {
    throw new Error('请输入正确的登录名或手机号');
  }
  if (typeof input?.password !== 'string' || input.password.length < 4 || input.password.length > 16) {
    throw new Error('密码需要为 4-16 位');
  }
  // Platform account resolution belongs to the server. Only credentials are
  // allowed through even if an unsafe caller adds a tenant field at runtime.
  return Object.freeze({ account, password: input.password });
}

export function tenantAuthPayload(operation, input) {
  if (imageOperations.has(operation)) return imageAuthPayload(operation, input);
  if (!Object.hasOwn(operations, operation)) throw new Error('不支持的商城认证操作');
  const data = Object.fromEntries(operations[operation].filter(key => Object.hasOwn(input || {}, key)).map(key => [key, input[key]]));
  data.tenantId = id(data.tenantId);
  const mobilePattern = operation === 'login' ? /^1[3-9]\d{9}$/ : /^1\d{10}$/;
  if (typeof data.mobile !== 'string' || !mobilePattern.test(data.mobile)) throw new Error('请输入正确的手机号');
  if (operation === 'send-code') {
    if (!['REGISTER', 'SMS_LOGIN', 'RESET_PASSWORD'].includes(data.purpose)) throw new Error('验证码用途无效');
  } else {
    const minimumPassword = operation === 'login' ? 4 : 6;
    if (operation !== 'sms-login' && (typeof data.password !== 'string' || data.password.length < minimumPassword || data.password.length > 16)) {
      throw new Error(`密码需要为 ${minimumPassword}-16 位`);
    }
    if (operation !== 'login' && (typeof data.challengeId !== 'string' || !/^[a-f0-9]{32}$/.test(data.challengeId)
      || typeof data.code !== 'string' || !/^[0-9]{6}$/.test(data.code))) throw new Error('请重新获取当前商城的六位验证码');
    if (operation === 'register') {
      if (typeof data.nickname !== 'string' || !data.nickname.trim() || data.nickname.trim().length > 30
        || data.agree !== true || !['buyer', 'supplier'].includes(data.memberIdentity)) throw new Error('请完善注册信息并同意协议');
      data.nickname = data.nickname.trim();
    }
  }
  return Object.freeze(data);
}

export function createTenantAuthClient(transport, legacyTenantId = '1') {
  const legacy = id(legacyTenantId);
  const call = async (request, current) => {
    checkCurrent(current);
    const response = await transport(request);
    checkCurrent(current);
    return envelope(response);
  };
  const header = tenant => ({ 'tenant-id': tenant, terminal: '20', 'Content-Type': 'application/json' });
  const platformHeader = accessToken => ({ terminal: '20', 'Content-Type': 'application/json',
    ...(accessToken ? { Authorization: 'Bearer ' + String(accessToken).replace(/^Bearer /, '') } : {}) });
  const verify = async (identity, tenant, current, expectedUser) => {
    const result = validateTenantIdentity(identity, tenant, expectedUser);
    const proof = await call({ path: '/member/mall-tenant/session', method: 'GET',
      headers: { ...header(result.tenantId), Authorization: 'Bearer ' + result.accessToken } }, current);
    if (!proof || id(proof.tenantId) !== result.tenantId || id(proof.userId) !== result.userId) {
      throw new Error('服务端会话与认证会员不匹配');
    }
    return result;
  };
  const platformProof = async (accessToken, current, expectedTenant, expectedUser) => {
    const proof = await call({ path: '/member/platform-account/session', method: 'GET',
      headers: platformHeader(accessToken) }, current);
    const tenant = id(proof?.sourceTenantId);
    const member = id(proof?.canonicalMemberId);
    id(proof?.platformAccountId);
    if ((expectedTenant !== undefined && tenant !== id(expectedTenant))
        || (expectedUser !== undefined && member !== id(expectedUser))) {
      throw new Error('平台账号会话与认证会员不匹配');
    }
    return Object.freeze({ tenantId: tenant, userId: member });
  };
  const verifyPlatform = async (identity, current) => {
    const result = validateTenantIdentity(identity, identity?.tenantId);
    await platformProof(result.accessToken, current, result.tenantId, result.userId);
    return result;
  };
  return Object.freeze({
    async discover(target = legacy, current = () => true) {
      const tenant = id(target);
      try {
        const value = await call({ path: '/member/mall-auth/config', method: 'GET', headers: {},
          ...(tenant === legacy ? {} : { params: { tenantId: tenant } }) }, current);
        if (tenant !== legacy && value?.protocol !== 'challenge-v1') throw new Error('目标商城不支持独立认证');
        return parseAuthEntry(value, tenant);
      } catch (error) {
        checkCurrent(current);
        if (tenant === legacy && (error.code === 404 || error.status === 404 || error.statusCode === 404)) {
          return parseAuthEntry({ protocol: 'legacy', tenantId: legacy, available: true, registrationAvailable: true, tenantSelection: false }, legacy);
        }
        throw error;
      }
    },
    async execute(operation, input, current = () => true) {
      const data = tenantAuthPayload(operation, input);
      const result = await call({ path: '/member/mall-auth/' + operation, method: 'POST',
        headers: header(data.tenantId), data }, current);
      if (authenticated.has(operation)) {
        try { return await verify(result, data.tenantId, current); }
        catch (error) {
          checkCurrent(current);
          // Registration may already be committed. Do not encourage another create request.
          if (operation === 'register' || operation === 'register-image') throw new Error('注册请求已返回，但登录状态未确认；请勿重复注册，稍后使用该商城账号登录');
          throw error;
        }
      }
      if (operation === 'image-challenge') return validateImageChallenge(result, data.tenantId);
      if (operation === 'send-code') {
        if (!result || id(result.tenantId) !== data.tenantId || !/^[a-f0-9]{32}$/.test(result.challengeId)
          || result.deliveryStatus !== 'SUBMITTED_UNCONFIRMED'
          || !Number.isInteger(result.expiresIn) || result.expiresIn < 1 || result.expiresIn > 600
          || !Number.isInteger(result.retryAfter) || result.retryAfter < 1 || result.retryAfter > result.expiresIn) {
          throw new Error('验证码响应与当前商城不一致');
        }
      } else if (result !== true) throw new Error('密码重置未确认，请重新检查');
      return result;
    },
    async executePlatformLogin(input, current = () => true) {
      const data = platformLoginPayload(input);
      const result = await call({ path: '/member/mall-auth/platform-login', method: 'POST',
        headers: platformHeader(), data }, current);
      return verifyPlatform(result, current);
    },
    verifySession(identity, tenant, current = () => true, userId) { return verify(identity, tenant, current, userId); },
    verifyPlatformSession(identity, current = () => true) { return verifyPlatform(identity, current); },
    async restoreSession(stored, current = () => true) {
      // Stored tenant/member hints are not evidence. Only the controlled entry is accepted here.
      const tenant = id(stored.tenantId);
      const access = String(stored.accessToken || '').replace(/^Bearer /, '');
      const refresh = stored.refreshToken || '';
      const invalid = () => new SessionRestoreError('invalid', '登录身份无法确认，请重新登录');
      const probe = async (accessToken, expectedUser) => {
        let proof;
        try {
          proof = await call({ path: '/member/mall-tenant/session', method: 'GET',
            headers: { ...header(tenant), Authorization: 'Bearer ' + accessToken } }, current);
        } catch (error) {
          if (statusOf(error) !== 404 || tenant !== legacy) throw error;
          // Compatibility requires an explicitly legacy auth entry AND an authenticated profile.
          const entry = await this.discover(tenant, current);
          if (entry.protocol !== 'legacy' || !entry.available) throw invalid();
          const profile = await call({ path: '/member/user/get', method: 'GET',
            headers: { ...header(tenant), Authorization: 'Bearer ' + accessToken } }, current);
          if (!profile || (profile.tenantId !== undefined && String(profile.tenantId) !== tenant)) throw invalid();
          proof = { tenantId: tenant, userId: profile.id };
        }
        try {
          if (id(proof?.tenantId) !== tenant || (expectedUser !== undefined && id(proof?.userId) !== id(expectedUser))) throw invalid();
          return { tenantId: tenant, userId: id(proof?.userId) };
        } catch { throw invalid(); }
      };
      try {
        checkCurrent(current);
        if (!token(access)) throw invalid();
        try {
          return { ...await probe(access), accessToken: stored.accessToken, refreshToken: refresh };
        } catch (error) {
          if (statusOf(error) !== 401 || !token(refresh)) throw error;
          // Only an explicit 401 permits one rotation. Network/403/invalid proof never rotate.
          const renewed = await call({ path: '/member/auth/refresh-token', method: 'POST',
            headers: header(tenant), params: { refreshToken: refresh } }, current);
          if (!renewed || !token(renewed.accessToken) || !token(renewed.refreshToken)
              || (renewed.tenantId !== undefined && String(renewed.tenantId) !== tenant)) throw invalid();
          const identity = await probe(renewed.accessToken, renewed.userId);
          checkCurrent(current);
          return { ...renewed, ...identity };
        }
      } catch (error) {
        if (!current()) throw new SessionRestoreError('stale', '登录状态已变化，请重新操作');
        if (error instanceof SessionRestoreError) throw error;
        if ([401, 403].includes(statusOf(error))) throw invalid();
        throw new SessionRestoreError('unavailable', '暂时无法核验登录状态，请稍后重试；尚未发送业务请求');
      }
    },
    async restorePlatformSession(stored, current = () => true) {
      const tenant = id(stored.tenantId);
      const access = String(stored.accessToken || '').replace(/^Bearer /, '');
      const refresh = stored.refreshToken || '';
      const invalid = () => new SessionRestoreError('invalid', '平台登录身份无法确认，请重新登录');
      const probe = async accessToken => platformProof(accessToken, current, tenant);
      try {
        checkCurrent(current);
        if (!token(access)) throw invalid();
        try {
          const identity = await probe(access);
          return { ...identity, accessToken: stored.accessToken, refreshToken: refresh };
        } catch (error) {
          if (statusOf(error) !== 401 || !token(refresh)) throw error;
          const renewed = await call({ path: '/member/auth/refresh-token', method: 'POST',
            headers: header(tenant), params: { refreshToken: refresh } }, current);
          if (!renewed || !token(renewed.accessToken) || !token(renewed.refreshToken)
              || (renewed.tenantId !== undefined && id(renewed.tenantId) !== tenant)) throw invalid();
          const identity = await probe(renewed.accessToken);
          return { ...renewed, ...identity };
        }
      } catch (error) {
        if (!current()) throw new SessionRestoreError('stale', '登录状态已变化，请重新操作');
        if (error instanceof SessionRestoreError) throw error;
        // Platform tokens are usable only together with a proved source-tenant
        // carrier. Any failed proof is fail-closed and clears this login.
        throw invalid();
      }
    },
    async refresh(identity, current = () => true) {
      const previous = validateTenantIdentity(identity, identity?.tenantId);
      const result = await call({ path: '/member/auth/refresh-token', method: 'POST',
        headers: header(previous.tenantId), params: { refreshToken: previous.refreshToken } }, current);
      return verify(result, previous.tenantId, current, previous.userId);
    },
  });
}
