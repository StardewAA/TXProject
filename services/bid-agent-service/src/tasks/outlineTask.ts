import { AppError } from '../errors.js';
import type { JsonSchema } from '../lib/jsonSchema.js';
import { buildSystemPrompt } from '../orchestrator/prompts.js';
import { stageOverrides } from './stageRuntime.js';
import type { TaskHandler, TaskRunContext } from '../orchestrator/types.js';
import { WORKSPACE_FILES } from '../workspace/manager.js';

export interface OutlineNode {
  nodeId: string;
  title: string;
  level?: number;
  parentId?: string;
  weight?: number;
  requiredFacts?: string[];
  writingBrief?: string;
}

const OUTPUT_SCHEMA: JsonSchema = {
  type: 'object',
  properties: {
    chapters: {
      type: 'array',
      description: '章节树，覆盖招标文件实质性要求',
      items: {
        type: 'object',
        properties: {
          nodeId: { type: 'string', description: '章节唯一标识，如 3.2.1' },
          title: { type: 'string' },
          level: { type: 'integer', minimum: 1, maximum: 4 },
          parentId: { type: 'string' },
          weight: { type: 'integer', description: '建议篇幅占比，合计 100' },
          requiredFacts: { type: 'array', items: { type: 'string' }, description: '撰写该章节必须的企业业绩/人员/资质' },
          writingBrief: { type: 'string', description: '该章节写作要点' },
        },
        required: ['nodeId', 'title'],
        additionalProperties: false,
      },
    },
    coverageNotes: { type: 'array', items: { type: 'string' }, description: '对废标条款的对应承接说明' },
  },
  required: ['chapters'],
  additionalProperties: false,
};

interface OutlineResult {
  chapters: OutlineNode[];
  coverageNotes?: string[];
}

export const outlineTask: TaskHandler = {
  type: 'outline',
  displayName: '标书大纲设计',

  async run(ctx: TaskRunContext) {
    await ctx.workspace.init();
    const { tenantId, projectId } = ctx.record;

    // ---------- INIT ----------
    const docSrc = String(ctx.record.payload?.sourceDocName ?? '').trim();
    const queried = await ctx.java.retrieve(
      {
        tenantId,
        projectId,
        query: '技术标 评分办法 响应性要求 章节要求',
        topK: ctx.config.retrieve.topK,
        ...(docSrc ? { filters: { sourceDocName: docSrc } } : {}),
      },
      ctx.signal,
    );
    await ctx.workspace.writeJson('outline-evidence.json', queried);
    await ctx.advance();

    // ---------- LOAD：只加载常驻文件，不做全量知识注入 ----------
    const resident = await ctx.workspace.residentContext();
    if (!resident.some((file) => file.name === WORKSPACE_FILES.factsCard)) {
      ctx.logger.warn('缺少项目事实卡，建议先执行 parse 任务', { taskId: ctx.record.taskId });
    }
    const tenderParse = (await ctx.workspace.readJson<Record<string, unknown>>('tenderParse.json', {})) as Record<
      string,
      unknown
    >;
    await ctx.advance();

    // ---------- PLAN ----------
    const system = buildSystemPrompt({
      ...stageOverrides(ctx, 'OUTLINE'),
      taskType: 'outline',
      tenantId,
      projectId,
      resident,
    });
    const plan = await ctx.loop.run<OutlineResult>({
      label: 'outline-plan',
      system,
      messages: [
        {
          role: 'user',
          content: [
            '请设计本项目的投标书章节大纲。',
            '要求：',
            '1. 必须完整覆盖招标文件中的废标条款与评分点，coverageNotes 说明承接关系；',
            '2. nodeId 采用层级编号（如 1 / 1.1 / 1.1.2），level 与层级一致；',
            '3. 每个末级章节给出 writingBrief 与 requiredFacts，便于后续逐节撰写时按需检索；',
            '4. weight 合计尽量接近 100。',
            ...(typeof ctx.record.payload.docCategory === 'string'
              ? [`标书类型：${ctx.record.payload.docCategory === 'business' ? '商务标书（章节以商务条款响应与报价口径为主线）' : '技术标书（章节以技术方案与评分点承接为主线）'}，请据此组织章节大纲。`]
              : []),
            '',
            `已解析的招标要求：\n${JSON.stringify(tenderParse, null, 2).slice(0, 6000)}`,
          ].join('\n'),
        },
      ],
      tools: ['bid-retrieve', 'read-file', 'json-validation'],
      outputSchema: OUTPUT_SCHEMA,
      outputHint: '输出章节大纲（严格 JSON）。',
      history: ctx.resumeHistory,
      signal: ctx.signal,
    });

    let outline = plan.value as OutlineResult;
    if (!outline.chapters?.length) {
      throw new AppError('大纲为空，转人工处理', { category: 'output_invalid', retryable: false });
    }
    await ctx.advance();

    // ---------- CONFIRM ----------
    const preview = outline.chapters
      .slice(0, 12)
      .map((chapter) => `- ${chapter.nodeId} ${chapter.title}`)
      .join('\n');
    const { answer } = await ctx.askUser({
      question: `已生成 ${outline.chapters.length} 个章节：\n${preview}\n\n请确认大纲；如需调整请直接写出修改意见（回复“确认”即通过）。`,
      options: ['确认'],
    });
    const needsRework = answer.trim() !== '' && !['确认', '通过', 'ok', 'OK', '同意'].includes(answer.trim());
    if (needsRework) {
      outline = (
        await ctx.loop.run<OutlineResult>({
          label: 'outline-rework',
          system,
          messages: [{ role: 'user', content: `请按以下人工意见修订大纲：\n${answer}` }],
          tools: ['bid-retrieve', 'read-file', 'json-validation'],
          outputSchema: OUTPUT_SCHEMA,
          outputHint: '输出修订后的章节大纲（严格 JSON）。',
          history: ctx.resumeHistory,
          signal: ctx.signal,
        })
      ).value as OutlineResult;
    }
    await ctx.advance();

    // ---------- PERSIST ----------
    await ctx.workspace.writeJson(WORKSPACE_FILES.outline, {
      projectId,
      chapters: outline.chapters,
      coverageNotes: outline.coverageNotes ?? [],
      savedAt: new Date().toISOString(),
    });
    await ctx.advance();

    return {
      summary: `大纲已生成，共 ${outline.chapters.length} 个章节`,
      data: { chapters: outline.chapters, coverageNotes: outline.coverageNotes ?? [] },
      artifacts: [{ path: WORKSPACE_FILES.outline, kind: 'json' as const }],
    };
  },
};
