import fs from 'node:fs';
import path from 'node:path';
import type { AppConfig } from '../config.js';
import { AppError } from '../errors.js';
import type { Logger } from '../logger.js';
import { buildDocx, type CoverInfo, type DocChapter } from '../lib/docx.js';
import { fillTemplate, type TemplateSection } from '../lib/templateDocx.js';
import { resolveFieldValues, type FactValue } from '../lib/templateFields.js';
import type { PricingData } from '../lib/pricingMapping.js';
import { WORKSPACE_FILES } from '../workspace/manager.js';

/**
 * 标书排版导出：把工作区内的 招标解析/大纲/章节草稿 组装成可交付的 Word(.docx)。
 * 两条路径：
 *   - template（优先）：存在标书模板（docx.templatePath）时，回填该模板（字段填充 + 章节正文插入），
 *     保留模板原有封面/目录/页眉页脚，作为正式交付件；
 *   - preview（兜底）：模板缺失时退回内置排版引擎，用于内部审阅/快速成型。
 * 产出统一落到 workspace/{tenant}/{project}/deliver/。
 * 字段来源三层：标准字段（事实卡/招标解析，COPY）→ 确定性换算（DERIVE）→ 报价数据（COPY_PRICING，可选注入）。
 */

interface OutlineChapter {
  nodeId?: string;
  title: string;
  level?: number;
}

export interface ExportResult {
  rel: string;
  absPath: string;
  bytes: number;
  chapters: number;
  /** template=回填标书模板（正式交付），preview=内置排版（内部预览） */
  deliveryMode: 'template' | 'preview';
  /** template 模式下成功回填的字段名 */
  filledFields?: string[];
  /** 类型校验未通过而留白的字段（导出阶段告警，人工复核兜底） */
  droppedFields?: Array<{ field: string; value: string; reason: string }>;
  /** 报价数据超出模板行数的截断明细（COPY_PRICING 通道） */
  pricingTruncated?: Array<{ table: string; expected: number; available: number }>;
}

function safeSegment(value: string): string {
  const cleaned = value.replace(/[^\p{L}\p{N}._-]/gu, '_').slice(0, 80);
  return cleaned.length > 0 ? cleaned : 'default';
}

export class BidDocxExporter {
  constructor(
    private readonly config: AppConfig,
    private readonly logger: Logger,
    /** COPY_PRICING 数据拉取（缺省不启用报价通道，如预览/降级场景） */
    private readonly pricingFetcher?: (
      projectId: string,
      tenantId: string,
    ) => Promise<PricingData | null> | PricingData | null,
  ) {}

  private wsDir(tenantId: string, projectId: string, taskId?: string): string {
    const base = path.join(this.config.paths.workspace, safeSegment(tenantId), safeSegment(projectId));
    return taskId ? path.join(base, safeSegment(taskId)) : base;
  }

  async export(tenantId: string, projectId: string, taskId?: string): Promise<ExportResult> {
    const dir = this.wsDir(tenantId, projectId, taskId);
    const outlinePath = path.join(dir, WORKSPACE_FILES.outline);
    const draftDir = path.join(dir, WORKSPACE_FILES.draftDir);
    const parsePath = path.join(dir, 'tenderParse.json');

    if (!fs.existsSync(outlinePath)) {
      throw new AppError('该项目的投标大纲尚未生成（outline.json 缺失）。请先执行“生成投标大纲 / 撰写章节草稿”，再导出 Word 文件', {
        category: 'validation',
        status: 400,
      });
    }

    // 报价数据（COPY_PRICING）：拉取失败不阻断交付，仅告警——计价表留白由人工复核兜底
    let pricing: PricingData | null = null;
    if (this.pricingFetcher) {
      try {
        pricing = await this.pricingFetcher(projectId, tenantId);
      } catch (error) {
        this.logger.warn('报价数据拉取失败，本次交付计价表将保持留白', {
          tenantId,
          projectId,
          message: error instanceof Error ? error.message : String(error),
        });
      }
      if (pricing) {
        const tableCount = Object.keys(pricing.tables ?? {}).length;
        if (tableCount === 0 && Object.keys(pricing.shared ?? {}).length === 0) {
          this.logger.warn('该项目暂无报价数据（PRICING 材料）：计价表约 1229 个书签将保持留白。'
            + '请在「知识库管理 → 资料管理」上传报价 Excel（角色 PRICING，标准模板见 docs/开发联调与运维/投标标书_报价数据标准模板.xlsx）后重新生成', {
            tenantId,
            projectId,
          });
        } else {
          this.logger.info('报价数据已接入', { tenantId, projectId, pricingTables: tableCount });
        }
      }
    }

    let outline: { chapters?: OutlineChapter[] } = {};
    try {
      outline = JSON.parse(fs.readFileSync(outlinePath, 'utf8')) as { chapters?: OutlineChapter[] };
    } catch {
      /* ignore */
    }
    const chapters = Array.isArray(outline.chapters) ? outline.chapters : [];

    // 按 outline 顺序逐章取草稿正文
    const docs: DocChapter[] = [];
    const used = new Set<string>();
    for (const ch of chapters) {
      const nodeId = ch.nodeId?.trim();
      const title = (ch.title || '').trim() || (nodeId ? `章节 ${nodeId}` : '（未命名章节）');
      let md = '';
      if (nodeId) {
        const mdPath = path.join(draftDir, `${nodeId}.md`);
        if (fs.existsSync(mdPath)) {
          md = fs.readFileSync(mdPath, 'utf8');
          used.add(nodeId);
        }
      }
      // 待复核：草稿含「待复核提示」小节（检索未命中留白）的章节，交付件中显著标注
      docs.push({ title, md, review: md.includes('待复核') });
    }
    if (fs.existsSync(draftDir)) {
      const extra = fs
        .readdirSync(draftDir)
        .filter((f) => f.endsWith('.md') && !used.has(path.basename(f, '.md')))
        .sort()
        .map((f) => {
          const nodeId = path.basename(f, '.md');
          const md = fs.readFileSync(path.join(draftDir, f), 'utf8');
          return {
            title: `附录章节 ${nodeId}`,
            md,
            review: md.includes('待复核'),
          } as DocChapter;
        });
      docs.push(...extra);
    }
    if (docs.length === 0) {
      throw new AppError('该项目还没有可导出的章节内容。请先完成“撰写章节草稿”', {
        category: 'validation',
        status: 400,
      });
    }

    // 封面信息：项目名取招标解析结果
    let projectName = '';
    let tenderSummary = '';
    let tenderParse: Record<string, unknown> = {};
    try {
      tenderParse = JSON.parse(fs.readFileSync(parsePath, 'utf8')) as Record<string, unknown>;
      projectName = String(tenderParse.projectName ?? '');
      tenderSummary = String(tenderParse.tenderSummary ?? '');
    } catch {
      /* ignore */
    }

    const deliverDir = path.join(dir, 'deliver');
    fs.mkdirSync(deliverDir, { recursive: true });
    const safeProject = safeSegment(projectId);
    const fileName = `投标文件-${safeProject}.docx`;
    const absPath = path.join(deliverDir, fileName);

    // 优先：配置的模板路径（标签+留白回填 / 书签占位回填）
    const templatePath = this.config.docx.templatePath;
    if (templatePath && fs.existsSync(templatePath)) {
      const { bytes, fields, inserted, dropped, pricingTruncated } = this.exportFromTemplate(
        templatePath,
        { tenantId, projectId, taskId, projectName, docs, pricing },
        absPath,
      );
      this.logger.info('标书已回填模板生成', {
        tenantId,
        projectId,
        fileName,
        bytes,
        chapters: docs.length,
        filledFields: fields.length,
        inserted,
        pricingTruncated: pricingTruncated?.length ?? 0,
      });
      if (pricingTruncated && pricingTruncated.length > 0) {
        this.logger.warn('报价数据超出模板行数，超出部分已截断（请用行扩展模板或精简报价数据）', {
          tenantId,
          projectId,
          truncated: pricingTruncated,
        });
      }
      return {
        rel: `deliver/${fileName}`,
        absPath,
        bytes,
        chapters: docs.length,
        deliveryMode: 'template',
        filledFields: fields,
        droppedFields: dropped,
        pricingTruncated,
      };
    }

    // 兜底：内置排版（预览）
    const cover: CoverInfo = {
      projectId,
      projectName: projectName || `项目 ${projectId}`,
      subTitle: tenderSummary || undefined,
    };
    const buffer = buildDocx({ cover, chapters: docs });
    fs.writeFileSync(absPath, buffer);
    this.logger.info('标书 Word（预览）已生成', {
      tenantId,
      projectId,
      fileName,
      bytes: buffer.byteLength,
      chapters: docs.length,
    });
    return {
      rel: `deliver/${fileName}`,
      absPath,
      bytes: buffer.byteLength,
      chapters: docs.length,
      deliveryMode: 'preview',
    };
  }

  /** 回填标书模板：字段取值来自事实卡 + 招标解析 + 报价数据；章节正文插入到「施工组织设计」标题之后 */
  private exportFromTemplate(
    templatePath: string,
    ctx: {
      tenantId: string;
      projectId: string;
      taskId?: string;
      projectName: string;
      docs: DocChapter[];
      pricing: PricingData | null;
    },
    absPath: string,
  ): { bytes: number; fields: string[]; inserted: boolean; dropped?: Array<{ field: string; value: string; reason: string }>; pricingTruncated?: Array<{ table: string; expected: number; available: number }> } {
    const dir = this.wsDir(ctx.tenantId, ctx.projectId, ctx.taskId);
    const template = fs.readFileSync(templatePath);

    const facts = this.readFacts(dir, ctx.projectName);
    const { values, dropped } = resolveFieldValues(facts, { projectName: ctx.projectName });
    if (dropped.length > 0) {
      this.logger.warn('部分字段取值未通过类型校验，已留白待人工复核（宁可留白，不填错值）', {
        tenantId: ctx.tenantId,
        projectId: ctx.projectId,
        dropped,
      });
    }

    // 章节正文全部作为技术标（施工组织设计）内容插入；剔除内部溯源/待复核小节，
    // 但待复核章节在交付件中以显著标注保留提示（一键生成无人工确认闸门）
    const markdown = ctx.docs
      .map((doc) => {
        const body = stripInternalSections(doc.md).trim();
        if (!body) return '';
        return doc.review ? `**【待复核】** ${doc.title}\n\n${body}` : body;
      })
      .filter((md) => md.length > 0)
      .join('\n\n');
    const sections: TemplateSection[] = markdown
      ? [{ heading: '六、施工组织设计', markdown }]
      : [];

    const result = fillTemplate(template, { values, sections, pricing: ctx.pricing ?? undefined });
    fs.writeFileSync(absPath, result.buffer);
    if (result.skippedBookmarks && result.skippedBookmarks.length > 0) {
      this.logger.warn('书签模板存在取值但无处回填的书签（可能误包住表头标签）', {
        skipped: result.skippedBookmarks,
      });
    }
    return {
      bytes: result.buffer.byteLength,
      fields: result.fields,
      inserted: result.inserted,
      dropped,
      pricingTruncated: result.pricingTruncated,
    };
  }

  /** 从事实卡（项目事实卡.md）解析事实，projectName 优先取招标解析结果 */
  private readFacts(dir: string, projectName: string): FactValue[] {
    const facts: FactValue[] = [];
    const cardPath = path.join(dir, WORKSPACE_FILES.factsCard);
    if (!fs.existsSync(cardPath)) return facts;
    for (const line of fs.readFileSync(cardPath, 'utf8').split(/\r?\n/)) {
      const eq = line.match(/^-\s*(.+?)\s*=\s*(.+?)(?:\s*（来源：.*)?$/);
      if (eq) {
        facts.push({ name: eq[1]!.trim(), value: eq[2]!.trim() });
        continue;
      }
      const colon = line.match(/^-\s*(.+?)[：:]\s*(.+)$/);
      if (colon) {
        facts.push({ name: colon[1]!.trim(), value: colon[2]!.trim() });
      }
    }
    // 项目名称作为工程名称的事实来源兜底
    if (projectName) facts.push({ name: '工程名称', value: projectName });
    return facts;
  }
}

/** 草稿里「溯源引用 / 待复核提示」属于内部审计痕迹，不进正式交付件 */
function stripInternalSections(md: string): string {
  const idx = md.search(/\n##\s*(溯源引用|待复核提示)/);
  return idx < 0 ? md : md.slice(0, idx).trimEnd();
}
