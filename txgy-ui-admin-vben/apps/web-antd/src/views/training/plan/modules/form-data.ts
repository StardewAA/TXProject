import type { VbenFormSchema } from '#/adapter/form';

import { handleTree } from '@vben/utils';

import { getSimpleDeptList } from '#/api/system/dept';
import { getSimplePostList } from '#/api/system/post';

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
      fieldName: 'scopeDeptIds',
      label: '参训部门',
      component: 'ApiTreeSelect',
      componentProps: {
        allowClear: true,
        api: async () => handleTree(await getSimpleDeptList()),
        labelField: 'name',
        valueField: 'id',
        childrenField: 'children',
        multiple: true,
        treeCheckable: true,
        showCheckedStrategy: 'SHOW_PARENT',
        treeDefaultExpandAll: true,
        placeholder: '按部门圈选参训人员（可多选）',
      },
    },
    {
      fieldName: 'scopeIncludeChildDept',
      label: '含下级部门',
      component: 'Switch',
      defaultValue: true,
      componentProps: {
        checkedChildren: '是',
        unCheckedChildren: '否',
      },
    },
    {
      fieldName: 'scopePostIds',
      label: '参训岗位',
      component: 'ApiSelect',
      componentProps: {
        allowClear: true,
        api: async () =>
          (await getSimplePostList()).filter((item) => item.status === 0),
        labelField: 'name',
        valueField: 'id',
        mode: 'multiple',
        optionFilterProp: 'label',
        placeholder: '按系统岗位圈选参训人员（可多选）',
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
