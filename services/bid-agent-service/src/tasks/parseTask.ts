import type { FactItem } from '../client/javaApi.js';
import { AppError } from '../errors.js';
import type { JsonSchema } from '../lib/jsonSchema.js';
import { buildSystemPrompt } from '../orchestrator/prompts.js';
import { stageOverrides } from './stageRuntime.js';
import type { TaskHandler, TaskRunContext } from '../orchestrator/types.js';
import { WORKSPACE_FILES } from '../workspace/manager.js';
import { EvidenceBundle, NamedFact, renderEvidence, renderFactsCard, retrievePath } from './common.js';

const OUTPUT_SCHEMA: JsonSchema = {
  type: 'object',
  properties: {
    projectName: { type: 'string', description: '招标项目名称' },
    tenderSummary: { type: 'string', description: '招标范围与标的的一句话概括' },
    invalidBidClauses: {
      type: 'array',
      description: '废标/无效投标条款，P0 验收要求 Recall@10 ≥ 0.9',
      items: {
        type: 'object',
        properties: {
          clause: { type: 'string', description: '条款原文摘要' },
          consequence: { type: 'string', description: '触发后的后果' },
          sourceBlockIds: { type: 'array', items: { type: 'string' } },
        },
        required: ['clause'],
      },
    },
    keyConstraints: {
      type: 'array',
      description: '对投标准备有约束力的要求',
      items: {
        type: 'object',
        properties: {
          name: { type: 'string' },
          value: { type: 'string' },
          sourceBlockIds: { type: 'array', items: { type: 'string' } },
        },
        required: ['name', 'value'],
      },
    },
    globalFacts: {
      type: 'array',
      description: '跨章节一致性锚点：工期/人数/金额/日期类事实',
      items: {
        type: 'object',
        properties: {
          name: { type: 'string' },
          value: { type: 'string' },
          unit: { type: 'string' },
          sourceDocName: { type: 'string' },
          sourceBlockIds: { type: 'array', items: { type: 'string' } },
        },
        required: ['name', 'value'],
      },
    },
    openQuestions: { type: 'array', items: { type: 'string' }, description: '需要人工澄清的问题' },
  },
  required: ['invalidBidClauses', 'keyConstraints', 'globalFacts'],
  additionalProperties: false,
};

interface TenderParse {
  projectName?: string;
  tenderSummary?: string;
  invalidBidClauses: Array<{ clause: string; consequence?: string; sourceBlockIds?: string[] }>;
  keyConstraints: NamedFact[];
  globalFacts: NamedFact[];
  openQuestions?: string[];
}

export interface MustQuery {
  key: string;
  query: string;
}

// A8：必检维度不再硬编码。默认面向“检修维护/防腐保温保冷类”语料，
// 商务类招标可用 payload.mustPreset='business' 切换，或直接用 payload.mustQueries 完全自定义。
const DEFAULT_MUST_QUERIES: readonly MustQuery[] = [
  { key: '质量', query: '质量要求 质量标准 验收标准 质量保证期 缺陷责任期' },
  { key: '工期', query: '工期 施工进度 计划 完成期限 节点计划' },
  { key: '资格', query: '资格 资质 证书 特种作业 项目经理 安全生产' },
  { key: '验收', query: '验收 竣工验收 中间验收 检验 检验记录 合格' },
  { key: '材料', query: '保温材料 保冷材料 防腐涂料 规格 性能检测' },
];

const BUSINESS_MUST_QUERIES: readonly MustQuery[] = [
  { key: '废标条款', query: '废标 无效投标 否决投标 拒绝投标 作废标处理' },
  { key: '工期要求', query: '工期 计划工期 服务期限 完成期限 里程碑' },
  { key: '质量要求', query: '质量要求 质量标准 验收标准 质量保证期' },
  { key: '报价要求', query: '报价 最高限价 暂列金额 价格调整 结算' },
  { key: '资格要求', query: '资格要求 资质 业绩 项目经理 财务 信用' },
];

/** 解析必检维度：payload.mustQueries 优先，其次 mustPreset，最后默认预设（A8） */
function resolveMustQueries(payload: Record<string, unknown>): MustQuery[] {
  const raw = payload.mustQueries;
  if (Array.isArray(raw) && raw.length > 0) {
    const parsed = raw
      .map((item) => (item && typeof item === 'object' ? (item as Record<string, unknown>) : null))
      .filter((item): item is Record<string, unknown> => Boolean(item))
      .map((item) => ({
        key: String(item.key ?? '').trim(),
        query: String(item.query ?? '').trim(),
      }))
      .filter((item) => item.key.length > 0 && item.query.length > 0);
    if (parsed.length === 0) {
      throw new AppError('payload.mustQueries 格式不合法：每项都需要 key 与 query', {
        category: 'validation',
        status: 400,
        retryable: false,
      });
    }
    return parsed;
  }
  const preset = String(payload.mustPreset ?? '').trim().toLowerCase();
  if (preset === 'business' || preset === '商务') return [...BUSINESS_MUST_QUERIES];
  return [...DEFAULT_MUST_QUERIES];
}

function toNamedFacts(facts: FactItem[]): NamedFact[] {
  return facts.map((fact) => ({
    name: fact.name,
    value: fact.value,
    unit: fact.unit,
    sourceDocName: fact.sourceDocName,
    sourceBlockIds: fact.sourceBlockIds,
  }));
}

export const parseTask: TaskHandler = {
  type: 'parse',
  displayName: '招标文件解析',

  async run(ctx: TaskRunContext) {
    await ctx.workspace.init();
    const { tenantId, projectId } = ctx.record;

    // ---------- INIT：拉项目事实卡（Java 侧已沉淀的全局事实）----------
    let enterpriseFacts: NamedFact[] = [];
    try {
      enterpriseFacts = toNamedFacts(await ctx.java.getFacts(tenantId, undefined, ctx.signal));
    } catch (error) {
      ctx.logger.warn('企业事实拉取失败，本轮不启用 COPY_FACT', { message: (error as Error).message });
    }
    await ctx.workspace.writeText(
      WORKSPACE_FILES.factsCard,
      renderFactsCard({ projectId, globalFacts: enterpriseFacts.slice(0, 20) }),
    );
    // 清空上轮解析产物，避免旧招标解析（可能来自其它文档）进入 resident 上下文串稿
    await ctx.workspace.writeText(WORKSPACE_FILES.tenderParse, '');
    await ctx.workspace.writeText('tenderParse.json', '');
    await ctx.advance();

    // ---------- RETRIEVE：必检通道，不经模型 ----------
    const docSrc = String(ctx.record.payload?.sourceDocName ?? '').trim();
    const mustQueries = resolveMustQueries(ctx.record.payload ?? {});
    const bundles: EvidenceBundle[] = [];
    const missed: string[] = [];
    for (const item of mustQueries) {
      const response = await ctx.java.retrieve(
        {
          tenantId,
          projectId,
          query: item.query,
          topK: ctx.config.retrieve.topK,
          mandatory: [item.key],
          ...(docSrc ? { filters: { sourceDocName: docSrc } } : {}),
        },
        ctx.signal,
      );
      await ctx.workspace.writeJson(retrievePath(item.key), {
        key: item.key,
        query: item.query,
        hit: response.hit,
        savedAt: new Date().toISOString(),
        chunks: response.chunks,
      });
      bundles.push({ key: item.key, query: item.query, hit: response.hit, chunks: response.chunks });
      if (!response.hit || response.chunks.length === 0) missed.push(item.key);
    }
    const coverage = mustQueries.length === 0 ? 1 : (mustQueries.length - missed.length) / mustQueries.length;
    if (missed.length === mustQueries.length) {
      throw new AppError('全部必检项均未命中，转人工处理', {
        category: 'retrieval_miss',
        retryable: false,
        details: { missed, mustQueries: mustQueries.map((item) => item.key) },
      });
    }
    ctx.logger.info('必检通道完成', {
      mustQueries: mustQueries.map((item) => item.key),
      missed,
      coverage,
    });
    await ctx.advance();

    // ---------- EXTRACT：模型抽取，输出受 schema 约束 ----------
    const resident = await ctx.workspace.residentContext();
    const system = buildSystemPrompt({
      ...stageOverrides(ctx, 'PARSE'),
      taskType: 'parse',
      tenantId,
      projectId,
      resident,
    });
    const extract = await ctx.loop.run<TenderParse>({
      label: 'parse-extract',
      system,
      messages: [
        {
          role: 'user',
          content: [
            '请基于下列检索证据完成招标文件结构化解析。',
            '要求：',
            '1. 废标条款尽量穷尽，每条都要附 sourceBlockIds；',
            '2. keyConstraints 记录对编制有约束力的要求（如响应文件格式、份数、签章要求、递交截止时间）；',
            '3. globalFacts 抽取数字类事实（工期天数、质量目标、最高限价、关键日期、项目人数）；',
            '',
            renderEvidence(bundles),
          ].join('\n'),
        },
      ],
      tools: ['bid-retrieve', 'read-file', 'json-validation', 'task-failure'],
      outputSchema: OUTPUT_SCHEMA,
      outputHint: '输出结构化解析结果（严格 JSON）。',
      history: ctx.resumeHistory,
      signal: ctx.signal,
    });

    let parsed = extract.value as TenderParse;
    await ctx.advance();

    // ---------- CONFIRM：人工确认闸门 ----------
    const { answer } = await ctx.askUser({
      question: [
        `招标文件解析完成：废标条款 ${parsed.invalidBidClauses.length} 条、关键约束 ${parsed.keyConstraints.length} 条、全局事实 ${parsed.globalFacts.length} 条。`,
        '请确认无误；如需修正请直接写出修改意见（回复“确认”即通过）。',
      ].join('\n'),
      options: ['确认'],
      context: missed.length > 0 ? `以下必检项未命中：${missed.join('、')}` : undefined,
    });
    const needsRework = answer.trim() !== '' && !['确认', '通过', 'ok', 'OK', '同意'].includes(answer.trim());
    if (needsRework) {
      ctx.logger.info('按人工意见重新解析', { answer });
      parsed = (
        await ctx.loop.run<TenderParse>({
          label: 'parse-extract-rework',
          system,
          messages: [
            {
              role: 'user',
              content: `请按以下人工意见修订解析结果：\n${answer}\n\n原始证据：\n${renderEvidence(bundles)}`,
            },
          ],
          tools: ['bid-retrieve', 'read-file', 'json-validation'],
          outputSchema: OUTPUT_SCHEMA,
          outputHint: '输出修订后的结构化解析结果（严格 JSON）。',
          history: ctx.resumeHistory,
          signal: ctx.signal,
        })
      ).value as TenderParse;
    }
    await ctx.advance();

    // ---------- PERSIST：落盘事实卡与解析结果 ----------
    // 占位事实（检索未命中时的「未找到/未命中」等）不进入事实卡，避免回填与数字校验被污染
    const isPlaceholderFact = (fact: NamedFact): boolean =>
      /^(未找到|未命中|暂无|待补充|未在检索证据中)/.test(fact.value?.trim() ?? '');
    const mergedFacts: NamedFact[] = parsed.globalFacts.filter((fact) => !isPlaceholderFact(fact));
    for (const fact of enterpriseFacts) {
      if (isPlaceholderFact(fact)) continue;
      if (!mergedFacts.some((item) => item.name === fact.name)) mergedFacts.push(fact);
    }
    const factsCard = renderFactsCard({
      projectId,
      projectName: parsed.projectName,
      globalFacts: mergedFacts,
      keyConstraints: parsed.keyConstraints,
    });
    await ctx.workspace.writeText(WORKSPACE_FILES.factsCard, factsCard);

    const markdown = [
      '# 招标文件解析',
      '',
      `- projectId：${projectId}`,
      `- 项目名称：${parsed.projectName ?? '（未识别）'}`,
      '',
      `## 标的概括`,
      parsed.tenderSummary ?? '（未生成）',
      '',
      '## 废标条款',
      ...(parsed.invalidBidClauses.length === 0
        ? ['（未解析到）']
        : parsed.invalidBidClauses.map(
            (clause, index) =>
              `${index + 1}. ${clause.clause}${clause.consequence ? ` —— 后果：${clause.consequence}` : ''}${
                clause.sourceBlockIds?.length ? ` [sourceBlockIds: ${clause.sourceBlockIds.join(', ')}]` : ''
              }`,
          )),
      '',
      '## 关键约束',
      ...(parsed.keyConstraints.length === 0
        ? ['（未解析到）']
        : parsed.keyConstraints.map((item) => `- ${item.name}：${item.value}${item.unit ?? ''}`)),
      '',
      '## 存疑项',
      ...(parsed.openQuestions?.length ? parsed.openQuestions.map((item) => `- ${item}`) : ['（无）']),
      '',
    ].join('\n');
    await ctx.workspace.writeText(WORKSPACE_FILES.tenderParse, markdown);
    await ctx.workspace.writeJson('tenderParse.json', {
      ...parsed,
      missed,
      extractedAt: new Date().toISOString(),
    });
    await ctx.advance();

    return {
      summary: `解析完成：废标条款 ${parsed.invalidBidClauses.length} 条，全局事实 ${parsed.globalFacts.length} 条（必检覆盖 ${(
        coverage * 100
      ).toFixed(0)}%）${missed.length > 0 ? `，未命中必检项 ${missed.join('、')}` : ''}`,
      data: {
        invalidBidClauses: parsed.invalidBidClauses.length,
        keyConstraints: parsed.keyConstraints.length,
        globalFacts: parsed.globalFacts,
        keyConstraintList: parsed.keyConstraints,
        missed,
        coverage,
        mustQueries: mustQueries.map((item) => item.key),
      },
      artifacts: [
        { path: WORKSPACE_FILES.factsCard, kind: 'markdown' as const },
        { path: WORKSPACE_FILES.tenderParse, kind: 'markdown' as const },
        { path: 'tenderParse.json', kind: 'json' as const },
      ],
    };
  },
};
