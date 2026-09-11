import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace CrmAfterSaleVisitRecordApi {
  /** 售后回访记录 */
  export interface VisitRecord {
    id?: number;
    taskId?: number;
    warrantyId?: number;
    customerId?: number;
    contractId?: number;
    contactId?: number;
    visitType?: number;
    visitTime?: string;
    content?: string;
    satisfactionLevel?: number;
    satisfactionRemark?: string;
    nextVisitDate?: string;
    picUrls?: string[];
    fileUrls?: string[];
    ticketId?: number;
    businessId?: number;
    createTime?: string;
    /** 扩展：质保编号 */
    warrantyNo?: string;
    /** 扩展：工程项目名称 */
    projectName?: string;
    /** 扩展：合同名称 */
    contractName?: string;
    /** 扩展：客户名称 */
    customerName?: string;
    /** 扩展：创建人昵称 */
    creatorNickname?: string;
  }

  /** 分页查询参数 */
  export interface PageReq extends PageParam, Partial<VisitRecord> {
    visitTime?: string[];
  }
}

/** 查询回访记录分页 */
export function getAfterSaleVisitRecordPage(
  params: CrmAfterSaleVisitRecordApi.PageReq,
) {
  return requestClient.get<PageResult<CrmAfterSaleVisitRecordApi.VisitRecord>>(
    '/crm/after-sale/visit-record/page',
    { params },
  );
}

/** 查询回访记录详情 */
export function getAfterSaleVisitRecord(id: number) {
  return requestClient.get<CrmAfterSaleVisitRecordApi.VisitRecord>(
    `/crm/after-sale/visit-record/get?id=${id}`,
  );
}

/** 新增回访记录 */
export function createAfterSaleVisitRecord(
  data: CrmAfterSaleVisitRecordApi.VisitRecord,
) {
  return requestClient.post('/crm/after-sale/visit-record/create', data);
}

/** 修改回访记录 */
export function updateAfterSaleVisitRecord(
  data: CrmAfterSaleVisitRecordApi.VisitRecord,
) {
  return requestClient.put('/crm/after-sale/visit-record/update', data);
}

/** 删除回访记录 */
export function deleteAfterSaleVisitRecord(id: number) {
  return requestClient.delete(`/crm/after-sale/visit-record/delete?id=${id}`);
}
