import { requestClient } from '#/api/request';

export namespace HrmSalaryRateLimitApi {
  /** 比例上限配置（按计薪项/参保项） */
  export interface RateLimit {
    id?: number;
    /** 业务类型：1 考勤计薪规则 2 参保方案 */
    bizType?: number;
    /** 计薪项/参保项编码 */
    itemCode?: string;
    /** 计薪项/参保项名称 */
    itemName?: string;
    /** 比例上限（%），考勤计薪项使用 */
    rateLimit?: number;
    /** 公司缴纳比例上限（%），参保项使用 */
    corporateRateLimit?: number;
    /** 个人缴纳比例上限（%），参保项使用 */
    personalRateLimit?: number;
    /** 状态：0 启用 1 停用（停用表示不做上限校验） */
    status?: number;
    remark?: string;
  }
}

/** 比例上限的业务类型 */
export const HrmSalaryRateLimitBizType = {
  /** 考勤计薪规则 */
  ATTENDANCE_RULE: 1,
  /** 参保方案 */
  INSURANCE_SCHEME: 2,
} as const;

export function getSalaryRateLimitList(bizType?: number) {
  return requestClient.get<HrmSalaryRateLimitApi.RateLimit[]>(
    '/hrm/salary/rate-limit/list',
    { params: { bizType } },
  );
}

export function updateSalaryRateLimit(
  data: HrmSalaryRateLimitApi.RateLimit,
) {
  return requestClient.put<boolean>('/hrm/salary/rate-limit/update', data);
}
