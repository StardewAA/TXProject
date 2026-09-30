import { describe, expect, it } from 'vitest';

import type { HrmPortalRecruitAgentApi } from '#/api/hrm/portal/recruit-agent';

import {
  applyDecision,
  applyDecisionToAll,
  buildChangeSummary,
  buildDecisionPayload,
  canSave,
  createInitialDecisions,
  summarize,
  updateFinalValue,
} from './resume-optimize-decisions';

type Item = Pick<
  HrmPortalRecruitAgentApi.OptimizeItem,
  'originalValue' | 'suggestedValue'
>;

const ITEMS: Item[] = [
  { originalValue: '负责现场施工组织', suggestedValue: '统筹现场施工组织与进度管控' },
  { originalValue: '累计交付 4 个项目', suggestedValue: '累计成功交付 4 个项目' },
  { originalValue: '熟悉管道保温工艺', suggestedValue: '熟练掌握管道保温工艺' },
];

describe('简历优化逐项确认逻辑', () => {
  it('初始状态全部为未决定，避免默认全量采纳', () => {
    const decisions = createInitialDecisions();
    expect(Object.keys(decisions)).toHaveLength(0);
    expect(summarize(decisions, ITEMS.length)).toEqual({
      accepted: 0,
      adjusted: 0,
      decided: 0,
      pending: 3,
      rejected: 0,
    });
  });

  it('接受时最终值取建议值', () => {
    const decisions = applyDecision(createInitialDecisions(), 0, 'ACCEPT', ITEMS[0]!);
    expect(decisions[0]).toEqual({
      decision: 'ACCEPT',
      finalValue: '统筹现场施工组织与进度管控',
    });
  });

  it('拒绝时最终值取原值，表示保持原样', () => {
    const decisions = applyDecision(createInitialDecisions(), 0, 'REJECT', ITEMS[0]!);
    expect(decisions[0]).toEqual({
      decision: 'REJECT',
      finalValue: '负责现场施工组织',
    });
  });

  it('手工调整以建议值为起点，便于在建议上微调', () => {
    const decisions = applyDecision(createInitialDecisions(), 0, 'ADJUST', ITEMS[0]!);
    expect(decisions[0]).toEqual({
      decision: 'ADJUST',
      finalValue: '统筹现场施工组织与进度管控',
    });
  });

  it('切换决定时保留已编辑的最终值', () => {
    let decisions = applyDecision(createInitialDecisions(), 0, 'ADJUST', ITEMS[0]!);
    decisions = updateFinalValue(decisions, 0, '我自己写的表述');
    // 切到接受再切回调整，不应丢掉用户编辑
    decisions = applyDecision(decisions, 0, 'ACCEPT', ITEMS[0]!);
    expect(decisions[0]!.finalValue).toBe('统筹现场施工组织与进度管控');
    decisions = applyDecision(decisions, 0, 'ADJUST', ITEMS[0]!);
    expect(decisions[0]!.finalValue).toBe('统筹现场施工组织与进度管控');
  });

  it('手工调整后修改最终值', () => {
    let decisions = applyDecision(createInitialDecisions(), 1, 'ADJUST', ITEMS[1]!);
    decisions = updateFinalValue(decisions, 1, '累计交付 4 个工业管道保温项目');
    expect(decisions[1]!.finalValue).toBe('累计交付 4 个工业管道保温项目');
  });

  it('未决定的项不接受最终值修改', () => {
    const decisions = createInitialDecisions();
    expect(updateFinalValue(decisions, 0, 'x')).toBe(decisions);
  });

  it('全部接受 / 全部拒绝会覆盖所有项', () => {
    const accepted = applyDecisionToAll(createInitialDecisions(), ITEMS, 'ACCEPT');
    expect(summarize(accepted, ITEMS.length)).toEqual({
      accepted: 3,
      adjusted: 0,
      decided: 3,
      pending: 0,
      rejected: 0,
    });

    const rejected = applyDecisionToAll(accepted, ITEMS, 'REJECT');
    expect(summarize(rejected, ITEMS.length)).toEqual({
      accepted: 0,
      adjusted: 0,
      decided: 3,
      pending: 0,
      rejected: 3,
    });
    expect(rejected[0]!.finalValue).toBe('负责现场施工组织');
  });

  it('存在未决定项时不允许保存', () => {
    const decisions = applyDecision(createInitialDecisions(), 0, 'ACCEPT', ITEMS[0]!);
    const stats = summarize(decisions, ITEMS.length);
    expect(canSave(stats, ITEMS.length, false)).toBe(false);
  });

  it('逐项确认完毕后允许保存；保存中或没有建议时不允许', () => {
    const decisions = applyDecisionToAll(createInitialDecisions(), ITEMS, 'ACCEPT');
    const stats = summarize(decisions, ITEMS.length);
    expect(canSave(stats, ITEMS.length, false)).toBe(true);
    expect(canSave(stats, ITEMS.length, true)).toBe(false);
    expect(canSave(summarize(createInitialDecisions(), 0), 0, false)).toBe(false);
  });

  it('提交载荷只包含已确认的项，并带齐原值/建议值/最终值/采纳状态', () => {
    let decisions = applyDecision(createInitialDecisions(), 0, 'ACCEPT', ITEMS[0]!);
    decisions = applyDecision(decisions, 2, 'REJECT', ITEMS[2]!);
    const fullItems = [
      { ...ITEMS[0]!, module: 'WORK_EXPERIENCE', fieldPath: 'description', reason: 'r' },
      { ...ITEMS[1]!, module: 'WORK_EXPERIENCE', fieldPath: 'achievements', reason: 'r' },
      { ...ITEMS[2]!, module: 'SKILLS', fieldPath: 'skills', reason: 'r' },
    ] as HrmPortalRecruitAgentApi.OptimizeItem[];

    const payload = buildDecisionPayload(fullItems, decisions);
    // 第 2 项未决定，不应提交
    expect(payload).toHaveLength(2);
    expect(payload[0]).toEqual({
      module: 'WORK_EXPERIENCE',
      fieldPath: 'description',
      originalValue: '负责现场施工组织',
      suggestedValue: '统筹现场施工组织与进度管控',
      finalValue: '统筹现场施工组织与进度管控',
      decision: 'ACCEPT',
    });
    expect(payload[1]!.decision).toBe('REJECT');
    expect(payload[1]!.finalValue).toBe('熟悉管道保温工艺');
  });

  it('修改摘要如实反映各类决定数量', () => {
    expect(
      buildChangeSummary({
        accepted: 2,
        adjusted: 1,
        decided: 4,
        pending: 0,
        rejected: 1,
      }),
    ).toBe('确认优化建议（接受 2 项、调整 1 项、拒绝 1 项）');
  });
});
