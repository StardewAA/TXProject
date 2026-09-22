const INTERNAL_INTEGRATION = /(?:ERP|erp).{0,32}(?:未对接|尚未对接|暂未对接|未接入|尚未接入|暂未接入|未配置|尚未配置|暂未配置|未启用|尚未启用|暂未启用|不支持|模拟|mock)/;

/**
 * API errors can contain an operator-facing integration detail. Mobile buyers only need
 * the action they can take; product names and rollout status belong in logs/admin tools.
 */
export function publicApiMessage(message, fallback = '') {
  if (typeof message !== 'string' || !message.trim()) return fallback;
  const text = message.trim();
  return INTERNAL_INTEGRATION.test(text) ? '相关服务暂不可用，请稍后重试' : text;
}
