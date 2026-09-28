import { requestClient } from '#/api/request';

export namespace CrmAfterSaleConfigApi {
  /** 售后配置（单行） */
  export interface AfterSaleConfig {
    id?: number;
    warrantyWarnDays?: number;
    autoVisitEnabled?: boolean;
    autoVisitCycleType?: number;
    autoVisitCycleValue?: number;
    taskRemindDays?: number;
    /** 回访任务逾期判定天数（窗口期天数：窗口期截止日期 = 计划回访日期 + 本值） */
    taskOverdueDays?: number;
    /** 是否允许提前提交回访记录：false=不允许（未到计划回访日期禁止提交） */
    allowEarlyVisit?: boolean;
    ticketOverdueDays?: number;
  }
}

/** 查询售后配置 */
export function getAfterSaleConfig() {
  return requestClient.get<CrmAfterSaleConfigApi.AfterSaleConfig>(
    '/crm/after-sale/config/get',
  );
}

/** 修改售后配置 */
export function updateAfterSaleConfig(
  data: CrmAfterSaleConfigApi.AfterSaleConfig,
) {
  return requestClient.put('/crm/after-sale/config/update', data);
}
