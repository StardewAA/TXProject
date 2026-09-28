/**
 * MES 空白单据模板：Schema 定义 + 意图识别 + Word 导出
 *
 * 【新增模块：空白单据模板】
 * 1. 只定义「框架」，不含任何业务数据：字段为空白下划线，表格只留空白行，供人工填写
 * 2. 网页预览（MesDocPreviewModal）与导出 Word 共用同一份 Schema，结构完全一致
 * 3. 意图识别 matchDocIntent：命中「新建/添加/创建/新增/生成/录入…」等动作词 + 单据同义词即触发；
 *    同义词按长度降序匹配，保证"入库单"优先于"入库"
 * 4. 新增单据：加一个 BlankDocSchema 并放进 ALL_BLANK_SCHEMAS 即可，无需改动预览组件
 */
import {
  AlignmentType,
  BorderStyle,
  Document,
  HeightRule,
  Packer,
  Paragraph,
  Table,
  TableCell,
  TableLayoutType,
  TableRow,
  TextRun,
  WidthType,
} from 'docx';

/** 表格定义：标题 + 固定表头 + 预留空白行数 */
export interface DocTableSchema {
  /** 表格标题，如 物料清单 */
  title: string;
  /** 固定表头 */
  headers: string[];
  /** 预留的空白数据行数 */
  blankRows: number;
}

/** 一份空白单据的完整结构 */
export interface BlankDocSchema {
  /** 唯一标识 */
  key: string;
  /** 单据大标题，如 生产工单 */
  title: string;
  /** 同义词（用于意图识别，含简称，如 工单 / 领料 / 采购） */
  aliases: string[];
  /** AI 回复里的链接文字 */
  linkText: string;
  /** 标题下方副标题行的字段（工单编号 / 制单日期） */
  subtitleFields: string[];
  /** 基础信息区的「标签」列表（值全部留空） */
  fields: string[];
  /** 业务表格列表 */
  tables: DocTableSchema[];
  /** 导出文件名 */
  fileName: string;
}

/** 【新增】生产工单空白模板 */
export const WORK_ORDER_SCHEMA: BlankDocSchema = {
  key: 'workOrder',
  title: '生产工单',
  aliases: ['生产工单', '生产订单', '制令单', '工单'],
  linkText: '【点击打开生产工单 Word 文档】',
  subtitleFields: ['工单编号', '制单日期'],
  fields: [
    '工单编号',
    '产品名称',
    '生产数量',
    '计划开工时间',
    '计划完工时间',
    '生产线',
  ],
  tables: [
    {
      title: '物料清单',
      headers: ['序号', '物料编码', '物料名称', '规格型号', '单位', '需求数量'],
      blankRows: 3,
    },
    {
      title: '工序明细',
      headers: ['序号', '工序名称', '工作中心', '标准工时(h)', '负责人'],
      blankRows: 3,
    },
  ],
  fileName: '生产工单.docx',
};

/** 【新增】领料单空白模板 */
export const PICK_ORDER_SCHEMA: BlankDocSchema = {
  key: 'pickOrder',
  title: '领料单',
  aliases: ['领料单', '发料单', '领料'],
  linkText: '【点击打开领料单 Word 文档】',
  subtitleFields: ['领料单号', '领料日期'],
  fields: ['领料单号', '工单编号', '物料名称', '领料数量', '领料仓库', '领料人'],
  tables: [
    {
      title: '领料明细',
      headers: ['序号', '物料编码', '物料名称', '规格型号', '单位', '领料数量'],
      blankRows: 3,
    },
  ],
  fileName: '领料单.docx',
};

/** 【新增】入库单空白模板 */
export const STOCK_IN_SCHEMA: BlankDocSchema = {
  key: 'stockIn',
  title: '入库单',
  aliases: ['入库单', '入库'],
  linkText: '【点击打开入库单 Word 文档】',
  subtitleFields: ['入库单号', '入库日期'],
  fields: ['入库单号', '物料名称', '入库数量', '仓库', '批次', '供应商'],
  tables: [
    {
      title: '入库明细',
      headers: ['序号', '物料编码', '物料名称', '库位', '数量', '单位'],
      blankRows: 3,
    },
  ],
  fileName: '入库单.docx',
};

/** 【新增】采购单空白模板 */
export const PURCHASE_ORDER_SCHEMA: BlankDocSchema = {
  key: 'purchaseOrder',
  title: '采购单',
  aliases: ['采购单', '采购订单', '采购'],
  linkText: '【点击打开采购单 Word 文档】',
  subtitleFields: ['采购单号', '制单日期'],
  fields: ['采购单号', '供应商', '物料名称', '采购数量', '交货日期', '采购员'],
  tables: [
    {
      title: '采购明细',
      headers: ['序号', '物料编码', '物料名称', '规格型号', '单位', '采购数量'],
      blankRows: 3,
    },
  ],
  fileName: '采购单.docx',
};

/** 【新增】检验单空白模板 */
export const INSPECTION_SCHEMA: BlankDocSchema = {
  key: 'inspection',
  title: '检验单',
  aliases: ['检验单', '质检单', '质量检验', '来料检验', '检验'],
  linkText: '【点击打开检验单 Word 文档】',
  subtitleFields: ['检验单号', '检验日期'],
  fields: ['检验单号', '产品名称', '检验数量', '检验标准', '检验员', '检验时间'],
  tables: [
    {
      title: '检验项目明细',
      headers: ['序号', '检验项目', '标准要求', '实测值', '判定'],
      blankRows: 3,
    },
  ],
  fileName: '检验单.docx',
};

/** 全部单据模板（意图识别与下拉选择的数据源） */
export const ALL_BLANK_SCHEMAS: BlankDocSchema[] = [
  WORK_ORDER_SCHEMA,
  PICK_ORDER_SCHEMA,
  STOCK_IN_SCHEMA,
  PURCHASE_ORDER_SCHEMA,
  INSPECTION_SCHEMA,
];

/**
 * 【核心】动作词：命中任意一个即可认为用户想"新建/生成"某类单据
 * 覆盖：添加 / 新增 / 新建 / 创建 / 生成 / 录入 / 登记 / 出具 / 打印 / 做 / 开 / 填 / 写 / 要 / 想 / 帮我 …
 */
const ACTION_PATTERN =
  /(添加|新增|新建|创建|生成|录入|登记|出具|打印|制作|发起|申请|做|开|填|写|要|想要|需要|帮我|给我|来一?个?|来份)/;

/**
 * 【新增】单据意图识别
 *
 * 规则：文本里同时出现「动作词」与「单据同义词」即命中；
 * 多个同义词命中时取最长的一个，避免"入库"抢掉"入库单"。
 *
 * 示例（全部命中生产工单）：
 *   添加工单 / 新建工单 / 创建工单 / 新增工单 / 生成工单
 *   我要做工单 / 我想添加工单 / 我想新建工单 / 我想生成一个工单 / 帮我开个工单
 *
 * @param text 用户输入的原始文本
 * @returns 命中的单据模板，未命中返回 null
 */
export function matchDocIntent(text: string): BlankDocSchema | null {
  if (!text) return null;
  const content = text.trim();
  if (!content) return null;
  // 1. 必须含动作词（我要…/帮我…/添加/新建/生成…）
  if (!ACTION_PATTERN.test(content)) return null;
  // 2. 按最长同义词匹配
  let matched: BlankDocSchema | null = null;
  let matchedLen = 0;
  for (const schema of ALL_BLANK_SCHEMAS) {
    for (const alias of schema.aliases) {
      if (content.includes(alias) && alias.length > matchedLen) {
        matched = schema;
        matchedLen = alias.length;
      }
    }
  }
  return matched;
}

/** 按 key 取模板 */
export function getBlankSchema(key?: string): BlankDocSchema {
  switch (key) {
    case 'pickOrder': {
      return PICK_ORDER_SCHEMA;
    }
    case 'stockIn': {
      return STOCK_IN_SCHEMA;
    }
    case 'purchaseOrder': {
      return PURCHASE_ORDER_SCHEMA;
    }
    case 'inspection': {
      return INSPECTION_SCHEMA;
    }
    default: {
      return WORK_ORDER_SCHEMA;
    }
  }
}

/** 单元格边框（细实线） */
const CELL_BORDER = { style: BorderStyle.SINGLE, size: 1, color: '999999' };

/** A4 内容宽度（twip）：页宽 11906 − 左右边距 1134×2 = 9638 */
const CONTENT_WIDTH = 9_638;

/**
 * 空白下划线：用全角空格 + 下划线渲染，Word 与网页预览都表现为一条待填写的横线
 * @param len 全角空格个数，决定横线长度
 */
function blankRun(len = 10): TextRun {
  return new TextRun({ text: '　'.repeat(len), underline: {} });
}

/**
 * 生成表格：固定表头 + 数据行
 *
 * 【关键】Word 中必须使用「固定布局 + DXA 列宽」，
 * 否则百分比宽度 + 全空单元格会把整张表压成窄窄一列。
 * @param table 表格 Schema
 * @param rows  用户填写的数据行；不传则按 blankRows 生成空行
 */
function buildTable(table: DocTableSchema, rows?: string[][]): Table {
  const dataRows: string[][] =
    rows && rows.length > 0
      ? rows
      : Array.from({ length: table.blankRows }, () =>
          table.headers.map(() => ''),
        );
  // 每列均分 A4 内容宽度
  const colWidth = Math.floor(CONTENT_WIDTH / table.headers.length);
  const columnWidths = table.headers.map(() => colWidth);

  const makeCell = (text: string, isHeader: boolean) =>
    new TableCell({
      width: { size: colWidth, type: WidthType.DXA },
      ...(isHeader ? { shading: { fill: 'F5F7FA' } } : {}),
      children: [
        new Paragraph({
          alignment: AlignmentType.CENTER,
          children: [
            new TextRun({ text, bold: isHeader, size: 20 }),
          ],
        }),
      ],
    });

  return new Table({
    width: { size: CONTENT_WIDTH, type: WidthType.DXA },
    columnWidths,
    layout: TableLayoutType.FIXED,
    borders: {
      top: CELL_BORDER,
      bottom: CELL_BORDER,
      left: CELL_BORDER,
      right: CELL_BORDER,
      insideHorizontal: CELL_BORDER,
      insideVertical: CELL_BORDER,
    },
    rows: [
      // 表头
      new TableRow({
        tableHeader: true,
        height: { value: 400, rule: HeightRule.ATLEAST },
        children: table.headers.map((h) => makeCell(h, true)),
      }),
      // 数据行（用户填写内容 / 空白），给空行保留可书写高度
      ...dataRows.map(
        (row) =>
          new TableRow({
            height: { value: 400, rule: HeightRule.ATLEAST },
            children: row.map((cell) => makeCell(cell ?? '', false)),
          }),
      ),
    ],
  });
}

/** 表单填写状态：预览里用户输入的内容，导出 Word 时一并写入（未填写的仍渲染为空白下划线） */
export interface BlankDocFormState {
  /** 副标题字段值（与 schema.subtitleFields 顺序一致） */
  subtitle: string[];
  /** 基础信息：标签 -> 值 */
  fields: Record<string, string>;
  /** 表格：标题 -> 行数据（行内元素与表头一一对应） */
  tables: Record<string, string[][]>;
}

/**
 * 【新增】导出单据 Word（A4，结构与网页预览一一对应）
 * @param schema 单据 Schema
 * @param state  用户在预览里填写的内容；不传则导出全空白模板
 * @returns docx Blob，供下载使用
 */
export async function exportBlankDoc(
  schema: BlankDocSchema,
  state?: BlankDocFormState,
): Promise<Blob> {
  // 单个字段取值：已填写返回文字，未填写返回空白下划线
  const subtitleValue = (index: number): TextRun => {
    const v = state?.subtitle?.[index]?.trim();
    return v ? new TextRun({ text: v, size: 20 }) : blankRun(8);
  };
  const fieldValue = (label: string): TextRun => {
    const v = state?.fields?.[label]?.trim();
    return v ? new TextRun({ text: v, size: 22 }) : blankRun(12);
  };

  const doc = new Document({
    sections: [
      {
        properties: {
          page: {
            // A4（单位：二十分之一磅）
            size: { width: 11_906, height: 16_838 },
            margin: { top: 1134, right: 1134, bottom: 1134, left: 1134 },
          },
        },
        children: [
          // 大标题：蓝色加粗居中
          new Paragraph({
            alignment: AlignmentType.CENTER,
            spacing: { after: 120 },
            children: [
              new TextRun({
                text: schema.title,
                bold: true,
                size: 36,
                color: '1677FF',
              }),
            ],
          }),
          // 副标题行：工单编号 ____  制单日期 ____
          new Paragraph({
            alignment: AlignmentType.CENTER,
            spacing: { after: 240 },
            children: schema.subtitleFields.flatMap((label, index) => [
              new TextRun({ text: `${label}：`, size: 20 }),
              subtitleValue(index),
              ...(index < schema.subtitleFields.length - 1
                ? [new TextRun({ text: '    ', size: 20 })]
                : []),
            ]),
          }),
          // 基础信息：标签加粗 + 填写内容 / 空白下划线
          ...schema.fields.map(
            (label) =>
              new Paragraph({
                spacing: { after: 120 },
                children: [
                  new TextRun({ text: `${label}：`, bold: true, size: 22 }),
                  fieldValue(label),
                ],
              }),
          ),
          // 业务表格（带用户填写的数据行）
          ...schema.tables.flatMap((table) => [
            new Paragraph({
              spacing: { before: 240, after: 120 },
              children: [
                new TextRun({ text: table.title, bold: true, size: 24 }),
              ],
            }),
            buildTable(table, state?.tables?.[table.title]),
          ]),
        ],
      },
    ],
  });
  return Packer.toBlob(doc);
}
