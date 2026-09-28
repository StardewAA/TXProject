import type { TaskHandler, TaskRunContext } from '../orchestrator/types.js';
import { PhaseMachine } from '../orchestrator/stateMachine.js';
import { draftTask } from './draftTask.js';
import { outlineTask } from './outlineTask.js';
import { parseTask } from './parseTask.js';

/**
 * 一键生成标书（全流程任务）：串起 parse → outline → draft。
 *
 * 设计要点：
 * 1. 阶段推进由本任务统一控制（PARSE/OUTLINE/DRAFT，与 Java 任务台账 current_step 对齐）；
 *    阶段 handler 内部的细粒度 phase patch 被收敛为当前阶段，避免"全流程任务没有 RETRIEVE 阶段"而报错。
 * 2. 不阻塞等人：全流程不启用 ask-user（工具白名单默认不含），未命中/待补充项汇总进 openQuestions。
 * 3. 模板回填不在任务阶段内做：字段取值与回填由导出阶段（bidDocxExporter）按「标签留白 / 书签」
 *    两种模板形态确定性完成（2026-09-22 起移除原 FILL/SDT 阶段）。
 * 4. 产出回写 Java：parse 后写项目事实卡/招标解析，outline 后写章节树；章节正文由草稿接口回写。
 */
export const generateTask: TaskHandler = {
  type: 'generate',
  displayName: '一键生成标书',

  async run(ctx: TaskRunContext) {
    const stages: Array<{ step: string; handler: TaskHandler }> = [
      { step: 'PARSE', handler: parseTask },
      { step: 'OUTLINE', handler: outlineTask },
      { step: 'DRAFT', handler: draftTask },
    ];

    const stageResults: Record<string, unknown> = {};
    const openQuestions: unknown[] = [];

    for (const stage of stages) {
      await ctx.patch({ phase: stage.step, progress: progressOf(stage.step) });
      // 阶段作用域上下文：收敛内部 phase（只上报当前阶段），阶段推进统一由本任务控制
      const [stageStart, stageEnd] = STAGE_RANGE[stage.step] ?? [0, 1];
      const stageSpan = Math.max(stageEnd - stageStart, 0.0001);
      const stageCtx: TaskRunContext = {
        ...ctx,
        // 相位机也要换作用域：SELECT/RETRIEVE/WRITE/VERIFY/COMMIT 这些细粒度相位属于子任务自身，
        // 全流程相位机只有 PARSE/OUTLINE/DRAFT，直接复用会在 draft 阶段抛「未知阶段 SELECT」，
        // 导致"一键生成"永远进不了正文写作（2026-09-17 真机联调实测）。
        sm: new PhaseMachine(stage.handler.type),
        patch: async (update) => {
          // 阶段 handler 内部的 0~1 进度映射到全流程区间，避免「进入 draft 后进度从 50% 掉回 0%」的回退
          const mapped = update.progress === undefined
            ? update
            : { ...update, progress: stageStart + update.progress * stageSpan };
          return ctx.patch({ ...mapped, phase: stage.step });
        },
        advance: async () => undefined,
      };
      const outcome = await stage.handler.run(stageCtx);
      stageResults[stage.step] = outcome.data ?? {};
      const questions = (outcome.data?.openQuestions as string[] | undefined) ?? [];
      openQuestions.push(...questions.map((item) => ({ step: stage.step, question: item })));

      // 产出回写 Java（失败不阻断：前台会看到"待确认/缺料"，但流程继续）
      try {
        if (stage.step === 'PARSE') {
          await ctx.java.saveProjectFacts({
            tenantId: ctx.record.tenantId,
            projectId: ctx.record.projectId,
            factsJson: JSON.stringify(outcome.data?.factsCard ?? outcome.data ?? {}),
            tenderParseJson: JSON.stringify(outcome.data ?? {}),
          });
        }
        if (stage.step === 'OUTLINE') {
          await ctx.java.saveOutline({
            tenantId: ctx.record.tenantId,
            projectId: ctx.record.projectId,
            outlineJson: JSON.stringify(outcome.data ?? {}),
          });
        }
      } catch (error) {
        // 回写失败不阻断流程：前台仍能看到阶段进度，待确认清单会体现缺料
        ctx.logger.warn('阶段产出回写 Java 失败', {
          stage: stage.step,
          error: (error as Error).message,
        });
      }
    }

    return {
      summary: `一键生成完成：${Object.keys(stageResults).join(' → ')}`,
      data: { stages: stageResults, openQuestions },
    };
  },
};

const STAGE_RANGE: Record<string, [number, number]> = {
  PARSE: [0.05, 0.35],
  OUTLINE: [0.35, 0.5],
  DRAFT: [0.5, 1],
};

function progressOf(step: string): number {
  return STAGE_RANGE[step]?.[0] ?? 0.05;
}
