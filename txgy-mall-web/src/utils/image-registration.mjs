// Shared PC/H5 image registration rules. Challenge answers stay on the server.
const challengeId = value => typeof value === 'string' && /^[a-f0-9]{32}$/.test(value);
const tenantId = value => {
  if (!['string', 'number'].includes(typeof value) || !/^[1-9]\d*$/.test(String(value))
    || !Number.isSafeInteger(Number(value))) throw new Error('注册租户编号无效');
  return String(value);
};
export function normalizeLoginName(value) {
  const normalized = typeof value === 'string' ? value.trim().toLowerCase() : '';
  if (!/^[a-z][a-z0-9_]{5,19}$/.test(normalized)) throw new Error('登录名需为 6-20 位字母、数字或下划线，且以字母开头');
  return normalized;
}
export function createCaptchaSession(random = globalThis.crypto) {
  if (typeof random?.getRandomValues !== 'function') throw new Error('当前浏览器无法创建图片验证会话，请使用安全连接重试');
  return Array.from(random.getRandomValues(new Uint8Array(16)), value => value.toString(16).padStart(2, '0')).join('');
}
const operations = Object.freeze({
  'image-challenge': ['tenantId', 'loginName', 'captchaSession', 'previousChallengeId', 'purpose'],
  'register-image': ['tenantId', 'loginName', 'password', 'confirmPassword', 'captchaSession', 'imageChallengeId', 'imageCode', 'contactMobile', 'nickname', 'memberIdentity', 'agree'],
  'account-login': ['tenantId', 'account', 'password'],
});
export function imageAuthPayload(operation, input) {
  if (!Object.hasOwn(operations, operation)) throw new Error('不支持的图片认证操作');
  const data = Object.fromEntries(operations[operation].filter(key => Object.hasOwn(input || {}, key)).map(key => [key, input[key]]));
  data.tenantId = tenantId(data.tenantId);
  if (operation === 'account-login') {
    const account = typeof data.account === 'string' ? data.account.trim() : '';
    data.account = /^1[3-9]\d{9}$/.test(account) ? account : normalizeLoginName(account);
  } else {
    data.loginName = normalizeLoginName(data.loginName);
    if (!challengeId(data.captchaSession)) throw new Error('图片验证会话无效，请刷新页面');
  }
  if (operation === 'image-challenge') {
    if (data.purpose !== 'REGISTER') throw new Error('图片验证码仅用于注册');
    if (data.previousChallengeId !== undefined && !challengeId(data.previousChallengeId)) throw new Error('请重新获取图片验证码');
  } else {
    const min = operation === 'account-login' ? 4 : 6;
    if (typeof data.password !== 'string' || data.password.length < min || data.password.length > 16) throw new Error(`密码需要为 ${min}-16 位`);
    if (operation === 'register-image') {
      if (data.password !== data.confirmPassword) throw new Error('两次输入的密码不一致');
      if (!challengeId(data.imageChallengeId) || typeof data.imageCode !== 'string' || !/^[a-z0-9]{5}$/i.test(data.imageCode.trim())) throw new Error('请输入图片中的 5 位验证码');
      data.imageCode = data.imageCode.trim().toUpperCase();
      if (data.agree !== true || !['buyer', 'supplier'].includes(data.memberIdentity)) throw new Error('请确认会员身份并同意协议');
      if (data.contactMobile !== undefined) {
        if (typeof data.contactMobile !== 'string') throw new Error('请输入正确的联系手机号');
        data.contactMobile = data.contactMobile.trim();
        if (!data.contactMobile) delete data.contactMobile;
        else if (!/^1[3-9]\d{9}$/.test(data.contactMobile)) throw new Error('请输入正确的联系手机号');
      }
      if (data.nickname !== undefined) {
        if (typeof data.nickname !== 'string' || data.nickname.trim().length > 30) throw new Error('昵称不能超过 30 位');
        data.nickname = data.nickname.trim();
        if (!data.nickname) delete data.nickname;
      }
    }
  }
  return Object.freeze(data);
}
export function validateImageChallenge(value, expectedTenant) {
  if (!value || tenantId(value.tenantId) !== tenantId(expectedTenant) || !challengeId(value.challengeId)
    || typeof value.imageDataUrl !== 'string' || value.imageDataUrl.length > 524288
    || !/^data:image\/png;base64,[A-Za-z0-9+/]+={0,2}$/.test(value.imageDataUrl)
    || !Number.isInteger(value.expiresIn) || value.expiresIn < 1 || value.expiresIn > 600
    || !Number.isInteger(value.retryAfter) || value.retryAfter < 1 || value.retryAfter > value.expiresIn) {
    throw new Error('图片验证码响应无效，请重新获取');
  }
  return Object.freeze({ tenantId: String(value.tenantId), challengeId: value.challengeId, imageDataUrl: value.imageDataUrl,
    expiresIn: value.expiresIn, retryAfter: value.retryAfter });
}
export function createImageRegistrationState(now = () => Date.now()) {
  let generation = 0, serial = 0, bindingKey = '', pending = null, challenge = null;
  let previousChallengeId, retryAt = 0;
  const normalized = value => imageAuthPayload('image-challenge', { ...value, purpose: 'REGISTER' });
  const key = value => JSON.stringify([value.tenantId, value.loginName, value.captchaSession]);
  const change = value => {
    const next = key(value);
    if (bindingKey !== next) { bindingKey = next; generation++; pending = null; challenge = null; }
  };
  const current = ticket => ticket.generation === generation && ticket.serial === serial && ticket.binding === bindingKey;
  return {
    invalidate() { generation++; pending = null; challenge = null; },
    begin(value) {
      const data = normalized(value); change(data);
      if (pending || retryAt > now()) throw new Error('请稍后再刷新图片验证码');
      challenge = null;
      pending = Object.freeze({ ...data, ...(previousChallengeId ? { previousChallengeId } : {}),
        binding: bindingKey, generation, serial: ++serial, startedAt: now() });
      return pending;
    },
    current,
    accept(ticket, value) {
      if (!current(ticket) || pending?.serial !== ticket.serial) return false;
      const result = validateImageChallenge(value, ticket.tenantId);
      if (ticket.startedAt + result.expiresIn * 1000 <= now()) throw new Error('图片验证码已过期，请重新获取');
      previousChallengeId = result.challengeId;
      retryAt = ticket.startedAt + result.retryAfter * 1000;
      challenge = { id: result.challengeId, expiresAt: ticket.startedAt + result.expiresIn * 1000 };
      pending = null;
      return true;
    },
    finish(ticket) { if (current(ticket) && pending?.serial === ticket.serial) pending = null; },
    remaining() { return Math.max(0, Math.ceil((retryAt - now()) / 1000)); },
    expiresIn() { return Math.max(0, Math.ceil(((challenge?.expiresAt || 0) - now()) / 1000)); },
    payload(value, code) {
      const data = normalized(value); change(data);
      if (!challenge || challenge.expiresAt <= now()) throw new Error('请为当前登录名重新获取图片验证码');
      if (typeof code !== 'string' || !/^[a-z0-9]{5}$/i.test(code.trim())) throw new Error('请输入图片中的 5 位验证码');
      return { tenantId: data.tenantId, loginName: data.loginName, captchaSession: data.captchaSession,
        imageChallengeId: challenge.id, imageCode: code.trim().toUpperCase() };
    },
  };
}
