import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace HrmCertificateWarningApi {
  /** 证件预警 */
  export interface CertificateWarning {
    id?: number; // 预警记录编号
    certificateId?: number; // 证件台账编号
    certificateName?: string; // 证件名称
    certificateNo?: string; // 证件编号
    certificateTypeName?: string; // 证件类型名称
    employeeId?: number; // 员工编号
    employeeName?: string; // 员工姓名
    employeeJobNumber?: string; // 员工工号
    deptId?: number; // 部门编号
    type?: number; // 预警类型，1 证件到期，2 证件复审
    warningDate?: string; // 预警触发日期
    expireDate?: number; // 到期日期或复审日期
    remainDays?: number; // 剩余天数，负数表示已过期
    status?: number; // 处理状态，0 待处理，1 已处理，2 已忽略
    handleUserId?: number; // 处理人用户编号
    handleTime?: number; // 处理时间
    handleRemark?: string; // 处理意见
    notifyStatus?: number; // 通知状态，0 待通知，1 已通知，2 通知失败
    notifyTime?: number; // 通知时间
    notifyFailReason?: string; // 通知失败原因
    notifyRetryCount?: number; // 通知重试次数
    createTime?: Date; // 创建时间
  }

  /** 预警处理 */
  export interface HandleReq {
    id: number; // 预警记录编号
    status: number; // 目标状态：已处理(1)、已忽略(2)、重新启用为待处理(0)
    handleRemark?: string; // 处理意见
  }
}

/** 查询证件预警分页 */
export function getCertificateWarningPage(params: PageParam) {
  return requestClient.get<
    PageResult<HrmCertificateWarningApi.CertificateWarning>
  >('/hrm/certificate-warning/page', { params });
}

/** 处理证件预警 */
export function handleCertificateWarning(
  data: HrmCertificateWarningApi.HandleReq,
) {
  return requestClient.put<boolean>('/hrm/certificate-warning/handle', data);
}

/** 查询待处理预警数量 */
export function getCertificateWarningPendingCount() {
  return requestClient.get<number>('/hrm/certificate-warning/pending-count');
}
