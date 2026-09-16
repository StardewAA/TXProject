import { requestClient } from '#/api/request';

export namespace CrmContractConfigApi {
  /** 合同配置信息 */
  export interface Config {
    notifyEnabled?: boolean;
    notifyDays?: number;
    // ==================== 我方主体信息 ====================
    /** 我方公司名称 */
    ourCompanyName?: string;
    /** 我方法定代表人 */
    ourLegalPerson?: string;
    /** 我方授权代表 */
    ourAgent?: string;
    /** 我方地址 */
    ourAddress?: string;
    /** 我方开户银行 */
    ourBankName?: string;
    /** 我方银行账号 */
    ourBankAccount?: string;
    /** 我方电话 */
    ourTel?: string;
    /** 我方传真 */
    ourFax?: string;
    // ==================== 条款默认值 ====================
    defaultGpcName?: string;
    defaultQualityStandard?: string;
    defaultSampleDays?: number;
    defaultDeliveryDays?: number;
    defaultDeliveryArriveDays?: number;
    defaultInstallDays?: number;
    defaultFirstAcceptDays?: number;
    defaultTrialDays?: number;
    defaultFinalAcceptDays?: number;
    defaultAutoAcceptDays?: number;
    defaultMaxRepairTimes?: number;
    defaultContractCopies?: number;
    defaultPrepayRatio?: number;
    defaultPrepayDays?: number;
    defaultAcceptPayRatio?: number;
    defaultAcceptPayDays?: number;
    defaultDepositRatio?: number;
    defaultDepositMonths?: number;
    defaultDepositDays?: number;
    defaultWarrantyYears?: number;
    defaultResponseHours?: number;
    defaultRepairHours?: number;
    defaultRepairTimes?: number;
    defaultReportRepairDays?: number;
    // ==================== 采购合同违约责任 ====================
    pBuyerRejectRatio?: number;
    pBuyerOverdueWan?: number;
    pBuyerOverdueDays?: number;
    pSellerQuality?: number;
    pSellerOverdueWan?: number;
    pSellerOverdueDays?: number;
    pSellerCompensate?: number;
    pSellerReplaceDays?: number;
    pSellerTerminate?: number;
    pSellerDefect?: number;
    // ==================== 购销合同违约责任 ====================
    sBuyerLateRatio?: number;
    sBuyerBreachRatio?: number;
    sSellerLateRatio?: number;
    sSellerBreachRatio?: number;
  }
}

/** 获取合同配置 */
export function getContractConfig() {
  return requestClient.get<CrmContractConfigApi.Config>(
    '/crm/contract-config/get',
  );
}

/** 更新合同配置 */
export function saveContractConfig(data: CrmContractConfigApi.Config) {
  return requestClient.put('/crm/contract-config/save', data);
}
