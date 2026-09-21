// Pure registration intent. The server remains authoritative on send and consume.
const id = value => {
  const text = String(value ?? '');
  if (!/^[1-9]\d*$/.test(text) || !Number.isSafeInteger(Number(text))) throw new Error('注册租户编号无效');
  return text;
};
export function parseRegistrationOptions(value) {
  if (!value || typeof value.enabled !== 'boolean' || typeof value.crossTenantReady !== 'boolean' || !Array.isArray(value.tenants)) {
    throw new Error('注册租户配置暂不可用，请重试');
  }
  if (!value.enabled) return { enabled: false, publicTenantId: null, tenants: [], crossTenantReady: false };
  const publicTenantId = id(value.publicTenantId), seen = new Set();
  const tenants = value.tenants.map(item => {
    const tenantId = id(item?.tenantId);
    if (typeof item.name !== 'string' || !item.name.trim() || seen.has(tenantId)) throw new Error('注册租户目录无效');
    seen.add(tenantId);
    return { tenantId, name: item.name.trim() };
  });
  if (!seen.has(publicTenantId)) throw new Error('公共商城暂未开放注册，请重试');
  return { enabled: true, publicTenantId, tenants, crossTenantReady: value.crossTenantReady };
}
export function resolveRegistrationChoice(options, selected, currentTenant) {
  if (!options?.enabled) return { tenantId: id(currentTenant), name: '当前商城', publicDefault: false };
  const publicDefault = selected === '' || selected === null || selected === undefined;
  const tenantId = publicDefault ? options.publicTenantId : id(selected);
  const item = options.tenants.find(item => item.tenantId === tenantId);
  if (!item) throw new Error('所选商城已不在注册名单中，请重新选择');
  return { ...item, publicDefault };
}
export function assertRegistrationContext(choice, currentTenant) {
  // Never submit another tenant's registration from the current app/account context.
  if (choice.tenantId !== String(currentTenant)) throw new Error('当前页面不能跨商城注册，请先进入所选商城；若入口未开放请联系平台');
}
export function registrationChoiceNotice(choice) {
  return (choice.publicDefault ? '未选择租户，将注册到平台自营公共商城：' : '本次注册归属：') + choice.name
    + '。账号按租户隔离，选择租户不代表取得企业采购或销售权限。';
}
