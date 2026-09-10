import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { DICT_TYPE } from '@vben/constants';
import { getDictOptions } from '@vben/hooks';

import { getAfterSaleWarrantyOptions } from '#/api/crm/after-sale/warranty';
import { getCustomerSimpleList } from '#/api/crm/customer';

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
      component: 'InputNumber',
      fieldName: 'taskId',
      label: '关联任务编号',
      componentProps: {
        class: '!w-full',
        min: 0,
        precision: 0,
        placeholder: '任务回访时填写，手动登记可留空',
      },
    },
    {
      component: 'Select',
      fieldName: 'visitType',
      label: '回访方式',
      rules: 'required',
      componentProps: {
        options: getDictOptions(DICT_TYPE.CRM_AFTER_SALE_VISIT_TYPE, 'number'),
        placeholder: '请选择回访方式',
        allowClear: true,
      },
      defaultValue: 1,
    },
    {
      component: 'DatePicker',
      fieldName: 'visitTime',
      label: '回访时间',
      componentProps: {
        showTime: true,
        placeholder: '留空则为当前时间',
        valueFormat: 'YYYY-MM-DD HH:mm:ss',
        format: 'YYYY-MM-DD HH:mm:ss',
      },
    },
    {
      component: 'Select',
      fieldName: 'satisfactionLevel',
      label: '满意度',
      componentProps: {
        options: getDictOptions(
          DICT_TYPE.CRM_AFTER_SALE_SATISFACTION,
          'number',
        ),
        placeholder: '请选择满意度',
        allowClear: true,
      },
    },
    {
      component: 'DatePicker',
      fieldName: 'nextVisitDate',
      label: '建议下次回访日期',
      componentProps: {
        placeholder: '请选择建议下次回访日期',
        valueFormat: 'YYYY-MM-DD',
        format: 'YYYY-MM-DD',
      },
    },
    {
      component: 'Textarea',
      fieldName: 'content',
      label: '回访内容',
      rules: 'required',
      componentProps: {
        placeholder: '请输入回访内容',
        rows: 4,
        maxLength: 2000,
      },
      formItemClass: 'col-span-2',
    },
    {
      component: 'Textarea',
      fieldName: 'satisfactionRemark',
      label: '满意度备注',
      componentProps: {
        placeholder: '请输入满意度备注',
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
      fieldName: 'visitType',
      label: '回访方式',
      component: 'Select',
      componentProps: {
        allowClear: true,
        placeholder: '请选择回访方式',
        options: getDictOptions(DICT_TYPE.CRM_AFTER_SALE_VISIT_TYPE, 'number'),
      },
    },
    {
      fieldName: 'satisfactionLevel',
      label: '满意度',
      component: 'Select',
      componentProps: {
        allowClear: true,
        placeholder: '请选择满意度',
        options: getDictOptions(DICT_TYPE.CRM_AFTER_SALE_SATISFACTION, 'number'),
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
      field: 'visitType',
      title: '回访方式',
      minWidth: 120,
      cellRender: {
        name: 'CellDict',
        props: { type: DICT_TYPE.CRM_AFTER_SALE_VISIT_TYPE },
      },
    },
    {
      field: 'visitTime',
      title: '回访时间',
      formatter: 'formatDateTime',
      minWidth: 180,
    },
    {
      field: 'content',
      title: '回访内容',
      minWidth: 260,
    },
    {
      field: 'satisfactionLevel',
      title: '满意度',
      minWidth: 120,
      cellRender: {
        name: 'CellDict',
        props: { type: DICT_TYPE.CRM_AFTER_SALE_SATISFACTION },
      },
    },
    {
      field: 'nextVisitDate',
      title: '下次回访日期',
      minWidth: 140,
    },
    {
      field: 'creatorNickname',
      title: '回访人',
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
      width: 160,
      fixed: 'right',
      slots: { default: 'actions' },
    },
  ];
}
