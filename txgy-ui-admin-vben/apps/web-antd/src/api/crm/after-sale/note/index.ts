import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace CrmAfterSaleTicketNoteApi {
  /** 工单流转记录 */
  export interface TicketNote {
    id?: number;
    ticketId?: number;
    operateType?: number;
    content?: string;
    operatorUserId?: number;
    createTime?: string;
    /** 扩展：操作人昵称 */
    operatorNickname?: string;
  }

  /** 分页查询参数 */
  export interface PageReq extends PageParam, Partial<TicketNote> {}
}

/** 查询工单流转记录分页 */
export function getAfterSaleTicketNotePage(
  params: CrmAfterSaleTicketNoteApi.PageReq,
) {
  return requestClient.get<PageResult<CrmAfterSaleTicketNoteApi.TicketNote>>(
    '/crm/after-sale/ticket-note/page',
    { params },
  );
}

/** 查询工单流转记录列表（时间正序，适合时间轴） */
export function getAfterSaleTicketNoteList(ticketId: number) {
  return requestClient.get<CrmAfterSaleTicketNoteApi.TicketNote[]>(
    `/crm/after-sale/ticket-note/list?ticketId=${ticketId}`,
  );
}
