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
    /**
     * 开始处理时间。
     * 后端 LocalDateTime 走「毫秒时间戳」反序列化（传日期字符串会被解析成 epoch 0），
     * 因此详情返回毫秒数字、提交时也应传毫秒数字；
     * 表单内部用 'YYYY-MM-DD HH:mm:ss' 字符串，提交前由 form.vue 统一转换。
     */
    startTime?: number | string;
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
    /** 提醒类型：1=工单超时预警（待办使用，实时查询且限本人及下属负责） */
    remindType?: number;
  }
}

/** 提醒类型：1=工单超时预警（待办「工单超时预警」使用，与后端 REMIND_TYPE_OVERDUE 保持一致） */
export const AFTER_SALE_TICKET_REMIND_TYPE_OVERDUE = 1;

/** 获得「工单超时预警」待办数量（实时查询，不落库） */
export function getAfterSaleTicketOverdueCount() {
  return requestClient.get<number>('/crm/after-sale/ticket/remind-count');
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
