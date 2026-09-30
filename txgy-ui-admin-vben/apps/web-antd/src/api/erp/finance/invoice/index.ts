import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace ErpInvoiceApi {
  /** 发票信息 */
  export interface Invoice {
    id?: number; // 发票编号
    no?: string; // 发票单号
    invoiceType?: number; // 发票类型：1 增值税专用发票 2 增值税普通发票 3 电子发票
    invoiceNo?: string; // 发票号码
    invoiceCode?: string; // 发票代码
    title?: string; // 发票抬头
    bizType?: number; // 业务类型：1 采购进项 2 销售销项
    supplierId?: number; // 供应商编号
    supplierName?: string; // 供应商名称
    customerId?: number; // 客户编号
    customerName?: string; // 客户名称
    orderNo?: string; // 关联业务单号
    invoiceDate?: number; // 开票日期
    amount?: number; // 价税合计
    taxAmount?: number; // 税额
    taxRate?: number; // 税率
    status?: number; // 发票状态：0 待认证 1 已认证 2 已入账 3 已作废
    certifyTime?: number; // 认证时间
    fileUrl?: string; // 附件地址
    remark?: string; // 备注
    creator?: string; // 创建人编号
    creatorName?: string; // 创建人名称
    createTime?: Date; // 创建时间
  }
}

/** 查询发票分页 */
export function getInvoicePage(params: PageParam) {
  return requestClient.get<PageResult<ErpInvoiceApi.Invoice>>(
    '/erp/invoice/page',
    { params },
  );
}

/** 查询发票详情 */
export function getInvoice(id: number) {
  return requestClient.get<ErpInvoiceApi.Invoice>('/erp/invoice/get', {
    params: { id },
  });
}

/** 新增发票 */
export function createInvoice(data: ErpInvoiceApi.Invoice) {
  return requestClient.post('/erp/invoice/create', data);
}

/** 修改发票 */
export function updateInvoice(data: ErpInvoiceApi.Invoice) {
  return requestClient.put('/erp/invoice/update', data);
}

/** 认证发票（待认证 → 已认证） */
export function certifyInvoice(id: number) {
  return requestClient.put('/erp/invoice/certify', null, { params: { id } });
}

/** 作废发票（非已作废 → 已作废） */
export function voidInvoice(id: number) {
  return requestClient.put('/erp/invoice/void', null, { params: { id } });
}

/** 删除发票 */
export function deleteInvoice(ids: number[]) {
  return requestClient.delete('/erp/invoice/delete', {
    params: { ids: ids.join(',') },
  });
}
