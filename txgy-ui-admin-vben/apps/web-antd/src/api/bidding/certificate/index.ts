import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

/** 契约来源：《电子证照模块-前端接口契约-v1.0.md》（SSOT，2026-09-13）+ 后端 Controller 逐一核对
 *  §1 元数据（dicts/configs）· §2 证照台账 · §3 文件版本 · §4 验真 · §5 预警运行 · §6 生命周期 · §7 材料完备性
 *  硬约束（契约 §0.5）：**枚举、阈值、天数一律取 §1 接口，前端不得硬编码**；
 *  写接口（§0.7）：幂等按钮防重复 + 乐观锁 version 回传；幂等拦截"重复请求，请稍后重试"时禁止自动重试。
 *  权限：bidding:certificate:{query,create,update,file,verify,alert-run,lifecycle:query|apply|audit}、bidding:qualification:material:query */
export namespace CertificateApi {
  /* ===================== §1 元数据 ===================== */
  export type DictTagType = 'default' | 'error' | 'info' | 'success' | 'warning';
  export interface DictItem {
    value: string;
    label: string;
    /** 标签色（前端直接用，勿自造映射） */
    tagType: DictTagType;
    /** 是否终态（终态不可再流转，用于禁用状态操作） */
    terminal: boolean;
    sort: number;
  }
  /** key = 字典名（cert_status / verification_status / cert_type / file_category / verify_channel / lifecycle_type / lifecycle_status / biz_domain / revoke_type / renewal_type / material_category / person_cert_type / person_cert_status …） */
  export type DictMap = Record<string, DictItem[]>;

  export interface RuleConfigItem {
    ruleKey: string;
    value: unknown;
    valueType: 'BOOL' | 'DECIMAL' | 'INT' | 'JSON' | 'STRING';
    description?: null | string;
    regulationRef?: null | string;
    /** 法定下限（配置值不得低于该值） */
    legalFloor?: null | { max?: number; min?: number };
    versionNo: number;
  }
  export interface RuleConfigData {
    items: RuleConfigItem[];
  }

  /* ===================== §2 证照台账 ===================== */
  export interface Certificate {
    id: number;
    /** 乐观锁版本（更新/状态流转必须回传） */
    version: number;
    certName: string;
    /** L3 敏感：界面按需掩码 */
    certNo: string;
    certType?: string;
    certCategoryCode?: string;
    certLevel?: string;
    certScope?: string;
    holderName: string;
    holderType?: 'EXTERNAL' | 'ORG' | 'PERSON' | string;
    holderId?: number;
    issuingAuthority?: string;
    issuingAuthorityCode?: string;
    issueDate?: string;
    validFrom?: string;
    /** null = 长期有效（界面显示"长期"） */
    validTo?: null | string;
    certStatus: string;
    verificationStatus?: string;
    lastVerifiedAt?: null | string;
    verifyApiSource?: string;
    officialVerifyUrl?: string;
    sourceChannel?: string;
    eCertIdent?: string;
    remark?: string;
    createTime?: string;
    updateTime?: string;
  }

  export interface PageReq extends PageParam {
    /** 模糊 */
    certName?: string;
    /** 精确（唯一键，模糊会误伤相似编号） */
    certNo?: string;
    certType?: string;
    certStatus?: string;
    holderName?: string;
    verificationStatus?: string;
  }

  /** 建档（§2.3）：certName/certNo 必填；holderType 默认 ORG；validTo 留空=长期 */
  export interface SaveReq {
    id?: number;
    version?: number;
    certName: string;
    certNo: string;
    certType?: string;
    certCategoryCode?: string;
    certLevel?: string;
    certScope?: string;
    holderName: string;
    holderType?: string;
    holderId?: number;
    issuingAuthority?: string;
    issuingAuthorityCode?: string;
    issueDate?: string;
    validFrom?: string;
    validTo?: string;
    remark?: string;
  }

  /** 状态流转（§2.5）：targetStatus 取 dicts.cert_status；终态不可再流转 */
  export interface StatusChangeReq {
    certificateId: number;
    targetStatus: string;
    version: number;
    /** 吊销/注销事由（建议必填） */
    reason?: string;
  }

  /* ===================== §3 文件版本 ===================== */
  export interface CertFileVersion {
    id: number;
    certId: number;
    fileCategory: string;
    fileName: string;
    fileId: number;
    fileFormat?: string;
    fileSize?: number;
    version: number;
    /** 当前生效版本 */
    isCurrent: boolean;
    sha256?: string;
    virusScanStatus?: string;
    copyExpiringTime?: null | string;
    createTime?: string;
  }

  /** 登记文件版本（§3.1）：前置=已用 /bidding/file/upload 拿到 fileId */
  export interface FileRegisterReq {
    certId: number;
    fileCategory: string;
    fileName: string;
    fileId: number;
    /** SHA-256（建议传：同证同哈希判重与防篡改） */
    fileHash?: string;
    fileFormat?: string;
    fileSize?: number;
    /** 加注时间/加注有效期：LocalDateTime → **epoch 毫秒**（平台无 @JsonFormat，统一毫秒序列化） */
    copyCreationTime?: number;
    copyCreator?: string;
    copyCause?: string;
    copyExpiringTime?: number;
    remark?: string;
  }

  /* ===================== §4 验真 ===================== */
  export interface VerifyReq {
    certId: number;
    /** 取 dicts.verify_channel；不传由后端按类别选默认渠道 */
    verifyChannel?: string;
  }

  /* ===================== §4b 验真历史（读出口） ===================== */
  /** 验真历史查询入参（后端口径：主表已按 verifiedAt 倒序，按 certId 定位即可，暂不按时间区间过滤） */
  export interface VerifyHistoryPageReq extends PageParam {
    /** 被验真证照 ID（精确；为空则查全部） */
    certId?: number;
    /** 验真渠道 code：FOUR_PLATFORM/MINISTRY/PROVINCE/THIRD_PARTY */
    verifyChannel?: string;
    /** 验真结论 code：MATCH/MISMATCH/UNAVAILABLE */
    verifyResult?: string;
  }
  /** 验真历史行（CertVerifyHistoryRespVO 逐字对齐） */
  export interface VerifyHistoryItem {
    id?: number;
    certId?: number;
    /** 后端按行批量回查证照名（避免 N+1）：历史行的 certId 对人不可读 */
    certName?: string;
    verifyChannel?: string;
    verifyResult?: string;
    /** 发起人 ID（含 SYSTEM） */
    verifyBy?: number;
    /** 验真完成时间（LocalDateTime → epoch 毫秒） */
    verifiedAt?: null | number;
    /** 是否留有 L3 报文原文（内容不下发，需调阅请按主键查库取证） */
    snapshotAvailable?: boolean;
  }

  /* ===================== §6 生命周期 ===================== */
  export type BizDomain = 'CERT' | 'ENTERPRISE_QUAL' | 'PERSON_CERT' | 'SAFETY_PERMIT';
  export type LifecycleType = 'ANNUAL' | 'CHANGE' | 'RENEW' | 'REVOKE';
  export type LifecycleStatus = 'APPROVED' | 'EFFECTIVE' | 'PENDING' | 'REJECTED' | 'REVIEWING';

  export interface FieldChange {
    fieldName: string;
    oldValue?: string;
    newValue?: string;
  }

  /** 发起流程（§6.1）：按类型条件必填（changes / renewalType / revokeType / reviewYear…），后端逐项校验 */
  export interface LifecycleApplyReq {
    bizDomain: BizDomain | string;
    bizId: number;
    lifecycleType: LifecycleType | string;
    /** 申请单号（业务自拟，唯一，用于幂等与对账） */
    applyNo: string;
    /** CHANGE 必填：一行一字段 */
    changes?: FieldChange[];
    changeSummary?: string;
    /** RENEW 必填：RENEW 延续 / REISSUE 补换证 */
    renewalType?: string;
    newValidFrom?: string;
    /** RENEW 必填 */
    newValidTo?: string;
    newNextReviewDate?: string;
    newCertNo?: string;
    resultNo?: string;
    /** REVOKE 必填：REVOKE/CANCEL/SUSPEND/RESTORE */
    revokeType?: string;
    revokeAuthority?: string;
    revokeReason?: string;
    revokeEffectiveAt?: string;
    /** ANNUAL 必填 */
    reviewYear?: number;
    reviewDate?: string;
    reviewResult?: number;
    newCertFileId?: number;
    /** 佐证材料文件 ID（后端为字符串字段，多个以半角逗号分隔） */
    /** 佐证材料文件 ID：后端为 List<Long>（JSON 数组），DB 侧 json 列由 Service 转换 */
    evidenceFileIds?: number[];
    remark?: string;
  }

  export interface Lifecycle {
    id: number;
    bizDomain: string;
    bizId: number;
    /** 对象快照名/编号（对象改名后历史仍可读） */
    refName?: string;
    refNo?: string;
    entId?: number;
    lifecycleType: string;
    status: string;
    applyNo: string;
    changeGroupId?: string;
    fieldName?: string;
    oldValue?: string;
    newValue?: string;
    changeSummary?: string;
    renewalType?: string;
    oldValidTo?: string;
    newValidFrom?: string;
    newValidTo?: string;
    newNextReviewDate?: string;
    newCertNo?: string;
    resultNo?: string;
    revokeType?: string;
    revokeAuthority?: string;
    revokeReason?: string;
    revokeEffectiveAt?: string;
    reviewYear?: number;
    reviewDate?: string;
    reviewResult?: number;
    newCertFileId?: number;
    /** 佐证材料文件 ID（后端 List<Long>） */
    evidenceFileIds?: number[];
    appliedBy?: number;
    appliedAt?: string;
    handledBy?: number;
    handledAt?: string;
    /** 空 = 未生效 */
    effectiveAt?: null | string;
    remark?: string;
    version: number;
  }

  export interface LifecyclePageReq extends PageParam {
    bizDomain?: string;
    bizId?: number;
    lifecycleType?: string;
    status?: string;
    applyNo?: string;
  }

  /** 审核流转（§6.2）：REVIEWING 受理 / APPROVED 通过 / REJECTED 驳回（驳回建议必填 remark） */
  export interface LifecycleAuditReq {
    id: number;
    targetStatus: string;
    version: number;
    remark?: string;
  }

  /** 生效（§6.3）：仅 APPROVED 可生效，会回写台账 */
  export interface LifecycleEffectReq {
    id: number;
    version: number;
    remark?: string;
  }

  /* ===================== §7 材料完备性 ===================== */
  export interface MaterialItem {
    materialType: string;
    /** 材料类型名称（中文，直接用；不要回退到 code） */
    materialLabel: string;
    /** 材料大类：IDENTITY/QUALIFICATION/STRENGTH/OTHER（字典 material_category） */
    category: string;
    mandatory: boolean;
    /** 字典 material_status：READY 齐备 / EXPIRING 临近到期 / MISSING 缺失 / EXPIRED 已过期 */
    status: 'EXPIRED' | 'EXPIRING' | 'MISSING' | 'READY' | string;
    /** 匹配到的记录主键（证照/资质/安许/业绩） */
    refId?: null | number;
    /** 匹配到的名称/编号 */
    refName?: null | string;
    /** 有效期止（字符串，可空 = 长期） */
    validTo?: null | string;
    message?: null | string;
  }
  /** 企业材料完备性结论（EnterpriseMaterialCompletenessRespVO，逐字段核对）
   *  注意：missingItems/expiringItems 是**字符串清单**（缺失材料名 / 名称+到期日），逐项明细在 items */
  export interface MaterialCompleteness {
    entId: number;
    /** 字典 material_result：PASS 齐备 / RISK 部分缺失或临近到期 / FAIL 必备材料缺失 */
    result: 'FAIL' | 'PASS' | 'RISK' | string;
    requiredCount: number;
    readyCount: number;
    items: MaterialItem[];
    /** 缺失清单（材料名称） */
    missingItems: string[];
    /** 临近到期清单（材料名称 + 到期日） */
    expiringItems: string[];
  }
}

/* ========== §1 元数据 ========== */
/** 字典（枚举值对照表）——登录即可，不额外校验业务权限
 *  @param types 需要的字典类型，逗号分隔（如 'cert_status,file_category'）；空=全部
 *  静默错误：字典失败时由调用方降级为"只显示 code"，不弹全局错误、不白屏（契约 §1.1③④） */
export function getCertDicts(types?: string) {
  return requestClient.get<CertificateApi.DictMap>('/bidding/meta/dicts', {
    params: { types },
    headers: { 'X-Silent-Error': 'true' },
  });
}
/** 业务阈值与规则配置（天数/比例/窗口等可变数值的唯一来源）
 *  @param params group=CERT/ALERT/PERSONNEL；keys=精确取键，逗号分隔 */
export function getCertConfigs(params?: { group?: string; keys?: string }) {
  return requestClient.get<CertificateApi.RuleConfigData>('/bidding/meta/configs', {
    params,
    headers: { 'X-Silent-Error': 'true' },
  });
}

/* ========== §2 证照台账 ========== */
export function getCertificatePage(params: CertificateApi.PageReq) {
  return requestClient.get<PageResult<CertificateApi.Certificate>>('/bidding/certificate/page', { params });
}
export function getCertificate(id: number | string) {
  return requestClient.get<CertificateApi.Certificate>('/bidding/certificate/get', { params: { id } });
}
export function createCertificate(data: CertificateApi.SaveReq) {
  return requestClient.post<number>('/bidding/certificate/create', data, { headers: { 'X-Silent-Error': 'true' } });
}
export function updateCertificate(data: CertificateApi.SaveReq) {
  return requestClient.put<boolean>('/bidding/certificate/update', data, { headers: { 'X-Silent-Error': 'true' } });
}
export function changeCertificateStatus(data: CertificateApi.StatusChangeReq) {
  return requestClient.post<boolean>('/bidding/certificate/change-status', data, { headers: { 'X-Silent-Error': 'true' } });
}

/* ========== §3 文件版本 ========== */
export function registerCertFile(data: CertificateApi.FileRegisterReq) {
  return requestClient.post<number>('/bidding/certificate/file/register', data, { headers: { 'X-Silent-Error': 'true' } });
}
export function getCertFileList(certId: number | string) {
  return requestClient.get<CertificateApi.CertFileVersion[]>('/bidding/certificate/file/list', { params: { certId } });
}

/* ========== §4 验真 ========== */
export function verifyCertificate(data: CertificateApi.VerifyReq) {
  return requestClient.post<number>('/bidding/certificate-verify/verify', data, { headers: { 'X-Silent-Error': 'true' } });
}
export function verifyCertificateFile(data: CertificateApi.VerifyReq) {
  return requestClient.post<number>('/bidding/certificate-verify/verify-file', data, { headers: { 'X-Silent-Error': 'true' } });
}

/* ========== §4b 验真历史（读出口） ==========
 * 背景（后端 Javadoc 原话）：/verify 与 /verify-file 每次都返回"验真历史主键"，但此前**没有任何接口能把它读回来**
 * ——留痕只进不出，主键等于一个再也打不开的句柄；事后追溯"谁在什么时候验了哪张证、结论是什么"只能查库。
 * GET /bidding/certificate-verify/history/page 补上该读出口，页面据此提供"验真历史"抽屉。
 * 注意：L3 报文原文（request_snapshot/result_snapshot）**不下发**——报文含证照编号等可识别信息、列表又是高频读取面，
 * 故只给 snapshotAvailable 布尔位，界面据此提示"可申请调阅"：既不泄露，也不丢可追溯性。 */
export function getCertVerifyHistoryPage(params: CertificateApi.VerifyHistoryPageReq) {
  return requestClient.get<PageResult<CertificateApi.VerifyHistoryItem>>(
    '/bidding/certificate-verify/history/page',
    { params },
  );
}

/* ========== §5 预警运行（运维补跑，均幂等） ========== */
export function runCertAlertScan() {
  return requestClient.post<number>('/bidding/certificate-alert-run/scan', undefined, { headers: { 'X-Silent-Error': 'true' } });
}
export function runCertAlertDispatch() {
  return requestClient.post<number>('/bidding/certificate-alert-run/dispatch', undefined, { headers: { 'X-Silent-Error': 'true' } });
}

/* ========== §6 生命周期 ========== */
export function applyCertLifecycle(data: CertificateApi.LifecycleApplyReq) {
  return requestClient.post<number>('/bidding/cert-lifecycle/apply', data, { headers: { 'X-Silent-Error': 'true' } });
}
export function auditCertLifecycle(data: CertificateApi.LifecycleAuditReq) {
  return requestClient.post<boolean>('/bidding/cert-lifecycle/audit', data, { headers: { 'X-Silent-Error': 'true' } });
}
export function effectCertLifecycle(data: CertificateApi.LifecycleEffectReq) {
  return requestClient.post<boolean>('/bidding/cert-lifecycle/effect', data, { headers: { 'X-Silent-Error': 'true' } });
}
export function getCertLifecycle(id: number | string) {
  return requestClient.get<CertificateApi.Lifecycle>('/bidding/cert-lifecycle/get', { params: { id } });
}
export function getCertLifecyclePage(params: CertificateApi.LifecyclePageReq) {
  return requestClient.get<PageResult<CertificateApi.Lifecycle>>('/bidding/cert-lifecycle/page', { params });
}
/** 某对象的生命周期轨迹（按 appliedAt 倒序） */
export function getCertLifecycleByBiz(bizDomain: string, bizId: number | string) {
  return requestClient.get<CertificateApi.Lifecycle[]>('/bidding/cert-lifecycle/list-by-biz', {
    params: { bizDomain, bizId },
  });
}

/* ========== §7 材料完备性自查 ========== */
export function getMaterialCompleteness(entId: number | string, asOf?: string) {
  return requestClient.get<CertificateApi.MaterialCompleteness>('/bidding/enterprise-material/completeness', {
    params: { entId, asOf },
    // 1060107013「企业材料不完备」是业务结论（部分场景作为阻断返回）：契约要求优先用 data 渲染，
    // 仅当无 data 时用 msg —— 故此处静默，由页面自行呈现结论。
    headers: { 'X-Silent-Error': 'true' },
  });
}

/* ========== 错误码工具（契约 §0.4 / §9：前端按段位兜底，具体文案直接用后端 msg） ========== */
/** 电子证照域错误码段位：106 证照台账/验真/预警；107 员工资质/生命周期 */
export const CERT_ERR = {
  CERT_NOT_FOUND: 10_6010_6000,
  CERT_NO_DUPLICATE: 10_6010_6001,
  OPTIMISTIC_LOCK: 10_6010_6002,
  STATUS_FLOW_DENIED: 10_6010_6003,
  ALERT_NOT_CONFIGURED: 10_6010_6012,
  LIFECYCLE_NOT_FOUND: 10_6010_7015,
  LIFECYCLE_FLOW_DENIED: 10_6010_7016,
  LIFECYCLE_OPTIMISTIC_LOCK: 10_6010_7017,
  CHANGE_EMPTY: 10_6010_7018,
  LIFECYCLE_FIELD_MISSING: 10_6010_7020,
  APPLY_NO_DUPLICATE: 10_6010_7021,
  LIFECYCLE_BIZ_NOT_FOUND: 10_6010_7022,
  MATERIAL_INCOMPLETE: 10_6010_7013,
  MATERIAL_LIST_EMPTY: 10_6010_7014,
  /* v1.1 安许风险联动（契约 §13） */
  SAFETY_PERMIT_NOT_EXISTS: 10_6010_6024,
  SAFETY_GRACE_REASON_NOT_ALLOWED: 10_6010_7025,
  SAFETY_GRACE_DAYS_EXCEEDED: 10_6010_7026,
  MULTI_POST_SAME_POST: 10_6010_7027,
  KEY_POST_INVALID: 10_6010_7028,
} as const;

/** 从 requestClient 抛出的错误里取业务 code（后端体挂在 error.response.data / error.data 上） */
export function certErrorCode(error: unknown): number | undefined {
  const e = error as { data?: { code?: number }; response?: { data?: { code?: number } } };
  const code = e?.response?.data?.code ?? e?.data?.code;
  return typeof code === 'number' ? code : undefined;
}

/** 从错误里取后端 msg（后端已本地化，前端直接展示） */
export function certErrorMsg(error: unknown): string {
  const e = error as {
    data?: { msg?: string };
    message?: string;
    response?: { data?: { msg?: string } };
  };
  return e?.response?.data?.msg ?? e?.data?.msg ?? e?.message ?? '请求失败';
}

/** 参数校验失败（400）时的逐字段错误（契约 §0.4 ValidationErrorData） */
export interface CertFieldError {
    field: string;
    message: string;
    /** 被拒原值（调试用；提示语不暴露内部细节） */
    rejectedValue?: unknown;
  }

export function certFieldErrors(error: unknown): CertFieldError[] {
  const e = error as {
    data?: { data?: { fieldErrors?: CertFieldError[] } };
    response?: { data?: { data?: { fieldErrors?: CertFieldError[] } } };
  };
  const body = e?.response?.data ?? e?.data;
  return body?.data?.fieldErrors ?? [];
}

/** §0.4：把 400 的字段级错误映射到表单字段（antd 表单 setFields 入参格式） */
export function certFieldErrorFields(error: unknown): Array<{ errors: string[]; name: string }> {
  return certFieldErrors(error)
    .filter((i) => !!i.field)
    .map((i) => ({ errors: [i.message || '参数不正确'], name: i.field }));
}

/** 统一 traceId（契约 §0.4：500 时 toast + 记录 traceId） */
export function certTraceId(error: unknown): string {
  const e = error as { data?: { data?: { traceId?: string } }; response?: { data?: { data?: { traceId?: string } } } };
  const body = e?.response?.data ?? e?.data;
  return body?.data?.traceId ?? '';
}
