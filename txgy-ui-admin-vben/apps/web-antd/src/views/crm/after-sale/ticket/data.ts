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
      component: 'Input',
      fieldName: 'title',
      label: '问题标题',
      rules: 'required',
      componentProps: {
        placeholder: '请输入问题标题',
        allowClear: true,
        maxLength: 200,
      },
    },
    {
      component: 'ApiSelect',
      fieldName: 'customerId',
      label: '客户名称',
      componentProps: {
        api: getCustomerSimpleList,
        labelField: 'name',
        valueField: 'id',
        placeholder: '留空则由质保档案带出',
        allowClear: true,
      },
    },
    {
      component: 'ApiSelect',
      fieldName: 'warrantyId',
      label: '关联质保档案',
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
      fieldName: 'source',
      label: '工单来源',
      componentProps: {
        options: getDictOptions(DICT_TYPE.CRM_AFTER_SALE_TICKET_SOURCE, 'number'),
        placeholder: '请选择工单来源',
        allowClear: true,
      },
      defaultValue: 2,
    },
    {
      component: 'Select',
      fieldName: 'priority',
      label: '优先级',
      componentProps: {
        options: getDictOptions(
          DICT_TYPE.CRM_AFTER_SALE_TICKET_PRIORITY,
          'number',
        ),
        placeholder: '请选择优先级',
        allowClear: true,
      },
      defaultValue: 2,
    },
    {
      component: 'Input',
      fieldName: 'problemType',
      label: '问题类型',
      componentProps: {
        placeholder: '请输入问题类型',
        allowClear: true,
      },
    },
    {
      component: 'Input',
      fieldName: 'contactName',
      label: '联系人',
      componentProps: {
        placeholder: '请输入报障联系人',
        allowClear: true,
        maxLength: 64,
      },
    },
    {
      component: 'Input',
      fieldName: 'contactMobile',
      label: '联系电话',
      componentProps: {
        placeholder: '请输入报障联系人电话',
        allowClear: true,
        maxLength: 20,
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
        placeholder: '可稍后指派',
        allowClear: true,
      },
    },
    {
      component: 'Textarea',
      fieldName: 'description',
      label: '问题描述',
      componentProps: {
        placeholder: '请输入问题描述',
        rows: 4,
        maxLength: 2000,
      },
      formItemClass: 'col-span-2',
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

/** 指派处理人的表单 */
export function useAssignFormSchema(): VbenFormSchema[] {
  return [
    {
      component: 'ApiSelect',
      fieldName: 'assigneeUserId',
      label: '处理人',
      rules: 'required',
      componentProps: {
        api: getSimpleUserList,
        labelField: 'nickname',
        valueField: 'id',
        placeholder: '请选择处理人',
        allowClear: true,
      },
    },
  ];
}

/** 列表的搜索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'no',
      label: '工单编号',
      component: 'Input',
      componentProps: {
        placeholder: '请输入工单编号',
        allowClear: true,
      },
    },
    {
      fieldName: 'title',
      label: '问题标题',
      component: 'Input',
      componentProps: {
        placeholder: '请输入问题标题',
        allowClear: true,
      },
    },
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
      fieldName: 'status',
      label: '工单状态',
      component: 'Select',
      componentProps: {
        allowClear: true,
        placeholder: '请选择工单状态',
        options: getDictOptions(
          DICT_TYPE.CRM_AFTER_SALE_TICKET_STATUS,
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
      field: 'no',
      title: '工单编号',
      minWidth: 180,
      fixed: 'left',
    },
    {
      field: 'title',
      title: '问题标题',
      minWidth: 200,
    },
    {
      field: 'customerName',
      title: '客户名称',
      minWidth: 160,
    },
    {
      field: 'warrantyNo',
      title: '质保编号',
      minWidth: 160,
    },
    {
      field: 'source',
      title: '工单来源',
      minWidth: 120,
      cellRender: {
        name: 'CellDict',
        props: { type: DICT_TYPE.CRM_AFTER_SALE_TICKET_SOURCE },
      },
    },
    {
      field: 'priority',
      title: '优先级',
      minWidth: 100,
      cellRender: {
        name: 'CellDict',
        props: { type: DICT_TYPE.CRM_AFTER_SALE_TICKET_PRIORITY },
      },
    },
    {
      field: 'status',
      title: '工单状态',
      minWidth: 110,
      cellRender: {
        name: 'CellDict',
        props: { type: DICT_TYPE.CRM_AFTER_SALE_TICKET_STATUS },
      },
    },
    {
      field: 'assigneeNickname',
      title: '处理人',
      minWidth: 120,
    },
    {
      field: 'creatorNickname',
      title: '创建人',
      minWidth: 120,
    },
    {
      field: 'createTime',
      title: '创建时间',
      formatter: 'formatDateTime',
      minWidth: 180,
    },
    {
      title: '操作',
      width: 320,
      fixed: 'right',
      slots: { default: 'actions' },
    },
  ];
}
