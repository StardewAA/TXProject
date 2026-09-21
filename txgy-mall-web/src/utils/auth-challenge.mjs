// Pure form-local state. No tokens, passwords or OTPs are persisted.
export function parseAuthEntry(value, expectedTenant) {
  if (!value || !['legacy', 'challenge-v1'].includes(value.protocol)
      || String(value.tenantId) !== String(expectedTenant)
      || typeof value.available !== 'boolean' || typeof value.registrationAvailable !== 'boolean'
      || typeof value.tenantSelection !== 'boolean') throw new Error('商城认证配置无效，请刷新重试');
  // Cross-tenant UI remains gated until customer affiliation and business scopes are complete.
  return Object.freeze({ ...value, tenantId: String(value.tenantId), tenantSelection: false });
}
export function assertAuthAvailable(entry, purpose) {
  if (!entry?.available || (purpose === 'REGISTER' && !entry.registrationAvailable)) {
    throw new Error('当前商城暂未开放此认证方式，请稍后重试');
  }
}
export function createChallengeState(now = () => Date.now()) {
  let binding = '', generation = 0, serial = 0, pending = null, challenge = null;
  const cooldowns = new Map();
  const key = value => JSON.stringify([String(value.tenantId), value.mobile, value.purpose, value.protocol]);
  const change = value => {
    const next = key(value);
    if (binding !== next) { binding = next; generation++; pending = null; challenge = null; }
  };
  const current = ticket => ticket.generation === generation && ticket.binding === binding && ticket.serial === serial;
  return {
    change,
    invalidate() { generation++; pending = null; challenge = null; },
    begin(value) {
      change(value);
      if (pending || (cooldowns.get(value.mobile) || 0) > now()) throw new Error('请稍后再发送验证码');
      challenge = null;
      pending = Object.freeze({ ...value, binding, generation, serial: ++serial, startedAt: now() });
      return pending;
    },
    current,
    accept(ticket, result) {
      if (!current(ticket) || pending?.serial !== ticket.serial) return false;
      let ttl = 300, retry = 60, id = '';
      if (ticket.protocol === 'challenge-v1') {
        if (!result || !/^[a-f0-9]{32}$/.test(result.challengeId)
            || String(result.tenantId) !== String(ticket.tenantId)
            || result.deliveryStatus !== 'SUBMITTED_UNCONFIRMED'
            || !Number.isInteger(result.expiresIn) || result.expiresIn < 1 || result.expiresIn > 600
            || !Number.isInteger(result.retryAfter) || result.retryAfter < 1 || result.retryAfter > result.expiresIn) {
          throw new Error('验证码响应与当前商城不一致，请重新获取');
        }
        ttl = result.expiresIn; retry = result.retryAfter; id = result.challengeId;
      }
      // Starting before HTTP avoids extending server validity by network latency.
      if (ticket.startedAt + ttl * 1000 <= now()) throw new Error('验证码已过期，请重新获取');
      cooldowns.set(ticket.mobile, ticket.startedAt + retry * 1000);
      challenge = { binding, id, expiresAt: ticket.startedAt + ttl * 1000 };
      pending = null;
      return true;
    },
    finish(ticket) { if (current(ticket) && pending?.serial === ticket.serial) pending = null; },
    remaining(mobile) { return Math.max(0, Math.ceil(((cooldowns.get(mobile) || 0) - now()) / 1000)); },
    payload(value, code) {
      change(value);
      if (value.protocol === 'legacy') return {};
      if (!/^[0-9]{6}$/.test(code)) throw new Error('请输入六位验证码');
      if (!challenge || challenge.binding !== binding || challenge.expiresAt <= now()) {
        throw new Error('请为当前手机号重新获取验证码');
      }
      return { tenantId: String(value.tenantId), challengeId: challenge.id };
    },
  };
}
