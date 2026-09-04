import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { MesPollutionLedgerApi } from '#/api/mes/safetyEnv/pollutionLedger';

/** 台账状态选项 */
export const LEDGER_STATUS_OPTIONS = [
  { label: '暂存', value: 'STORED' },
  { label: '处置中', value: 'PROCESSING' },
  { label: '已回用', value: 'REUSED' },
  { label: '已排放', value: 'DISCHARGED' },
  { label: '已处置', value: 'DISPOSED' },
  { label: '已解除', value: 'CLEARED' },
];

/** 台账状态文案 */
export const LEDGER_STATUS_MAP: Record<string, { text: string; color: string }> = {
  STORED: { text: '暂存', color: 'orange' },
  PROCESSING: { text: '处置中', color: 'processing' },
  REUSED: { text: '已回用', color: 'cyan' },
  DISCHARGED: { text: '已排放', color: 'purple' },
  DISPOSED: { text: '已处置', color: 'success' },
  CLEARED: { text: '已解除', color: 'default' },
};

/** 可人工流转的目标状态(暂存为自动登记态) */
export const LEDGER_FLOW_OPTIONS = [
  { label: '处置中', value: 'PROCESSING' },
  { label: '已回用', value: 'REUSED' },
  { label: '已排放', value: 'DISCHARGED' },
  { label: '已处置', value: 'DISPOSED' },
];

/** 处置方式文案(精简) */
export const DISPOSITION_MAP: Record<string, string> = {
  NORMAL_INBOUND: '正常入库',
  CONTROLLED_STORAGE: '受控存储',
  ISSUE_ALLOWED: '允许领用',
  REJECT_ISSUE: '拒绝领用',
  REUSE: '回用',
  DISCHARGE: '排放',
  ISOLATE_STORAGE: '隔离暂存',
  MARKED_STORAGE: '标记存储',
};

/** 处置流转表单(状态 + 备注) */
export function useStatusFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'status',
      label: '流转至',
      component: 'Select',
      componentProps: {
        options: LEDGER_FLOW_OPTIONS,
        placeholder: '选择目标状态(终态将放行该批次)',
      },
      rules: 'required',
    },
    {
      fieldName: 'remark',
      label: '备注',
      component: 'Textarea',
      componentProps: {
        placeholder: '如处置方式/去向说明',
        rows: 3,
      },
      formItemClass: 'col-span-2',
    },
  ];
}

/** 搜索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
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
      fieldName: 'status',
      label: '台账状态',
      component: 'Select',
      componentProps: {
        allowClear: true,
        options: LEDGER_STATUS_OPTIONS,
        placeholder: '请选择',
      },
    },
    {
      fieldName: 'location',
      label: '去向/库位',
      component: 'Input',
      componentProps: {
        allowClear: true,
        placeholder: '如：危废暂存间B',
      },
    },
    {
      fieldName: 'sourceRecordNo',
      label: '来源判定记录',
      component: 'Input',
      componentProps: {
        allowClear: true,
        placeholder: 'PC-...',
      },
    },
  ];
}

/** 列表字段 */
export function useGridColumns(): VxeTableGridOptions<MesPollutionLedgerApi.Ledger>['columns'] {
  return [
    { field: 'sourceRecordNo', title: '来源判定记录', minWidth: 180, showOverflow: true },
    { field: 'stage', title: '环节', width: 110, slots: { default: 'stage' } },
    { field: 'bizNo', title: '关联单号', minWidth: 150, showOverflow: true },
    { field: 'batchNo', title: '批次号', minWidth: 140 },
    { field: 'itemName', title: '物料/产品名称', minWidth: 150 },
    { field: 'disposition', title: '处置方式', width: 120, formatter: ({ cellValue }) => (cellValue ? (DISPOSITION_MAP[cellValue] ?? cellValue) : '-') },
    { field: 'location', title: '去向/库位', minWidth: 130 },
    { field: 'status', title: '台账状态', width: 100, slots: { default: 'status' } },
    { field: 'statusBy', title: '最近流转人', width: 100 },
    { field: 'statusTime', title: '最近流转时间', width: 165, formatter: 'formatDateTime' },
    {
      title: '操作',
      width: 170,
      fixed: 'right',
      slots: {
        default: 'actions',
      },
    },
  ];
}
