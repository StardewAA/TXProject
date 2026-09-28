import { requestClient } from '#/api/request';

export namespace HrmContractExpireApi {
  /** 到期预警条目 */
  export interface ContractExpire {
    id?: number;
    no?: string;
    employeeId?: number;
    employeeName?: string;
    jobNumber?: string;
    type?: number;
    startTime?: string;
    endTime?: string;
    remainDays?: number;
    signCompany?: string;
    urgency?: number;
    urgencyName?: string;
  }
}

/** 劳动合同到期预警清单 */
export function getContractExpireWarningList(warnDays = 30) {
  return requestClient.get<HrmContractExpireApi.ContractExpire[]>(
    '/hrm/employee/contract/expire-warning',
    { params: { warnDays } },
  );
}
