import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { MesSetPollutionCheckApi } from '#/api/mes/safetyEnv/pollutionCheck';

/** 环节选项 */
export const STAGE_OPTIONS = [
  { label: '采购入库', value: 'PURCHASE_INBOUND' },
  { label: '生产领用', value: 'MATERIAL_ISSUE' },
  { label: '中间废弃物', value: 'WASTE_INTERMEDIATE' },
  { label: '成品', value: 'FINISHED_PRODUCT' },
];

/** 环节文案 */
export const STAGE_MAP: Record<string, string> = Object.fromEntries(
  STAGE_OPTIONS.map((item) => [item.value, item.label]),
);

/** AI 初筛结果 */
export const AI_RESULT_OPTIONS = [
  { label: '无污染', value: 'CLEAN' },
  { label: '有污染', value: 'POLLUTED' },
  { label: '不确定', value: 'UNCERTAIN' },
];

/** AI 结果展示(色值用于 tag) */
export const AI_RESULT_MAP: Record<string, { text: string; color: string }> = {
  CLEAN: { text: '无污染', color: 'success' },
  POLLUTED: { text: '有污染', color: 'error' },
  UNCERTAIN: { text: '不确定', color: 'warning' },
};

/** 人工复核结果(终态二值) */
export const REVIEW_RESULT_OPTIONS = [
  { label: '无污染', value: 'CLEAN' },
  { label: '有污染', value: 'POLLUTED' },
];

/** 人工结果展示 */
export const REVIEW_RESULT_MAP: Record<string, { text: string; color: string }> = {
  CLEAN: { text: '无污染', color: 'success' },
  POLLUTED: { text: '有污染', color: 'error' },
};

/** 处置方式选项 */
export const DISPOSITION_OPTIONS = [
  { label: '正常入库/直接存储', value: 'NORMAL_INBOUND' },
  { label: '受控存储', value: 'CONTROLLED_STORAGE' },
  { label: '允许领用', value: 'ISSUE_ALLOWED' },
  { label: '拒绝领用', value: 'REJECT_ISSUE' },
  { label: '回用', value: 'REUSE' },
  { label: '排放(需合规登记)', value: 'DISCHARGE' },
  { label: '隔离暂存/保存处理', value: 'ISOLATE_STORAGE' },
  { label: '标记存储', value: 'MARKED_STORAGE' },
];

/** 处置方式文案 */
export const DISPOSITION_MAP: Record<string, string> = Object.fromEntries(
  DISPOSITION_OPTIONS.map((item) => [item.value, item.label]),
);

/** 去向/库位选项(隔离暂存区/受控区/处置区/回用区/排放口) */
export const LOCATION_OPTIONS = [
  { label: '危废暂存间A-01', value: '危废暂存间A-01' },
  { label: '危废暂存间A-06', value: '危废暂存间A-06' },
  { label: '危废暂存间B-05', value: '危废暂存间B-05' },
  { label: '危废暂存间C-03', value: '危废暂存间C-03' },
  { label: '成品受控区C-02', value: '成品受控区C-02' },
  { label: '处置作业区D-02', value: '处置作业区D-02' },
  { label: '回用暂存区E-01', value: '回用暂存区E-01' },
  { label: '合规排放口F-02', value: '合规排放口F-02' },
];

/** 新增/修改污染判定的表单（AI 初筛字段由后端回填，无需录入） */
export function useFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'id',
      component: 'Input',
      dependencies: {
        triggerFields: [''],
        show: () => false,
      },
    },
    {
      fieldName: 'stage',
      label: '环节',
      component: 'Select',
      componentProps: {
        options: STAGE_OPTIONS,
        placeholder: '请选择判定环节',
        allowClear: true,
      },
      rules: 'required',
    },
    {
      fieldName: 'itemName',
      label: '物料/产品名称',
      component: 'Input',
      componentProps: {
        allowClear: true,
        placeholder: '如：含铅涂料 / 无铅锡膏（AI 据此初筛）',
      },
      rules: 'required',
    },
    {
      fieldName: 'itemCode',
      label: '物料/产品编码',
      component: 'Input',
      componentProps: {
        allowClear: true,
        placeholder: '请输入物料/产品编码',
      },
    },
    {
      fieldName: 'itemSpec',
      label: '规格',
      component: 'Input',
      componentProps: {
        allowClear: true,
        placeholder: '请输入规格',
      },
    },
    {
      fieldName: 'bizNo',
      label: '关联单号',
      component: 'Input',
      componentProps: {
        allowClear: true,
        placeholder: '入库单/领料单/报工单号',
      },
    },
    {
      fieldName: 'batchNo',
      label: '批次号',
      component: 'Input',
      componentProps: {
        allowClear: true,
        placeholder: '请输入批次号',
      },
    },
    {
      fieldName: 'remark',
      label: '备注',
      component: 'Textarea',
      componentProps: {
        placeholder: '请输入备注',
        rows: 2,
      },
      formItemClass: 'col-span-3',
    },
  ];
}

/** 人工复核的表单 */
export function useReviewFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'reviewResult',
      label: '人工复核结论',
      component: 'RadioGroup',
      componentProps: {
        options: REVIEW_RESULT_OPTIONS,
      },
      rules: 'required',
    },
    {
      fieldName: 'disposition',
      label: '处置方式',
      component: 'Select',
      componentProps: {
        options: DISPOSITION_OPTIONS,
        placeholder: '请选择(留空按环节+结论取默认)',
        allowClear: true,
      },
    },
    {
      fieldName: 'storageMethod',
      label: '最终存储方法',
      component: 'Input',
      componentProps: {
        allowClear: true,
        placeholder: '可沿用 AI 推荐',
      },
    },
    {
      fieldName: 'location',
      label: '去向/库位',
      component: 'Select',
      componentProps: {
        options: LOCATION_OPTIONS,
        allowClear: true,
        placeholder: '请选择去向/库位',
      },
    },
    {
      fieldName: 'remark',
      label: '复核备注',
      component: 'Textarea',
      componentProps: {
        placeholder: '请输入复核备注',
        rows: 2,
      },
      formItemClass: 'col-span-3',
    },
  ];
}

/** 列表的搜索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'stage',
      label: '环节',
      component: 'Select',
      componentProps: {
        allowClear: true,
        options: STAGE_OPTIONS,
        placeholder: '请选择环节',
      },
    },
    {
      fieldName: 'bizNo',
      label: '关联单号',
      component: 'Input',
      componentProps: {
        allowClear: true,
        placeholder: '采购入库/领料/产品入库等单据号',
      },
    },
    {
      fieldName: 'itemName',
      label: '物料名称',
      component: 'Input',
      componentProps: {
        allowClear: true,
        placeholder: '物料名称模糊',
      },
    },
    {
      fieldName: 'batchNo',
      label: '批次号',
      component: 'Input',
      componentProps: {
        allowClear: true,
        placeholder: '请输入批次号',
      },
    },
    {
      fieldName: 'aiResult',
      label: 'AI 初筛',
      component: 'Select',
      componentProps: {
        allowClear: true,
        options: AI_RESULT_OPTIONS,
        placeholder: '请选择',
      },
    },
    {
      fieldName: 'reviewed',
      label: '判定状态',
      component: 'Select',
      componentProps: {
        allowClear: true,
        options: [
          { label: '待复核', value: false },
          { label: '已复核', value: true },
        ],
        placeholder: '全部',
      },
    },
  ];
}

/** 列表的字段 */
export function useGridColumns(): VxeTableGridOptions<MesSetPollutionCheckApi.PollutionCheck>['columns'] {
  return [
    { field: 'recordNo', title: '记录编号', minWidth: 200 },
    { field: 'bizNo', title: '关联单号', minWidth: 160, showOverflow: true },
    { field: 'stage', title: '环节', width: 110, slots: { default: 'stage' } },
    { field: 'batchNo', title: '批次号', minWidth: 150 },
    { field: 'itemName', title: '物料/产品名称', minWidth: 160 },
    { field: 'aiResult', title: 'AI 初筛', width: 110, slots: { default: 'aiResult' } },
    { field: 'aiConfidence', title: '置信度', width: 90, formatter: ({ cellValue }) => (cellValue != null ? `${cellValue}%` : '-') },
    { field: 'suggestedStorage', title: 'AI 推荐存储方法', minWidth: 200, showOverflow: true },
    { field: 'reviewResult', title: '人工复核', width: 110, slots: { default: 'reviewResult' } },
    { field: 'disposition', title: '处置方式', width: 140, formatter: ({ cellValue }) => (cellValue ? (DISPOSITION_MAP[cellValue] ?? cellValue) : '-') },
    { field: 'storageMethod', title: '最终存储方法', minWidth: 150, showOverflow: true },
    { field: 'location', title: '去向/库位', minWidth: 130 },
    { field: 'marked', title: '标记', width: 90, slots: { default: 'marked' } },
    { field: 'reviewBy', title: '复核人', width: 100 },
    { field: 'reviewTime', title: '复核时间', width: 170, formatter: 'formatDateTime' },
    {
      title: '操作',
      width: 220,
      fixed: 'right',
      slots: {
        default: 'actions',
      },
    },
  ];
}
