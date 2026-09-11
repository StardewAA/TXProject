/**
 * 企业资质域接口（菜单 60005200「企业资质」/ component bidding/qualification/index / componentName QualificationIndex）
 *
 * 契约出处（逐字段核对，未臆造）：
 *   · QualificationController          POST /bidding/qualification/{create,renew,approve,reject}
 *   · QualificationAlertRunController  POST /bidding/qualification-alert-run/scan
 *   · EnterpriseMaterialController     GET  /bidding/enterprise-material/completeness（前端复用 api/bidding/certificate 的 getMaterialCompleteness）
 *
 * ⚠️ 后端缺口（本次核查结论，需后端补齐后本页才能出「资质台账」列表）：
 *   · 资质主档 **没有查询接口**：qualification 包内不存在 page/get/get-by-code，也没有 QualificationRespVO/PageReqVO；
 *     导致 renew(qualificationId+version)、approve/reject(applyId+version)、element/*(qualId) 这些写接口
 *     **拿不到入参**——它们的前置数据必须由列表提供，故本页暂不提供这些动作。
 *   · 需要的接口：GET /bidding/qualification/page（含 qualStatus/validTo/version）、GET /bidding/qualification/get、
 *     GET /bidding/qualification/apply/page（延续申报台账，供审批选单）。
 *
 * 口径：
 *   · 日期字段为 LocalDate（yyyy-MM-dd 字符串，不做 epoch 毫秒转换）；
 *   · 写接口统一 X-Silent-Error：失败由页面按 §0.4 结构自行呈现（code/msg/traceId/fieldErrors）；
 *   · version 为乐观锁字段，写接口必须回传列表/详情取到的值（§0.7）。 */
import { requestClient } from '#/api/request';

export namespace QualificationApi {
  /** 资质序列（逐字取自 v3.3 表 15-1 DDL 注释：CONSTRUCTION 施工 / SURVEY 勘察 / DESIGN 设计 /
   *  SUPERVISION 监理 / TESTING 检测）
   *  ⚠️ 该码表**尚未**在 bidding_dict_data 注册（契约 §0.5 要求枚举/状态码可动态配置），
   *  故此处仅作为**建议值**下拉，不构成封闭校验；字典登记后应改为读 /bidding/meta/dicts。 */
  export const QUAL_SERIAL_OPTIONS = [
    { label: '施工（CONSTRUCTION）', value: 'CONSTRUCTION' },
    { label: '勘察（SURVEY）', value: 'SURVEY' },
    { label: '设计（DESIGN）', value: 'DESIGN' },
    { label: '监理（SUPERVISION）', value: 'SUPERVISION' },
    { label: '检测（TESTING）', value: 'TESTING' },
  ];

  /** 资质建档入参（EnterpriseQualificationSaveReqVO） */
  export interface SaveReq {
    id?: number;
    version?: number;
    /** 关联企业主体（必填） */
    entId: number;
    /** 资质序列 code（必填） */
    qualSerial: string;
    /** 资质类别（必填）：总承包/专业承包/劳务，如「市政公用工程施工总承包」 */
    qualCategory: string;
    /** 资质等级（必填）：特级/一级/二级…（对齐国标表110） */
    qualGrade: string;
    /** 资质证书编号（必填，租户内 UK） */
    certNo: string;
    /** 发证/许可机关 */
    issuingAuthority?: string;
    /** 发证日期（LocalDate yyyy-MM-dd） */
    issueDate?: null | string;
    /** 有效期至（LocalDate；NULL=长期） */
    validTo?: null | string;
    /** 批准承包/承揽范围 */
    approvalScope?: string;
    /** 关联 certificate_info（影像/验真） */
    bindedCertId?: null | number;
    remark?: string;
  }

  /** 延续申报入参（QualificationRenewReqVO）：主档置 PENDING_RENEW，申报单 SUBMITTED */
  export interface RenewReq {
    qualificationId: number;
    version: number;
    /** 申请延续至（LocalDate） */
    dueDate?: null | string;
    remark?: string;
  }

  /** 延续审批通过（QualificationApproveReqVO）：APPROVED→ISSUED，回写主档 VALID + newValidTo */
  export interface ApproveReq {
    applyId: number;
    version: number;
    resultNo?: string;
    newCertNo?: string;
    newValidTo: string;
  }

  /** 延续审批退回（QualificationRejectReqVO）：REJECTED，主档还原 VALID */
  export interface RejectReq {
    applyId: number;
    version: number;
    remark?: string;
  }
}

/** 写接口统一静默：错误由调用方按 §0.4 呈现，避免全局 toast 与页面提示重复 */
const SILENT = { headers: { 'X-Silent-Error': 'true' } } as const;

/** 资质建档（UK：tenant+serial+category+grade+certNo 判重；bindedCertId 联动证照台账） */
export function createQualification(data: QualificationApi.SaveReq) {
  return requestClient.post<number>('/bidding/qualification/create', data, SILENT);
}

/** 延续申报：需列表提供的 qualificationId + version（当前无列表接口，暂由后端/脚本触发） */
export function submitQualificationRenew(data: QualificationApi.RenewReq) {
  return requestClient.post<number>('/bidding/qualification/renew', data, SILENT);
}

/** 延续审批通过：需申报台账提供的 applyId + version */
export function approveQualificationRenew(data: QualificationApi.ApproveReq) {
  return requestClient.post<number>('/bidding/qualification/approve', data, SILENT);
}

/** 延续审批退回 */
export function rejectQualificationRenew(data: QualificationApi.RejectReq) {
  return requestClient.post<number>('/bidding/qualification/reject', data, SILENT);
}

/** 手动触发资质到期预警 QUAL_EXPIRY 三源扫描（同日幂等），返回命中条数 */
export function runQualificationAlertScan() {
  return requestClient.post<number>('/bidding/qualification-alert-run/scan', undefined, SILENT);
}
