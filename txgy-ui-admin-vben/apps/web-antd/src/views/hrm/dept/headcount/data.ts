import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { HrmDeptHeadcountApi } from '#/api/hrm/employee/headcount';

import { getSimpleDeptList } from '#/api/system/dept';

/** 列表搜索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'deptId',
      label: '部门',
      component: 'ApiSelect',
      componentProps: {
        api: getSimpleDeptList,
        labelField: 'name',
        valueField: 'id',
        placeholder: '请选择部门',
        allowClear: true,
        showSearch: true,
      },
    },
  ];
}

/** 列表字段 */
export function useGridColumns(): VxeTableGridOptions<HrmDeptHeadcountApi.DeptHeadcount>['columns'] {
  return [
    { field: 'deptName', title: '部门 / 项目部', minWidth: 240 },
    { field: 'headcount', title: '编制人数', width: 110 },
    { field: 'onboardCount', title: '在册人数', width: 110 },
    {
      field: 'gapCount',
      title: '缺口 / 超编',
      width: 140,
      slots: { default: 'gap' },
    },
    { field: 'remark', title: '备注', minWidth: 220 },
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
      fieldName: 'deptId',
      label: '部门 / 项目部',
      component: 'ApiTreeSelect',
      rules: 'required',
      componentProps: {
        api: getSimpleDeptList,
        labelField: 'name',
        valueField: 'id',
        childrenField: 'children',
        placeholder: '请选择部门或项目部',
        allowClear: true,
        showSearch: true,
      },
      help: '一个部门只允许配置一条编制；部门本身在「系统管理 → 部门管理」维护',
    },
    {
      fieldName: 'headcount',
      label: '编制人数',
      component: 'InputNumber',
      rules: 'required',
      defaultValue: 0,
      componentProps: { min: 0, class: 'w-full', placeholder: '例如：30' },
      help: '按部门整体编制填写（不区分正式/劳务）',
    },
    {
      fieldName: 'remark',
      label: '备注',
      component: 'Textarea',
      componentProps: {
        rows: 3,
        maxlength: 500,
        allowClear: true,
        placeholder: '例如：含劳务分包班组 12 人',
      },
      formItemClass: 'col-span-2',
    },
  ];
}
