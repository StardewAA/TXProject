import type { TaskRunContext } from '../orchestrator/types.js';

/**
 * 取"智能体管理下发的提示词与技能片段"（Java GET /contract/agent-runtime）。
 *
 * 单任务（parse/outline/draft）直接用 ctx.runtime；
 * 全流程任务（generate）按阶段从 ctx.runtime.stages[stage] 取，实现"每类智能体各自的提示词/技能"。
 */
export interface StageOverrides {
  systemPromptOverride?: string;
  skillFragments?: Array<{ name: string; fragment: string }>;
}

export function stageOverrides(ctx: TaskRunContext, stage?: string): StageOverrides {
  const byStage = stage ? ctx.runtime?.stages?.[stage] : undefined;
  const source = byStage ?? ctx.runtime ?? undefined;
  const skills = source?.skills ?? [];
  return {
    systemPromptOverride: source?.systemPrompt?.trim() ? source.systemPrompt.trim() : undefined,
    skillFragments: skills
      .filter((skill) => Boolean(skill.promptFragment && skill.promptFragment.trim()))
      .map((skill) => ({ name: skill.name, fragment: skill.promptFragment as string })),
  };
}
