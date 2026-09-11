import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace CrmAfterSaleTicketApi {
  /** 售后问题工单 */
  export interface Ticket {
    id?: number;
    no?: string;
    title?: string;
    warrantyId?: number;
    customerId?: number;
    contractId?: number;
    visitRecordId?: number;
    source?: number;
    priority?: number;
    problemType?: string;
    description?: string;
    contactName?: string;
    contactMobile?: string;
    assigneeUserId?: number;
    status?: number;
    handleResult?: string;
    startTime?: string;
    finishTime?: string;
    closeTime?: string;
    isChargeable?: boolean;
    remark?: string;
    createTime?: string;
    /** 扩展：客户名称 */
    customerName?: string;
    /** 扩展：合同编号 */
    contractNo?: string;
    /** 扩展：质保编号 */
    warrantyNo?: string;
    /** 扩展：处理人昵称 */
    assigneeNickname?: string;
    /** 扩展：创建人昵称 */
    creatorNickname?: string;
  }

  /** 分页查询参数 */
  export interface PageReq extends PageParam, Partial<Ticket> {
    createTime?: string[];
  }
}

/** 查询问题工单分页 */
export function getAfterSaleTicketPage(
  params: CrmAfterSaleTicketApi.PageReq,
) {
  return requestClient.get<PageResult<CrmAfterSaleTicketApi.Ticket>>(
    '/crm/after-sale/ticket/page',
    { params },
  );
}

/** 查询问题工单详情 */
export function getAfterSaleTicket(id: number) {
  return requestClient.get<CrmAfterSaleTicketApi.Ticket>(
    `/crm/after-sale/ticket/get?id=${id}`,
  );
}

/** 新增问题工单 */
export function createAfterSaleTicket(data: CrmAfterSaleTicketApi.Ticket) {
  return requestClient.post('/crm/after-sale/ticket/create', data);
}

/** 修改问题工单 */
export function updateAfterSaleTicket(data: CrmAfterSaleTicketApi.Ticket) {
  return requestClient.put('/crm/after-sale/ticket/update', data);
}

/** 删除问题工单 */
export function deleteAfterSaleTicket(id: number) {
  return requestClient.delete(`/crm/after-sale/ticket/delete?id=${id}`);
}

/** 指派问题工单处理人 */
export function assignAfterSaleTicket(id: number, assigneeUserId: number) {
  return requestClient.post(
    `/crm/after-sale/ticket/assign?id=${id}&assigneeUserId=${assigneeUserId}`,
  );
}

/** 变更问题工单状态 */
export function updateAfterSaleTicketStatus(
  id: number,
  status: number,
  content?: string,
) {
  return requestClient.post(
    `/crm/after-sale/ticket/update-status?id=${id}&status=${status}&content=${encodeURIComponent(content ?? '')}`,
  );
}
