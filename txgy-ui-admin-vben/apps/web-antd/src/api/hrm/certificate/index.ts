import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

/** 核验状态（对齐后端 HrmCertificateVerifyStatusEnum） */
export const HrmCertificateVerifyStatus = {
  UNVERIFIED: 0, // 未核验
  VERIFIED: 1, // 已核验
  REJECTED: 2, // 核验不通过
} as const;

export const HrmCertificateVerifyStatusOptions = [
  { label: '未核验', value: HrmCertificateVerifyStatus.UNVERIFIED },
  { label: '已核验', value: HrmCertificateVerifyStatus.VERIFIED },
  { label: '核验不通过', value: HrmCertificateVerifyStatus.REJECTED },
] as const;

/** 到期状态（对齐后端 HrmCertificateExpireStatusEnum） */
export const HrmCertificateExpireStatus = {
  VALID: 1, // 有效
  EXPIRING: 2, // 临期
  EXPIRED: 3, // 已过期
} as const;

export const HrmCertificateExpireStatusOptions = [
  { label: '有效', value: HrmCertificateExpireStatus.VALID },
  { label: '临期', value: HrmCertificateExpireStatus.EXPIRING },
  { label: '已过期', value: HrmCertificateExpireStatus.EXPIRED },
] as const;

export namespace HrmCertificateTypeApi {
  /** 证照类型 */
  export interface CertificateType {
    id?: number;
    name?: string;
    code?: string;
    specialOperation?: boolean;
    mandatory?: boolean;
    needReview?: boolean;
    warnDays?: number;
    issuingAuthority?: string;
    sort?: number;
    status?: number;
    remark?: string;
    createTime?: Date;
    certificateCount?: number;
  }

  export interface CertificateTypePageParams extends PageParam {
    name?: string;
    specialOperation?: boolean;
    mandatory?: boolean;
    status?: number;
  }
}

export namespace HrmCertificateLedgerApi {
  /** 证照台账记录 */
  export interface Ledger {
    id?: number;
    employeeId?: number;
    employeeName?: string;
    jobNumber?: string;
    mobile?: string;
    typeId?: number;
    typeName?: string;
    specialOperation?: boolean;
    mandatory?: boolean;
    name?: string;
    level?: string;
    no?: string;
    issuingAuthority?: string;
    issuingTime?: Date;
    startTime?: Date;
    endTime?: Date;
    reviewDate?: string;
    expireStatus?: number;
    expireStatusName?: string;
    remainDays?: number;
    verifyStatus?: number;
    verifyStatusName?: string;
    verifyTime?: Date;
    fileUrls?: string[];
    remark?: string;
  }

  export interface LedgerPageParams extends PageParam {
    typeId?: number;
    employeeId?: number;
    certificateName?: string;
    verifyStatus?: number;
    expireStatus?: number;
    warnDays?: number;
  }
}

/** 查询证照类型分页 */
export function getCertificateTypePage(
  params: HrmCertificateTypeApi.CertificateTypePageParams,
) {
  return requestClient.get<PageResult<HrmCertificateTypeApi.CertificateType>>(
    '/hrm/certificate-type/page',
    { params },
  );
}

/** 查询证照类型精简列表（下拉用） */
export function getCertificateTypeSimpleList() {
  return requestClient.get<HrmCertificateTypeApi.CertificateType[]>(
    '/hrm/certificate-type/simple-list',
  );
}

/** 查询证照类型详情 */
export function getCertificateType(id: number) {
  return requestClient.get<HrmCertificateTypeApi.CertificateType>(
    '/hrm/certificate-type/get',
    { params: { id } },
  );
}

/** 新增证照类型 */
export function createCertificateType(
  data: HrmCertificateTypeApi.CertificateType,
) {
  return requestClient.post<number>('/hrm/certificate-type/create', data);
}

/** 修改证照类型 */
export function updateCertificateType(
  data: HrmCertificateTypeApi.CertificateType,
) {
  return requestClient.put<boolean>('/hrm/certificate-type/update', data);
}

/** 删除证照类型 */
export function deleteCertificateType(id: number) {
  return requestClient.delete<boolean>('/hrm/certificate-type/delete', {
    params: { id },
  });
}

/** 查询证照台账分页 */
export function getCertificateLedgerPage(
  params: HrmCertificateLedgerApi.LedgerPageParams,
) {
  return requestClient.get<PageResult<HrmCertificateLedgerApi.Ledger>>(
    '/hrm/employee/certificate/ledger/page',
    { params },
  );
}

/** 查询证照到期预警清单 */
export function getCertificateExpireWarning(warnDays?: number) {
  return requestClient.get<HrmCertificateLedgerApi.Ledger[]>(
    '/hrm/employee/certificate/ledger/expire-warning',
    { params: { warnDays } },
  );
}

/** 证照核验 */
export function verifyCertificate(id: number, verifyStatus: number) {
  return requestClient.put<boolean>('/hrm/employee/certificate/ledger/verify', null, {
    params: { id, verifyStatus },
  });
}

/** 导出证照台账 */
export function exportCertificateLedger(
  params: HrmCertificateLedgerApi.LedgerPageParams,
) {
  return requestClient.download(
    '/hrm/employee/certificate/ledger/export-excel',
    { params },
  );
}
