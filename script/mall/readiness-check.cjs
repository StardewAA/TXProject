'use strict';

// Anonymous local-only smoke check. No login, order writes, Docker mutation, or DB access.
const LIMIT = 256 * 1024;
const DEFAULTS = Object.freeze({
  gateway: 'http://localhost:48080', pc: 'http://localhost:15175',
  h5: 'http://localhost:3000', admin: 'http://localhost:5173', tenant: 1,
});

function localOrigin(value) {
  const url = new URL(value);
  if (!['http:', 'https:'].includes(url.protocol)
      || !['localhost', '127.0.0.1', '[::1]'].includes(url.hostname)
      || url.username || url.password || url.search || url.hash
      || url.pathname !== '/') throw new Error('Only loopback origins without credentials or paths are allowed');
  return url.origin;
}

function config(input = {}) {
  const result = { ...DEFAULTS, ...input };
  for (const key of ['gateway', 'pc', 'h5', 'admin']) result[key] = localOrigin(result[key]);
  if (!Number.isSafeInteger(result.tenant) || result.tenant <= 0) throw new Error('tenant must be a positive safe integer');
  return result;
}

async function boundedText(response) {
  if (!response.body) return '';
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let bytes = 0, text = '';
  try {
    for (;;) {
      const { done, value } = await reader.read();
      if (done) return text + decoder.decode();
      bytes += value.length;
      if (bytes > LIMIT) {
        await reader.cancel();
        throw new Error('BODY_TOO_LARGE');
      }
      text += decoder.decode(value, { stream: true });
    }
  } finally { reader.releaseLock(); }
}

async function run(input = {}, fetcher = fetch) {
  const settings = config(input);
  const report = { ok: false, at: new Date().toISOString(), tenant: settings.tenant, checks: [], capabilities: {},
    scope: 'Anonymous HTTP checks only; does not prove authenticated workflows or external integrations.' };
  const specs = [
    ['PC 页面', 'pc', '/', 'html'], ['H5 页面', 'h5', '/', 'html'], ['后台页面', 'admin', '/', 'html'],
    ['注册配置', 'gateway', '/app-api/member/mall-auth/config', 'registration'],
    ['租户入口配置', 'gateway', '/app-api/member/mall-tenant/config', 'tenant'],
    ['交易配置', 'gateway', '/app-api/trade/config/get', 'api'],
    ['客服能力', 'gateway', '/app-api/promotion/kefu-chat/capability', 'chat'],
    ['支付单匿名拒绝', 'gateway', '/app-api/pay/order/get?id=1', 'private'],
    ['比较清单匿名拒绝', 'gateway', '/app-api/product/comparison/list', 'private'],
    ['客服会话匿名拒绝', 'gateway', '/app-api/promotion/kefu-chat/thread?sessionId=mall-readiness-nonexistent', 'private'],
    ['后台 FAQ 匿名拒绝', 'gateway', '/admin-api/promotion/kefu-chat/faq?scope=PLATFORM&shopId=0', 'private'],
    ['内部订单卡不公开', 'gateway', `/rpc-api/trade/order/service-card?id=1&userId=1&tenantId=${settings.tenant}`, 'internal'],
  ];
  await Promise.all(specs.map(async ([name, service, path, kind], index) => {
    const check = { name, service, ok: false };
    report.checks[index] = check;
    try {
      const response = await fetcher(settings[service] + path, { method: 'GET', redirect: 'error',
        credentials: 'omit', headers: { 'tenant-id': String(settings.tenant), Accept: kind === 'html' ? 'text/html' : 'application/json' },
        signal: AbortSignal.timeout(10000) });
      check.httpStatus = response.status;
      const text = await boundedText(response);
      let data;
      if (kind !== 'html') { try { data = JSON.parse(text); } catch { /* Report only fixed reasons, never server bodies. */ } }
      if (Number.isSafeInteger(data?.code)) check.code = data.code;
      const contradictorySuccess = data?.code === 0 || data?.data != null || Array.isArray(data);
      if (kind === 'private') {
        // Require an actual authentication refusal, not an arbitrary validation/permission/server failure.
        check.ok = !contradictorySuccess && (response.status === 401 || (response.status === 200 && data?.code === 401));
      } else if (kind === 'internal') {
        check.ok = !contradictorySuccess && ([401, 403, 404].includes(response.status)
          || (response.status === 200 && [401, 403, 404].includes(data?.code)));
      } else if (kind === 'html') {
        check.ok = response.status === 200 && /<html(?:\s|>)/i.test(text) && /<\/html>/i.test(text);
      } else {
        check.ok = response.status === 200 && data?.code === 0 && data.data != null;
        if (check.ok && kind === 'registration') {
          check.ok = data.data.registrationMethod === 'image-v1' && data.data.tenantId === settings.tenant;
          report.capabilities.registration = check.ok ? 'image-v1' : 'unexpected';
        }
        if (check.ok && kind === 'tenant') {
          check.ok = typeof data.data.enabled === 'boolean';
          if (check.ok) report.capabilities.tenantEntryEnabled = data.data.enabled;
        }
        if (check.ok && kind === 'chat') {
          check.ok = data.data.version === 'scoped-chat-v1' && data.data.mode === 'FAQ_HUMAN'
            && typeof data.data.enabled === 'boolean' && data.data.attachmentsEnabled === false
            && /(?:^|[,\s])no-store(?:$|[,\s])/i.test(response.headers.get('cache-control') || '');
          if (check.ok) report.capabilities.scopedChatEnabled = data.data.enabled;
        }
      }
      if (!check.ok) check.reason = 'UNEXPECTED_HTTP_OR_CONTRACT';
    } catch (error) {
      check.reason = error?.name === 'TimeoutError' ? 'TIMEOUT'
        : error?.message === 'BODY_TOO_LARGE' ? 'BODY_TOO_LARGE' : 'NETWORK_OR_REDIRECT_ERROR';
    }
  }));
  report.ok = report.checks.every(check => check.ok);
  return report;
}

if (require.main === module) {
  const env = process.env;
  run({ gateway: env.MALL_CHECK_GATEWAY || DEFAULTS.gateway, pc: env.MALL_CHECK_PC || DEFAULTS.pc,
    h5: env.MALL_CHECK_H5 || DEFAULTS.h5, admin: env.MALL_CHECK_ADMIN || DEFAULTS.admin,
    tenant: env.MALL_CHECK_TENANT ? Number(env.MALL_CHECK_TENANT) : DEFAULTS.tenant })
    .then(report => { process.stdout.write(JSON.stringify(report, null, 2) + '\n'); process.exitCode = report.ok ? 0 : 1; })
    .catch(() => { process.stderr.write('Invalid local check configuration. No checks completed.\n'); process.exitCode = 2; });
}

module.exports = { run, config, boundedText };
