import type { PageParam, PageResult } from '@vben/request';

import type { CrmPermissionApi } from '#/api/crm/permission';

import { requestClient } from '#/api/request';

export namespace CrmContractApi {
  /** 合同信息 */
  export interface Contract {
    id: number;
    name: string;
    no: string;
    customerId: number;
    customerName?: string;
    businessId: number;
    businessName: string;
    contactLastTime: Date;
    ownerUserId: number;
    ownerUserName?: string;
    ownerUserDeptName?: string;
    processInstanceId: number;
    auditStatus: number;
    orderDate: Date;
    startTime: Date;
    endTime: Date;
    totalProductPrice: number;
    discountPercent: number;
    totalPrice: number;
    totalReceivablePrice: number;
    signContactId: number;
    signContactName?: string;
    signUserId: number;
    signUserName: string;
    remark: string;
    // ==================== 合同模板相关字段 ====================
    /** 合同模板类型：1=采购合同，2=购销合同 */
    templateType?: number;
    signPlace?: string;
    signDate?: Date;
    projectNo?: string;
    projectName?: string;
    projectArea?: string;
    attachmentCopies?: number;
    attachmentPages?: number;
    contractCopies?: number;
    gpcName?: string;
    /** 质量、交货与验收 */
    qualityStandard?: string;
    sampleDays?: number;
    deliveryDays?: number;
    deliveryArriveDays?: number;
    installDays?: number;
    deliveryDeadline?: Date;
    firstAcceptDays?: number;
    trialDays?: number;
    finalAcceptDays?: number;
    autoAcceptDays?: number;
    maxRepairTimes?: number;
    /** 付款方式 */
    prepayAmount?: number;
    prepayDays?: number;
    prepayRatio?: number;
    acceptPayDays?: number;
    acceptPayRatio?: number;
    acceptPayAmount?: number;
    depositMonths?: number;
    depositDays?: number;
    depositAmount?: number;
    depositRatio?: number;
    /** 售后服务 */
    warrantyYears?: number;
    responseHours?: number;
    repairHours?: number;
    repairTimes?: number;
    reportRepairDays?: number;
    /** 甲方（采购人）信息 */
    partyAName?: string;
    partyALegalPerson?: string;
    partyAAgent?: string;
    partyAAddress?: string;
    partyABankName?: string;
    partyABankAccount?: string;
    partyATel?: string;
    partyAFax?: string;
    /** 乙方（供应商）信息 */
    partyBName?: string;
    partyBLegalPerson?: string;
    partyBAgent?: string;
    partyBAddress?: string;
    partyBBankName?: string;
    partyBBankAccount?: string;
    partyBTel?: string;
    partyBFax?: string;
    /** 违约责任条款（JSON） */
    penaltyTerms?: string;
    createTime?: Date;
    creator: string;
    creatorName: string;
    updateTime?: Date;
    products?: ContractProduct[];
    contactName?: string;
  }

  /** 合同产品信息 */
  export interface ContractProduct {
    id: number;
    productId: number;
    productName: string;
    productNo: string;
    productUnit: number;
    productPrice: number;
    contractPrice: number;
    count: number;
    totalPrice: number;
    /** 合同模板「一、合同货物」表格字段 */
    spec?: string;
    unit?: string;
    /** 资金来源：1=预算内，2=预算外，3=其他 */
    fundSource?: number;
    accessories?: string;
    deliveryPeriod?: string;
  }
}

/** 查询合同列表 */
export function getContractPage(params: PageParam) {
  return requestClient.get<PageResult<CrmContractApi.Contract>>(
    '/crm/contract/page',
    { params },
  );
}

/** 查询合同列表，基于指定客户 */
export function getContractPageByCustomer(params: PageParam) {
  return requestClient.get<PageResult<CrmContractApi.Contract>>(
    '/crm/contract/page-by-customer',
    { params },
  );
}

/** 查询合同列表，基于指定商机 */
export function getContractPageByBusiness(params: PageParam) {
  return requestClient.get<PageResult<CrmContractApi.Contract>>(
    '/crm/contract/page-by-business',
    { params },
  );
}

/** 查询合同详情 */
export function getContract(id: number) {
  return requestClient.get<CrmContractApi.Contract>(
    `/crm/contract/get?id=${id}`,
  );
}

/** 查询合同下拉列表 */
export function getContractSimpleList(customerId: number) {
  return requestClient.get<CrmContractApi.Contract[]>(
    `/crm/contract/simple-list?customerId=${customerId}`,
  );
}

/** 新增合同 */
export function createContract(data: CrmContractApi.Contract) {
  return requestClient.post('/crm/contract/create', data);
}

/** 修改合同 */
export function updateContract(data: CrmContractApi.Contract) {
  return requestClient.put('/crm/contract/update', data);
}

/** 删除合同 */
export function deleteContract(id: number) {
  return requestClient.delete(`/crm/contract/delete?id=${id}`);
}

/** 导出合同 */
export function exportContract(params: any) {
  return requestClient.download('/crm/contract/export-excel', { params });
}

/** 生成合同 Word 文档 */
export function exportContractWord(id: number) {
  return requestClient.download(`/crm/contract/export-word?id=${id}`);
}

/** 提交审核 */
export function submitContract(id: number) {
  return requestClient.put(`/crm/contract/submit?id=${id}`);
}

/** 合同转移 */
export function transferContract(data: CrmPermissionApi.BusinessTransferReqVO) {
  return requestClient.put('/crm/contract/transfer', data);
}

/** 获得待审核合同数量 */
export function getAuditContractCount() {
  return requestClient.get<number>('/crm/contract/audit-count');
}

/** 获得即将到期（提醒）的合同数量 */
export function getRemindContractCount() {
  return requestClient.get<number>('/crm/contract/remind-count');
}
