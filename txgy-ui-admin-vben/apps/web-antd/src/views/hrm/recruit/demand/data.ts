import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { HrmRecruitDemandApi } from '#/api/hrm/recruit/demand';

import {
  HrmRecruitDemandStatusOptions,
  HrmRecruitDemandUrgencyOptions,
} from '#/api/hrm/recruit/demand';
import { getCompanyProfileList } from '#/api/hrm/company';
import { getSimpleDeptList } from '#/api/system/dept';

/** 常见工种（防腐保温行业口径） */
const WORK_TYPE_OPTIONS = [
  { label: '防腐工', value: '防腐工' },
  { label: '保温工', value: '保温工' },
  { label: '喷砂工', value: '喷砂工' },
  { label: '除锈工', value: '除锈工' },
  { label: '油漆工', value: '油漆工' },
  { label: '探伤工（无损检测）', value: '探伤工' },
  { label: '架子工', value: '架子工' },
  { label: '焊工', value: '焊工' },
  { label: '辅工', value: '辅工' },
];

/** 列表搜索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'demandName',
      label: '需求名称',
      component: 'Input',
      componentProps: { placeholder: '请输入需求名称', allowClear: true },
    },
    {
      fieldName: 'workType',
      label: '工种',
      component: 'Select',
      componentProps: {
        options: WORK_TYPE_OPTIONS,
        placeholder: '请选择工种',
        allowClear: true,
        showSearch: true,
      },
    },
    {
      fieldName: 'status',
      label: '状态',
      component: 'Select',
      componentProps: {
        options: [...HrmRecruitDemandStatusOptions],
        placeholder: '请选择状态',
        allowClear: true,
      },
    },
    {
      fieldName: 'urgency',
      label: '紧急程度',
      component: 'Select',
      componentProps: {
        options: [...HrmRecruitDemandUrgencyOptions],
        placeholder: '请选择紧急程度',
        allowClear: true,
      },
    },
  ];
}

/** 列表字段 */
export function useGridColumns(): VxeTableGridOptions<HrmRecruitDemandApi.RecruitDemand>['columns'] {
  return [
    { field: 'demandNo', title: '需求单号', width: 170 },
    { field: 'demandName', title: '需求名称', minWidth: 220 },
    { field: 'deptName', title: '用工部门/项目部', minWidth: 160 },
    { field: 'workType', title: '工种', width: 110 },
    { field: 'certRequire', title: '证照要求', minWidth: 170 },
    {
      field: 'headcount',
      title: '需求/已到岗/缺口',
      width: 150,
      slots: { default: 'progress' },
    },
    { field: 'planEntryDate', title: '计划进场', width: 120 },
    { field: 'expectSalary', title: '参考单价', width: 130 },
    {
      field: 'urgency',
      title: '紧急程度',
      width: 100,
      slots: { default: 'urgency' },
    },
    {
      field: 'status',
      title: '状态',
      width: 100,
      slots: { default: 'status' },
    },
    { field: 'applicant', title: '提出人', width: 100 },
    {
      title: '操作',
      width: 170,
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
      fieldName: 'demandName',
      label: '需求名称',
      component: 'Input',
      rules: 'required',
      componentProps: {
        placeholder: '例如：秦川化工园区二期 保温工用人需求',
        maxlength: 128,
        allowClear: true,
      },
      formItemClass: 'col-span-2',
    },
    {
      fieldName: 'deptId',
      label: '用工部门/项目部',
      component: 'ApiSelect',
      componentProps: {
        api: getSimpleDeptList,
        labelField: 'name',
        valueField: 'id',
        placeholder: '请选择项目部或用工部门',
        allowClear: true,
        showSearch: true,
      },
    },
    {
      fieldName: 'companyProfileId',
      label: '用工主体',
      component: 'ApiSelect',
      componentProps: {
        api: getCompanyProfileList,
        labelField: 'name',
        valueField: 'id',
        placeholder: '请选择用工主体（合同签署主体）',
        allowClear: true,
        showSearch: true,
      },
    },
    {
      fieldName: 'workType',
      label: '工种',
      component: 'Select',
      rules: 'required',
      componentProps: {
        options: WORK_TYPE_OPTIONS,
        placeholder: '请选择工种',
        allowClear: true,
        showSearch: true,
      },
    },
    {
      fieldName: 'certRequire',
      label: '证照要求',
      component: 'Input',
      componentProps: {
        placeholder: '例如：高处作业证、焊工证（多个用、分隔）',
        maxlength: 512,
        allowClear: true,
      },
    },
    {
      fieldName: 'headcount',
      label: '需求人数',
      component: 'InputNumber',
      rules: 'required',
      defaultValue: 1,
      componentProps: { min: 1, class: 'w-full', placeholder: '例如：20' },
    },
    {
      fieldName: 'planEntryDate',
      label: '计划进场日期',
      component: 'DatePicker',
      componentProps: {
        format: 'YYYY-MM-DD',
        valueFormat: 'YYYY-MM-DD',
        class: 'w-full',
        placeholder: '例如：2026-10-08',
      },
    },
    {
      fieldName: 'expectSalary',
      label: '参考单价',
      component: 'Input',
      componentProps: {
        placeholder: '例如：320 元/天 或 45 元/㎡',
        maxlength: 64,
        allowClear: true,
      },
    },
    {
      fieldName: 'urgency',
      label: '紧急程度',
      component: 'Select',
      defaultValue: 1,
      componentProps: {
        options: [...HrmRecruitDemandUrgencyOptions],
        placeholder: '请选择紧急程度',
      },
    },
    {
      fieldName: 'status',
      label: '状态',
      component: 'Select',
      defaultValue: 1,
      componentProps: {
        options: [...HrmRecruitDemandStatusOptions],
        placeholder: '请选择状态',
      },
    },
    {
      fieldName: 'applicant',
      label: '提出人',
      component: 'Input',
      componentProps: { placeholder: '例如：王工', maxlength: 64, allowClear: true },
    },
    {
      fieldName: 'contactMobile',
      label: '联系方式',
      component: 'Input',
      componentProps: { placeholder: '例如：13800000000', maxlength: 32, allowClear: true },
    },
    {
      fieldName: 'remark',
      label: '备注',
      component: 'Textarea',
      componentProps: {
        rows: 3,
        maxlength: 500,
        allowClear: true,
        placeholder: '例如：甲方要求 10 月 8 日前进场，需自带工具',
      },
      formItemClass: 'col-span-2',
    },
  ];
}
