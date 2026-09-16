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

/** 业务标签（模块二 新增课程：业务标签分三种） */
export const COURSE_BIZ_TAG_OPTIONS = [
  { label: '岗前培训', value: '岗前培训' },
  { label: '复工强制', value: '复工强制' },
  { label: '持证上岗', value: '持证上岗' },
] as const;

/**
 * 成长等级（六级人才成长体系 training_growth_level 字典，与后台「成长等级维护」保持一致，
 * 二级子模块 23 课程成长等级标记/模块九人才成长统计引用）
 */
export const GROWTH_LEVEL_OPTIONS = [
  { label: '学徒', value: 'APPRENTICE' },
  { label: '初级工', value: 'JUNIOR' },
  { label: '中级工', value: 'INTERMEDIATE' },
  { label: '高级工', value: 'SENIOR' },
  { label: '技师', value: 'TECHNICIAN' },
  { label: '高级技师', value: 'SENIOR_TECHNICIAN' },
] as const;

export const GROWTH_LEVEL_TEXT: Record<string, string> = {
  APPRENTICE: '学徒',
  JUNIOR: '初级工',
  INTERMEDIATE: '中级工',
  SENIOR: '高级工',
  TECHNICIAN: '技师',
  SENIOR_TECHNICIAN: '高级技师',
};

export const COURSE_MEDIA_TYPE_OPTIONS = [
  { label: '文档', value: 1 },
  { label: '视频', value: 2 },
  { label: '图片', value: 3 },
  { label: 'VR/3D仿真', value: 4 },
] as const;

export const COURSE_MEDIA_TYPE_TEXT: Record<number, string> = {
  0: '未绑定',
  1: '文档',
  2: '视频',
  3: '图文',
  4: 'VR/3D',
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
      label: '课程名称',
      component: 'Input',
      componentProps: {
        allowClear: true,
        placeholder: '请输入课程名称',
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
      fieldName: 'courseLevel',
      label: '成长等级',
      component: 'Select',
      componentProps: {
        allowClear: true,
        placeholder: '请选择成长等级',
        options: GROWTH_LEVEL_OPTIONS,
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
    {
      fieldName: 'versionScope',
      label: '版本范围',
      component: 'Select',
      componentProps: {
        allowClear: true,
        placeholder: '默认仅当前版本',
        options: [
          { label: '仅当前版本（默认）', value: 'current' },
          { label: '全部版本（含历史）', value: 'all' },
          { label: '仅历史版本', value: 'history' },
        ],
      },
    },
  ];
}

/** 列表的字段 */
export function useGridColumns(): VxeTableGridOptions['columns'] {
  return [
    {
      field: 'title',
      title: '课程名称',
      minWidth: 220,
      showOverflow: true,
      slots: { default: 'title' },
    },
    {
      field: 'code',
      title: '课程编码',
      width: 160,
      showOverflow: true,
    },
    {
      field: 'courseLevel',
      title: '成长等级',
      width: 90,
      slots: { default: 'courseLevel' },
    },
    {
      field: 'level',
      title: '级别',
      width: 80,
      slots: { default: 'level' },
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
      width: 120,
      align: 'center',
      slots: { default: 'version' },
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
