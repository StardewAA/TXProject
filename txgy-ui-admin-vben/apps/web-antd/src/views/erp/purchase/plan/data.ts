import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { getSimpleDeptList } from '#/api/system/dept';
import { getSimpleUserList } from '#/api/system/user';
import { getRangePickerDefaultProps } from '#/utils';

/** 审批状态：0 草稿、10 审批中、20 审批通过、30 审批驳回、40 已撤销 */
export const auditStatusOptions = [
  { label: '草稿', value: 0 },
  { label: '审批中', value: 10 },
  { label: '审批通过', value: 20 },
  { label: '审批驳回', value: 30 },
  { label: '已撤销', value: 40 },
];

/** 审批状态的中文名 */
export function getAuditStatusLabel(status?: number) {
  return auditStatusOptions.find((item) => item.value === status)?.label ?? '-';
}

/** 新增/修改的表单 */
export function useFormSchema(): VbenFormSchema[] {
  return [
    {
      component: 'Input',
      fieldName: 'id',
      dependencies: {
        triggerFields: [''],
        show: () => false,
      },
    },
    {
      component: 'Input',
      fieldName: 'planName',
      label: '计划名称',
      rules: 'required',
      componentProps: {
        placeholder: '请输入计划名称',
      },
    },
    {
      component: 'Input',
      fieldName: 'planPeriod',
      label: '计划期间',
      componentProps: {
        placeholder: '请输入计划期间，如 2026-Q4 / 2026-09',
      },
    },
    {
      component: 'ApiSelect',
      fieldName: 'departmentId',
      label: '需求部门',
      componentProps: {
        placeholder: '请选择需求部门',
        allowClear: true,
        showSearch: true,
        api: getSimpleDeptList,
        labelField: 'name',
        valueField: 'id',
      },
    },
    {
      component: 'ApiSelect',
      fieldName: 'requesterId',
      label: '计划负责人',
      componentProps: {
        placeholder: '请选择计划负责人',
        allowClear: true,
        showSearch: true,
        api: getSimpleUserList,
        labelField: 'nickname',
        valueField: 'id',
      },
    },
    {
      component: 'DatePicker',
      fieldName: 'planTime',
      label: '编制时间',
      rules: 'required',
      componentProps: {
        placeholder: '请选择编制时间',
        showTime: true,
        format: 'YYYY-MM-DD HH:mm:ss',
        valueFormat: 'x',
      },
    },
    {
      component: 'DatePicker',
      fieldName: 'startDate',
      label: '计划开始日期',
      componentProps: {
        placeholder: '请选择计划开始日期',
        showTime: true,
        format: 'YYYY-MM-DD HH:mm:ss',
        valueFormat: 'x',
      },
    },
    {
      component: 'DatePicker',
      fieldName: 'endDate',
      label: '计划结束日期',
      componentProps: {
        placeholder: '请选择计划结束日期',
        showTime: true,
        format: 'YYYY-MM-DD HH:mm:ss',
        valueFormat: 'x',
      },
    },
    {
      component: 'InputNumber',
      fieldName: 'budgetAmount',
      label: '预算金额',
      componentProps: {
        placeholder: '请输入预算金额',
        min: 0,
        class: '!w-full',
      },
    },
    {
      component: 'Input',
      fieldName: 'purpose',
      label: '计划说明',
      componentProps: {
        placeholder: '请输入计划说明',
      },
    },
    {
      component: 'Input',
      fieldName: 'fileUrl',
      label: '附件地址',
      componentProps: {
        placeholder: '请输入附件地址',
      },
    },
    {
      component: 'Textarea',
      fieldName: 'remark',
      label: '备注',
      componentProps: {
        placeholder: '请输入备注',
        autoSize: { minRows: 1, maxRows: 2 },
      },
      formItemClass: 'col-span-2',
    },
  ];
}

/** 列表的搜索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'no',
      label: '计划编号',
      component: 'Input',
      componentProps: {
        placeholder: '请输入计划编号',
        allowClear: true,
      },
    },
    {
      fieldName: 'planName',
      label: '计划名称',
      component: 'Input',
      componentProps: {
        placeholder: '请输入计划名称',
        allowClear: true,
      },
    },
    {
      fieldName: 'planPeriod',
      label: '计划期间',
      component: 'Input',
      componentProps: {
        placeholder: '请输入计划期间',
        allowClear: true,
      },
    },
    {
      fieldName: 'status',
      label: '审批状态',
      component: 'Select',
      componentProps: {
        placeholder: '请选择审批状态',
        allowClear: true,
        options: auditStatusOptions,
      },
    },
    {
      fieldName: 'planTime',
      label: '编制时间',
      component: 'RangePicker',
      componentProps: {
        ...getRangePickerDefaultProps(),
        allowClear: true,
      },
    },
  ];
}

/** 列表的字段 */
export function useGridColumns(): VxeTableGridOptions['columns'] {
  return [
    {
      field: 'no',
      title: '计划编号',
      width: 190,
      fixed: 'left',
    },
    {
      field: 'planName',
      title: '计划名称',
      minWidth: 160,
      showOverflow: 'tooltip',
    },
    {
      field: 'planPeriod',
      title: '计划期间',
      width: 110,
    },
    {
      field: 'productNames',
      title: '计划物料',
      minWidth: 160,
      showOverflow: 'tooltip',
    },
    {
      field: 'planTime',
      title: '编制时间',
      width: 180,
      formatter: 'formatDateTime',
    },
    {
      field: 'endDate',
      title: '计划结束日期',
      width: 180,
      formatter: 'formatDate',
    },
    {
      field: 'totalCount',
      title: '计划总数量',
      width: 110,
      formatter: 'formatAmount3',
    },
    {
      field: 'budgetAmount',
      title: '预算金额',
      width: 120,
      formatter: 'formatAmount2',
    },
    {
      field: 'creatorName',
      title: '创建人',
      width: 110,
    },
    {
      field: 'status',
      title: '审批状态',
      width: 110,
      // 直接按状态值映射为中文，不使用字典
      formatter: ({ cellValue }: any) => getAuditStatusLabel(cellValue),
    },
    {
      title: '操作',
      width: 300,
      fixed: 'right',
      slots: { default: 'actions' },
    },
  ];
}
