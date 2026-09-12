import type { PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

/** 契约来源：《电子证照模块-前端接口契约-v1.1-安许风险联动.md》§12（v1.1 为纯增量，v1.0 接口未改）
 *  + 后端 SafetyPermitRiskController / VO 字段逐一核对（含"契约文字有歧义处以后端为准"的修正）
 *  权限：bidding:qualification:permit:{query,update}（#1~#4，**当前菜单尚未登记，会 403**）、
 *        bidding:personnel:cert:query（#5）、bidding:personnel:profile:query（#6）、bidding:project:query（#7）
 *  口径：riskFlag=null 表示"未评估、不阻断"；blocked 命中 safety.abc.block.flags 时为硬阻断。
 *  说明：本文件只做接口封装，不做本地缓存；页面按需自行决定是否缓存（建议 60s + 提交前强制重取）。 */
export namespace SafetyPermitApi {
  /** #1 风险状态（GET /bidding/safety-permit/risk） */
  export interface Risk {
    permitId?: number;
    entId?: number;
    /** 字典 safety_risk_flag（NORMAL/YELLOW/RED/INVALID/FREQUENT）；**null = 未评估，不阻断** */
    riskFlag?: null | string;
    /** 字典 risk_level（LOW/MEDIUM/HIGH/MAJOR） */
    riskLevel?: null | string;
    aCount?: number;
    bCount?: number;
    cCount?: number;
    graceApplied?: boolean;
    graceExpireAt?: null | string;
    graceReason?: null | string;
    /** 命中 safety.abc.block.flags → 硬阻断 */
    blocked?: boolean;
    /** 命中规则说明（展示用文案，后端已组装） */
    hitRules?: null | string;
    ruleVersion?: string;
    riskEvaluatedAt?: null | string;
  }

  /** #3 风险快照（含哈希链，审计可比对） */
  export interface RiskSnapshot {
    id: number;
    riskFlag?: string;
    prevRiskFlag?: string;
    riskLevel?: string;
    aCount?: number;
    bCount?: number;
    cCount?: number;
    graceApplied?: boolean;
    hitRulesJson?: string;
    minRequiredJson?: string;
    ruleVersion?: string;
    batchNo?: string;
    evaluatedAt?: string;
    /** 本批哈希与上批哈希（校验链是否断裂） */
    recordHash?: string;
    prevHash?: string;
  }

  /** #4 整改宽限登记（原因须在 safety.abc.grace.reason.whitelist 内，天数不得超配置上限） */
  export interface GraceRegisterReq {
    permitId?: number;
    entId?: number;
    reason: string;
    sourceRef?: string;
    days?: number;
    remark?: string;
  }

  /** #5 多岗兼任校验 */
  export interface MultiPostCheck {
    pass?: boolean;
    requiredCertType?: null | string;
    requiredCertSubType?: null | string;
    matchedCertId?: null | number;
    failReason?: null | string;
    checkedAt?: null | number | string;
  }

  /** #6 人员风险（不自动拒绝，仅提示留痕） */
  export interface PersonRisk {
    personId?: number;
    /** 字典 person_risk_flag（NORMAL/ABNORMAL/FREQUENT） */
    personRiskFlag?: null | string;
    jobChangeCount12m?: number;
    jobChangeWindowStart?: null | number | string;
    abnormal?: boolean;
    personRiskEvaluatedAt?: null | number | string;
  }

  /** #7 项目关键岗位变更超限 */
  export interface KeyPostChanges {
    projectId?: number;
    windowDays?: number;
    windowStart?: null | number | string;
    anyExceeded?: boolean;
    items?: Array<{
      changeCount?: number;
      exceeded?: boolean;
      lastChangeDate?: null | number | string;
      personIds?: number[];
      postCode?: string;
      postLabel?: string;
      threshold?: number;
    }>;
  }

  /** #7b 关键岗位任职/变更登记（自动闭合上任任期；达阈值当场提示挂靠嫌疑） */
  export interface KeyPostAssignmentReq {
    projectId: number;
    postCode: string;
    personId: number;
    /** 任职开始日 'YYYY-MM-DD'；留空 = 后端取今天 */
    startAt?: null | string;
    /** 变更原因（留痕，便于申诉时说明） */
    changeReason?: string;
  }

  /* 出参逐字对齐后端 KeyPostAssignmentRespVO —— 后端把"窗口内变更次数 / 阈值 / 是否超阈值 / 原任职人"
   * 一起回传，是为了让前端在**保存成功的同一屏**给出挂靠嫌疑结论（该 VO 的 Javadoc 明确写了这个口径：
   * "若让前端保存后再调一次统计接口，会出现保存成功但提示闪烁/失败的中间态"）。
   * 教训：本类型此前只声明了 7 个字段，漏掉下面 7 个风险字段 —— 类型不全会让"后端算了风险、前端根本没接"
   * 这类缺口在编译期完全静默（vue-tsc 一旦补齐类型即可当场暴露）。 */
  export interface KeyPostAssignmentResp {
    id?: number;
    projectId?: number;
    postCode?: string;
    postLabel?: string;
    personId?: number;
    /** LocalDate → 'YYYY-MM-DD' */
    startAt?: null | string;
    /** 该岗位第几次变更（首次任职为 1） */
    changeSeq?: number;
    /** 被本次变更结束任期的上一位人员（首次任职为空）；前端解析成姓名后展示 */
    previousPersonId?: null | number;
    /** 上一位人员的任期结束日（= 本次任职开始日；首次任职为空）LocalDate → 'YYYY-MM-DD' */
    previousEndAt?: null | string;
    /** 统计窗口天数（配置中心 project.key.post.change.window.days） */
    windowDays?: number;
    /** 统计窗口起点 LocalDate → 'YYYY-MM-DD' */
    windowStart?: null | string;
    /** 窗口内该岗位变更次数（含本次；首次任职算 0） */
    windowChangeCount?: number;
    /** 变更次数阈值（配置中心 project.key.post.change.threshold） */
    threshold?: number;
    /** 是否已达阈值（true = 前端应提示挂靠嫌疑） */
    exceeded?: boolean;
  }
}

/** #1 读取安许风险状态（entId 必填；asOf 可回填做历史预演） */
export function getSafetyPermitRisk(entId: number | string, asOf?: string) {
  return requestClient.get<SafetyPermitApi.Risk>('/bidding/safety-permit/risk', { params: { entId, asOf } });
}

/** #2 重算安许风险；dryRun=true 只算不落库（后端为 query 参数，非 JSON body） */
export function recheckSafetyPermitRisk(permitId: number | string, asOf?: string, dryRun = false) {
  return requestClient.post<SafetyPermitApi.Risk>('/bidding/safety-permit/risk/recheck', null, {
    params: { permitId, asOf, dryRun },
  });
}

/** #3 安许风险快照分页（permitId 必填） */
export function getSafetyPermitRiskHistory(permitId: number | string, pageNo = 1, pageSize = 10) {
  return requestClient.get<PageResult<SafetyPermitApi.RiskSnapshot>>('/bidding/safety-permit/risk/history', {
    params: { permitId, pageNo, pageSize },
  });
}

/** #4 登记整改宽限（permitId / entId 二选一必填） */
export function registerSafetyGrace(data: SafetyPermitApi.GraceRegisterReq) {
  return requestClient.post<boolean>('/bidding/safety-permit/grace', data);
}

/** #5 多岗兼任合规校验（兼任岗位须持对应考核合格证；结论落库留痗） */
export function checkMultiPost(data: { personId: number; primaryPost: string; secondaryPost: string }) {
  return requestClient.post<SafetyPermitApi.MultiPostCheck>(
    '/bidding/safety-permit/personnel/multi-post-check',
    data,
  );
}

/** #6 人员风险（人员维度：12 个月岗位变更次数等） */
export function getPersonnelRisk(personId: number | string) {
  return requestClient.get<SafetyPermitApi.PersonRisk>('/bidding/safety-permit/personnel/risk', {
    params: { personId },
  });
}

/** #7 项目关键岗位变更（windowDays 缺省由后端取配置 project.key.post.change.window.days） */
export function getProjectKeyPostChanges(projectId: number | string, windowDays?: number) {
  return requestClient.get<SafetyPermitApi.KeyPostChanges>('/bidding/safety-permit/project/key-post-changes', {
    params: { projectId, windowDays },
  });
}

/** #7b 登记关键岗位任职/变更（@Idempotent；后端自动闭合上任任期，达阈值当场提示挂靠嫌疑，待办由 Job 产出） */
export function assignKeyPost(data: SafetyPermitApi.KeyPostAssignmentReq) {
  return requestClient.post<SafetyPermitApi.KeyPostAssignmentResp>(
    '/bidding/safety-permit/project/key-post-assignment',
    data,
  );
}
