import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { DICT_TYPE } from '@vben/constants';
import { getDictOptions } from '@vben/hooks';

import { getAfterSaleWarrantyOptions } from '#/api/crm/after-sale/warranty';
import { getCustomerSimpleList } from '#/api/crm/customer';
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
      component: 'ApiSelect',
      fieldName: 'assigneeUserId',
      label: '处理人',
      componentProps: {
        api: getSimpleUserList,
        labelField: 'nickname',
        valueField: 'id',
        placeholder: '留空则默认质保档案的售后责任人',
        allowClear: true,
      },
    },
    {
      component: 'DatePicker',
      fieldName: 'taskDate',
      label: '计划执行日期',
      rules: 'required',
      componentProps: {
        placeholder: '请选择计划执行日期',
        valueFormat: 'YYYY-MM-DD',
        format: 'YYYY-MM-DD',
      },
    },
    {
      component: 'Textarea',
      fieldName: 'remark',
      label: '回访提示',
      rules: 'required',
      componentProps: {
        placeholder: '请输入任务备注/回访提示',
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
      fieldName: 'customerId',
      label: '客户',
      component: 'ApiSelect',
      componentProps: {
        api: getCustomerSimpleList,
        labelField: 'name',
        valueField: 'id',
        placeholder: '请选择客户',
        allowClear: true,
      },
    },
    {
      fieldName: 'assigneeUserId',
      label: '处理人',
      component: 'ApiSelect',
      componentProps: {
        api: getSimpleUserList,
        labelField: 'nickname',
        valueField: 'id',
        placeholder: '请选择处理人',
        allowClear: true,
      },
    },
    {
      fieldName: 'status',
      label: '任务状态',
      component: 'Select',
      componentProps: {
        allowClear: true,
        placeholder: '请选择任务状态',
        options: getDictOptions(
          DICT_TYPE.CRM_AFTER_SALE_VISIT_TASK_STATUS,
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
      field: 'warrantyNo',
      title: '质保编号',
      minWidth: 160,
      fixed: 'left',
    },
    {
      field: 'bizNo',
      title: '业务编号',
      width: 90,
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
      field: 'taskDate',
      title: '计划回访日期',
      minWidth: 140,
    },
    {
      field: 'windowEndDate',
      title: '窗口期截止日期',
      minWidth: 140,
    },
    {
      field: 'assigneeNickname',
      title: '处理人',
      minWidth: 120,
    },
    {
      field: 'status',
      title: '任务状态',
      minWidth: 110,
      slots: { default: 'status' },
    },
    {
      field: 'finishTime',
      title: '完成时间',
      formatter: 'formatDateTime',
      minWidth: 180,
    },
    {
      field: 'recordStatus',
      title: '回访记录',
      minWidth: 120,
      slots: { default: 'recordStatus' },
    },
    {
      field: 'remark',
      title: '回访提示',
      minWidth: 200,
    },
    {
      field: 'createTime',
      title: '创建时间',
      formatter: 'formatDateTime',
      minWidth: 180,
    },
    {
      title: '操作',
      width: 280,
      fixed: 'right',
      slots: { default: 'actions' },
    },
  ];
}
