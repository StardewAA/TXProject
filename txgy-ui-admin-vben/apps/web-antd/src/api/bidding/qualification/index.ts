/**
 * 企业资质域接口（菜单 60005200「企业资质」/ component bidding/qualification/index / componentName QualificationIndex）
 *
 * 契约出处（逐字段核对，未臆造）：
 *   · QualificationController   GET  /bidding/qualification/page | /get | /apply/page
 *                               POST /bidding/qualification/{create,renew,approve,reject}
 *   · QualificationAlertRunController  POST /bidding/qualification-alert-run/scan
 *   · QualificationController          POST /bidding/qualification/alert/handle（版本 CAS 标记已处理）
 *   · EnterpriseMaterialController     GET  /bidding/enterprise-material/completeness
 *     （前端复用 api/bidding/certificate 的 getMaterialCompleteness）
 *
 * 口径：
 *   · LocalDate（issueDate/validTo/submitDate/dueDate/newValidTo）→ 'yyyy-MM-dd' 字符串，不做毫秒转换；
 *   · LocalDateTime（latestReview.reviewedAt）→ epoch 毫秒，展示过 fmtDateTime；
 *   · version 为乐观锁：renew/approve/reject 必须回传列表/详情取到的值（§0.7）；
 *   · 写接口统一 X-Silent-Error，失败由页面按 §0.4 结构自呈现；
 *   · ⚠️ qualStatus / qualSerial / qualGrade / applyType / applyStatus / reviewResult 尚未登记字典，
 *     故标签一律走 dictLabel 优先、缺字典回退原始 code（禁止在前端自造中文枚举，契约 §0.5）。 */
import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace QualificationApi {
  /** 资质序列（逐字取自 v3.3 表 15-1 DDL 注释 = QualificationSerialEnum） */
  export const QUAL_SERIAL_OPTIONS = [
    { label: '施工（CONSTRUCTION）', value: 'CONSTRUCTION' },
    { label: '勘察（SURVEY）', value: 'SURVEY' },
    { label: '设计（DESIGN）', value: 'DESIGN' },
    { label: '监理（SUPERVISION）', value: 'SUPERVISION' },
    { label: '检测（TESTING）', value: 'TESTING' },
  ];

  /** 资质台账分页入参（QualificationPageReqVO） */
  export interface PageReq extends PageParam {
    /** 企业主体（本模块多数动作以此为主键维度） */
    entId?: number;
    qualSerial?: string;
    qualCategory?: string;
    qualGrade?: string;
    qualStatus?: string;
    /** 仅看 N 天内到期 */
    expiringInDays?: number;
    /** 关键字（证书编号/机关等，后端定义） */
    keyword?: string;
  }

  /** 资质台账行（QualificationRespVO） */
  export interface Qualification {
    id: number;
    entId: number;
    qualSerial: string;
    qualCategory: string;
    qualGrade: string;
    certNo: string;
    issuingAuthority?: string;
    /** LocalDate yyyy-MM-dd */
    issueDate?: null | string;
    /** LocalDate；NULL=长期 */
    validTo?: null | string;
    /** QualificationStatusEnum：VALID/EXPIRING_SOON/EXPIRED/PENDING_RENEW/LIMITED/REVOKED */
    qualStatus: string;
    /** 距到期天数（后端算，负数=已过期） */
    daysToExpire?: null | number;
    bindedCertId?: null | number;
    /** 是否有在途延续申报 */
    hasPendingApply?: boolean;
    sourceChannel?: string;
    remark?: string;
    /** 乐观锁 */
    version?: number;
  }

  /** 资质详情（QualificationDetailRespVO = RespVO + 绑定证照摘要 + 最近一次达标核查） */
  export interface Detail extends Qualification {
    bindedCertName?: string;
    bindedCertNo?: string;
    bindedCertStatus?: string;
    latestReview?: {
      diffItems?: string;
      /** LocalDateTime → epoch 毫秒 */
      reviewedAt?: number;
      /** QualificationReviewResultEnum：PASS/RISK/FAIL（与字典 review_result 的 1/0 年检结论**不同义**） */
      reviewResult?: string;
      stdVersion?: string;
    } | null;
  }

  /** 延续申报单分页入参（QualificationApplyPageReqVO） */
  export interface ApplyPageReq extends PageParam {
    entId?: number;
    qualId?: number;
    /** QualificationApplyTypeEnum：NEW/UPGRADE/RENEW/ADDITION/CHANGE/CANCEL */
    applyType?: string;
    /** QualificationApplyStatusEnum：DRAFT/SUBMITTED/ACCEPTED/REVIEWING/APPROVED/PUBLISHED/ISSUED/REJECTED */
    status?: string;
  }

  /** 延续申报单（QualificationApplyRespVO） */
  export interface ApplyRecord {
    id: number;
    entId: number;
    qualId: number;
    applyType: string;
    targetGrade?: string;
    submitDate?: null | string;
    dueDate?: null | string;
    status: string;
    resultNo?: string;
    newCertNo?: string;
    newValidTo?: null | string;
    ownerBy?: number;
    remark?: string;
    version?: number;
  }

  /** 资质建档入参（EnterpriseQualificationSaveReqVO） */
  export interface SaveReq {
    id?: number;
    version?: number;
    entId: number;
    qualSerial: string;
    qualCategory: string;
    qualGrade: string;
    certNo: string;
    issuingAuthority?: string;
    issueDate?: null | string;
    validTo?: null | string;
    approvalScope?: string;
    bindedCertId?: null | number;
    remark?: string;
  }

  /* ===== v1.1 新增读接口（页面级契约 §3.11~§3.20） ===== */

  /** 资质标准登记（StandardCreateReqVO）：同一「序列+类别+等级+版本」判重（§0.7） */
  export interface StandardCreateReq {
    qualSerial: string;
    qualCategory: string;
    qualGrade: string;
    stdVersion: string;
    reqStaff?: string;
    reqAssets?: string;
    reqPerformance?: string;
    reqEquipment?: string;
    /** LocalDate */
    effectiveFrom?: null | string;
    /** LocalDate；空=长期有效 */
    effectiveTo?: null | string;
    sourceRef?: string;
  }

  /** 资质标准分页入参（QualificationStandardPageReqVO） */
  export interface StandardPageReq extends PageParam {
    qualSerial?: string;
    qualCategory?: string;
    qualGrade?: string;
    stdVersion?: string;
    onlyEffective?: boolean;
  }

  /** 资质标准（QualificationStandardRespVO；req* 为文本要求，非结构化） */
  export interface Standard {
    id: number;
    stdVersion?: string;
    qualSerial?: string;
    qualCategory?: string;
    qualGrade?: string;
    reqAssets?: string;
    reqStaff?: string;
    reqPerformance?: string;
    reqEquipment?: string;
    /** LocalDate */
    effectiveFrom?: null | string;
    /** LocalDate；空=长期有效 */
    effectiveTo?: null | string;
    sourceRef?: string;
    isEffective?: boolean;
  }

  /** 资质维度选项（QualificationStandardOptionsRespVO）：序列 → 类别 → 等级 */
  export interface StandardOptions {
    stdVersions?: string[];
    serials?: Array<{
      categories?: Array<{ grades?: string[]; qualCategory?: string; }>;
      qualSerial?: string;
    }>;
  }

  /** 要素台账分页入参（QualificationElementPageReqVO，三个要素接口共用） */
  export interface ElementPageReq extends PageParam {
    entId?: number;
    qualId?: number;
  }

  /** 资质人员台账（QualificationStaffRespVO） */
  export interface Staff {
    id: number;
    entId?: number;
    qualId?: number;
    staffRole?: string;
    personName?: string;
    major?: string;
    regNo?: string;
    titleLevel?: string;
    isFullTime?: boolean;
    socialSecurityStatus?: string;
    socialSecurityTenant?: string;
    eduHours?: number;
    eduHoursRequired?: number;
    /** LocalDate */
    regValidTo?: null | string;
    bindedCertId?: null | number;
    version?: number;
  }

  /** 工程业绩台账（QualificationPerformanceRespVO） */
  export interface Performance {
    id: number;
    entId?: number;
    qualId?: number;
    projectName?: string;
    projectType?: string;
    scaleMetrics?: string;
    contractAmount?: number;
    /** LocalDate */
    completionDate?: null | string;
    role?: string;
    qualityResult?: string;
    ownerName?: string;
    isPlatformVerified?: boolean;
    evidenceFileIds?: string;
  }

  /** 资产/技术装备台账（QualificationAssetRespVO） */
  export interface Asset {
    id: number;
    entId?: number;
    assetType?: string;
    name?: string;
    spec?: string;
    quantity?: number;
    valueAmount?: number;
    statYear?: number;
    evidenceFileIds?: string;
  }

  /** 达标核查历史入参（QualificationReviewPageReqVO） */
  export interface ReviewPageReq extends PageParam {
    entId?: number;
    qualId?: number;
    reviewResult?: string;
  }

  /** 达标核查记录（QualificationReviewRespVO；elementSnapshot/diffItems 为 JSON 文本） */
  export interface Review {
    id: number;
    entId?: number;
    qualId?: number;
    stdVersion?: string;
    reviewType?: string;
    reviewResult?: string;
    elementSnapshot?: string;
    diffItems?: string;
    reviewer?: null | number;
    /** LocalDateTime → epoch 毫秒 */
    reviewedAt?: null | number | string;
    linkedApplyId?: null | number;
  }

  /** 安许证台账入参（QualificationPermitPageReqVO） */
  export interface PermitPageReq extends PageParam {
    entId?: number;
    permitStatus?: string;
    /** 距到期天数（天；负数=已过期，0=今天到期） */
    expiringInDays?: number;
    keyword?: string;
  }

  /** 安许证（QualificationPermitRespVO；risk* 为联动快照，grace* 为整改宽限） */
  export interface Permit {
    id: number;
    entId?: number;
    permitNo?: string;
    issuingAuthority?: string;
    /** LocalDate */
    issueDate?: null | string;
    /** LocalDate；空=长期 */
    validTo?: null | string;
    /** 距到期天数（天；null=长期或未填有效期，负数=已过期） */
    daysToExpire?: null | number;
    permitStatus?: string;
    legalPerson?: string;
    bindedCertId?: null | number;
    riskFlag?: null | string;
    riskLevel?: null | string;
    riskEvaluatedAt?: null | number | string;
    graceExpireAt?: null | number | string;
    graceReason?: null | string;
    graceSourceRef?: null | string;
    frequentAlertCount?: null | number;
    frequentWindowStart?: null | number | string;
    ruleVersion?: null | string;
    /** 乐观锁版本（状态流转必传） */
    version?: number;
    /** 备注（后端下发；此前未声明） */
  remark?: string;
}

  /** 安许状态流转（SafetyPermitStatusChangeReqVO）：version 必传，命中乐观锁失败返回 §0.4 错误 */
  export interface PermitStatusChangeReq {
    permitId: number;
    version: number;
    targetStatus: string;
    reason?: string;
  }

  /** 预警清单入参（QualificationAlertPageReqVO） */
  export interface AlertPageReq extends PageParam {
    entId?: number;
    /** QUAL / SAFETY_PERMIT / STAFF */
    refType?: string;
    /** 字典 risk_level */
    alertLevel?: string;
    handled?: boolean;
  }

  /** 预警处理入参（QualificationAlertHandleReqVO）：alertId 与 version 后端标 @NotNull，handleNote 选填 */
  export interface AlertHandleReq {
    alertId: number | string;
    /** 处理备注（后端 handleNote；CAS 冲突被拒时这段备注不会落库） */
    handleNote?: string;
    /** 乐观锁版本：**原样回传清单里的 version**；与库中不一致 = 已被他人处理 */
    version: number;
  }

  /** 预警记录（QualificationAlertRespVO） */
  export interface Alert {
    id: number;
    refType?: string;
    refId?: null | number;
    refName?: string;
    certId?: null | number;
    alertType?: string;
    alertLevel?: string;
    alertMessage?: string;
    /** LocalDateTime → epoch 毫秒 */
    triggeredAt?: null | number | string;
    idempotentKey?: string;
    notifyChannel?: string;
    notifyTarget?: string;
    notifyStatus?: string;
    handled?: boolean;
    handledBy?: null | number;
    handledAt?: null | number | string;
    handleNote?: string;
    /** 乐观锁版本：**处理预警必须回传**（QualificationAlertRespVO 下发；此前前端未声明 → 处理接口一直无法接线） */
  version?: number;
}

  /** 预警来源类型（QualificationAlertRefTypeEnum：资质 / 安许 / 人员配置） */
  export const ALERT_REF_TYPE_OPTIONS = [
    { label: '资质（QUAL）', value: 'QUAL' },
    { label: '安许证（SAFETY_PERMIT）', value: 'SAFETY_PERMIT' },
    { label: '人员配置（STAFF）', value: 'STAFF' },
  ];

  /** 资质人员录入（StaffSaveReqVO，QualificationElementController#staff） */
  export interface StaffSaveReq {
    entId: number;
    qualId: number;
    personName: string;
    /** QualificationStaffRoleEnum */
    staffRole?: string;
    major?: string;
    regNo?: string;
    titleLevel?: string;
    isFullTime?: boolean;
    /** LocalDate */
    regValidTo?: null | string;
    bindedCertId?: null | number;
  }

  /** 工程业绩录入（PerformanceSaveReqVO） */
  export interface PerformanceSaveReq {
    entId: number;
    qualId: number;
    projectName: string;
    projectType?: string;
    /** PerformanceRoleEnum：GENERAL 总承包 / SUB 分包 */
    role?: string;
    contractAmount?: null | number;
    /** LocalDate */
    completionDate?: null | string;
    qualityResult?: string;
    ownerName?: string;
    isPlatformVerified?: boolean;
    /** 证据文件 id 的 JSON 数组字符串（后端 String 列） */
    evidenceFileIds?: string;
  }

  /** 资产/技术装备录入（AssetSaveReqVO，无 qualId 字段） */
  export interface AssetSaveReq {
    entId: number;
    /** QualificationAssetTypeEnum */
    assetType?: string;
    name: string;
    spec?: string;
    quantity?: null | number;
    valueAmount?: null | number;
    statYear?: null | number;
    evidenceFileIds?: string;
  }

  /** 达标核查执行（ComplianceReviewRunReqVO）：只返回核查主键，结论由详情 latestReview 回读 */
  export interface ReviewRunReq {
    qualId: number;
    /** QualificationReviewTypeEnum：SELF / DYNAMIC / PRE_APPLY */
    reviewType?: string;
    linkedApplyId?: null | number;
  }

  /** 安许建档（PermitCreateReqVO，permit_no UK 判重） */
  export interface PermitCreateReq {
    entId: number;
    permitNo: string;
    issuingAuthority?: string;
    /** LocalDate */
    issueDate?: null | string;
    /** LocalDate；NULL=长期 */
    validTo?: null | string;
    legalPerson?: string;
    bindedCertId?: null | number;
    remark?: string;
  }

  /** 资质人员角色（QualificationStaffRoleEnum，尚未登记字典 → 建议值） */
  export const STAFF_ROLE_OPTIONS = [
    { label: '注册建造师（REGISTERED_BUILDER）', value: 'REGISTERED_BUILDER' },
    { label: '技术负责人（TECH_LEAD）', value: 'TECH_LEAD' },
    { label: '中级职称（MID_TITLE）', value: 'MID_TITLE' },
    { label: '技术工人（SKILLED_WORKER）', value: 'SKILLED_WORKER' },
    { label: '现场管理人员（SITE_MGR）', value: 'SITE_MGR' },
  ];
  /** 业绩角色（PerformanceRoleEnum） */
  export const PERFORMANCE_ROLE_OPTIONS = [
    { label: '总承包（GENERAL）', value: 'GENERAL' },
    { label: '分包（SUB）', value: 'SUB' },
  ];
  /** 资产类型（QualificationAssetTypeEnum） */
  export const ASSET_TYPE_OPTIONS = [
    { label: '净资产（NET_ASSET）', value: 'NET_ASSET' },
    { label: '厂房（PLANT）', value: 'PLANT' },
    { label: '主要设备（MAJOR_EQUIP）', value: 'MAJOR_EQUIP' },
    { label: '检测仪器（TEST_INSTR）', value: 'TEST_INSTR' },
  ];
  /** 达标核查类型（QualificationReviewTypeEnum） */
  export const REVIEW_TYPE_OPTIONS = [
    { label: '企业自查（SELF）', value: 'SELF' },
    { label: '动态核查（DYNAMIC）', value: 'DYNAMIC' },
    { label: '申报前核查（PRE_APPLY）', value: 'PRE_APPLY' },
  ];

  /** 延续申报入参（QualificationRenewReqVO） */
  export interface RenewReq {
    qualificationId: number;
    version: number;
    dueDate?: null | string;
    remark?: string;
  }

  /** 延续审批通过（QualificationApproveReqVO） */
  export interface ApproveReq {
    applyId: number;
    version: number;
    resultNo?: string;
    newCertNo?: string;
    /** 必填（LocalDate）：延续后有效期 */
    newValidTo: string;
  }

  /** 延续审批退回（QualificationRejectReqVO） */
  export interface RejectReq {
    applyId: number;
    version: number;
    remark?: string;
  }
}

/** 写接口统一静默：错误由调用方按 §0.4 呈现（code/msg/traceId/fieldErrors） */
const SILENT = { headers: { 'X-Silent-Error': 'true' } } as const;

/* ================= 申报（apply）选项：值域以后端 Schema 为准，勿再直出英文码 ================= */

/** 申报类型选项（apply_type）——权威来源：QualificationApplyPageReqVO.applyType 的 @Schema
 * "申报类型 code：RENEW 延续 / UPGRADE 升级 …（当前仅 RENEW 支持）"。
 * 为什么只有这两项：此前界面把 6 个英文码（NEW/UPGRADE/RENEW/ADDITION/CHANGE/CANCEL）**直出为选项文案**，
 * 用户能选到后端 Schema 未收录、也无枚举文件的值（NEW/ADDITION/CHANGE/CANCEL）→ 选完查不到数据。
 * UPGRADE 后端已识别但注明"当前仅 RENEW 支持"，故保留可见但 disabled（提示而非撒谎）。
 * 业务若确需其余 4 类，请后端补枚举后在此登记（契约 13 追加项）。 */
export const APPLY_TYPE_OPTIONS = [
  { label: '延续（RENEW）', value: 'RENEW' },
  { disabled: true, label: '升级（UPGRADE）· 后端暂未支持', value: 'UPGRADE' },
];

/** 申报状态选项（status）——权威来源：QualificationApplyPageReqVO.status 的 @Schema
 * "SUBMITTED 待批 / APPROVED 已批 / REJECTED 已退回 / ISSUED 已办结"。
 * 此前是**手输英文 code 的输入框**（placeholder「状态 code（如 SUBMITTED）」）——业务人员背英文码既易错，
 * 也不该出现在中文界面；改下拉后与后端值域一一对应。 */
export const APPLY_STATUS_OPTIONS = [
  { label: '待批（SUBMITTED）', value: 'SUBMITTED' },
  { label: '已批（APPROVED）', value: 'APPROVED' },
  { label: '已退回（REJECTED）', value: 'REJECTED' },
  { label: '已办结（ISSUED）', value: 'ISSUED' },
];

/* ========== 查询 ========== */
export function getQualificationPage(params: QualificationApi.PageReq) {
  return requestClient.get<PageResult<QualificationApi.Qualification>>('/bidding/qualification/page', { params });
}
export function getQualification(id: number | string) {
  return requestClient.get<QualificationApi.Detail>('/bidding/qualification/get', { params: { id } });
}
export function getQualificationApplyPage(params: QualificationApi.ApplyPageReq) {
  return requestClient.get<PageResult<QualificationApi.ApplyRecord>>('/bidding/qualification/apply/page', { params });
}

/* ========== 写入 ========== */
export function createQualification(data: QualificationApi.SaveReq) {
  return requestClient.post<number>('/bidding/qualification/create', data, SILENT);
}
export function submitQualificationRenew(data: QualificationApi.RenewReq) {
  return requestClient.post<number>('/bidding/qualification/renew', data, SILENT);
}
export function approveQualificationRenew(data: QualificationApi.ApproveReq) {
  return requestClient.post<number>('/bidding/qualification/approve', data, SILENT);
}
export function rejectQualificationRenew(data: QualificationApi.RejectReq) {
  return requestClient.post<number>('/bidding/qualification/reject', data, SILENT);
}
/* ===== v1.1 读接口（页面级契约 §3.11~§3.20） ===== */

/** 资质标准分页（权限 bidding:qualification:query） */
export function getStandardPage(params: QualificationApi.StandardPageReq) {
  return requestClient.get<PageResult<QualificationApi.Standard>>('/bidding/qualification/standard/page', { params });
}

/** 资质维度选项：序列 → 类别 → 等级（供下拉联动，避免前端硬编码枚举） */
export function getStandardOptions() {
  return requestClient.get<QualificationApi.StandardOptions>('/bidding/qualification/standard/options');
}

/** 要素台账 · 人员配置分页 */
export function getQualificationStaffPage(params: QualificationApi.ElementPageReq) {
  return requestClient.get<PageResult<QualificationApi.Staff>>('/bidding/qualification/element/staff/page', {
    params,
  });
}

/** 要素台账 · 工程业绩分页 */
export function getQualificationPerformancePage(params: QualificationApi.ElementPageReq) {
  return requestClient.get<PageResult<QualificationApi.Performance>>(
    '/bidding/qualification/element/performance/page',
    { params },
  );
}

/** 要素台账 · 资产/技术装备分页 */
export function getQualificationAssetPage(params: QualificationApi.ElementPageReq) {
  return requestClient.get<PageResult<QualificationApi.Asset>>('/bidding/qualification/element/asset/page', {
    params,
  });
}

/** 达标核查历史分页 */
export function getQualificationReviewPage(params: QualificationApi.ReviewPageReq) {
  return requestClient.get<PageResult<QualificationApi.Review>>('/bidding/qualification/review/page', { params });
}

/** 安许证台账分页 */
export function getQualificationPermitPage(params: QualificationApi.PermitPageReq) {
  return requestClient.get<PageResult<QualificationApi.Permit>>('/bidding/qualification/permit/page', { params });
}

/** 安许证详情（含联动风险与宽限快照） */
export function getQualificationPermit(id: number | string) {
  return requestClient.get<QualificationApi.Permit>('/bidding/qualification/permit/get', { params: { id } });
}

/** 安许状态流转（version 必传；并发冲突按 §0.4 返回 fieldErrors） */
export function changePermitStatus(data: QualificationApi.PermitStatusChangeReq) {
  return requestClient.post<boolean>('/bidding/qualification/permit/status', data, SILENT);
}

/** 资质标准登记（权限 bidding:qualification:standard） */
export function createQualificationStandard(data: QualificationApi.StandardCreateReq) {
  return requestClient.post<number>('/bidding/qualification/standard/create', data, SILENT);
}

/** 到期/风险预警清单分页（三源：QUAL / SAFETY_PERMIT / STAFF） */
export function getQualificationAlertPage(params: QualificationApi.AlertPageReq) {
  return requestClient.get<PageResult<QualificationApi.Alert>>('/bidding/qualification/alert/page', { params });
}

/** 把到期预警标记为已处理（销账）。
 *  CAS 口径：version 必须与清单一致，否则后端拒绝——那说明**别人已经处理过**，
 *  此时绝不能重试覆盖（会盖掉他人的处理备注），页面只重新拉取清单让用户看到最新状态。 */
export function handleQualificationAlert(payload: QualificationApi.AlertHandleReq) {
  return requestClient.post<boolean>('/bidding/qualification/alert/handle', payload);
}

/** 资质人员录入（需 qualId；权限 bidding:qualification:element） */
export function createQualificationStaff(data: QualificationApi.StaffSaveReq) {
  return requestClient.post<number>('/bidding/qualification/element/staff', data, SILENT);
}
/** 工程业绩录入（权限 bidding:qualification:element） */
export function createQualificationPerformance(data: QualificationApi.PerformanceSaveReq) {
  return requestClient.post<number>('/bidding/qualification/element/performance', data, SILENT);
}
/** 资产/技术装备录入（权限 bidding:qualification:element） */
export function createQualificationAsset(data: QualificationApi.AssetSaveReq) {
  return requestClient.post<number>('/bidding/qualification/element/asset', data, SILENT);
}
/** 执行一次达标核查（权限 bidding:qualification:review）：返回核查主键，结论经 /get 的 latestReview 回读 */
export function runComplianceReview(data: QualificationApi.ReviewRunReq) {
  return requestClient.post<number>('/bidding/qualification/review/run', data, SILENT);
}
/** 安许建档（权限 bidding:qualification:permit；状态流转 /permit/status 需 permitId+version，待安许列表接口） */
export function createSafetyPermit(data: QualificationApi.PermitCreateReq) {
  return requestClient.post<number>('/bidding/qualification/permit/create', data, SILENT);
}
/** 手动触发资质到期预警 QUAL_EXPIRY 三源扫描（同日幂等），返回命中条数 */
export function runQualificationAlertScan() {
  return requestClient.post<number>('/bidding/qualification-alert-run/scan', undefined, SILENT);
}

