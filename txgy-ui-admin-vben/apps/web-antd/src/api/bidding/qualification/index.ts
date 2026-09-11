/**
 * 企业资质域接口（菜单 60005200「企业资质」/ component bidding/qualification/index / componentName QualificationIndex）
 *
 * 契约出处（逐字段核对，未臆造）：
 *   · QualificationController   GET  /bidding/qualification/page | /get | /apply/page
 *                               POST /bidding/qualification/{create,renew,approve,reject}
 *   · QualificationAlertRunController  POST /bidding/qualification-alert-run/scan
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
/** 手动触发资质到期预警 QUAL_EXPIRY 三源扫描（同日幂等），返回命中条数 */
export function runQualificationAlertScan() {
  return requestClient.post<number>('/bidding/qualification-alert-run/scan', undefined, SILENT);
}
