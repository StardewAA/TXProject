import type { VbenFormSchema } from '#/adapter/form';

import { CLASS_TYPE_OPTIONS } from '../data';

/** 常用岗位（定岗-四定之一，也可手工输入） */
export const POST_OPTIONS = [
  '管道工',
  '焊工',
  '防腐工',
  '保温工',
  '电工',
  '起重工',
  '架子工',
  '质检员',
  '安全员',
  '特种作业操作员',
];

/**
 * 培训班表单 schema
 * @param courseOptions 上架课程下拉（加载自 /training/course/page status=3）
 */
export function useFormSchema(
  courseOptions: { label: string; value: number }[],
): VbenFormSchema[] {
  return [
    {
      fieldName: 'name',
      label: '培训班名称',
      component: 'Input',
      rules: 'required',
      componentProps: {
        placeholder: '请输入培训班名称，如：2026 年管道工岗前培训（一期）',
        maxlength: 200,
      },
    },
    {
      fieldName: 'type',
      label: '培训类型',
      component: 'Select',
      rules: 'required',
      componentProps: {
        placeholder: '请选择培训类型',
        options: CLASS_TYPE_OPTIONS,
      },
    },
    {
      fieldName: 'projectId',
      label: '工程项目编号',
      component: 'Input',
      componentProps: {
        placeholder: '关联项目编号（岗前/复工培训用于上岗资格核查）',
        maxlength: 64,
      },
    },
    {
      fieldName: 'projectName',
      label: '工程项目名称',
      component: 'Input',
      componentProps: {
        placeholder: '关联项目名称，可空',
        maxlength: 200,
      },
    },
    {
      fieldName: 'courseIds',
      label: '绑定课程(定课)',
      component: 'Select',
      componentProps: {
        mode: 'multiple',
        placeholder: '仅可选择已上架课程，开班后不可调整',
        options: courseOptions,
        optionFilterProp: 'label',
      },
    },
    {
      fieldName: 'targetPostsText',
      label: '定岗',
      component: 'Select',
      componentProps: {
        mode: 'tags',
        placeholder: '结业后岗位，可输入回车新增',
        options: POST_OPTIONS.map((item) => ({ label: item, value: item })),
      },
    },
    {
      fieldName: 'startTime',
      label: '开班时间',
      component: 'DatePicker',
      componentProps: {
        showTime: true,
        format: 'YYYY-MM-DD HH:mm:ss',
        valueFormat: 'x',
        placeholder: '选择开班时间',
      },
    },
    {
      fieldName: 'deadline',
      label: '培训截止时间',
      component: 'DatePicker',
      rules: 'required',
      componentProps: {
        showTime: true,
        format: 'YYYY-MM-DD HH:mm:ss',
        valueFormat: 'x',
        placeholder: '选择培训截止时间（开班必须设置）',
      },
    },
    {
      fieldName: 'targetCondition',
      label: '参训对象筛选',
      component: 'InputTextArea',
      componentProps: {
        placeholder: '筛选条件（JSON），如 {"posts":["管道工"],"dept":"管道一班"}，可空',
        maxlength: 1000,
        rows: 3,
      },
    },
    {
      fieldName: 'remark',
      label: '备注',
      component: 'InputTextArea',
      componentProps: {
        placeholder: '备注，可空',
        maxlength: 500,
        rows: 2,
      },
    },
  ];
}
