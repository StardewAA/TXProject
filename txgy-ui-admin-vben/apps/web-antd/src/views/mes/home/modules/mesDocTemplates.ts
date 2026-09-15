/**
 * MES 助手 - 业务单据 Word 模板数据 与 docx 生成
 *
 * 【新增模块 3】业务单据模板数据 + 文档生成
 * 1. 三类单据：生产工单 / 质检单 / 入库单；新增单据只需在这里加一项 MES_DOC_META + 一个 build 函数 + createDocxBlob 分支
 * 2. matchDocCommand：识别用户输入里的"生成生产工单/质检单/入库单"指令
 * 3. createDocxBlob：用 docx 库在前端直接生成 .docx Blob，供预览（docx-preview）与下载（file-saver）复用
 */
import {
  AlignmentType,
  Document,
  HeadingLevel,
  Packer,
  Paragraph,
  Table,
  TableCell,
  TableRow,
  TextRun,
  WidthType,
} from 'docx';

/** 支持的单据类型 */
export type MesDocType = 'workOrder' | 'qualityCheck' | 'stockIn';

/** 单据元信息：链接文案、文件名、识别关键词 */
export interface MesDocMeta {
  /** 单据类型 */
  type: MesDocType;
  /** 单据名称 */
  label: string;
  /** AI 回复里的链接文字 */
  linkText: string;
  /** 下载时的文件名 */
  fileName: string;
  /** 识别关键词（命中任一即可） */
  keywords: string[];
}

/** 【新增模块 3-1】单据元信息表（扩展新单据时只改这里） */
export const MES_DOC_META: Record<MesDocType, MesDocMeta> = {
  workOrder: {
    type: 'workOrder',
    label: '生产工单',
    linkText: '【点击打开生产工单 Word 文档】',
    fileName: '生产工单.docx',
    keywords: ['生产工单', '工单'],
  },
  qualityCheck: {
    type: 'qualityCheck',
    label: '质检单',
    linkText: '【点击打开质检单 Word 文档】',
    fileName: '质检单.docx',
    keywords: ['质检单', '检验单', '质量检验'],
  },
  stockIn: {
    type: 'stockIn',
    label: '入库单',
    linkText: '【点击打开入库单 Word 文档】',
    fileName: '入库单.docx',
    keywords: ['入库单', '入库'],
  },
};

/**
 * 【新增模块 3-2】指令识别：从用户输入里匹配要生成的单据
 * 规则：命中"生成/创建/导出/出具/打印/做/出"等动作词，且命中某类单据关键词
 * @param text 用户输入的原始文本
 * @returns 命中的单据类型，未命中返回 null
 */
export function matchDocCommand(text: string): MesDocType | null {
  if (!text) return null;
  const content = text.trim();
  const hasAction = /(生成|创建|新建|导出|出具|打印|做一?个?|出)/.test(content);
  if (!hasAction) return null;
  for (const meta of Object.values(MES_DOC_META)) {
    if (meta.keywords.some((k) => content.includes(k))) return meta.type;
  }
  return null;
}

/** 单据里的"标签-值"字段 */
interface FieldItem {
  label: string;
  value: string;
}

/** 单据里的表格：表头 + 数据行 */
interface TableItem {
  title: string;
  headers: string[];
  rows: string[][];
}

/** 一份完整单据的数据结构 */
export interface MesDocData {
  title: string;
  /** 标题下方的编号/日期等附加信息 */
  subtitle: string;
  fields: FieldItem[];
  tables: TableItem[];
  remark: string;
}

/** 生成单号：前缀 + 日期 + 三位流水（演示用，接后端时替换为真实数据） */
function buildNo(prefix: string): string {
  const d = new Date();
  const ymd = `${d.getFullYear()}${String(d.getMonth() + 1).padStart(2, '0')}${String(d.getDate()).padStart(2, '0')}`;
  return `${prefix}-${ymd}-001`;
}

/** 【新增模块 3-3】生产工单模板数据 */
export function buildWorkOrderData(): MesDocData {
  return {
    title: '生 产 工 单',
    subtitle: `工单编号：${buildNo('MO')}    制单日期：${new Date().toLocaleDateString('zh-CN')}`,
    fields: [
      { label: '工单编号', value: buildNo('MO') },
      { label: '产品名称', value: '防腐保温钢管 DN200' },
      { label: '生产数量', value: '500 米' },
      { label: '计划开工时间', value: '2026-09-11 08:00' },
      { label: '计划完工时间', value: '2026-09-13 18:00' },
      { label: '生产线', value: '一号防腐生产线' },
    ],
    tables: [
      {
        title: '物料清单',
        headers: ['序号', '物料编码', '物料名称', '规格型号', '单位', '需求数量'],
        rows: [
          ['1', 'IF2022082432', 'PVC颗粒', 'DN200', 'kg', '120'],
          ['2', 'IF2022082433', '钢管基材', 'DN200×6m', '根', '84'],
          ['3', 'IF2022082434', '防腐涂料', '环氧型', 'kg', '60'],
        ],
      },
      {
        title: '工序明细',
        headers: ['序号', '工序名称', '工作中心', '标准工时(h)', '负责人'],
        rows: [
          ['1', '下料切割', '切割工位', '2.0', '张伟'],
          ['2', '防腐处理', '防腐工位', '6.5', '李强'],
          ['3', '保温包覆', '保温工位', '4.0', '王芳'],
          ['4', '成品检验', '质检工位', '1.5', '赵敏'],
        ],
      },
    ],
    remark: '按客户技术要求执行，生产过程中做好首件确认与过程巡检记录。',
  };
}

/** 【新增模块 3-3】质检单模板数据 */
export function buildQualityCheckData(): MesDocData {
  return {
    title: '质 量 检 验 单',
    subtitle: `检验单号：${buildNo('QC')}    检验日期：${new Date().toLocaleDateString('zh-CN')}`,
    fields: [
      { label: '检验单号', value: buildNo('QC') },
      { label: '产品名称', value: '防腐保温钢管 DN200' },
      { label: '检验数量', value: '50 米' },
      { label: '检验标准', value: 'GB/T 23257-2017' },
      { label: '检验结果', value: '合格' },
      { label: '检验员', value: '赵敏' },
      { label: '检验时间', value: '2026-09-10 10:30' },
    ],
    tables: [
      {
        title: '检验项目明细',
        headers: ['序号', '检验项目', '标准要求', '实测值', '判定'],
        rows: [
          ['1', '外观', '无气泡、无裂纹', '符合要求', '合格'],
          ['2', '防腐层厚度', '≥ 2.5 mm', '2.8 mm', '合格'],
          ['3', '保温层厚度', '≥ 30 mm', '32 mm', '合格'],
          ['4', '附着力', '≥ 50 N/cm', '58 N/cm', '合格'],
        ],
      },
    ],
    remark: '本批次抽检合格，准予入库；后续批次请继续保持首件确认。',
  };
}

/** 【新增模块 3-3】入库单模板数据 */
export function buildStockInData(): MesDocData {
  return {
    title: '入 库 单',
    subtitle: `入库单号：${buildNo('IN')}    入库日期：${new Date().toLocaleDateString('zh-CN')}`,
    fields: [
      { label: '入库单号', value: buildNo('IN') },
      { label: '物料名称', value: 'PVC颗粒' },
      { label: '入库数量', value: '500 kg' },
      { label: '仓库', value: '原料仓' },
      { label: '批次', value: 'BATCH_ITEM_1' },
      { label: '供应商', value: '天信管业有限公司' },
      { label: '入库时间', value: '2026-09-10 09:00' },
    ],
    tables: [
      {
        title: '入库明细',
        headers: ['序号', '物料编码', '物料名称', '库位', '数量', '单位'],
        rows: [
          ['1', 'IF2022082432', 'PVC颗粒', 'A-01-01', '500', 'kg'],
        ],
      },
    ],
    remark: '请库管员核对实物与单据一致后签字确认。',
  };
}

/** 按类型取单据数据 */
export function buildDocData(type: MesDocType): MesDocData {
  switch (type) {
    case 'qualityCheck': {
      return buildQualityCheckData();
    }
    case 'stockIn': {
      return buildStockInData();
    }
    default: {
      return buildWorkOrderData();
    }
  }
}

/** 生成表格（表头灰底 + 数据行） */
function buildTable(table: TableItem): Table {
  return new Table({
    width: { size: 100, type: WidthType.PERCENTAGE },
    rows: [
      new TableRow({
        children: table.headers.map(
          (h) =>
            new TableCell({
              shading: { fill: 'F2F2F2' },
              children: [
                new Paragraph({
                  alignment: AlignmentType.CENTER,
                  children: [new TextRun({ text: h, bold: true, size: 20 })],
                }),
              ],
            }),
        ),
      }),
      ...table.rows.map(
        (row) =>
          new TableRow({
            children: row.map(
              (cell) =>
                new TableCell({
                  children: [
                    new Paragraph({
                      alignment: AlignmentType.CENTER,
                      children: [new TextRun({ text: cell, size: 20 })],
                    }),
                  ],
                }),
            ),
          }),
      ),
    ],
  });
}

/**
 * 【新增模块 3-4】生成 Word 文档的 Blob（预览与下载共用同一个 Blob，避免重复生成）
 * @param type 单据类型
 */
export async function createDocxBlob(type: MesDocType): Promise<Blob> {
  const data = buildDocData(type);
  const doc = new Document({
    sections: [
      {
        children: [
          // 标题
          new Paragraph({
            alignment: AlignmentType.CENTER,
            heading: HeadingLevel.HEADING_1,
            children: [new TextRun({ text: data.title, bold: true, size: 36 })],
          }),
          // 副标题（单号 / 日期）
          new Paragraph({
            alignment: AlignmentType.CENTER,
            spacing: { after: 200 },
            children: [new TextRun({ text: data.subtitle, size: 20, color: '666666' })],
          }),
          // 基本信息：标签加粗 + 值
          ...data.fields.map(
            (f) =>
              new Paragraph({
                spacing: { after: 80 },
                children: [
                  new TextRun({ text: `${f.label}：`, bold: true, size: 22 }),
                  new TextRun({ text: f.value, size: 22 }),
                ],
              }),
          ),
          // 明细表格
          ...data.tables.flatMap((t) => [
            new Paragraph({
              spacing: { before: 240, after: 120 },
              children: [new TextRun({ text: t.title, bold: true, size: 24 })],
            }),
            buildTable(t),
          ]),
          // 备注
          new Paragraph({
            spacing: { before: 240 },
            children: [
              new TextRun({ text: '备注：', bold: true, size: 22 }),
              new TextRun({ text: data.remark, size: 22 }),
            ],
          }),
        ],
      },
    ],
  });
  return Packer.toBlob(doc);
}
