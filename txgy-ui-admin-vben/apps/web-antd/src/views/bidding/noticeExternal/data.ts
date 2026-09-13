import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

/* ===== 外部公告管理端 · 枚举 → 展示文案/颜色（01 §2 / 02 §2，label 与门户口径一致） =====
 *
 * 定位变更（2026-09-25）：状态 / 审核 / 内容形态三类**展示标签已改为"字典优先、本表兜底"**
 * （见 index.vue 的 dictTagFirst/contentTypeLabel）。原因有两条，缺一不可：
 *   ① 用户硬约束「状态码、枚举值必须动态配置，严禁硬编码」——字典才是主路径；
 *   ② 本页用户**未必有** /bidding/meta/* 权限（后端 @PreAuthorize 只列 5 个电子证照/资质/人员域权限，
 *      已按 docs/前端规范-全域接口说明/12-字典元数据读取权限契约-2026-09-25.md 请后端放开），
 *      若纯字典化，这些角色会从"中文"直接退化成"英文 code"——所以本表必须保留为兜底，不是欠债。
 * 字典类型：notice_external_status / notice_external_audit_status / notice_external_content_type（均已注册）。
 * 行业相关 / 抽取状态 / AI 抽取 / 正文结构化四类暂无后端枚举可对齐，仍以本表为主（见棘轮基线登记）。 */


export const STATUS_MAP: Record<string, { color: string; text: string; }> = {
  NEW: { text: '待审核', color: 'orange' },
  ACTIVE: { text: '已生效', color: 'green' },
  INACTIVE: { text: '已失效', color: 'default' },
  ARCHIVED: { text: '已归档', color: 'blue' },
};

export const AUDIT_MAP: Record<string, { color: string; text: string; }> = {
  UNREVIEWED: { text: '未审核', color: 'default' },
  APPROVED: { text: '审核通过', color: 'green' },
  REJECTED: { text: '已驳回', color: 'red' },
  TAKEN_DOWN: { text: '已下线', color: 'purple' },
};

export const CONTENT_TYPE_MAP: Record<string, string> = {
  HTML: 'HTML网页',
  PDF: 'PDF文件',
  ATTACHMENT: '附件',
  IMAGE: '图片',
  OTHER: '其他',
};

export const INDUSTRY_MAP: Record<string, { color: string; text: string; }> = {
  BELONGS: { text: '行业相关', color: 'blue' },
  NOT_BELONGS: { text: '不相关', color: 'default' },
  UNCERTAIN: { text: '未知', color: 'orange' },
};

export const EXTRACTION_MAP: Record<string, { color: string; text: string; }> = {
  NONE: { text: '未抽取', color: 'default' },
  SUCCESS: { text: '抽取成功', color: 'green' },
  FAIL_EMPTY: { text: '空(扫描件)', color: 'orange' },
  FAIL: { text: '抽取失败', color: 'red' },
};

export const AI_MAP: Record<string, { color: string; text: string; }> = {
  NONE: { text: '未执行', color: 'default' },
  SUCCESS: { text: 'AI成功', color: 'green' },
  FAILED: { text: 'AI失败', color: 'red' },
};

export const BODY_MAP: Record<string, { color: string; text: string; }> = {
  NONE: { text: '未结构化', color: 'default' },
  SUCCESS: { text: '已结构化', color: 'green' },
  FAILED: { text: '结构化失败', color: 'red' },
};

const tagOpts = (map: Record<string, { color: string; text: string; }>) =>
  Object.entries(map).map(([value, c]) => ({ label: c.text, value }));

/** 列表搜索表单（manage/page 结构化过滤：状态/审核/形态/行业/来源/类型/项目编号） */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'status',
      label: '展示状态',
      component: 'Select',
      componentProps: {
        options: tagOpts(STATUS_MAP),
        placeholder: '全部',
        allowClear: true,
      },
    },
    {
      fieldName: 'auditStatus',
      label: '审核状态',
      component: 'Select',
      componentProps: {
        options: tagOpts(AUDIT_MAP),
        placeholder: '全部',
        allowClear: true,
      },
    },
    {
      fieldName: 'contentType',
      label: '内容形态',
      component: 'Select',
      componentProps: {
        options: Object.entries(CONTENT_TYPE_MAP).map(([value, label]) => ({ label, value })),
        placeholder: '全部',
        allowClear: true,
      },
    },
    {
      fieldName: 'industryFlag',
      label: '行业判定',
      component: 'Select',
      componentProps: {
        options: tagOpts(INDUSTRY_MAP),
        placeholder: '全部',
        allowClear: true,
      },
    },
    {
      fieldName: 'sourceCode',
      label: '来源站 code',
      component: 'Input',
      componentProps: { placeholder: '如 EBIDDING', allowClear: true },
    },
    {
      fieldName: 'noticeTypeRaw',
      label: '公告类型',
      component: 'Input',
      componentProps: { placeholder: '如 招标公告', allowClear: true },
    },
    {
      fieldName: 'projectCode',
      label: '项目编号',
      component: 'Input',
      componentProps: { placeholder: '跟踪维度', allowClear: true },
    },
  ];
}

/**
 * 时间格式化（02 §5：publishTime/effectiveFrom/effectiveTo 出参为 epoch 毫秒整数，
 * 兼容 ISO 字符串 'yyyy-MM-ddTHH:mm:ss' 与数字两种形态）。
 */
const p2t = (n: number) => String(n).padStart(2, '0');
const fmtT = (v?: null | number | string): string => {
  if (v == null || v === '') return '';
  let d: Date;
  if (typeof v === 'number') {
    d = new Date(v);
  } else if (/^\d{13}$/.test(String(v).trim())) {
    d = new Date(Number(v));
  } else {
    d = new Date(String(v).replace(' ', 'T'));
  }
  if (Number.isNaN(+d)) return String(v).slice(0, 19).replace('T', ' ');
  return `${d.getFullYear()}-${p2t(d.getMonth() + 1)}-${p2t(d.getDate())} ${p2t(d.getHours())}:${p2t(d.getMinutes())}:${p2t(d.getSeconds())}`;
};
const textOf = (m: Record<string, string>, v?: string) => (v && m[v] ? m[v] : (v || ''));

/** 列表列（状态/审核/行业/抽取/正文结构化用 slots 上色，见 index.vue 对应 # 模板） */
/** 字典桥（可选）：传入则"字典优先"，不传/为空则用本文件映射表兜底（见文件头"定位变更"） */
export interface NoticeExternalDictBridge {
  /** 内容形态的中文名（字典优先；缺字典时调用方回退 textOf(CONTENT_TYPE_MAP, v)） */
  contentTypeLabel?: (v?: string) => string;
}

export function useGridColumns(dict: NoticeExternalDictBridge = {}): VxeTableGridOptions['columns'] {
  return [
    { type: 'checkbox', width: 40 },
    { field: 'id', title: 'ID', width: 90 },
    { field: 'title', title: '公告标题', minWidth: 240, showOverflow: true },
    { field: 'sourceName', title: '来源', width: 130, showOverflow: true },
    { field: 'noticeTypeRaw', title: '公告类型', width: 110, showOverflow: true },
    {
      field: 'contentType',
      title: '形态',
      width: 95,
      formatter: ({ cellValue }) =>
        dict.contentTypeLabel
          ? dict.contentTypeLabel(cellValue as string | undefined)
          : textOf(CONTENT_TYPE_MAP, cellValue as string | undefined),
    },
    {
      field: '__industry',
      title: '行业判定',
      width: 105,
      slots: { default: 'col_industry' },
    },
    {
      field: '__status',
      title: '展示 / 审核',
      width: 145,
      slots: { default: 'col_status' },
    },
    { field: 'issuerName', title: '发布单位', width: 150, showOverflow: true },
    {
      field: 'publishTime',
      title: '发布时间',
      width: 150,
      formatter: ({ cellValue }) => fmtT(cellValue as string | undefined),
    },
    {
      field: '__proc',
      title: '抽取 / AI / 正文',
      width: 200,
      slots: { default: 'col_proc' },
    },
    {
      field: 'operation',
      title: '操作',
      width: 170,
      fixed: 'right',
      slots: { default: 'operation' },
    },
  ];
}
