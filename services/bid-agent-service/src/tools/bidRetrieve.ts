import type { RetrievedChunk } from '../client/javaApi.js';
import { fail, ok, type Tool, type ToolContext } from './types.js';
import { WORKSPACE_FILES } from '../workspace/manager.js';

export interface BidRetrieveArgs {
  query: string;
  /** 命中结果落盘到 检索结果/{saveAs}.json（按需检索，避免上下文爆炸） */
  saveAs?: string;
  docType?: string;
  validOn?: string;
  domain?: string;
  /** 标段级隔离：仅检索该标段的招标文件（企业资料不绑标段，仍会召回） */
  bidSectionCode?: string;
  /** 多标段检索：一次检索多个标段 */
  bidSectionCodes?: string[];
  topK?: number;
  /** 必检通道：这些词条必须命中，否则 hit=false */
  mandatory?: string[];
}

const CONTENT_LIMIT = 1500;

function brief(chunk: RetrievedChunk): Record<string, unknown> {
  return {
    chunkId: chunk.chunkId,
    sourceDocName: chunk.sourceDocName,
    bidSectionCode: chunk.bidSectionCode,
    chapterPath: chunk.chapterPath,
    pageNo: chunk.pageNo,
    score: chunk.score,
    matchedBy: chunk.matchedBy,
    sourceBlockIds: chunk.sourceBlockIds ?? [],
    content: chunk.content.length > CONTENT_LIMIT ? `${chunk.content.slice(0, CONTENT_LIMIT)}…` : chunk.content,
  };
}

/** 检索入口：向量+BM25+必检+阈值+溯源都在 Java 侧完成（§5） */
export const bidRetrieveTool: Tool<BidRetrieveArgs> = {
  name: 'bid-retrieve',
  description:
    '在企业资料库/招标文件库中检索证据片段。返回命中片段及其溯源信息（sourceDocName/chapterPath/pageNo/sourceBlockIds）。hit=false 时严禁编造，必须转人工或改用其它检索词重试一次。',
  parameters: {
    type: 'object',
    properties: {
      query: { type: 'string', description: '检索词，建议包含专业术语与关键约束' },
      saveAs: { type: 'string', description: '可选，把结果落盘到 检索结果/{saveAs}.json' },
      docType: { type: 'string', description: '限定文档类型，如 资质/业绩/技术方案' },
      validOn: { type: 'string', description: '有效期判定日期 YYYY-MM-DD' },
      domain: { type: 'string', description: '限定业务域' },
      bidSectionCode: {
        type: 'string',
        description: '限定标段编号：招标文件按标段隔离，企业资料不绑标段仍会召回',
      },
      bidSectionCodes: {
        type: 'array',
        items: { type: 'string' },
        description: '一次检索多个标段（长文本招标文件按标段拆分时使用）',
      },
      topK: { type: 'integer', description: '返回条数', minimum: 1, maximum: 50 },
      mandatory: { type: 'array', items: { type: 'string' }, description: '必检词列表' },
    },
    required: ['query'],
    additionalProperties: false,
  },
  async execute(args, ctx: ToolContext) {
    const response = await ctx.java.retrieve(
      {
        tenantId: ctx.record.tenantId,
        projectId: ctx.record.projectId,
        query: args.query,
        filters: {
          docType: args.docType,
          validOn: args.validOn,
          domain: args.domain,
          bidSectionCode: args.bidSectionCode,
          bidSectionCodes: args.bidSectionCodes,
        },
        topK: args.topK,
        mandatory: args.mandatory,
      },
      ctx.signal,
    );

    const chunks = response.chunks;
    if (args.saveAs) {
      await ctx.workspace.writeJson(`${WORKSPACE_FILES.retrieveDir}/${args.saveAs}.json`, {
        query: args.query,
        hit: response.hit,
        savedAt: new Date().toISOString(),
        chunks,
      });
    }

    if (!response.hit || chunks.length === 0) {
      return fail(
        {
          hit: false,
          chunks: [],
          advice: '未命中证据。禁止编造内容；请改写检索词再试一次，仍不命中则调用 ask-user 转人工。',
        },
        '检索未命中',
      );
    }

    return ok(
      {
        hit: true,
        count: chunks.length,
        topScore: chunks[0]?.score ?? 0,
        savedAs: args.saveAs ? `${WORKSPACE_FILES.retrieveDir}/${args.saveAs}.json` : undefined,
        chunks: chunks.map(brief),
        hint: '引用时必须携带对应片段的 sourceBlockIds，不得引用未出现的编号、金额或日期。',
      },
      `命中 ${chunks.length} 条证据`,
    );
  },
};
