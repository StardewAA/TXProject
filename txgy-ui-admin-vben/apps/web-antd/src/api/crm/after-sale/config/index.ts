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
    taskOverdueDays?: number;
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
