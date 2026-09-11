import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { DICT_TYPE } from '@vben/constants';
import { getDictOptions } from '@vben/hooks';

import { getAfterSaleWarrantyOptions } from '#/api/crm/after-sale/warranty';
import { getSimpleUserList } from '#/api/system/user';

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
      fieldName: 'name',
      label: '计划名称',
      rules: 'required',
      componentProps: {
        placeholder: '请输入计划名称',
        allowClear: true,
        maxLength: 128,
      },
    },
    {
      component: 'ApiSelect',
      fieldName: 'warrantyId',
      label: '关联质保档案',
      rules: 'required',
      componentProps: {
        api: getAfterSaleWarrantyOptions,
        labelField: 'label',
        valueField: 'value',
        placeholder: '请选择关联质保档案',
        allowClear: true,
        showSearch: true,
        optionFilterProp: 'label',
      },
    },
    {
      component: 'Select',
      fieldName: 'planType',
      label: '计划类型',
      rules: 'required',
      componentProps: {
        options: getDictOptions(
          DICT_TYPE.CRM_AFTER_SALE_VISIT_PLAN_TYPE,
          'number',
        ),
        placeholder: '请选择计划类型',
        allowClear: true,
      },
      defaultValue: 1,
    },
    {
      component: 'Select',
      fieldName: 'cycleType',
      label: '回访周期',
      rules: 'required',
      componentProps: {
        options: getDictOptions(DICT_TYPE.CRM_AFTER_SALE_VISIT_CYCLE, 'number'),
        placeholder: '请选择回访周期',
        allowClear: true,
      },
      defaultValue: 1,
    },
    {
      component: 'InputNumber',
      fieldName: 'cycleValue',
      label: '周期值',
      rules: 'required',
      componentProps: {
        class: '!w-full',
        min: 1,
        max: 12,
        precision: 0,
        placeholder: '周期倍数；到期前重点回访时为到期前天数',
      },
      defaultValue: 1,
    },
    {
      component: 'DatePicker',
      fieldName: 'startDate',
      label: '计划开始日期',
      componentProps: {
        placeholder: '留空则默认质保开始日',
        valueFormat: 'YYYY-MM-DD',
        format: 'YYYY-MM-DD',
      },
    },
    {
      component: 'DatePicker',
      fieldName: 'endDate',
      label: '计划结束日期',
      componentProps: {
        placeholder: '留空则默认质保到期日',
        valueFormat: 'YYYY-MM-DD',
        format: 'YYYY-MM-DD',
      },
    },
    {
      component: 'Select',
      fieldName: 'assigneeType',
      label: '处理人类型',
      componentProps: {
        options: [
          { label: '售后人员', value: 1 },
          { label: '业务员', value: 2 },
        ],
        placeholder: '请选择处理人类型',
        allowClear: true,
      },
      defaultValue: 1,
    },
    {
      component: 'ApiSelect',
      fieldName: 'assigneeUserId',
      label: '指定处理人',
      componentProps: {
        api: getSimpleUserList,
        labelField: 'nickname',
        valueField: 'id',
        placeholder: '留空则按处理人类型自动带出',
        allowClear: true,
      },
    },
    {
      component: 'Select',
      fieldName: 'visitType',
      label: '回访方式',
      componentProps: {
        options: getDictOptions(DICT_TYPE.CRM_AFTER_SALE_VISIT_TYPE, 'number'),
        placeholder: '请选择回访方式',
        allowClear: true,
      },
      defaultValue: 1,
    },
    {
      component: 'Select',
      fieldName: 'status',
      label: '计划状态',
      componentProps: {
        options: getDictOptions(
          DICT_TYPE.CRM_AFTER_SALE_VISIT_PLAN_STATUS,
          'number',
        ),
        placeholder: '请选择计划状态',
        allowClear: true,
      },
      defaultValue: 1,
    },
    {
      component: 'Textarea',
      fieldName: 'remark',
      label: '备注',
      componentProps: {
        placeholder: '请输入备注',
        rows: 3,
        maxLength: 500,
      },
      formItemClass: 'col-span-2',
    },
  ];
}

/** 列表的搜索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'name',
      label: '计划名称',
      component: 'Input',
      componentProps: {
        placeholder: '请输入计划名称',
        allowClear: true,
      },
    },
    {
      fieldName: 'planType',
      label: '计划类型',
      component: 'Select',
      componentProps: {
        allowClear: true,
        placeholder: '请选择计划类型',
        options: getDictOptions(
          DICT_TYPE.CRM_AFTER_SALE_VISIT_PLAN_TYPE,
          'number',
        ),
      },
    },
    {
      fieldName: 'status',
      label: '计划状态',
      component: 'Select',
      componentProps: {
        allowClear: true,
        placeholder: '请选择计划状态',
        options: getDictOptions(
          DICT_TYPE.CRM_AFTER_SALE_VISIT_PLAN_STATUS,
          'number',
        ),
      },
    },
  ];
}

/** 列表的字段 */
export function useGridColumns(): VxeTableGridOptions['columns'] {
  return [
    {
      field: 'name',
      title: '计划名称',
      minWidth: 180,
      fixed: 'left',
    },
    {
      field: 'warrantyNo',
      title: '质保编号',
      minWidth: 160,
    },
    {
      field: 'projectName',
      title: '工程项目名称',
      minWidth: 200,
    },
    {
      field: 'customerName',
      title: '客户名称',
      minWidth: 160,
    },
    {
      field: 'planType',
      title: '计划类型',
      minWidth: 110,
      cellRender: {
        name: 'CellDict',
        props: { type: DICT_TYPE.CRM_AFTER_SALE_VISIT_PLAN_TYPE },
      },
    },
    {
      field: 'cycleType',
      title: '回访周期',
      minWidth: 160,
      cellRender: {
        name: 'CellDict',
        props: { type: DICT_TYPE.CRM_AFTER_SALE_VISIT_CYCLE },
      },
    },
    {
      field: 'cycleValue',
      title: '周期值',
      minWidth: 100,
    },
    {
      field: 'startDate',
      title: '开始日期',
      minWidth: 130,
    },
    {
      field: 'endDate',
      title: '结束日期',
      minWidth: 130,
    },
    {
      field: 'assigneeNickname',
      title: '处理人',
      minWidth: 120,
    },
    {
      field: 'status',
      title: '计划状态',
      minWidth: 110,
      cellRender: {
        name: 'CellDict',
        props: { type: DICT_TYPE.CRM_AFTER_SALE_VISIT_PLAN_STATUS },
      },
    },
    {
      field: 'createTime',
      title: '创建时间',
      formatter: 'formatDateTime',
      minWidth: 180,
    },
    {
      title: '操作',
      width: 220,
      fixed: 'right',
      slots: { default: 'actions' },
    },
  ];
}
