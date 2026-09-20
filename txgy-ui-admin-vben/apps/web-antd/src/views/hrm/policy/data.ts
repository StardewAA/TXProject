import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { HrmPolicyApi } from '#/api/hrm/policy';

/** 政策类别选项 */
export const POLICY_CATEGORY_OPTIONS = [
  { label: '社保', value: 'social_insurance' },
  { label: '公积金', value: 'provident_fund' },
  { label: '劳动合同', value: 'labor_contract' },
  { label: '培训', value: 'training' },
  { label: '其他', value: 'other' },
];

/** 适用范围选项 */
export const POLICY_SCOPE_OPTIONS = [
  { label: '集团', value: 'group' },
  { label: '租户', value: 'tenant' },
  { label: '地区', value: 'region' },
];

const categoryLabel = (value?: string) =>
  POLICY_CATEGORY_OPTIONS.find((item) => item.value === value)?.label ?? '-';

const scopeLabel = (value?: string) =>
  POLICY_SCOPE_OPTIONS.find((item) => item.value === value)?.label ?? '-';

/** 列表搜索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'name',
      label: '政策名称',
      component: 'Input',
      componentProps: { placeholder: '请输入政策名称', allowClear: true },
    },
    {
      fieldName: 'category',
      label: '类别',
      component: 'Select',
      componentProps: {
        options: POLICY_CATEGORY_OPTIONS,
        placeholder: '请选择类别',
        allowClear: true,
      },
    },
    {
      fieldName: 'scope',
      label: '适用范围',
      component: 'Select',
      componentProps: {
        options: POLICY_SCOPE_OPTIONS,
        placeholder: '请选择适用范围',
        allowClear: true,
      },
    },
    {
      fieldName: 'status',
      label: '状态',
      component: 'Select',
      componentProps: {
        options: [
          { label: '启用', value: 0 },
          { label: '停用', value: 1 },
        ],
        placeholder: '请选择状态',
        allowClear: true,
      },
    },
  ];
}

/** 列表字段 */
export function useGridColumns(): VxeTableGridOptions<HrmPolicyApi.Policy>['columns'] {
  return [
    { field: 'id', title: '编号', width: 90 },
    { field: 'name', title: '政策名称', minWidth: 180 },
    {
      field: 'category',
      title: '类别',
      width: 110,
      formatter: ({ cellValue }) => categoryLabel(cellValue),
    },
    { field: 'docNo', title: '文号', width: 150 },
    { field: 'publishOrg', title: '发布机关', minWidth: 150 },
    { field: 'publishDate', title: '发布日期', width: 120 },
    { field: 'effectiveDate', title: '生效日期', width: 120 },
    { field: 'expireDate', title: '失效日期', width: 120 },
    {
      field: 'scope',
      title: '适用范围',
      width: 110,
      formatter: ({ cellValue }) => scopeLabel(cellValue),
    },
    {
      field: 'status',
      title: '状态',
      width: 90,
      formatter: ({ cellValue }) => (cellValue === 0 ? '启用' : '停用'),
    },
    { field: 'remark', title: '备注', minWidth: 160 },
    {
      field: 'createTime',
      title: '创建时间',
      width: 180,
      formatter: 'formatDateTime',
    },
    {
      title: '操作',
      width: 150,
      fixed: 'right',
      slots: { default: 'actions' },
    },
  ];
}

/** 新增/修改表单 */
export function useFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'id',
      component: 'Input',
      dependencies: { triggerFields: [''], show: () => false },
    },
    {
      fieldName: 'name',
      label: '政策名称',
      component: 'Input',
      rules: 'required',
      componentProps: {
        placeholder: '请输入政策名称',
        maxlength: 100,
        allowClear: true,
      },
    },
    {
      fieldName: 'category',
      label: '类别',
      component: 'Select',
      rules: 'required',
      componentProps: {
        options: POLICY_CATEGORY_OPTIONS,
        placeholder: '请选择类别',
      },
    },
    {
      fieldName: 'docNo',
      label: '文号',
      component: 'Input',
      componentProps: { placeholder: '请输入文号', maxlength: 64, allowClear: true },
    },
    {
      fieldName: 'publishOrg',
      label: '发布机关',
      component: 'Input',
      componentProps: {
        placeholder: '请输入发布机关',
        maxlength: 100,
        allowClear: true,
      },
    },
    {
      fieldName: 'publishDate',
      label: '发布日期',
      component: 'DatePicker',
      componentProps: {
        class: 'w-full',
        valueFormat: 'YYYY-MM-DD',
        placeholder: '请选择发布日期',
      },
    },
    {
      fieldName: 'effectiveDate',
      label: '生效日期',
      component: 'DatePicker',
      componentProps: {
        class: 'w-full',
        valueFormat: 'YYYY-MM-DD',
        placeholder: '请选择生效日期',
      },
    },
    {
      fieldName: 'expireDate',
      label: '失效日期',
      component: 'DatePicker',
      componentProps: {
        class: 'w-full',
        valueFormat: 'YYYY-MM-DD',
        placeholder: '请选择失效日期',
      },
    },
    {
      fieldName: 'scope',
      label: '适用范围',
      component: 'Select',
      rules: 'required',
      componentProps: {
        options: POLICY_SCOPE_OPTIONS,
        placeholder: '请选择适用范围',
      },
    },
    {
      fieldName: 'scopeValue',
      label: '范围明细',
      component: 'Input',
      componentProps: {
        placeholder: '例如地区名称',
        maxlength: 64,
        allowClear: true,
      },
    },
    {
      fieldName: 'tags',
      label: '标签',
      component: 'Select',
      componentProps: {
        mode: 'tags',
        placeholder: '输入后回车添加标签',
        allowClear: true,
      },
    },
    {
      fieldName: 'status',
      label: '状态',
      component: 'Select',
      rules: 'required',
      componentProps: {
        options: [
          { label: '启用', value: 0 },
          { label: '停用', value: 1 },
        ],
        placeholder: '请选择状态',
      },
    },
    {
      fieldName: 'content',
      label: '政策正文',
      component: 'Textarea',
      componentProps: {
        placeholder: '请输入政策正文',
        rows: 6,
        allowClear: true,
      },
    },
    {
      fieldName: 'remark',
      label: '备注',
      component: 'Textarea',
      componentProps: {
        placeholder: '请输入备注',
        maxlength: 500,
        rows: 3,
        allowClear: true,
      },
    },
  ];
}
