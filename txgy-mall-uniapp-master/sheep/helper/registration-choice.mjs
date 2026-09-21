// Pure registration intent. The server remains authoritative on send and consume.
const id = value => {
  const text = String(value ?? '');
  if (!/^[1-9]\d*$/.test(text) || !Number.isSafeInteger(Number(text))) throw new Error('注册租户编号无效');
  return text;
};
const RETIRED_PUBLIC_TENANT_ID = '3000';
export function parseRegistrationOptions(value) {
  if (!value || typeof value.enabled !== 'boolean' || typeof value.crossTenantReady !== 'boolean' || !Array.isArray(value.tenants)) {
    throw new Error('注册租户配置暂不可用，请重试');
  }
  if (!value.enabled) return { enabled: false, publicTenantId: null, tenants: [], crossTenantReady: false, selectionRequired: false };
  if (value.publicTenantId !== null || value.selectionRequired !== true) throw new Error('注册租户配置暂不可用，请重试');
  const seen = new Set();
  const tenants = value.tenants.map(item => {
    const tenantId = id(item?.tenantId);
    if (tenantId === RETIRED_PUBLIC_TENANT_ID || typeof item.name !== 'string' || !item.name.trim() || seen.has(tenantId)) throw new Error('注册租户目录无效');
    seen.add(tenantId);
    return { tenantId, name: item.name.trim() };
  });
  if (!tenants.length) throw new Error('当前没有可注册的租户，请稍后重试');
  return { enabled: true, publicTenantId: null, tenants, crossTenantReady: value.crossTenantReady, selectionRequired: true };
}
export function resolveRegistrationChoice(options, selected, currentTenant) {
  if (!options?.enabled) return { tenantId: id(currentTenant), name: '当前商城', publicDefault: false };
  if (selected === '' || selected === null || selected === undefined) throw new Error('请选择注册归属租户');
  const tenantId = id(selected);
  if (tenantId === RETIRED_PUBLIC_TENANT_ID) throw new Error('该租户已停止注册，请重新选择');
  const item = options.tenants.find(item => item.tenantId === tenantId);
  if (!item) throw new Error('所选商城已不在注册名单中，请重新选择');
  return { ...item, publicDefault: false };
}
export function assertRegistrationContext(choice, currentTenant) {
  // Never submit another tenant's registration from the current app/account context.
  if (choice.tenantId !== String(currentTenant)) throw new Error('当前页面不能跨商城注册，请先进入所选商城；若入口未开放请联系平台');
}
export function assertExplicitRegistrationTarget(selected, currentTenant) {
  const tenantId = id(selected);
  if (tenantId === RETIRED_PUBLIC_TENANT_ID) throw new Error('该租户已停止注册，请重新选择');
  if (tenantId !== id(currentTenant)) throw new Error('注册目标与当前商城入口不一致，请重新进入');
  return tenantId;
}
export function registrationChoiceNotice(choice) {
  return '本次注册归属：' + choice.name
    + '。账号按租户隔离，选择租户不代表取得企业采购或销售权限。';
}
