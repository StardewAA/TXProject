import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace ErpPurchaseContractApi {
  /** 采购合同信息 */
  export interface PurchaseContract {
    id?: number; // 合同编号
    no?: string; // 合同单号
    contractName?: string; // 合同名称
    supplierId?: number; // 供应商编号
    supplierName?: string; // 供应商名称
    purchaseOrderId?: number; // 关联采购订单编号
    purchaseOrderNo?: string; // 关联采购订单号
    signDate?: number; // 签订日期
    effectiveDate?: number; // 生效日期
    expireDate?: number; // 到期日期
    totalAmount?: number; // 合同总金额
    taxRate?: number; // 税率
    paymentTerms?: string; // 付款条件
    deliveryPlace?: string; // 交货地点
    deliveryDeadline?: number;
    signPlace?: string;
    currency?: string;
    signerId?: number; // 签订人编号
    signerName?: string;
    qualityStandard?: string;
    acceptancePeriodDays?: number;
    warrantyPeriodMonths?: number;
    afterSaleResponseHours?: number;
    onSiteServiceHours?: number;
    maxRepairTimes?: number;
    advancePaymentRatio?: number;
    advancePaymentAmount?: number;
    advancePaymentDays?: number;
    acceptancePaymentRatio?: number;
    acceptancePaymentAmount?: number;
    acceptancePaymentDays?: number;
    warrantyRetentionRatio?: number;
    warrantyRetentionAmount?: number;
    warrantyReleaseDays?: number;
    warrantyReleaseCondition?: string;
    buyerRejectPenaltyRatio?: number;
    buyerOverdueRate?: number;
    buyerTerminationDays?: number;
    sellerQualityPenaltyRatio?: number;
    sellerOverdueRate?: number;
    sellerTerminationDays?: number;
    sellerTerminationCompensationRatio?: number;
    ipDefectPenaltyRatio?: number;
    buyerName?: string;
    buyerLegalRepresentative?: string;
    buyerAddress?: string;
    buyerBankName?: string;
    buyerBankAccount?: string;
    buyerPhone?: string;
    buyerFax?: string;
    supplierNameSnapshot?: string;
    sellerLegalRepresentative?: string;
    sellerAddress?: string;
    sellerBankName?: string;
    sellerBankAccount?: string;
    sellerPhone?: string;
    sellerFax?: string;
    templateId?: number;
    templateName?: string;
    templateVersion?: string;
    legalTerms?: string;
    status?: number; // 合同状态
    reverseApproveReason?: string; // 反审批原因
    fileUrl?: string; // 附件地址
    remark?: string; // 备注
    creator?: string; // 创建人编号
    creatorName?: string; // 创建人名称
    createTime?: Date; // 创建时间
  }

  export interface ContractTemplate {
    id?: number;
    name?: string;
    version?: string;
    content?: string;
    defaultFlag?: boolean;
    status?: number;
    remark?: string;
  }
}

/** 查询采购合同分页 */
export function getPurchaseContractPage(params: PageParam) {
  return requestClient.get<
    PageResult<ErpPurchaseContractApi.PurchaseContract>
  >('/erp/purchase-contract/page', { params });
}

/** 查询采购合同详情 */
export function getPurchaseContract(id: number) {
  return requestClient.get<ErpPurchaseContractApi.PurchaseContract>(
    '/erp/purchase-contract/get',
    { params: { id } },
  );
}

/** 从已审批采购订单初始化合同草稿 */
export function initPurchaseContractFromOrder(orderId: number) {
  return requestClient.get<ErpPurchaseContractApi.PurchaseContract>(
    '/erp/purchase-contract/init-from-order',
    { params: { orderId } },
  );
}

/** 导出采购合同 PDF */
export function exportPurchaseContractPdf(id: number) {
  return requestClient.download('/erp/purchase-contract/export-pdf', {
    params: { id },
  });
}

export function getPurchaseContractTemplateList() {
  return requestClient.get<ErpPurchaseContractApi.ContractTemplate[]>(
    '/erp/purchase-contract-template/list',
  );
}

export function createPurchaseContractTemplate(
  data: ErpPurchaseContractApi.ContractTemplate,
) {
  return requestClient.post('/erp/purchase-contract-template/create', data);
}

export function updatePurchaseContractTemplate(
  data: ErpPurchaseContractApi.ContractTemplate,
) {
  return requestClient.put('/erp/purchase-contract-template/update', data);
}

export function deletePurchaseContractTemplate(id: number) {
  return requestClient.delete('/erp/purchase-contract-template/delete', {
    params: { id },
  });
}

/** 新增采购合同 */
export function createPurchaseContract(
  data: ErpPurchaseContractApi.PurchaseContract,
) {
  return requestClient.post('/erp/purchase-contract/create', data);
}

/** 修改采购合同 */
export function updatePurchaseContract(
  data: ErpPurchaseContractApi.PurchaseContract,
) {
  return requestClient.put('/erp/purchase-contract/update', data);
}

/** 提交采购合同审批 */
export function submitPurchaseContract(id: number) {
  return requestClient.put('/erp/purchase-contract/submit', null, {
    params: { id },
  });
}

/** 审批通过采购合同 */
export function approvePurchaseContract(id: number) {
  return requestClient.put('/erp/purchase-contract/approve', null, {
    params: { id },
  });
}

/** 驳回采购合同 */
export function rejectPurchaseContract(id: number) {
  return requestClient.put('/erp/purchase-contract/reject', null, {
    params: { id },
  });
}

/** 删除采购合同 */
export function deletePurchaseContract(ids: number[]) {
  return requestClient.delete('/erp/purchase-contract/delete', {
    params: { ids: ids.join(',') },
  });
}
