import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace HrmPolicyApi {
  /** HRM 政策 */
  export interface Policy {
    id?: number; // 政策编号
    name?: string; // 政策名称
    category?: string; // 类别：social_insurance/provident_fund/labor_contract/training/other
    docNo?: string; // 文号
    publishOrg?: string; // 发布机关
    publishDate?: string; // 发布日期
    effectiveDate?: string; // 生效日期
    expireDate?: string; // 失效日期
    scope?: string; // 适用范围：group/tenant/region
    scopeValue?: string; // 范围明细
    content?: string; // 政策正文
    fileUrls?: string[]; // 附件地址
    tags?: string[]; // 标签
    status?: number; // 状态：0 启用 1 停用
    remark?: string; // 备注
    createTime?: Date; // 创建时间
  }
}

/** 查询政策分页 */
export function getPolicyPage(params: PageParam) {
  return requestClient.get<PageResult<HrmPolicyApi.Policy>>('/hrm/policy/page', {
    params,
  });
}

/** 查询政策详情 */
export function getPolicy(id: number) {
  return requestClient.get<HrmPolicyApi.Policy>(`/hrm/policy/get?id=${id}`);
}

/** 新增政策 */
export function createPolicy(data: HrmPolicyApi.Policy) {
  return requestClient.post<number>('/hrm/policy/create', data);
}

/** 修改政策 */
export function updatePolicy(data: HrmPolicyApi.Policy) {
  return requestClient.put<boolean>('/hrm/policy/update', data);
}

/** 删除政策 */
export function deletePolicy(id: number) {
  return requestClient.delete<boolean>(`/hrm/policy/delete?id=${id}`);
}
