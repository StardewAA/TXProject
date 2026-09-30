import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

/** 静默失败：这些接口后端尚未实现，页面自行降级为空态；
 *  不该把"后端没上线"渲染成红色报错（否则进页面像坏了）。 */
const SILENT = { headers: { 'X-Silent-Error': 'true' } } as const;

/** 安许风险联动 · 风险条目与风险规则（原型「页面五」主数据区）
 *
 * 契约出处：docs/电子证照前前端契约/后端接口契约-资质证书五页改造-2026-09-18.md
 *
 * 与既有能力的边界（**别混用**）：
 *   · 既有 `safety-permit/*`（v1.1 §12）给的是**结论型**数据：安许 A/B/C 配备结论、人员风险标签、
 *     项目关键岗位变更超限统计——它们是"某一类风险算出来是什么"，**不是可指派、可流转的工单**。
 *   · 本文件定义的「风险条目」是**风险工单**：有等级、有责任人、有处理状态、可指派、可留痕。
 *     原型"风险大盘 + 风险清单 + 指派"要的是这一套。
 *   · 既有 `rule-config/*` 是**阈值键值**（ruleKey/value/legalFloor + 版本/审计）；
 *     原型"规则名称 / 触发条件 / 影响范围 / 启用禁用"是**规则实体**，另属 risk-rule。
 *
 * 说明：本模块后端尚未实现。前端按契约先行接入；未就绪时请求层提示、页面保留空态，不白屏。
 * 主键口径：雪花 ID 十进制字符串，原样回传、禁止 Number()。 */
export namespace SafetyRiskApi {
  /** 风险等级 code（字典 risk_level：HIGH/MEDIUM/LOW） */
  export type RiskLevelCode = 'HIGH' | 'LOW' | 'MEDIUM';

  /** 风险条目状态 code（字典 risk_item_status：PENDING 待处理 / ASSIGNED 已指派 / HANDLING 处理中 / HANDLED 已处理 / IGNORED 已忽略） */
  export type RiskStatus = 'ASSIGNED' | 'HANDLING' | 'HANDLED' | 'IGNORED' | 'PENDING';

  /** 风险条目（RiskItemRespVO） */
export interface RiskItem {
    /** 主键（雪花 ID 十进制字符串） */
    id?: string;
    /** 风险等级 code（字典 risk_level） */
    riskLevel?: string;
    /** 风险描述（原型"风险描述"列；建议后端拼好可读文案） */
    riskDesc?: string;
    /** 风险类型/来源 code（如 QUAL_EXPIRY / SAFETY_PERMIT / PERSON_RISK / KEY_POST_CHANGE） */
    riskType?: string;
    /** 关联证照（展示名快照；可为空=非证照类风险） */
    certRef?: null | string;
    certId?: null | string;
    /** 影响主体（企业/项目名快照） */
    entName?: null | string;
    entId?: null | string;
    /** 责任人（指派对象） */
    assigneeId?: null | string;
    assigneeName?: null | string;
    /** 处理状态 code（字典 risk_item_status） */
    status?: string;
    /** 发现时间（epoch 毫秒） */
    foundAt?: number;
    /** 处理/指派留痕 */
    handledBy?: null | string;
    handledAt?: null | number;
    remark?: null | string;
    /** 乐观锁（指派/处理必须回传） */
    version?: number;
  }

  /** 风险清单分页入参（RiskItemPageReqVO extends PageParam） */
export interface RiskItemPageReq extends PageParam {
    riskLevel?: string;
    status?: string;
    assigneeId?: string;
    /** 关键字（风险描述 / 关联证照 / 影响主体） */
    keyword?: string;
    entId?: string;
  }

  /** 风险大盘统计（RiskItemStatsRespVO） */
export interface RiskStats {
    high?: number;
    medium?: number;
    low?: number;
    handled?: number;
    total?: number;
  }

  /** 指派入参（RiskItemAssignReqVO） */
export interface RiskAssignReq {
    id: string;
    assigneeId: string;
    version?: number;
    remark?: string;
  }

  /** 处理入参（RiskItemHandleReqVO） */
  export interface RiskHandleReq {
    id: string;
    /** 目标状态（HANDLED 已处理 / IGNORED 已忽略 / HANDLING 处理中） */
    targetStatus: string;
    version?: number;
    remark?: string;
  }

  /* ===== 风险规则（原型"规则配置区"：规则名称 / 触发条件 / 影响范围 / 启用禁用） ===== */

  /** 风险规则（RiskRuleRespVO） */
  export interface Rule {
    id?: string;
    /** 规则名称 */
    ruleName?: string;
    /** 触发条件（自然语言或表达式；后端定） */
    condition?: string;
    /** 触发条件类型 code（字典 risk_rule_cond_type：THRESHOLD/EXPRESSION/NATURAL） */
    conditionType?: string;
    /** 影响范围（如"全部企业资质" / "安许 A/B/C" / 指定企业） */
    scope?: string;
    /** 关联阈值键（可选：与既有 rule-config 的 ruleKey 挂钩） */
    ruleKey?: null | string;
    /** 启用 / 禁用 */
    enabled?: boolean;
    /** 风险等级（该规则命中后产出的条目等级） */
    riskLevel?: string;
    remark?: string;
    version?: number;
    createTime?: number;
    updateTime?: number;
  }

  /** 规则分页入参 */
export interface RulePageReq extends PageParam {
    ruleName?: string;
    enabled?: boolean;
    riskLevel?: string;
  }

  /** 规则新增/修改入参（RiskRuleSaveReqVO） */
export interface RuleSaveReq {
    id?: string;
    version?: number;
    ruleName: string;
    condition?: string;
    conditionType?: string;
    scope?: string;
    ruleKey?: string;
    enabled?: boolean;
    riskLevel?: string;
    remark?: string;
  }
}

/** 契约：GET /bidding/safety-risk/page —— 风险条目分页；权限 bidding:safety-risk:query */
export function getRiskItemPage(params: SafetyRiskApi.RiskItemPageReq) {
  return requestClient.get<PageResult<SafetyRiskApi.RiskItem>>('/bidding/safety-risk/page', { params, ...SILENT });
}

/** 契约：GET /bidding/safety-risk/get?id= —— 风险详情 */
export function getRiskItem(id: string) {
  return requestClient.get<SafetyRiskApi.RiskItem>('/bidding/safety-risk/get', { params: { id }, ...SILENT });
}

/** 契约：GET /bidding/safety-risk/stats —— 风险大盘（高/中/低/已处理/总数） */
export function getRiskStats(params: { entId?: string } = {}) {
  return requestClient.get<SafetyRiskApi.RiskStats>('/bidding/safety-risk/stats', { params, ...SILENT });
}

/** 契约：POST /bidding/safety-risk/assign —— 指派责任人；权限 bidding:safety-risk:assign */
export function assignRiskItem(data: SafetyRiskApi.RiskAssignReq) {
  return requestClient.post<boolean>('/bidding/safety-risk/assign', data, {
    headers: { 'X-Silent-Error': 'true' },
  });
}

/** 契约：POST /bidding/safety-risk/handle —— 处理风险（状态流转）；权限 bidding:safety-risk:handle */
export function handleRiskItem(data: SafetyRiskApi.RiskHandleReq) {
  return requestClient.post<boolean>('/bidding/safety-risk/handle', data, {
    headers: { 'X-Silent-Error': 'true' },
  });
}

/** 契约：POST /bidding/safety-risk/scan —— 重新扫描风险（按启用规则重算并产出/更新风险条目）；
 *  权限 bidding:safety-risk:scan。注：与既有 safety-permit/risk/recheck（只算 A/B/C）不是一回事。 */
export function scanRiskItem() {
  return requestClient.post<number>('/bidding/safety-risk/scan', undefined, {
    headers: { 'X-Silent-Error': 'true' },
  });
}

/** 契约：POST /bidding/safety-risk/alert/batch —— 一键预警（对所选风险条目批量发提醒，幂等） */
export function batchAlertRiskItem(data: { ids: string[]; message?: string }) {
  return requestClient.post<number>('/bidding/safety-risk/alert/batch', data, {
    headers: { 'X-Silent-Error': 'true' },
  });
}

/** 契约：GET /bidding/safety-risk/export —— 导出风险报告（按当前筛选，文件流） */
export function exportRiskReport(params: SafetyRiskApi.RiskItemPageReq) {
  return requestClient.download('/bidding/safety-risk/export', { params });
}

/* ===== 风险规则 ===== */

/** 契约：GET /bidding/risk-rule/page —— 规则分页；权限 bidding:safety-risk:rule */
export function getRiskRulePage(params: SafetyRiskApi.RulePageReq) {
  return requestClient.get<PageResult<SafetyRiskApi.Rule>>('/bidding/risk-rule/page', { params, ...SILENT });
}

/** 契约：POST /bidding/risk-rule/create —— 新增规则 */
export function createRiskRule(data: SafetyRiskApi.RuleSaveReq) {
  return requestClient.post<string>('/bidding/risk-rule/create', data, {
    headers: { 'X-Silent-Error': 'true' },
  });
}

/** 契约：PUT /bidding/risk-rule/update —— 修改规则（乐观锁 version 必传） */
export function updateRiskRule(data: SafetyRiskApi.RuleSaveReq) {
  return requestClient.put<boolean>('/bidding/risk-rule/update', data, {
    headers: { 'X-Silent-Error': 'true' },
  });
}

/** 契约：PUT /bidding/risk-rule/status?id=&enabled= —— 启用 / 禁用规则 */
export function updateRiskRuleStatus(id: string, enabled: boolean) {
  return requestClient.put<boolean>('/bidding/risk-rule/status', undefined, { params: { id, enabled } });
}

/** 契约：DELETE /bidding/risk-rule/delete?id= —— 删除规则 */
export function deleteRiskRule(id: string) {
  return requestClient.delete<boolean>('/bidding/risk-rule/delete', { params: { id } });
}
