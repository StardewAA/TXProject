import type { VbenFormSchema } from '#/adapter/form';

import { z } from '#/adapter/form';
import { COURSE_LEVEL_OPTIONS, GROWTH_LEVEL_OPTIONS } from '../data';

/** 新增/修改课程的表单 */
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
      fieldName: 'code',
      label: '课程编码',
      component: 'Input',
      componentProps: {
        maxLength: 64,
        placeholder: '选填，审核通过后自动锁定',
      },
    },
    {
      fieldName: 'title',
      label: '课程标题',
      component: 'Input',
      rules: z.string().min(1, '课程标题不能为空').max(200),
      componentProps: {
        maxLength: 200,
        placeholder: '请输入课程标题',
      },
    },
    {
      fieldName: 'subtitle',
      label: '课程副标题',
      component: 'Input',
      componentProps: {
        maxLength: 300,
        placeholder: '请输入课程副标题',
      },
    },
    {
      fieldName: 'coverUrl',
      label: '封面地址',
      component: 'Input',
      componentProps: {
        placeholder: 'https://…（资源中心媒资地址，模块一后打通）',
      },
    },
    {
      fieldName: 'level',
      label: '课程级别',
      component: 'Select',
      componentProps: {
        options: COURSE_LEVEL_OPTIONS,
        placeholder: '请选择课程级别（旧版 1/2/3）',
      },
    },
    {
      fieldName: 'courseLevel',
      label: '成长等级',
      component: 'Select',
      componentProps: {
        options: GROWTH_LEVEL_OPTIONS,
        placeholder: '请选择成长等级（六级成长体系）',
      },
    },
    {
      fieldName: 'credit',
      label: '课程学分',
      component: 'InputNumber',
      componentProps: {
        min: 0,
        precision: 2,
        placeholder: '请输入学分',
      },
    },
    {
      fieldName: 'durationMinutes',
      label: '总时长(分钟)',
      component: 'InputNumber',
      componentProps: {
        min: 0,
        placeholder: '请输入课程总时长',
      },
    },
    {
      fieldName: 'industry',
      label: '面向行业',
      component: 'Input',
      componentProps: {
        placeholder: '如：防腐保温',
      },
    },
    {
      fieldName: 'applicablePosts',
      label: '适用岗位/工种',
      component: 'Input',
      componentProps: {
        placeholder: 'JSON 数组，如 ["管道工","防腐工"]',
      },
    },
    {
      fieldName: 'ownerFlag',
      label: '资源归属',
      component: 'Input',
      componentProps: {
        placeholder: '如 DEPT_AI_FACTORY（资源中心 owner_flag 快照）',
      },
    },
    {
      fieldName: 'lecturer',
      label: '主讲教师',
      component: 'Input',
      componentProps: {
        placeholder: '请输入主讲教师',
      },
    },
    {
      fieldName: 'lecturerBrief',
      label: '讲师简介',
      component: 'Textarea',
      componentProps: {
        rows: 2,
        placeholder: '请输入讲师简介',
      },
    },
    {
      fieldName: 'bizTags',
      label: '业务标签',
      component: 'Input',
      componentProps: {
        placeholder: 'JSON 数组，如 ["岗前培训"]',
      },
    },
    {
      fieldName: 'isRequired',
      label: '是否必修',
      component: 'Switch',
    },
    {
      fieldName: 'isComposite',
      label: '复合课程',
      component: 'Switch',
    },
    {
      fieldName: 'compositeTags',
      label: '复合子模块标签',
      component: 'Input',
      dependencies: {
        triggerFields: ['isComposite'],
        show: (values: Record<string, any>) => Boolean(values.isComposite),
      },
      componentProps: {
        placeholder: 'JSON 数组，如 ["管道安装","防腐保温"]',
      },
    },
    {
      fieldName: 'examId',
      label: '结课考试',
      component: 'Select',
      componentProps: {
        allowClear: true,
        placeholder: '暂无（模块三试卷中心接入后可选）',
        options: [],
      },
    },
    {
      fieldName: 'sort',
      label: '排序',
      component: 'InputNumber',
      componentProps: {
        min: 0,
        placeholder: '数字越小越靠前',
      },
    },
    {
      fieldName: 'summary',
      label: '课程简介',
      component: 'Textarea',
      componentProps: {
        rows: 3,
        placeholder: '请输入课程简介',
      },
    },
  ];
}
