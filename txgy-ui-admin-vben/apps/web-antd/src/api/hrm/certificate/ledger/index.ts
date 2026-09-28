import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace HrmCertificateLedgerApi {
  /** 证件台账 */
  export interface CertificateLedger {
    id?: number; // 证件台账编号
    employeeId?: number; // 员工编号
    employeeName?: string; // 员工姓名
    employeeJobNumber?: string; // 员工工号
    deptId?: number; // 部门编号
    typeId?: number; // 证件类型编号
    typeName?: string; // 证件类型名称
    specialOperation?: boolean; // 是否特种作业
    name?: string; // 证件名称
    level?: string; // 证件级别
    no?: string; // 证件编号
    startTime?: number; // 有效开始日期
    endTime?: number; // 有效结束日期
    reviewTime?: number; // 复审日期
    issuingAuthority?: string; // 发证机构
    issuingTime?: number; // 发证日期
    attachmentUrl?: string; // 证件附件地址
    status?: number; // 证件状态，参见 hrm_certificate_status
    remainDays?: number; // 距到期剩余天数，负数表示已过期
    pendingWarningCount?: number; // 待处理预警数量
    remark?: string; // 备注
    sort?: number; // 显示顺序
    createTime?: Date; // 创建时间
  }

  /** 证件状态变更 */
  export interface StatusReq {
    id: number; // 证件台账编号
    status: number; // 目标状态：注销(4)、恢复为有效(1)
  }

  /** 续证/复审办理 */
  export interface RenewalCreateReq {
    type: number; // 1 续证，2 复审
    newNo?: string; // 办理后证件编号
    newStartTime?: number; // 办理后有效开始日期
    newEndTime?: number; // 办理后有效结束日期
    reviewTime?: number; // 复审日期，复审类型必填
    issuingAuthority?: string; // 发证机构
    issuingTime?: number; // 发证日期
    attachmentUrl?: string; // 新证件附件地址
    remark?: string; // 备注
  }

  /** 续证/复审历史 */
  export interface CertificateRenewal {
    id?: number; // 续证/复审记录编号
    certificateId?: number; // 证件台账编号
    employeeId?: number; // 员工编号
    type?: number; // 1 续证，2 复审
    oldNo?: string; // 办理前证件编号
    newNo?: string; // 办理后证件编号
    oldStartTime?: number; // 办理前有效开始日期
    oldEndTime?: number; // 办理前有效结束日期
    newStartTime?: number; // 办理后有效开始日期
    newEndTime?: number; // 办理后有效结束日期
    reviewTime?: number; // 复审日期
    issuingAuthority?: string; // 发证机构
    issuingTime?: number; // 发证日期
    attachmentUrl?: string; // 新证件附件地址
    operatorUserId?: number; // 经办人用户编号
    renewTime?: number; // 办理时间
    remark?: string; // 备注
    createTime?: Date; // 创建时间
  }
}

/** 查询证件台账分页 */
export function getCertificateLedgerPage(params: PageParam) {
  return requestClient.get<PageResult<HrmCertificateLedgerApi.CertificateLedger>>(
    '/hrm/certificate/page',
    { params },
  );
}

/** 查询证件台账详情 */
export function getCertificateLedger(id: number) {
  return requestClient.get<HrmCertificateLedgerApi.CertificateLedger>(
    `/hrm/certificate/get?id=${id}`,
  );
}

/** 新增证件台账 */
export function createCertificateLedger(
  data: HrmCertificateLedgerApi.CertificateLedger,
) {
  return requestClient.post<number>('/hrm/certificate/create', data);
}

/** 修改证件台账 */
export function updateCertificateLedger(
  data: HrmCertificateLedgerApi.CertificateLedger,
) {
  return requestClient.put<boolean>('/hrm/certificate/update', data);
}

/** 修改证件状态（注销 / 恢复） */
export function updateCertificateLedgerStatus(
  data: HrmCertificateLedgerApi.StatusReq,
) {
  return requestClient.put<boolean>('/hrm/certificate/update-status', data);
}

/** 删除证件台账 */
export function deleteCertificateLedger(id: number) {
  return requestClient.delete<boolean>('/hrm/certificate/delete', {
    params: { id },
  });
}

/** 办理证件续证/复审 */
export function createCertificateRenewal(
  id: number,
  data: HrmCertificateLedgerApi.RenewalCreateReq,
) {
  return requestClient.post<number>(`/hrm/certificate/${id}/renewals`, data);
}

/** 查询证件的续证/复审历史 */
export function getCertificateRenewalList(certificateId: number) {
  return requestClient.get<HrmCertificateLedgerApi.CertificateRenewal[]>(
    '/hrm/certificate/renewal/list',
    { params: { certificateId } },
  );
}
