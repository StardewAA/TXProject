import type { Inquiry, InquiryItem } from '@/api/inquiry'
// 只比较可编辑业务字段；服务器编号、状态和时间戳不算用户修改，不持久化到浏览器。
export function inquiryDraftSnapshot(form: Inquiry, items: InquiryItem[]): string {
  return JSON.stringify([form.title, form.contactName, form.contactMobile, form.deliveryAreaId ?? null,
    form.address, form.remark,
    Number(form.deadline), form.expectedDate ? Number(form.expectedDate) : null,
    form.visibility ?? 'TENANT_PUBLIC', [...(form.invitedShopIds ?? [])].sort((a, b) => a - b),
    (form.invitedScopes ?? []).map(scope => [scope.targetType, scope.targetTenantId,
      scope.targetEnterpriseId ?? null, scope.targetShopId ?? null]).sort((a, b) => JSON.stringify(a).localeCompare(JSON.stringify(b))),
    items.map(item => [item.skuId ?? null, item.code ?? '', item.name, item.category, item.specification ?? '',
      Number(item.quantity), item.unit, item.remark ?? '', item.picUrl || ''])])
}
