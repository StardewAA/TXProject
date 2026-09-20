/**
 * 简历优化逐项确认的纯逻辑（A03）。
 *
 * 抽成纯函数便于单测：组件只负责渲染，决定、统计与提交载荷都由这里计算。
 */
import type { HrmPortalRecruitAgentApi } from '#/api/hrm/portal/recruit-agent';

export type Decision = HrmPortalRecruitAgentApi.OptimizeDecision;

export interface DecisionState {
  decision: Decision;
  finalValue: string;
}

export type DecisionMap = Record<number, DecisionState>;

export interface DecisionStats {
  accepted: number;
  adjusted: number;
  decided: number;
  pending: number;
  rejected: number;
}

/** 初始状态：全部「未决定」，必须由用户逐项确认后才可保存 */
export function createInitialDecisions(): DecisionMap {
  return {};
}

/**
 * 设置某一项的决定。
 *
 * 最终值规则：接受用建议值、拒绝用原值、手工调整在已有编辑基础上继续，
 * 没有编辑过时以建议值为起点，方便用户在建议上微调。
 */
export function applyDecision(
  decisions: DecisionMap,
  index: number,
  decision: Decision,
  item: Pick<
    HrmPortalRecruitAgentApi.OptimizeItem,
    'originalValue' | 'suggestedValue'
  >,
): DecisionMap {
  const existing = decisions[index];
  const finalValue =
    decision === 'REJECT'
      ? item.originalValue
      : decision === 'ACCEPT'
        ? item.suggestedValue
        : existing?.finalValue || item.suggestedValue;
  return { ...decisions, [index]: { decision, finalValue } };
}

/** 修改手工调整的最终值 */
export function updateFinalValue(
  decisions: DecisionMap,
  index: number,
  value: string,
): DecisionMap {
  const existing = decisions[index];
  if (!existing) return decisions;
  return { ...decisions, [index]: { ...existing, finalValue: value } };
}

/** 批量设置（全部接受 / 全部拒绝） */
export function applyDecisionToAll(
  decisions: DecisionMap,
  items: Array<
    Pick<HrmPortalRecruitAgentApi.OptimizeItem, 'originalValue' | 'suggestedValue'>
  >,
  decision: Decision,
): DecisionMap {
  let next: DecisionMap = { ...decisions };
  items.forEach((item, index) => {
    next = applyDecision(next, index, decision, item);
  });
  return next;
}

/** 统计各项数量，用于界面提示与保存按钮可用性 */
export function summarize(decisions: DecisionMap, itemCount: number): DecisionStats {
  let accepted = 0;
  let adjusted = 0;
  let rejected = 0;
  let decided = 0;
  for (let index = 0; index < itemCount; index++) {
    const state = decisions[index];
    if (!state) continue;
    decided++;
    if (state.decision === 'ACCEPT') accepted++;
    else if (state.decision === 'ADJUST') adjusted++;
    else rejected++;
  }
  return { accepted, adjusted, decided, pending: itemCount - decided, rejected };
}

/** 是否允许保存：必须逐项确认完毕 */
export function canSave(stats: DecisionStats, itemCount: number, saving: boolean) {
  return itemCount > 0 && stats.pending === 0 && !saving;
}

/** 生成提交载荷；未决定的项不提交 */
export function buildDecisionPayload(
  items: HrmPortalRecruitAgentApi.OptimizeItem[],
  decisions: DecisionMap,
): HrmPortalRecruitAgentApi.OptimizeDecisionItem[] {
  const payload: HrmPortalRecruitAgentApi.OptimizeDecisionItem[] = [];
  items.forEach((item, index) => {
    const state = decisions[index];
    if (!state) return;
    payload.push({
      module: item.module,
      fieldPath: item.fieldPath,
      originalValue: item.originalValue,
      suggestedValue: item.suggestedValue,
      finalValue: state.finalValue,
      decision: state.decision,
    });
  });
  return payload;
}

/** 生成版本修改摘要 */
export function buildChangeSummary(stats: DecisionStats) {
  return `确认优化建议（接受 ${stats.accepted} 项、调整 ${stats.adjusted} 项、拒绝 ${stats.rejected} 项）`;
}
