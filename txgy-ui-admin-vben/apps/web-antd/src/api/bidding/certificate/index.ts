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
  /** 契约来源：DictItemRespVO */
export interface DictItem {
    value: string;
    label: string;
    /** 标签色（前端直接用，勿自造映射） */
    tagType: DictTagType;
    /** 是否终态（终态不可再流转，用于禁用状态操作） */
    terminal: boolean;
    sort: number;
    /** 字典类型键（前端按域筛选字典项时使用）
   *  契约来源：DictItemRespVO——本轮仅补类型，未接 UI（避免"页面拿不到数据"与"没做界面"被混为一谈）。 */
  dictTypeKey?: string;
}
  /** key = 字典名（cert_status / verification_status / cert_type / file_category / verify_channel / lifecycle_type / lifecycle_status / biz_domain / revoke_type / renewal_type / material_category / person_cert_type / person_cert_status …） */
  export type DictMap = Record<string, DictItem[]>;

  /** 契约来源：RuleConfigItemRespVO */
export interface RuleConfigItem {
    ruleKey: string;
    value: unknown;
    valueType: 'BOOL' | 'DECIMAL' | 'INT' | 'JSON' | 'STRING';
    description?: null | string;
    regulationRef?: null | string;
    /** 法定下限（配置值不得低于该值） */
    legalFloor?: null | { max?: number; min?: number };
    versionNo: number;
    /** 规则分组（RuleConfigGroupEnum）
   *  契约来源：RuleConfigItemRespVO——本轮仅补类型，未接 UI（避免"页面拿不到数据"与"没做界面"被混为一谈）。 */
  ruleGroup?: string;
  /** 规则来源（平台默认/租户覆盖）
   *  契约来源：RuleConfigItemRespVO——本轮仅补类型，未接 UI（避免"页面拿不到数据"与"没做界面"被混为一谈）。 */
  source?: string;
}
  export interface RuleConfigData {
    items: RuleConfigItem[];
  }

  /* ===================== §2 证照台账 =====================  * 契约来源：CertificateRespVO（GET /bidding/certificate/page|get）
     */
  export interface Certificate {
    id: number;
    /** 乐观锁版本（更新/状态流转必须回传） */
    version: number;
    certName: string;
    /** L3 敏感：界面按需掩码 */
    certNo: string;
    certType?: string;
    /** 证照类型名称（字典 cert_type；后端已下发，直接显示，勿再自行映射枚举 —— 2026-09-20 字典治理） */
    certTypeName?: string;
    /** 证照大类 code（字典 cert_category：IDENTITY/QUALIFICATION/PERFORMANCE/CREDIT；四类归口，2026-09-20 新增）
     *  ⚠️ 与下面的 certCategoryCode 不是同一个东西：certCategoryCode = GB/T 36902 ZZLXDM（证照类型代码） */
    certCategory?: string;
    /** 证照大类名称（字典 cert_category） */
    certCategoryName?: string;
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
    /** 台账状态名称（字典 cert_status；后端已下发，直接显示 —— 2026-09-20 字典治理） */
    certStatusName?: string;
    verificationStatus?: string;
    /** 验真状态名称（字典 verification_status；后端已下发，直接显示） */
    verificationStatusName?: string;
    lastVerifiedAt?: null | string;
    verifyApiSource?: string;
    officialVerifyUrl?: string;
    sourceChannel?: string;
    eCertIdent?: string;
    remark?: string;
    /** 创建时间（epoch 毫秒；后端 LocalDateTime → 数字，类型兼容 number|string） */
    createTime?: string;
    updateTime?: string;
  }

  export interface PageReq extends PageParam {
    /** 模糊 */
    certName?: string;
    /** 精确（唯一键，模糊会误伤相似编号） */
    certNo?: string;
    /** 证照大类 code（字典 cert_category：IDENTITY/QUALIFICATION/PERFORMANCE/CREDIT）
     *  后端按 cert_type_category 展开为该大类下的类型集合（certType in (...)）；与 certType 同传取交集。
     *  R2 回执（2026-09-21）实现；大类下无类型时返回空集而非全量（避免"筛选没生效"的体感）。 */
    certCategory?: string;
    certType?: string;
    certStatus?: string;
    holderName?: string;
    verificationStatus?: string;
    /** 所属企业主键（历史字段）。**「所属企业名录」后端未实施（后端口径已定不做）**，
     *  前端已撤下该筛选入口；字段保留仅为兼容既有请求体，不再由任何页面写入。 */
    companyId?: string;
    /** 有效期至 ≥ （yyyy-MM-dd；2026-09-18 新增，高级筛选用） */
    validToFrom?: string;
    /** 有效期至 ≤ （yyyy-MM-dd） */
    validToTo?: string;
    /** 有效期状态：LONG_TERM 长期有效 / EXPIRED 已过期 / EXPIRING 临期（2026-09-18 新增，高级筛选用） */
    validityState?: string;
  }

  /** 建档（§2.3）：certName/certNo 必填；holderType 默认 ORG；validTo 留空=长期  * 契约来源：CertificateSaveReqVO
   */
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

  /** 状态流转（§2.5）：targetStatus 取 dicts.cert_status；终态不可再流转  * 契约来源：CertificateStatusChangeReqVO
   */
  export interface StatusChangeReq {
    certificateId: number;
    targetStatus: string;
    version: number;
    /** 吊销/注销事由（建议必填） */
    reason?: string;
  }

  /* ===================== §3 文件版本 =====================  * 契约来源：CertFileVersionRespVO
     */
  export interface CertFileVersion {
    id: number;
    certId: number;
    fileCategory: string;
    /** 文件类别名称（字典 file_category；后端已下发，直接显示 —— 2026-09-20 字典治理） */
    fileCategoryName?: string;
    fileName: string;
    fileId: number;
    fileFormat?: string;
    /** 文件大小（字节） */
    fileSize?: number;
    version: number;
    /** 当前生效版本 */
    isCurrent: boolean;
    /** 是否可预览/下载（false=病毒未过或已作废）：**据此禁用按钮**，不要点了才报错 */
    downloadable?: boolean;
    /** 预览/下载地址（业务受控出口 /admin-api/bidding/file/download?id=xxx，**浏览器内联预览**，非对象存储裸 url）
     *  不可预览时为 null —— 直接用，不要自己拼 */
    previewUrl?: null | string;
  /** 文件 SHA-256（**后端字段名是 fileHash**：本页曾错写 sha256，导致该列恒为空——写入侧用的是 fileHash，读取侧却绑 sha256，2026-09-14 修正） */
  fileHash?: string;
    /** 病毒扫描状态（后端字段名为 **virusStatus**：UNKNOWN/PENDING/CLEAN/INFECTED） */
    virusStatus?: string;
    /** @deprecated 后端已统一为 virusStatus（提交 79193301a 统一病毒扫描字段口径），勿再读取 */
    virusScanStatus?: string;
    /** 是否已作废（F2；作废行保留展示，列表需标灰/加「已作废」角标） */
    voided?: boolean;
    /** 作废原因（仅作废行非空） */
    voidReason?: string;
    /** 作废时间（仅作废行非空） */
    voidedAt?: null | string;
    /** 备注（展示/留痕类；F2 更正目标字段，契约 CertFileVersionRespVO） */
    remark?: string;
    /** 加注件制作者（GB/T 36903 5.4.2 JZJZZZ；F2 更正目标字段） */
    copyCreator?: string;
    /** 加注件制作事由（GB/T 36903 5.4.3 JZJZZSY；F2 更正目标字段） */
    copyCause?: string;
    /** 加注时间（epoch 毫秒；F2 更正目标字段） */
    copyCreationTime?: null | string;
    copyExpiringTime?: null | string;
    /** 创建时间（epoch 毫秒；后端 LocalDateTime → 数字，类型兼容 number|string） */
    createTime?: string;
  }

  /** 登记文件版本（§3.1）：前置=已用 /bidding/file/upload 拿到 fileId  * 契约来源：CertFileRegisterReqVO
   */
  export interface FileRegisterReq {
    certId: number;
    fileCategory: string;
    fileName: string;
    fileId: number;
    /** SHA-256（建议传：同证同哈希判重与防篡改） */
    fileHash?: string;
    fileFormat?: string;
    /** 文件大小（字节） */
    fileSize?: number;
    /** 加注时间/加注有效期：LocalDateTime → **epoch 毫秒**（平台无 @JsonFormat，统一毫秒序列化） */
    copyCreationTime?: number;
    copyCreator?: string;
    copyCause?: string;
    copyExpiringTime?: number;
    remark?: string;
  }

  /* ===================== §4 验真 =====================  * 契约来源：CertificateVerifyReqVO
     */
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

  /** 契约来源：CertLifecycleApplyReqVO.FieldChange（嵌套类；CHANGE 申请一行一字段） */
export interface FieldChange {
    fieldName: string;
    oldValue?: string;
    newValue?: string;
  }

  /** 发起流程（§6.1）：按类型条件必填（changes / renewalType / revokeType / reviewYear…），后端逐项校验  * 契约来源：CertLifecycleApplyReqVO
   */
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

  /** 证件生命周期附件清单项（契约 Q16 / CertLifecycleFileRespVO） */
export interface CertLifecycleFileItem {
    /** 文件主键（FileApi fileId，一致性依据） */
    fileId: number;
    /** 文件名（本地台账可解析时给出；佐证材料经 FileApi 上传的为空） */
    fileName?: string;
    /** 文件类别 code（ORIGINAL/COPY/CHANGE_PAGE/RENEWAL_PAGE/E_CERT/OTHER） */
    fileCategory?: string;
    /** 上传时间 */
    uploadTime?: number | string;
  }

  /** 契约来源：CertLifecycleRespVO（GET /bidding/cert-lifecycle/page） */
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
    /** 附件清单（契约 Q16 / CertLifecycleFileRespVO：fileId/文件名/类别/上传时间）
     *  说明：佐证材料经 FileApi 上传的 fileName 为空（后端按 id 查元数据未开放），
     *  前端经上传回执缓存（views/bidding/vo/fileRefCache）补齐名称与可访问 URL。 */
    files?: CertLifecycleFileItem[];
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

  /** 审核流转（§6.2）：REVIEWING 受理 / APPROVED 通过 / REJECTED 驳回（驳回建议必填 remark）  * 契约来源：CertLifecycleAuditReqVO
   */
  export interface LifecycleAuditReq {
    id: number;
    targetStatus: string;
    version: number;
    remark?: string;
  }

  /** 生效（§6.3）：仅 APPROVED 可生效，会回写台账  * 契约来源：CertLifecycleEffectReqVO
   */
  export interface LifecycleEffectReq {
    id: number;
    version: number;
    remark?: string;
  }

  /* ===================== §7 材料完备性 =====================  * 契约来源：EnterpriseMaterialCompletenessRespVO.MaterialItem（嵌套类）
   */
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

  /* ===================== §3.3 F2 文件版本作废/更正（后端变更通知-给前端.md §F2，2026-09-26） ===================== */
  /** 作废证照文件版本（软删留痕；作废原因必填，是整改依据）——契约出处：CertFileVoidReqVO */
  export interface CertFileVoidReq {
    /** 文件版本主键（file/list 返回的 id） */
    fileVersionId: number | string;
    /** 作废原因（必填，留痕用） */
    reason: string;
  }
  /** 更正证照文件版本元数据（只允许更正展示/留痕类字段；至少传一个）——契约出处：CertFileCorrectReqVO */
  export interface CertFileCorrectReq {
    fileVersionId: number | string;
    /** 备注（可空：不传则不改） */
    remark?: string;
    /** 加注件制作时间（GB/T 36903 5.4.1 JZJZZSJ；epoch 毫秒） */
    copyCreationTime?: number;
    /** 加注件制作者（GB/T 36903 5.4.2 JZJZZZ） */
    copyCreator?: string;
    /** 加注件制作事由（GB/T 36903 5.4.3 JZJZZSY） */
    copyCause?: string;
    /** 加注件有效期截止时间（GB/T 36903 5.4.4 JZJYXQJZSJ；epoch 毫秒） */
    copyExpiringTime?: number;
  }

  /* ===================== §5.1 证照到期预警清单（后端变更通知-给前端.md §7） ===================== */
  /** 证照预警分页参数——契约出处：CertificateAlertPageReqVO */
  export interface CertAlertPageReq extends PageParam {
    /** 企业主体 id（空=本租户全部） */
    entId?: number;
    /** 某张证照的预警历史 */
    certId?: number;
    /** EXPIRY 到期 / REVIEW 复审 / RENEWAL 换证 */
    alertType?: string;
    /** INFO / WARNING / CRITICAL */
    alertLevel?: string;
    /** false=待处理；true=已处理；不传=全部 */
    handled?: boolean;
  }
  /** 证照预警行——契约出处：CertificateAlertRespVO（refName 可能为空：证照类以 certId 定位） */
  export interface CertAlert {
    id: number;
    /** 版本号（将来标记已处理时原样回传，乐观锁） */
    version: number;
    /** 证照 id（证照类预警填；资质/人员类为空） */
    certId?: number;
    refType?: string;
    refId?: number;
    /** 业务对象显示名（证照类可能为空） */
    refName?: string;
    /** EXPIRY / REVIEW / RENEWAL */
    alertType: string;
    /** INFO / WARNING / CRITICAL */
    alertLevel: string;
    /** 预警内容（文案已含证照名与剩余天数，直接展示） */
    alertMessage: string;
    triggeredAt: number;
    handled: boolean;
    handledBy?: number;
    handledAt?: number;
    handleNote?: string;
  }

  /* ===================== §8 统一候选选项（24-统一候选接口-options-契约-2026-09-15） ===================== */
  /** 候选类型：SUBJECT / PERSONNEL / PROJECT / CERTIFICATE / QUALIFICATION / SAFETY_PERMIT / PERSONNEL_CERT */
  export type OptionType =
    | 'CERTIFICATE'
    /* 注：曾登记的 SUBJECT_COMPANY（所属企业名录）**后端未实施**，后端 BiddingOptionTypeEnum
       不含该类型（传入会返回「候选类型不支持」）⇒ 已从类型联合中移除，不要再使用。 */
    | 'PERSONNEL'
    | 'PERSONNEL_CERT'
    | 'PROJECT'
    | 'QUALIFICATION'
    | 'SAFETY_PERMIT'
    | 'SUBJECT';
  /** 统一候选选项项——契约出处：OptionItemRespVO（value 为雪花 ID 十进制字符串，原样回传禁止 Number()） */
  export interface OptionItem {
    /** 主键十进制字符串（前端原样回传，禁止 Number()） */
    value: string;
    /** 展示名（名称全空时回落为「未命名」） */
    label: string;
    /** 区分同名对象：统一社会信用代码 / 证书编号 / 工号 / 发证机关 */
    extra?: null | string;
    /** true=置灰但保留展示（失效证照、离职人员），不要过滤 */
    disabled: boolean;
    /** 该行业务状态 code（配合字典展示） */
    status?: null | string;
  }

  /* ===================== 2026-09-18 原型改造新增（契约：后端接口契约-资质证书五页改造） ===================== */

  /** 证照统计（CertificateStatsRespVO）——看板用；按状态逐码回传，前端不分桶猜口径 */
  export interface CertificateStats {
    total?: number;
    /** 生效可用（cert_status tagType=success 的各码计数） */
    active?: number;
    /** 临期（tagType=warning：即将到期 + 暂扣） */
    expiring?: number;
    /** 异常/过期（tagType=error：已过期 + 吊销） */
    abnormal?: number;
    /** 已注销（tagType=info） */
    cancelled?: number;
    /** 未验真（verification_status=UNVERIFIED） */
    unverified?: number;
    /** 按 certStatus 逐码计数，键为字典 code（前端不硬编码状态集合） */
    byStatus?: Record<string, number>;
  }

  /** 批量验真入参（CertificateVerifyBatchReqVO） */
  export interface VerifyBatchReq {
    /** 证照主键（十进制字符串，原样回传） */
    certIds: string[];
    /** 验真渠道 code（字典 verify_channel）；不传由后端按类别选默认渠道 */
    verifyChannel?: string;
  }

  /** 批量验真逐条结果（CertificateVerifyBatchRespVO.items 元素） */
  export interface VerifyBatchItem {
    certId?: string;
    certName?: string;
    /** 是否本次受理成功（不代表验真结论为"一致"） */
    accepted?: boolean;
    /** 验真结论 code（字典 verify_result：MATCH/MISMATCH/UNAVAILABLE） */
    verifyResult?: null | string;
    verifyChannel?: null | string;
    /** 失败原因（accepted=false 时有值） */
    message?: null | string;
  }

  /** 批量导入回执（CertificateImportRespVO） */
  export interface ImportResp {
    /** 总行数（不含表头） */
    totalRows?: number;
    successCount?: number;
    failCount?: number;
    /** 失败行明细（行号从 2 起，含表头为第 1 行） */
    failures?: Array<{ message?: string; rowNum?: number }>;
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
/**
 * 电子证照域错误码段位（后端 ErrorCodeConstants，2026-09-26 段位迁移）：
 *   · 1_060_109_xxx = 证照台账/验真/预警（原 106 段 1060106xxx 迁来，避开与公告/采集域 000~026 重叠）；
 *   · 1_060_107_xxx = 员工资质/生命周期/材料完备性/安许宽限多岗。
 * 前端与后端数值必须一致（否则按码分支永不命中），迁移前对照 ErrorCodeConstants 逐项核对。
 */
export const CERT_ERR = {
  CERT_NOT_FOUND: 1_060_109_000,
  CERT_NO_DUPLICATE: 1_060_109_001,
  OPTIMISTIC_LOCK: 1_060_109_002,
  STATUS_FLOW_DENIED: 1_060_109_003,
  /** 预警未启用或阈值未配置：证照域无该语义码，语义对应员工域 1_060_107_012（PERSONNEL_ALERT_SCAN_DISABLED） */
  ALERT_NOT_CONFIGURED: 1_060_107_012,
  LIFECYCLE_NOT_FOUND: 1_060_107_015,
  LIFECYCLE_FLOW_DENIED: 1_060_107_016,
  LIFECYCLE_OPTIMISTIC_LOCK: 1_060_107_017,
  CHANGE_EMPTY: 1_060_107_018,
  LIFECYCLE_FIELD_MISSING: 1_060_107_020,
  APPLY_NO_DUPLICATE: 1_060_107_021,
  LIFECYCLE_BIZ_NOT_FOUND: 1_060_107_022,
  MATERIAL_INCOMPLETE: 1_060_107_013,
  MATERIAL_LIST_EMPTY: 1_060_107_014,
  /* v1.1 安许风险联动（契约 §13） */
  SAFETY_PERMIT_NOT_EXISTS: 1_060_109_024,
  SAFETY_GRACE_REASON_NOT_ALLOWED: 1_060_107_025,
  SAFETY_GRACE_DAYS_EXCEEDED: 1_060_107_026,
  MULTI_POST_SAME_POST: 1_060_107_027,
  KEY_POST_INVALID: 1_060_107_028,
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

/** 参数校验失败（400）时的逐字段错误（契约 §0.4 ValidationErrorData）  * 契约来源：FieldErrorRespVO
 */
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

/* ========== §3.3 F2 文件版本作废/更正（后端变更通知-给前端.md §F2） ========== */
/** 作废证照文件版本（软删留痕；作废原因必填） */
export function voidCertFile(data: CertificateApi.CertFileVoidReq) {
  return requestClient.post<boolean>('/bidding/certificate/file/void', data, { headers: { 'X-Silent-Error': 'true' } });
}
/** 更正证照文件版本元数据（备注 + 加注件 copy_* 四要素；至少传一个） */
export function correctCertFile(data: CertificateApi.CertFileCorrectReq) {
  return requestClient.post<boolean>('/bidding/certificate/file/correct', data, { headers: { 'X-Silent-Error': 'true' } });
}

/* ========== §5.1 证照到期预警清单（后端变更通知-给前端.md §7；只读，处理入口待后端开放） ========== */
export function getCertAlertPage(params: CertificateApi.CertAlertPageReq) {
  return requestClient.get<PageResult<CertificateApi.CertAlert>>('/bidding/certificate-alert-run/alert/page', {
    params,
    headers: { 'X-Silent-Error': 'true' },
  });
}

/* ========== §8 统一候选选项（24-统一候选接口-options-契约-2026-09-15：消灭手输主键） ========== */
/** 统一候选选项：value 为雪花 ID 十进制字符串，前端原样回传、禁止 Number() */
export function getMetaOptions(type: CertificateApi.OptionType, keyword?: string, limit?: number) {
  return requestClient.get<CertificateApi.OptionItem[]>('/bidding/meta/options', {
    params: { type, keyword, limit },
    headers: { 'X-Silent-Error': 'true' },
  });
}

/* ========== §9 看板统计 / 批量操作 / 催办（2026-09-18 原型改造；后端接口契约见
 *            docs/电子证照前前端契约/后端接口契约-资质证书五页改造-2026-09-18.md） ==========
 * 说明：以下四类接口**后端尚未实现**。前端按契约先行接入；未就绪时请求层提示错误、页面保留空态，不白屏。 */

/** 契约：GET /bidding/certificate/stats —— 证照看板全量统计（含暂扣/吊销/注销）；
 *  权限 bidding:certificate:query。
 *  替代方案：如果该接口未就绪，页面可用 getCertificatePage(pageSize=1) 并发计数兜住前四类，
 *  但拿不到"已注销"且状态参数只能传单个 code。 */
export function getCertificateStats() {
  return requestClient.get<CertificateApi.CertificateStats>('/bidding/certificate/stats', {
    headers: { 'X-Silent-Error': 'true' },
  });
}

/** 契约：POST /bidding/certificate/import —— 批量导入证照（先经 /bidding/file/upload 拿 fileId）；
 *  返回**行级回执**（成功数 / 失败行号 + 原因）；权限 bidding:certificate:import。 */
export function importCertificates(data: { fileId: number }) {
  return requestClient.post<CertificateApi.ImportResp>('/bidding/certificate/import', data, {
    headers: { 'X-Silent-Error': 'true' },
  });
}

/** 契约：POST /bidding/certificate-verify/verify-batch —— 批量验真（逐条结果，失败不影响其余）；
 *  权限复用 bidding:certificate:verify。**前端不做"循环调单条"**（会真实触发 N 次验真、无事务无进度）。 */
export function batchVerifyCertificates(data: CertificateApi.VerifyBatchReq) {
  return requestClient.post<{ items?: CertificateApi.VerifyBatchItem[] }>(
    '/bidding/certificate-verify/verify-batch',
    data,
    { headers: { 'X-Silent-Error': 'true' } },
  );
}

/** 契约：POST /bidding/cert-lifecycle/urge —— 生命周期节点催办（产出通知/待办，幂等）；
 *  权限 bidding:certificate:lifecycle:urge。 */
export function urgeCertLifecycle(data: { id: number | string; remark?: string }) {
  return requestClient.post<boolean>('/bidding/cert-lifecycle/urge', data, {
    headers: { 'X-Silent-Error': 'true' },
  });
}
