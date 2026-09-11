import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

/** 契约来源：《电子证照模块-前端接口契约-v1.0.md》§8 员工证书（证照域延伸：人证合一）
 *  + 后端 PersonnelCertificateController / PersonnelAlertRunController 逐一核对
 *  权限：bidding:personnel:cert:{query,create,update,delete}、bidding:personnel:alert-run
 *  说明：员工档案（profile）与履历（培训/社保/劳动合同）由「员工资质」另一份契约覆盖，本文件只接证书相关端点。 */
export namespace PersonnelCertApi {
  export interface Cert {
    id: number;
    entId?: number;
    /** 持证员工主键 */
    personId?: number;
    personName?: string;
    certType?: string;
    certName?: string;
    /** 精确匹配用 */
    certNo?: string;
    certLevel?: string;
    /** 证书细分：安许 A/B/C、特种设备 G1/G2/G3、C1/C2/C3（安许配备统计依赖它） */
    certSubType?: string;
    /** 下次复审日（与到期日独立：司炉/ISO 等"到期前就已失效"） */
    nextReviewDate?: string;
    reviewRemainingDays?: number;
    major?: string;
    eduLevel?: string;
    issuingAuthority?: string;
    issueDate?: string;
    validFrom?: string;
    validTo?: string;
    /** 剩余天数（后端算好，前端不自己算阈值天数口径） */
    remainingDays?: number;
    /** 注册单位/注册编号（人证合一核查） */
    registerUnit?: string;
    registerUnitCode?: string;
    registerNo?: string;
    certStatus?: string;
    platformVerified?: boolean;
    verifyUrl?: string;
    verifiedAt?: string;
    bindedCertId?: number;
    fileId?: number;
    sourceChannel?: string;
    remark?: string;
    version?: number;
  }

  export interface PageReq extends PageParam {
    personId?: number;
    entId?: number;
    /** 字典 person_cert_type */
    certType?: string;
    certNo?: string;
    /** 字典 person_cert_status */
    certStatus?: string;
    major?: string;
    /** 到期窗口：仅返回 validTo ∈ [今天, 今天+N] */
    expiringDays?: number;
  }

  export interface SaveReq {
    id?: number;
    entId?: number;
    personId?: number;
    certType?: string;
    certName?: string;
    certNo?: string;
    certLevel?: string;
    certSubType?: string;
    nextReviewDate?: string;
    major?: string;
    eduLevel?: string;
    issuingAuthority?: string;
    issueDate?: string;
    validFrom?: string;
    validTo?: string;
    registerUnit?: string;
    registerUnitCode?: string;
    registerNo?: string;
    platformVerified?: boolean;
    verifyUrl?: string;
    bindedCertId?: number;
    fileId?: number;
    extraProps?: string;
    remark?: string;
    version?: number;
  }

  export interface StatusChangeReq {
    id: number;
    targetStatus: string;
    version: number;
    reason?: string;
  }
}

/** 员工证书分页（支持到期窗口 expiringDays） */
export function getPersonnelCertPage(params: PersonnelCertApi.PageReq) {
  return requestClient.get<PageResult<PersonnelCertApi.Cert>>('/bidding/personnel-certificate/page', { params });
}
/** 某员工的全部在册证书 */
export function getPersonnelCertByPerson(personId: number | string) {
  return requestClient.get<PersonnelCertApi.Cert[]>('/bidding/personnel-certificate/list-by-person', {
    params: { personId },
  });
}
/** 到期清单（today ~ today+days，默认 30；建议 days 取 §1.2 配置值） */
export function getPersonnelCertExpiring(entId?: number | string, days?: number) {
  return requestClient.get<PersonnelCertApi.Cert[]>('/bidding/personnel-certificate/list-expiring', {
    params: { entId, days },
  });
}
/** 按证书细分统计可用持证人数（安许 A/B/C 配备自查）；返回 { 细分code: 人数 } */
export function countPersonnelCertBySubType(entId: number | string, certType: string) {
  return requestClient.get<Record<string, number>>('/bidding/personnel-certificate/count-by-sub-type', {
    params: { entId, certType },
  });
}
/** 法定预警下限（key = 证照类别 code，value = 法定下限天数） */
export function getPersonnelLegalFloorDays() {
  return requestClient.get<Record<string, number>>('/bidding/personnel-alert-run/legal-floor-days');
}
/** 员工证书登记 */
export function createPersonnelCert(data: PersonnelCertApi.SaveReq) {
  return requestClient.post<number>('/bidding/personnel-certificate/create', data);
}
/** 员工证书更新（乐观锁 version） */
export function updatePersonnelCert(data: PersonnelCertApi.SaveReq) {
  return requestClient.put<boolean>('/bidding/personnel-certificate/update', data);
}
/** 员工证书状态流转（吊销/注销/恢复） */
export function changePersonnelCertStatus(data: PersonnelCertApi.StatusChangeReq) {
  return requestClient.put<boolean>('/bidding/personnel-certificate/change-status', data);
}
/** 删除员工证书（软删） */
export function deletePersonnelCert(id: number | string) {
  return requestClient.delete<boolean>('/bidding/personnel-certificate/delete', { params: { id } });
}
/** 手动触发员工证书/复审/身份证到期扫描（同日幂等） */
export function runPersonnelAlertScan() {
  return requestClient.post<number>('/bidding/personnel-alert-run/scan');
}
