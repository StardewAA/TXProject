import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

/** 契约来源：《电子证照模块-前端接口契约-v1.0.md》§8 员工证书（证照域延伸：人证合一）
 *  + 后端 PersonnelCertificateController / PersonnelAlertRunController 逐一核对
 *  权限：bidding:personnel:cert:{query,create,update,delete}、bidding:personnel:alert-run
 *  说明：员工档案（profile）与履历（培训/社保/劳动合同）于 2026-09-20 后端接口就绪后一并接入，
 *  见本文件末尾 PersonnelProfileApi（端点 PersonnelProfileController / PersonnelRecordController）。 */
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

  /** 人员证书建档/编辑入参（POST /bidding/personnel-certificate/create、update）；契约来源：PersonnelCertificateSaveReqVO */
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

  /** 人员证书状态变更入参；契约来源：PersonnelCertificateStatusChangeReqVO */
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

/**
 * ===== 员工档案（profile）与履历（培训 / 社保 / 劳动合同） =====
 * 端点：PersonnelProfileController（/bidding/personnel-profile）与 PersonnelRecordController（/bidding/personnel-record）
 * 权限：bidding:personnel:profile:{query,create,update}、bidding:personnel:record:{query,create}
 * 口径：idCard 只写不读——列表/详情回传的是 idCardMasked（脱敏），故类型里没有 idCard 字段。
 */
export namespace PersonnelProfileApi {
  /** 员工档案保存（PersonnelProfileSaveReqVO；id 为空=新建，否则更新） */
  export interface SaveReq {
    id?: number;
    entId: number;
    personNo?: string;
    personName: string;
    /** 身份证号（仅提交，回读为脱敏值） */
    idCard?: string;
    /** LocalDate */
    idCardValidTo?: null | string;
    /** 字典 gender */
    gender?: string;
    /** LocalDate */
    birthDate?: null | string;
    mobile?: string;
    email?: string;
    /** 字典 edu_level */
    eduLevel?: string;
    eduMajor?: string;
    school?: string;
    /** LocalDate */
    graduateDate?: null | string;
    titleLevel?: string;
    titleMajor?: string;
    /** LocalDate */
    workStartDate?: null | string;
    /** LocalDate */
    joinDate?: null | string;
    deptName?: string;
    /** 字典 employment_type（PersonnelEmploymentTypeEnum：FULL_TIME/PART_TIME/LABOR_DISPATCH/RETIRED_REHIRE/OTHER） */
    employmentType?: string;
    remark?: string;
    /**
     * 岗位名称（Profile 出参已有同名字段，SaveReq 此前未声明 → 界面上填不了、存不下）。
     * 2026-09-25 由"类型对等"门禁发现（PersonnelProfileSaveReqVO ← SaveReq 缺 4 字段）。
     */
    positionName?: string;
    /** 人事档案号（同上，Profile 出参有、SaveReq 缺） */
    archiveNo?: string;
    /**
     * 关联平台登录用户 id（员工自助维护本人证书时使用，可空）——**只能写、读不回**：
     * PersonnelProfileRespVO 未下发该字段，故编辑时无法回填；要接 UI 需另找"可选登录用户"数据源（人员选择器），
     * 当前仅按类型对等门禁补齐声明。 */
    bindedAuthUserId?: number;
    /** 乐观锁版本（更新时回传防并发覆盖）：Profile 出参带回，编辑时原样提交；不传则并发更新可能被后端拒绝 */
    version?: number;
  }

  /** 员工档案（PersonnelProfileRespVO） */
  export interface Profile {
    id: number;
    tenantId?: number;
    entId?: number;
    personNo?: string;
    personName?: string;
    /** 脱敏身份证号 */
    idCardMasked?: string;
    idCardValidTo?: null | string;
    gender?: null | string;
    birthDate?: null | string;
    mobile?: string;
    email?: string;
    eduLevel?: null | string;
    eduMajor?: string;
    school?: string;
    graduateDate?: null | string;
    titleLevel?: null | string;
    titleMajor?: string;
    workStartDate?: null | string;
    joinDate?: null | string;
    deptName?: string;
    /** 岗位名称 */
    positionName?: string;
    /** 字典 employment_type（PersonnelEmploymentTypeEnum） */
    employmentType?: null | string;
    /** 是否本单位在职（后端口径：仅 FULL_TIME 为真；null = 未评估） */
    isFullTime?: boolean;
    /** 字典 personnel_status */
    personnelStatus?: string;
    /** 最近状态变更日期 LocalDate → 'YYYY-MM-DD' */
    statusChangeDate?: null | string;
    /** 人事档案号 */
    archiveNo?: string;
    remark?: string;
    /** 在册证书数量（注意与 Overview.totalCertCount 的区别：本字段来自档案出参） */
    certCount?: number;
    /** 可用（有效/临近到期）证书数量 */
    usableCertCount?: number;
    /** 身份证是否在有效期内 */
    idCardValid?: boolean;
    /** 乐观锁版本（状态流转必传） */
    version?: number;
  }

  /** 员工档案分页入参（PersonnelProfilePageReqVO） */
  export interface PageReq extends PageParam {
    entId?: number;
    personNo?: string;
    personName?: string;
    deptName?: string;
    personnelStatus?: string;
    eduLevel?: string;
    titleLevel?: string;
    certType?: string;
  }

  /** 状态流转（PersonnelProfileStatusChangeReqVO）：迁移表由 PersonnelStatusEnum 校验 */
  export interface StatusChangeReq {
    id: number;
    targetStatus: string;
    version: number;
    reason?: string;
  }

  /** 履职概览（PersonnelOverviewRespVO）：证书/社保/合同/学时与风险项汇总 */
  export interface Overview {
    profile?: Profile;
    idCardValid?: boolean;
    usableCertCount?: number;
    totalCertCount?: number;
    certificates?: PersonnelCertApi.Cert[];
    latestSocialSecurityMonth?: null | string;
    latestSocialSecurityStatus?: null | string;
    latestSocialSecurityPayer?: null | string;
    socialSecurityConsistent?: boolean;
    laborContractValid?: boolean;
    laborContractConsistent?: boolean;
    totalEduHours?: null | string;
    risks?: string[];
    usableForTender?: boolean;
  }

  /** 培训记录保存（PersonnelTrainingSaveReqVO） */
  export interface TrainingSaveReq {
    entId: number;
    personId: number;
    trainingName: string;
    trainingType?: string;
    organizer?: string;
    /** LocalDate */
    startDate?: null | string;
    /** LocalDate */
    endDate?: null | string;
    eduHours?: null | number;
    result?: string;
    score?: null | number;
    certNo?: string;
    /** LocalDate */
    certValidTo?: null | string;
    fileId?: null | number;
    remark?: string;
  }

  export interface Training {
    id: number;
    entId?: number;
    personId?: number;
    trainingName?: string;
    trainingType?: string;
    organizer?: string;
    startDate?: null | string;
    endDate?: null | string;
    eduHours?: null | number;
    result?: string;
    score?: null | number;
    certNo?: string;
    certValidTo?: null | string;
    fileId?: null | number;
    remark?: string;
  }

  /** 社保记录保存（PersonnelSocialSecuritySaveReqVO） */
  export interface SocialSecuritySaveReq {
    entId: number;
    personId: number;
    payerName?: string;
    payerCreditCode?: string;
    /** yyyy-MM */
    payMonth: string;
    payStatus?: string;
    payBase?: null | number;
    payAmount?: null | number;
    socialNo?: string;
    sourceChannel?: string;
    evidenceFileId?: null | number;
    remark?: string;
  }

  export interface SocialSecurity {
    id: number;
    entId?: number;
    personId?: number;
    payerName?: string;
    payerCreditCode?: string;
    payMonth?: string;
    payStatus?: string;
    payBase?: null | number;
    payAmount?: null | number;
    socialNo?: string;
    sourceChannel?: string;
    evidenceFileId?: null | number;
    remark?: string;
  }

  /** 劳动合同保存（PersonnelLaborContractSaveReqVO） */
  export interface LaborContractSaveReq {
    entId: number;
    personId: number;
    contractNo: string;
    contractType?: string;
    /** LocalDate */
    signDate?: null | string;
    /** LocalDate */
    startDate?: null | string;
    /** LocalDate */
    endDate?: null | string;
    contractStatus?: string;
    positionName?: string;
    fileId?: null | number;
    remark?: string;
  }

  export interface LaborContract {
    id: number;
    entId?: number;
    personId?: number;
    contractNo?: string;
    contractType?: string;
    signDate?: null | string;
    startDate?: null | string;
    endDate?: null | string;
    contractStatus?: string;
    positionName?: string;
    fileId?: null | number;
    remark?: string;
  }
}

/** 员工档案分页（权限 bidding:personnel:profile:query） */
export function getPersonnelProfilePage(params: PersonnelProfileApi.PageReq) {
  return requestClient.get<PageResult<PersonnelProfileApi.Profile>>('/bidding/personnel-profile/page', { params });
}

/** 员工档案详情 */
export function getPersonnelProfile(id: number | string) {
  return requestClient.get<PersonnelProfileApi.Profile>('/bidding/personnel-profile/get', { params: { id } });
}

/** 履职概览：证书可用数 / 社保一致性 / 合同有效性 / 学时与风险项 */
export function getPersonnelOverview(id: number | string) {
  return requestClient.get<PersonnelProfileApi.Overview>('/bidding/personnel-profile/overview', { params: { id } });
}

/** 建档（权限 bidding:personnel:profile:create） */
export function createPersonnelProfile(data: PersonnelProfileApi.SaveReq) {
  return requestClient.post<number>('/bidding/personnel-profile/create', data);
}

/** 编辑（权限 bidding:personnel:profile:update；乐观锁 version 由后端校验） */
export function updatePersonnelProfile(data: PersonnelProfileApi.SaveReq) {
  return requestClient.put<boolean>('/bidding/personnel-profile/update', data);
}

/** 档案状态流转（在职/停职/离职/退休；迁移表 PersonnelStatusEnum） */
export function changePersonnelProfileStatus(data: PersonnelProfileApi.StatusChangeReq) {
  return requestClient.put<boolean>('/bidding/personnel-profile/change-status', data);
}

/** 培训记录录入（权限 bidding:personnel:record:create） */
export function createPersonnelTraining(data: PersonnelProfileApi.TrainingSaveReq) {
  return requestClient.post<number>('/bidding/personnel-record/training/create', data);
}
/** 培训记录（某员工，倒序） */
export function getPersonnelTrainingList(personId: number | string) {
  return requestClient.get<PersonnelProfileApi.Training[]>('/bidding/personnel-record/training/list', {
    params: { personId },
  });
}
/** 社保记录录入 */
export function createPersonnelSocialSecurity(data: PersonnelProfileApi.SocialSecuritySaveReq) {
  return requestClient.post<number>('/bidding/personnel-record/social-security/create', data);
}
/** 社保记录（某员工，倒序） */
export function getPersonnelSocialSecurityList(personId: number | string) {
  return requestClient.get<PersonnelProfileApi.SocialSecurity[]>(
    '/bidding/personnel-record/social-security/list',
    { params: { personId } },
  );
}
/** 劳动合同录入 */
export function createPersonnelLaborContract(data: PersonnelProfileApi.LaborContractSaveReq) {
  return requestClient.post<number>('/bidding/personnel-record/labor-contract/create', data);
}
/** 劳动合同（某员工，倒序） */
export function getPersonnelLaborContractList(personId: number | string) {
  return requestClient.get<PersonnelProfileApi.LaborContract[]>('/bidding/personnel-record/labor-contract/list', {
    params: { personId },
  });
}
