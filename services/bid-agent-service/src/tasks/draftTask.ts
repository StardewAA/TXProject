import type { RetrievedChunk } from '../client/javaApi.js';
import { AppError } from '../errors.js';
import type { JsonSchema } from '../lib/jsonSchema.js';
import { bigramOverlap, factualNumbers, numberSet, scanNumbers } from '../lib/valueRules.js';
import { buildSystemPrompt } from '../orchestrator/prompts.js';
import { stageOverrides } from './stageRuntime.js';
import type { TaskHandler, TaskRunContext } from '../orchestrator/types.js';
import { WORKSPACE_FILES } from '../workspace/manager.js';
import { type OutlineNode } from './outlineTask.js';
import { boolish, chunkIdSet, draftPath, renderEvidence, retrievePath } from './common.js';

const OUTPUT_SCHEMA: JsonSchema = {
  type: 'object',
  properties: {
    content: { type: 'string', description: '章节正文，markdown 格式，含二级/三级标题' },
    claims: {
      type: 'array',
      description: '关键结论清单，每条必须给出来源 blockId',
      items: {
        type: 'object',
        properties: {
          claim: { type: 'string' },
          sourceBlockIds: { type: 'array', items: { type: 'string' } },
        },
        required: ['claim'],
      },
    },
    openQuestions: { type: 'array', items: { type: 'string' } },
  },
  required: ['content', 'claims'],
  additionalProperties: false,
};

interface DraftResult {
  content: string;
  claims: Array<{ claim: string; sourceBlockIds?: string[] }>;
  openQuestions?: string[];
}

/** 结论与引用证据的 2-gram 重合度低于该阈值时给出人工复核提示（A6） */
const CLAIM_OVERLAP_WARN = 0.35;

export const draftTask: TaskHandler = {
  type: 'draft',
  displayName: '章节正文撰写',

  async run(ctx: TaskRunContext) {
    await ctx.workspace.init();
    const { tenantId, projectId } = ctx.record;

    // ---------- INIT：确定待写章节 ----------
    const allNodes = await resolveNodes(ctx);
    if (allNodes.length === 0) {
      throw new AppError('没有可撰写的章节，请先执行 outline 任务或在 payload 中传入 nodes', {
        category: 'validation',
        retryable: false,
      });
    }
    // A4：按检查点跳过已提交章节，实现断点续跑与单章重跑；payload.redo=true 时强制全部重写
    const committed = ctx.checkpoints.committedNodeIds();
    const redoAll = boolish(ctx.record.payload.redo);
    const nodes = redoAll ? allNodes : allNodes.filter((node) => !committed.has(node.nodeId));
    const skipped = allNodes
      .filter((node) => !nodes.includes(node))
      .map((node) => node.nodeId);
    const planned: string[] = allNodes.map((node) => node.nodeId);
    if (skipped.length > 0) {
      ctx.logger.info('跳过已提交章节（断点续跑）', { skipped });
    }
    if (nodes.length === 0) {
      await ctx.sm.resetTo('DONE');
      await ctx.patch({ phase: 'DONE', progress: 1 });
      return {
        summary: `全部 ${planned.length} 个章节此前已提交，无需重写（跳过 ${skipped.length} 个）`,
        data: { planned, completed: [], skipped },
        artifacts: [],
      };
    }
    const completed: Array<{ nodeId: string; refs: string[]; status: string; warnings: string[] }> = [];
    await ctx.patch({ currentNodeId: nodes[0]!.nodeId, progress: 0 });
    await ctx.advance();

    const system = buildSystemPrompt({
      ...stageOverrides(ctx, 'DRAFT'),
      taskType: 'draft',
      tenantId,
      projectId,
      resident: await ctx.workspace.residentContext(),
    });
    const factsCard = await ctx.workspace.readText(WORKSPACE_FILES.factsCard).catch(() => '');

    // 单章撰写：SELECT → RETRIEVE → WRITE → VERIFY → COMMIT。
    // 一键生成（generate）链路里失败不中止全流程：由循环层捕获并降级为「空白占位 + 待复核」。
    const writeChapter = async (index: number): Promise<void> => {
      const node = nodes[index]!;
      await ctx.patch({ currentNodeId: node.nodeId });

      // ---------- SELECT ----------
      ctx.sm.resetTo('SELECT');
      await ctx.patch({ phase: 'SELECT', progress: index / nodes.length });

      // ---------- RETRIEVE：必检项用 requiredFacts ----------
      const bundles = await retrieveForNode(ctx, node);
      const chunks: RetrievedChunk[] = bundles.flatMap((bundle) => bundle.chunks);
      if (chunks.length === 0) {
        // 整章检索未命中：不再中止整单，留空白章节并标记待补充，继续写其余章节（用户口径 2026-09-19）
        ctx.logger.warn('章节整章检索未命中，留空白并标记待补充，继续其余章节', { nodeId: node.nodeId });
        const placeholder = '> 本章节未检索到招标文件相关证据，暂留空白，待人工补充。';
        await ctx.workspace.writeText(
          draftPath(node.nodeId),
          `# ${node.nodeId} ${node.title}\n\n${placeholder}\n\n## 待复核提示\n- 整章检索未命中（无可用证据），正文留空白待人工补充\n`,
        );
        await ctx.java.saveChapterDraft(
          {
            projectId,
            nodeId: node.nodeId,
            content: placeholder,
            refs: [],
            status: 'review',
            version: Number(ctx.record.payload.version ?? 1),
          },
          tenantId,
          ctx.signal,
        );
        completed.push({
          nodeId: node.nodeId,
          refs: [],
          status: 'review',
          warnings: ['整章检索未命中（无可用证据），正文留空白待人工补充'],
        });
        await ctx.checkpoints.save({
          taskId: ctx.record.taskId,
          type: 'draft',
          phase: 'COMMIT',
          nodeId: node.nodeId,
          snapshot: { planned, completed, currentIndex: index },
        });
        await ctx.patch({ phase: 'COMMIT', progress: (index + 1) / nodes.length });
        return;
      }
      // A7：部分子主题未命中时不再整任务中止，降级为"待复核 + 留痕"
      const missingEvidence = bundles
        .filter((bundle) => !bundle.hit || bundle.chunks.length === 0)
        .map((bundle) => `子主题「${bundle.key}」检索未命中（query=${bundle.query}），相关内容需人工补充或复核`);
      await ctx.patch({ phase: 'RETRIEVE' });

      // ---------- WRITE ----------
      const write = await ctx.loop.run<DraftResult>({
        label: `draft-write:${node.nodeId}`,
        system,
        messages: [
          {
            role: 'user',
            content: [
              `请撰写章节：${node.nodeId} ${node.title}`,
              node.writingBrief ? `写作要点：${node.writingBrief}` : '',
              node.requiredFacts?.length ? `必须体现的企业素材：${node.requiredFacts.join('、')}` : '',
              '要求：正文使用中文markdown；claims 列出关键结论并给出 sourceBlockIds；不得出现无来源的数字、日期、金额与证书编号。',
              '',
              renderEvidence(bundles),
            ]
              .filter(Boolean)
              .join('\n'),
          },
        ],
        tools: ['bid-retrieve', 'read-file', 'json-validation', 'task-failure', 'ask-user'],
        outputSchema: OUTPUT_SCHEMA,
        outputHint: '输出本章正文与溯源结论（严格 JSON）。',
        history: ctx.resumeHistory,
        signal: ctx.signal,
      });

      let draft = write.value as DraftResult;
      await ctx.patch({ phase: 'WRITE' });

      // ---------- VERIFY：claim 溯源、数字一致性与证据覆盖校验（A5 / A6 / A7）----------
      let verification = verifyDraft(draft, chunks, factsCard);
      if (verification.violations.length > 0) {
        ctx.logger.warn('初稿校验未通过，触发一次修复生成', {
          nodeId: node.nodeId,
          violations: verification.violations,
        });
        const repaired = await ctx.loop.run<DraftResult>({
          label: `draft-repair:${node.nodeId}`,
          system,
          messages: [
            {
              role: 'user',
              content: [
                `上一版正文未通过一致性校验，问题如下：\n${verification.violations
                  .map((item) => `- ${item}`)
                  .join('\n')}`,
                '请删除无法溯源的表述，或在 claims 中补齐 sourceBlockIds 后重写本章。',
                '',
                renderEvidence(bundles),
              ].join('\n'),
            },
          ],
          tools: ['bid-retrieve', 'read-file', 'json-validation'],
          outputSchema: OUTPUT_SCHEMA,
          outputHint: '输出修订后的本章正文与溯源结论（严格 JSON）。',
          history: ctx.resumeHistory,
          signal: ctx.signal,
        });
        draft = repaired.value as DraftResult;
        verification = verifyDraft(draft, chunks, factsCard);
      }
      await ctx.patch({ phase: 'VERIFY' });

      const warnings = [...missingEvidence, ...verification.warnings];
      let status: 'draft' | 'review' = 'draft';
      if (verification.violations.length > 0) {
        const { answer } = await ctx.askUser({
          question: `章节「${node.title}」仍存在不可溯源的表述：\n${verification.violations
            .map((item) => `- ${item}`)
            .join('\n')}\n\n请补充资料或确认保留（回复“保留”将标记为待复核）。`,
          options: ['保留', '补充资料后重跑'],
          context: `nodeId=${node.nodeId}`,
        });
        if (answer.includes('重跑')) {
          throw new AppError(`人工要求补充资料后重跑：${node.nodeId}`, {
            category: 'validation',
            retryable: false,
            details: { nodeId: node.nodeId, completed },
          });
        }
        status = 'review';
      }
      if (missingEvidence.length > 0) status = 'review';

      // ---------- COMMIT ----------
      const refs = uniqueRefs(draft.claims);
      const file = draftPath(node.nodeId);
      const warningBlock = warnings.length > 0
        ? `\n## 待复核提示\n${warnings.map((item) => `- ${item}`).join('\n')}\n`
        : '';
      await ctx.workspace.writeText(
        file,
        `# ${node.nodeId} ${node.title}\n\n${draft.content}\n\n## 溯源引用\n${refs
          .map((ref) => `- ${ref}`)
          .join('\n')}\n${warningBlock}`,
      );
      await ctx.java.saveChapterDraft(
        {
          projectId,
          nodeId: node.nodeId,
          content: draft.content,
          refs,
          status,
          version: Number(ctx.record.payload.version ?? 1),
        },
        tenantId,
        ctx.signal,
      );
      completed.push({ nodeId: node.nodeId, refs, status, warnings });
      await ctx.checkpoints.save({
        taskId: ctx.record.taskId,
        type: 'draft',
        phase: 'COMMIT',
        nodeId: node.nodeId,
        snapshot: { planned, completed, currentIndex: index },
      });
      await ctx.patch({ phase: 'COMMIT', progress: (index + 1) / nodes.length });
    };

    for (let index = 0; index < nodes.length; index += 1) {
      try {
        await writeChapter(index);
      } catch (error) {
        // 单独跑的 draft 任务保留原语义（失败可见、可重跑）；一键生成按「缺料不阻断」口径降级；
        // 中断（abort）必须照常抛出，不得被降级吞掉
        const category = (error as { category?: string } | null)?.category;
        if (ctx.record.type !== 'generate' || category === 'abort') {
          throw error;
        }
        const node = nodes[index]!;
        const message = (error as Error).message ?? String(error);
        ctx.logger.warn('一键生成：章节撰写失败，降级为空白占位并标记待复核', {
          nodeId: node.nodeId,
          message,
        });
        const placeholder = `> 本章节撰写失败（${message.slice(0, 120)}），暂留空白，待人工补充。`;
        await ctx.workspace.writeText(
          draftPath(node.nodeId),
          `# ${node.nodeId} ${node.title}\n\n${placeholder}\n\n## 待复核提示\n- 撰写失败：${message.slice(0, 200)}\n`,
        );
        await ctx.java
          .saveChapterDraft(
            {
              projectId,
              nodeId: node.nodeId,
              content: placeholder,
              refs: [],
              status: 'review',
              version: Number(ctx.record.payload.version ?? 1),
            },
            tenantId,
            ctx.signal,
          )
          .catch(() => undefined);
        completed.push({
          nodeId: node.nodeId,
          refs: [],
          status: 'review',
          warnings: [`章节撰写失败已降级：${message.slice(0, 160)}`],
        });
        await ctx.checkpoints.save({
          taskId: ctx.record.taskId,
          type: 'draft',
          phase: 'COMMIT',
          nodeId: node.nodeId,
          snapshot: { planned, completed, currentIndex: index },
        });
        await ctx.patch({ phase: 'COMMIT', progress: (index + 1) / nodes.length });
      }
    }

    await ctx.sm.resetTo('DONE');
    await ctx.patch({ phase: 'DONE', progress: 1 });

    return {
      summary: `已撰写 ${completed.length} 个章节，其中待复核 ${completed.filter((item) => item.status === 'review').length} 个${
        skipped.length > 0 ? `；跳过已提交章节 ${skipped.length} 个` : ''
      }`,
      data: { planned, completed, skipped },
      artifacts: completed.map((item) => ({ path: draftPath(item.nodeId), kind: 'markdown' as const })),
    };
  },
};

async function resolveNodes(ctx: TaskRunContext): Promise<OutlineNode[]> {
  const payloadNodes = ctx.record.payload.nodes;
  if (Array.isArray(payloadNodes) && payloadNodes.length > 0) {
    return payloadNodes as OutlineNode[];
  }
  const nodeIds = ctx.record.payload.nodeIds;
  const outline = await ctx.workspace.readJson<{ chapters?: OutlineNode[] }>(WORKSPACE_FILES.outline, {
    chapters: [],
  });
  const chapters = outline.chapters ?? [];
  if (Array.isArray(nodeIds) && nodeIds.length > 0) {
    return chapters.filter((chapter) => (nodeIds as string[]).includes(chapter.nodeId));
  }
  // 只写末级章节：有子节点的章节不作为撰写单元
  const parents = new Set(chapters.map((chapter) => chapter.parentId).filter(Boolean));
  return chapters.filter((chapter) => !parents.has(chapter.nodeId));
}

async function retrieveForNode(ctx: TaskRunContext, node: OutlineNode) {
  const queries = [`${node.title} ${node.writingBrief ?? ''}`.trim()];
  for (const fact of (node.requiredFacts ?? []).slice(0, 5)) {
    queries.push(`${node.title} ${fact}`);
  }
  const bundles = [];
  const docSrc = String(ctx.record.payload?.sourceDocName ?? '').trim();
  for (let i = 0; i < queries.length; i += 1) {
    const query = queries[i]!;
    const response = await ctx.java.retrieve(
      {
        tenantId: ctx.record.tenantId,
        projectId: ctx.record.projectId,
        query,
        topK: ctx.config.retrieve.topK,
        mandatory: i === 0 ? node.requiredFacts : undefined,
        ...(docSrc ? { filters: { sourceDocName: docSrc } } : {}),
      },
      ctx.signal,
    );
    await ctx.workspace.writeJson(retrievePath(`${node.nodeId}-${i + 1}`), {
      query,
      hit: response.hit,
      savedAt: new Date().toISOString(),
      chunks: response.chunks,
    });
    bundles.push({ key: `${node.nodeId}#${i + 1}`, query, hit: response.hit, chunks: response.chunks });
  }
  return bundles;
}

/** 章节校验结果：violations 必须修复或转人工，warnings 仅提示复核（A5 / A6） */
interface VerifyResult {
  violations: string[];
  warnings: string[];
}

function numbersIn(text: string): Set<string> {
  const values = new Set<string>();
  for (const hit of scanNumbers(text)) values.add(hit.normalized);
  return values;
}

/**
 * 章节校验：
 * - A5：数字一致性改为集合级比对。旧实现用 `evidenceText.includes(number)` 做子串判断，
 *   "正文写 100、证据里是 1000"会被误判为合规；同时不再用「小于 10 一律放行」的规则，
 *   改为只排除条款号/章节号/列表序号等结构编号，小整数事实（如"3 人"）同样纳入校验。
 * - A6：claim 除校验 sourceBlockId 是否存在外，还要与其引用的证据内容对齐
 *   （结论中的数字必须出现在被引证据里；重合度过低时给出人工复核提示）。
 */
function verifyDraft(draft: DraftResult, chunks: RetrievedChunk[], factsCard: string): VerifyResult {
  const violations: string[] = [];
  const warnings: string[] = [];
  const knownIds = chunkIdSet(chunks);
  const contentById = new Map<string, string>();
  for (const chunk of chunks) {
    contentById.set(chunk.chunkId, chunk.content);
    for (const blockId of chunk.sourceBlockIds ?? []) contentById.set(blockId, chunk.content);
  }

  for (const claim of draft.claims ?? []) {
    const ids = claim.sourceBlockIds ?? [];
    if (ids.length === 0) {
      violations.push(`结论缺少溯源：${claim.claim.slice(0, 60)}`);
      continue;
    }
    const unknown = ids.filter((id) => !knownIds.has(id));
    if (unknown.length > 0) {
      violations.push(`引用了不存在的 sourceBlockId：${unknown.join(', ')}`);
      continue;
    }
    const cited = ids.map((id) => contentById.get(id) ?? '').join('\n');
    const citedNumbers = numbersIn(cited);
    const missing = factualNumbers(claim.claim).filter((value) => !citedNumbers.has(value));
    if (missing.length > 0) {
      violations.push(
        `结论中的数字「${missing.join('、')}」未出现在其引用的证据中：${claim.claim.slice(0, 40)}`,
      );
      continue;
    }
    const overlap = bigramOverlap(claim.claim, cited);
    if (overlap < CLAIM_OVERLAP_WARN) {
      warnings.push(
        `结论与引用证据的重合度偏低（${(overlap * 100).toFixed(0)}%），建议人工复核：${claim.claim.slice(0, 40)}`,
      );
    }
  }

  const evidenceNumbers = numberSet([chunks.map((chunk) => chunk.content).join('\n'), factsCard]);
  for (const value of factualNumbers(draft.content ?? '')) {
    if (evidenceNumbers.has(value)) continue;
    violations.push(`正文中的数字「${value}」在检索证据与事实卡中均查无实据`);
  }

  return { violations: [...new Set(violations)], warnings: [...new Set(warnings)] };
}

function uniqueRefs(claims: DraftResult['claims']): string[] {
  const set = new Set<string>();
  for (const claim of claims) {
    for (const id of claim.sourceBlockIds ?? []) set.add(id);
  }
  return [...set];
}
