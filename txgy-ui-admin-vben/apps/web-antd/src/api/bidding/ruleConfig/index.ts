import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

/**
 * 规则配置中心（RuleConfigController + MetaController 的同一份 item 结构）
 * 权限：bidding:rule-config:query（get / history / audit/page）、bidding:rule-config:update（save / rollback）
 * 治权口径（§0.5 不写死阈值）：阈值一律来自本接口，前端不得内联默认值——页面上展示的 legalFloor / description
 * 也取自后端，避免"提示文案与后端校验范围分叉"。
 * 状态机（RuleConfigStatusEnum）：DRAFT→{PUBLISHED,GRAY,ROLLED_BACK}、PUBLISHED→{GRAY,ROLLED_BACK}、
 * GRAY→{PUBLISHED,ROLLED_BACK}、ROLLED_BACK→∅（已回滚版本不可再发布，只能改成新版本再发布）。
 * 乐观锁：查询返回 versionNo，保存时回传为 version（§0.7 并发改配置识别）。
 */
export namespace RuleConfigApi {
  /** 生效配置项（RuleConfigItemRespVO） */
  export interface Item {
    ruleKey: string;
    /** 已按 valueType 反序列化（数字/布尔/字符串/对象） */
    value: unknown;
    valueType: string;
    description?: string;
    regulationRef?: string;
    /** 允许边界，如 {"min":1,"max":365}；null=不限制 */
    legalFloor?: string;
    /** 乐观锁版本号 → 保存时作为 version 回传 */
    versionNo?: number;
    ruleGroup?: string;
    /** 配置来源：TENANT 租户级 / PLATFORM 平台默认 / FALLBACK 代码兜底 */
    source?: string;
  }

  /** 保存（RuleConfigSaveReqVO） */
  export interface SaveReq {
    ruleKey: string;
    /** 配置值；JSON 类型传 JSON 文本，后端按 valueType 强校验 */
    configValue: string;
    valueType: string;
    ruleGroup?: string;
    description?: string;
    regulationRef?: string;
    legalFloor?: string;
    /** 变更原因（必填） */
    changeReason: string;
    /** 写入目标状态：DRAFT / PUBLISHED / GRAY */
    status?: string;
    /** 灰度范围（status=GRAY 时有效） */
    grayScope?: string;
    /** 乐观锁：回传查询得到的 versionNo */
    version?: number;
  }

  /** 版本历史（RuleConfigHistoryRespVO） */
  export interface HistoryRow {
    id?: number;
    ruleKey?: string;
    fromVersion?: number;
    toVersion?: number;
    fromValue?: string;
    toValue?: string;
    changeAction?: string;
    changedBy?: string;
    changedAt?: null | number | string;
    changeReason?: string;
    recordHash?: string;
    prevHash?: string;
  }

  /** 读取审计（RuleConfigAuditRespVO）：谁在什么场景读了哪一版、命中哪一层 */
  export interface AuditRow {
    id?: number;
    ruleKey?: string;
    ruleVersion?: number;
    /** TENANT / PLATFORM / GRAY / FALLBACK */
    hitSource?: string;
    bizScene?: string;
    readBy?: string;
    readAt?: null | number | string;
    traceId?: string;
  }

  /** 读取审计分页入参（RuleConfigAuditPageReqVO） */
  export interface AuditPageReq extends PageParam {
    ruleKey?: string;
    hitSource?: string;
    bizScene?: string;
    readBy?: string;
    /** LocalDateTime；请求侧时间格式未在契约中约定，页面不传 */
    readAtFrom?: null | number | string;
    readAtTo?: null | number | string;
  }
}

/** 值类型（逐字取自 RuleConfigValueTypeEnum） */
export const RULE_VALUE_TYPE_OPTIONS = [
  { label: '整数 INT（天数/条数）', value: 'INT' },
  { label: '小数 DECIMAL（相似度）', value: 'DECIMAL' },
  { label: '布尔 BOOL（开关）', value: 'BOOL' },
  { label: '字符串 STRING（枚举码/模型标识）', value: 'STRING' },
  { label: 'JSON（多级阈值/映射矩阵）', value: 'JSON' },
];

/** 可写入的目标状态（ROLLED_BACK 只能由回滚接口产生，故不在写入选项内） */
export const RULE_STATUS_OPTIONS = [
  { label: '草稿 DRAFT（暂存不生效）', value: 'DRAFT' },
  { label: '发布 PUBLISHED（全租户生效）', value: 'PUBLISHED' },
  { label: '灰度 GRAY（仅 grayScope 命中租户）', value: 'GRAY' },
];

/** 状态迁移表（RuleConfigStatusEnum.ALLOWED）：用于保存前过滤非法目标状态 */
export const RULE_STATUS_TRANSITIONS: Record<string, string[]> = {
  DRAFT: ['DRAFT', 'GRAY', 'PUBLISHED', 'ROLLED_BACK'],
  GRAY: ['GRAY', 'PUBLISHED', 'ROLLED_BACK'],
  PUBLISHED: ['PUBLISHED', 'GRAY', 'ROLLED_BACK'],
  ROLLED_BACK: ['ROLLED_BACK'],
};

/** 解析命中层（hitSource；FALLBACK 为代码兜底） */
export const RULE_HIT_SOURCE_OPTIONS = [
  { label: '租户级 TENANT', value: 'TENANT' },
  { label: '平台默认 PLATFORM', value: 'PLATFORM' },
  { label: '灰度 GRAY', value: 'GRAY' },
  { label: '代码兜底 FALLBACK', value: 'FALLBACK' },
];

/**
 * 常用规则键（来自《电子资质七大模块技术方案》阈值清单；仅作输入提示，实际边界以后端 legalFloor 为准）。
 * 之所以内置这份清单：规则键是自由字符串，手打极易写错键名导致"配置了但不生效"。
 */
export const RULE_KEY_SUGGESTIONS: Array<{ key: string; note: string }> = [
  { key: 'cert.expiring.window.days', note: '证照到期预警窗口（天）' },
  { key: 'cert.annotation.max.days', note: '证照标注最长天数' },
  { key: 'safety.abc.required', note: '三类人员最低配备 {"A":1,"B":1,"C":1}' },
  { key: 'safety.abc.roles', note: '参与判定的角色集合' },
  { key: 'safety.abc.exempt.ent.types', note: '豁免矩阵：劳务分包 B 证为 0' },
  { key: 'safety.abc.grace.legal.person.change.days', note: '法定代表人变更宽限天数' },
  { key: 'safety.abc.grace.reason.whitelist', note: '允许触发宽限的原因白名单' },
  { key: 'safety.abc.frequent.alert.threshold', note: '频繁预警次数阈值' },
  { key: 'safety.abc.frequent.window.days', note: '频繁预警滑动窗口（天）' },
  { key: 'safety.abc.invalid.grace.days', note: '不整改转失效宽限（天）' },
  { key: 'safety.abc.block.flags', note: '阻断业务的档位' },
  { key: 'personnel.job.change.threshold', note: '人员更换受聘企业次数阈值' },
  { key: 'personnel.job.change.window.days', note: '人员变更统计窗口（天）' },
  { key: 'personnel.multi.post.require.cert', note: '兼任 ≥2 岗是否必须持另一岗位证书' },
  { key: 'project.key.post.change.threshold', note: '同项目同关键岗位变更次数阈值' },
  { key: 'project.key.post.change.window.days', note: '关键岗位变更统计窗口（天）' },
  { key: 'risk.level.mapping', note: 'risk_flag → risk_level 映射' },
  { key: 'inspection.lead.days.LOW', note: '低风险检查提前量（天）' },
  { key: 'inspection.lead.days.MEDIUM', note: '中风险检查提前量（天）' },
  { key: 'inspection.lead.days.HIGH', note: '高风险检查提前量（天）' },
  { key: 'inspection.lead.days.MAJOR', note: '重大风险检查提前量（天，0=立即）' },
  { key: 'inspection.phase.overrides', note: '阶段化覆盖 {"MAIN":{"LOW":30}}' },
];

/** 查询生效值（GET /bidding/rule-config/get?ruleKey=；权限 bidding:rule-config:query） */
export function getRuleConfig(ruleKey: string) {
  return requestClient.get<RuleConfigApi.Item>('/bidding/rule-config/get', { params: { ruleKey } });
}

/** 保存/发布配置（POST /bidding/rule-config/save；权限 bidding:rule-config:update；changeReason 必填） */
export function saveRuleConfig(data: RuleConfigApi.SaveReq) {
  return requestClient.post<number>('/bidding/rule-config/save', data);
}

/** 回滚到指定版本（POST /bidding/rule-config/rollback；权限 bidding:rule-config:update） */
export function rollbackRuleConfig(ruleKey: string, targetVersion: number, changeReason: string) {
  return requestClient.post<number>('/bidding/rule-config/rollback', null, {
    params: { changeReason, ruleKey, targetVersion },
  });
}

/** 版本历史（GET /bidding/rule-config/history?ruleKey=；权限 bidding:rule-config:query） */
export function getRuleConfigHistory(ruleKey: string) {
  return requestClient.get<RuleConfigApi.HistoryRow[]>('/bidding/rule-config/history', { params: { ruleKey } });
}

/** 读取审计分页（GET /bidding/rule-config/audit/page；权限 bidding:rule-config:query） */
export function getRuleConfigAuditPage(params: RuleConfigApi.AuditPageReq) {
  return requestClient.get<PageResult<RuleConfigApi.AuditRow>>('/bidding/rule-config/audit/page', { params });
}
