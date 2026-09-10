import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace CrmAfterSaleWarrantyApi {
  /** 售后质保档案 */
  export interface Warranty {
    id?: number;
    no?: string;
    contractId?: number;
    customerId?: number;
    contractProductId?: number;
    projectName?: string;
    projectAcceptDate?: string;
    warrantyYears?: number;
    warrantyEndDate?: string;
    warrantyScope?: string;
    status?: number;
    ownerUserId?: number;
    serviceUserId?: number;
    lastVisitTime?: string;
    nextVisitTime?: string;
    remindSentDate?: string;
    remark?: string;
    createTime?: string;
    updateTime?: string;
    /** 扩展：合同编号 */
    contractNo?: string;
    /** 扩展：合同名称 */
    contractName?: string;
    /** 扩展：客户名称 */
    customerName?: string;
    /** 扩展：质保剩余天数，负数表示已到期 */
    remainDays?: number;
  }

  /** 分页查询参数 */
  export interface PageReq extends PageParam, Partial<Warranty> {
    warrantyEndDate?: string[];
  }
}

/** 查询质保档案分页 */
export function getAfterSaleWarrantyPage(
  params: CrmAfterSaleWarrantyApi.PageReq,
) {
  return requestClient.get<PageResult<CrmAfterSaleWarrantyApi.Warranty>>(
    '/crm/after-sale/warranty/page',
    { params },
  );
}

/** 查询质保档案详情 */
export function getAfterSaleWarranty(id: number) {
  return requestClient.get<CrmAfterSaleWarrantyApi.Warranty>(
    `/crm/after-sale/warranty/get?id=${id}`,
  );
}

/** 新增质保档案 */
export function createAfterSaleWarranty(
  data: CrmAfterSaleWarrantyApi.Warranty,
) {
  return requestClient.post('/crm/after-sale/warranty/create', data);
}

/** 修改质保档案 */
export function updateAfterSaleWarranty(
  data: CrmAfterSaleWarrantyApi.Warranty,
) {
  return requestClient.put('/crm/after-sale/warranty/update', data);
}

/** 删除质保档案 */
export function deleteAfterSaleWarranty(id: number) {
  return requestClient.delete(`/crm/after-sale/warranty/delete?id=${id}`);
}

/** 获取质保档案下拉数据（基于分页接口，最多 200 条） */
export async function getAfterSaleWarrantyOptions() {
  const data = await getAfterSaleWarrantyPage({ pageNo: 1, pageSize: 200 });
  return (data?.list ?? []).map((item) => ({
    label: item.no
      ? `${item.no} - ${item.projectName ?? ''}`
      : (item.projectName ?? ''),
    value: item.id,
  }));
}
