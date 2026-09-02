import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

/** 课程状态 */
export const COURSE_STATUS_OPTIONS = [
  { label: '草稿', value: 0, color: 'default' },
  { label: '待审核', value: 1, color: 'processing' },
  { label: '审核通过', value: 2, color: 'cyan' },
  { label: '已上架', value: 3, color: 'success' },
  { label: '已下架', value: 4, color: 'warning' },
] as const;

export const COURSE_LEVEL_OPTIONS = [
  { label: '初级', value: 1 },
  { label: '中级', value: 2 },
  { label: '高级', value: 3 },
] as const;

export const COURSE_MEDIA_TYPE_OPTIONS = [
  { label: '文档', value: 1 },
  { label: '视频', value: 2 },
  { label: '图片', value: 3 },
] as const;

export const COURSE_MEDIA_TYPE_TEXT: Record<number, string> = {
  0: '未绑定',
  1: '文档',
  2: '视频',
  3: '图文',
};

export const COURSE_LEVEL_TEXT: Record<number, string> = {
  1: '初级',
  2: '中级',
  3: '高级',
};

/** 列表的搜索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'title',
      label: '课程标题',
      component: 'Input',
      componentProps: {
        allowClear: true,
        placeholder: '请输入课程标题',
      },
    },
    {
      fieldName: 'status',
      label: '课程状态',
      component: 'Select',
      componentProps: {
        allowClear: true,
        placeholder: '请选择课程状态',
        options: COURSE_STATUS_OPTIONS,
      },
    },
    {
      fieldName: 'code',
      label: '课程编码',
      component: 'Input',
      componentProps: {
        allowClear: true,
        placeholder: '请输入课程编码',
      },
    },
  ];
}

/** 列表的字段 */
export function useGridColumns(): VxeTableGridOptions['columns'] {
  return [
    {
      field: 'title',
      title: '课程标题',
      minWidth: 220,
      showOverflow: true,
    },
    {
      field: 'code',
      title: '课程编码',
      width: 160,
      showOverflow: true,
    },
    {
      field: 'level',
      title: '级别',
      width: 80,
      slots: { default: 'level' },
    },
    {
      field: 'credit',
      title: '学分',
      width: 80,
    },
    {
      field: 'lecturer',
      title: '主讲教师',
      width: 100,
    },
    {
      field: 'status',
      title: '状态',
      width: 100,
      slots: { default: 'status' },
    },
    {
      field: 'mediaType',
      title: '内容类型',
      width: 90,
      slots: { default: 'mediaType' },
    },
    {
      field: 'version',
      title: '版本',
      width: 70,
      align: 'center',
    },
    {
      field: 'mediaCount',
      title: '媒资数',
      width: 80,
      align: 'center',
    },
    {
      field: 'createTime',
      title: '创建时间',
      width: 170,
      formatter: 'formatDateTime',
    },
    {
      title: '操作',
      width: 300,
      fixed: 'right',
      slots: { default: 'actions' },
    },
  ];
}
