import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { HrmEmploymentTypeApi } from '#/api/hrm/employment-type';

import { HrmSalaryModeOptions } from '#/api/hrm/employment-type';

const STATUS_OPTIONS = [
  { label: '启用', value: 0 },
  { label: '停用', value: 1 },
];

const salaryModeLabel = (value?: number) =>
  HrmSalaryModeOptions.find((item) => item.value === value)?.label ?? '-';

/** 列表搜索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'name',
      label: '用工形式',
      component: 'Input',
      componentProps: { placeholder: '请输入用工形式名称', allowClear: true },
    },
    {
      fieldName: 'salaryMode',
      label: '计薪方式',
      component: 'Select',
      componentProps: {
        options: [...HrmSalaryModeOptions],
        placeholder: '请选择计薪方式',
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
export function useGridColumns(): VxeTableGridOptions<HrmEmploymentTypeApi.EmploymentType>['columns'] {
  return [
    { field: 'name', title: '用工形式', minWidth: 130 },
    { field: 'code', title: '编码', width: 130 },
    {
      field: 'contractTemplateName',
      title: '默认合同模板',
      minWidth: 220,
      formatter: ({ cellValue }) => cellValue || '未绑定',
    },
    {
      field: 'salaryMode',
      title: '计薪方式',
      width: 110,
      formatter: ({ cellValue }) => salaryModeLabel(cellValue),
    },
    {
      field: 'needWorkDay',
      title: '记工日',
      width: 90,
      formatter: ({ cellValue }) => (cellValue ? '是' : '否'),
    },
    {
      field: 'needFaceClock',
      title: '人脸打卡',
      width: 100,
      formatter: ({ cellValue }) => (cellValue ? '是' : '否'),
    },
    {
      field: 'joinRegular',
      title: '参与转正',
      width: 100,
      formatter: ({ cellValue }) => (cellValue ? '是' : '否'),
    },
    { field: 'sort', title: '排序', width: 80 },
    {
      field: 'status',
      title: '状态',
      width: 90,
      formatter: ({ cellValue }) => (cellValue === 0 ? '启用' : '停用'),
    },
    { field: 'remark', title: '备注', minWidth: 180 },
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
      label: '用工形式',
      component: 'Input',
      rules: 'required',
      componentProps: {
        placeholder: '例如：临时工、劳务用工、日结工',
        maxlength: 64,
        allowClear: true,
      },
    },
    {
      fieldName: 'code',
      label: '编码',
      component: 'Input',
      rules: 'required',
      componentProps: {
        placeholder: '例如：TEMPORARY（租户内唯一）',
        maxlength: 32,
        allowClear: true,
      },
    },
    {
      fieldName: 'contractTemplateId',
      label: '默认合同模板',
      component: 'Select',
      componentProps: {
        options: [],
        placeholder: '请选择劳动合同类模板',
        allowClear: true,
        showSearch: true,
        optionFilterProp: 'label',
      },
      help: '转员工时按该模板自动生成合同；未绑定模板的用工形式不允许用于新入职',
    },
    {
      fieldName: 'salaryMode',
      label: '计薪方式',
      component: 'Select',
      rules: 'required',
      defaultValue: 1,
      componentProps: {
        options: [...HrmSalaryModeOptions],
        placeholder: '请选择默认计薪方式',
      },
    },
    {
      fieldName: 'needWorkDay',
      label: '按工日统计',
      component: 'Switch',
      defaultValue: false,
      componentProps: { checkedChildren: '是', unCheckedChildren: '否' },
    },
    {
      fieldName: 'needFaceClock',
      label: '需人脸打卡',
      component: 'Switch',
      defaultValue: false,
      componentProps: { checkedChildren: '是', unCheckedChildren: '否' },
    },
    {
      fieldName: 'joinRegular',
      label: '参与转正',
      component: 'Switch',
      defaultValue: true,
      componentProps: { checkedChildren: '是', unCheckedChildren: '否' },
      help: '选择「否」后，该用工形式的员工不会被自动转正 Job 处理',
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
        placeholder: '例如：项目制短工，以完成一定工作任务为期限',
        maxlength: 255,
        rows: 3,
        allowClear: true,
      },
    },
  ];
}
