import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { HrmCertificateTypeApi } from '#/api/hrm/certificate';

const STATUS_OPTIONS = [
  { label: '启用', value: 0 },
  { label: '停用', value: 1 },
];

/** 列表搜索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'name',
      label: '证照类型',
      component: 'Input',
      componentProps: { placeholder: '请输入证照类型名称', allowClear: true },
    },
    {
      fieldName: 'specialOperation',
      label: '特种作业',
      component: 'Select',
      componentProps: {
        options: [
          { label: '是', value: true },
          { label: '否', value: false },
        ],
        placeholder: '请选择',
        allowClear: true,
      },
    },
    {
      fieldName: 'mandatory',
      label: '是否必持',
      component: 'Select',
      componentProps: {
        options: [
          { label: '是', value: true },
          { label: '否', value: false },
        ],
        placeholder: '请选择',
        allowClear: true,
      },
    },
    {
      fieldName: 'status',
      label: '状态',
      component: 'Select',
      componentProps: {
        options: STATUS_OPTIONS,
        placeholder: '请选择状态',
        allowClear: true,
      },
    },
  ];
}

/** 列表字段 */
export function useGridColumns(): VxeTableGridOptions<HrmCertificateTypeApi.CertificateType>['columns'] {
  return [
    { field: 'name', title: '证照类型', minWidth: 200 },
    { field: 'code', title: '编码', width: 160 },
    {
      field: 'specialOperation',
      title: '特种作业',
      width: 100,
      formatter: ({ cellValue }) => (cellValue ? '是' : '否'),
    },
    {
      field: 'mandatory',
      title: '必持',
      width: 80,
      formatter: ({ cellValue }) => (cellValue ? '是' : '否'),
    },
    {
      field: 'needReview',
      title: '需复审',
      width: 90,
      formatter: ({ cellValue }) => (cellValue ? '是' : '否'),
    },
    { field: 'warnDays', title: '预警天数', width: 100 },
    { field: 'issuingAuthority', title: '发证机关', minWidth: 160 },
    { field: 'certificateCount', title: '持证条数', width: 100 },
    { field: 'sort', title: '排序', width: 80 },
    {
      field: 'status',
      title: '状态',
      width: 90,
      formatter: ({ cellValue }) => (cellValue === 0 ? '启用' : '停用'),
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
      label: '证照类型',
      component: 'Input',
      rules: 'required',
      componentProps: {
        placeholder: '例如：高处作业操作证',
        maxlength: 128,
        allowClear: true,
      },
    },
    {
      fieldName: 'code',
      label: '编码',
      component: 'Input',
      rules: 'required',
      componentProps: {
        placeholder: '例如：HIGH_WORK（租户内唯一）',
        maxlength: 64,
        allowClear: true,
      },
    },
    {
      fieldName: 'specialOperation',
      label: '是否特种作业',
      component: 'Switch',
      defaultValue: false,
      componentProps: { checkedChildren: '是', unCheckedChildren: '否' },
    },
    {
      fieldName: 'mandatory',
      label: '是否必持',
      component: 'Switch',
      defaultValue: false,
      componentProps: { checkedChildren: '是', unCheckedChildren: '否' },
      help: '必持证照缺失或过期时，员工不允许进场、排班',
    },
    {
      fieldName: 'needReview',
      label: '需要复审',
      component: 'Switch',
      defaultValue: false,
      componentProps: { checkedChildren: '是', unCheckedChildren: '否' },
    },
    {
      fieldName: 'warnDays',
      label: '到期预警天数',
      component: 'InputNumber',
      rules: 'required',
      defaultValue: 60,
      componentProps: { min: 0, class: 'w-full', placeholder: '默认 60 天' },
    },
    {
      fieldName: 'issuingAuthority',
      label: '发证机关',
      component: 'Input',
      componentProps: {
        placeholder: '例如：应急管理局',
        maxlength: 128,
        allowClear: true,
      },
    },
    {
      fieldName: 'sort',
      label: '显示顺序',
      component: 'InputNumber',
      defaultValue: 0,
      componentProps: { min: 0, class: 'w-full' },
    },
    {
      fieldName: 'status',
      label: '状态',
      component: 'Select',
      rules: 'required',
      defaultValue: 0,
      componentProps: { options: STATUS_OPTIONS, placeholder: '请选择状态' },
    },
    {
      fieldName: 'remark',
      label: '备注',
      component: 'Textarea',
      componentProps: {
        rows: 2,
        maxlength: 500,
        allowClear: true,
        placeholder: '例如：登高架设、高处安装维护拆除作业',
      },
      formItemClass: 'col-span-2',
    },
  ];
}
