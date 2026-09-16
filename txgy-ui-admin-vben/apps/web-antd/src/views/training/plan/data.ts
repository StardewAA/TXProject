import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

/** 培训类型（8 类） */
export const CLASS_TYPE_OPTIONS = [
  { label: '岗前培训', value: 1 },
  { label: '复工培训', value: 2 },
  { label: '工人技能培训', value: 3 },
  { label: '质检专项', value: 4 },
  { label: '安全管理', value: 5 },
  { label: '特种作业持证', value: 6 },
  { label: '学徒进阶培训班', value: 7 },
  { label: '复合型人才专项培训班', value: 8 },
] as const;

export const CLASS_TYPE_TEXT: Record<number, string> = {
  1: '岗前培训',
  2: '复工培训',
  3: '工人技能培训',
  4: '质检专项',
  5: '安全管理',
  6: '特种作业持证',
  7: '学徒进阶培训班',
  8: '复合型人才专项培训班',
};

/** 培训班状态 */
export const CLASS_STATUS_OPTIONS = [
  { label: '草稿', value: 0, color: 'default' },
  { label: '进行中', value: 1, color: 'processing' },
  { label: '已完成', value: 2, color: 'success' },
  { label: '已取消', value: 3, color: 'error' },
] as const;

export const CLASS_STATUS_TEXT: Record<number, string> = {
  0: '草稿',
  1: '进行中',
  2: '已完成',
  3: '已取消',
};

/** 学习任务状态（总状态 & 课程完成状态共用） */
export const TASK_STATUS_OPTIONS = [
  { label: '未开始', value: 0, color: 'default' },
  { label: '进行中', value: 1, color: 'processing' },
  { label: '已完成', value: 2, color: 'success' },
] as const;

export const TASK_STATUS_TEXT: Record<number, string> = {
  0: '未开始',
  1: '进行中',
  2: '已完成',
};

/** 考试状态 */
export const EXAM_STATUS_OPTIONS = [
  { label: '未参加', value: 0 },
  { label: '已通过', value: 1 },
  { label: '未通过', value: 2 },
] as const;

export const EXAM_STATUS_TEXT: Record<number, string> = {
  0: '未参加',
  1: '已通过',
  2: '未通过',
};

/** 进度看板-学员进度维度 */
export const OVERALL_STATUS_OPTIONS = [
  { label: '未开始', value: 0, color: 'default' },
  { label: '进行中', value: 1, color: 'processing' },
  { label: '已完成', value: 2, color: 'success' },
  { label: '考试未通过', value: 3, color: 'error' },
] as const;

export const OVERALL_STATUS_TEXT: Record<number, string> = {
  0: '未开始',
  1: '进行中',
  2: '已完成',
  3: '考试未通过',
};

/** 列表的搜索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'name',
      label: '培训班名称',
      component: 'Input',
      componentProps: {
        allowClear: true,
        placeholder: '请输入培训班名称',
      },
    },
    {
      fieldName: 'type',
      label: '培训类型',
      component: 'Select',
      componentProps: {
        allowClear: true,
        placeholder: '请选择培训类型',
        options: CLASS_TYPE_OPTIONS,
      },
    },
    {
      fieldName: 'status',
      label: '状态',
      component: 'Select',
      componentProps: {
        allowClear: true,
        placeholder: '请选择状态',
        options: CLASS_STATUS_OPTIONS,
      },
    },
    {
      fieldName: 'code',
      label: '培训班编码',
      component: 'Input',
      componentProps: {
        allowClear: true,
        placeholder: '请输入培训班编码',
      },
    },
  ];
}

/** 列表的字段 */
export function useGridColumns(): VxeTableGridOptions['columns'] {
  return [
    {
      field: 'name',
      title: '培训班名称',
      minWidth: 220,
      showOverflow: true,
    },
    {
      field: 'type',
      title: '培训类型',
      width: 150,
      slots: { default: 'type' },
    },
    {
      field: 'projectName',
      title: '关联项目',
      minWidth: 150,
      showOverflow: true,
      formatter: ({ cellValue }) => cellValue || '-',
    },
    {
      field: 'courseCount',
      title: '课程',
      width: 70,
      align: 'center',
    },
    {
      field: 'studentCount',
      title: '学员',
      width: 70,
      align: 'center',
    },
    {
      field: 'startTime',
      title: '开班时间',
      width: 160,
      formatter: 'formatDateTime',
    },
    {
      field: 'deadline',
      title: '截止时间',
      width: 160,
      formatter: 'formatDateTime',
    },
    {
      field: 'status',
      title: '状态',
      width: 90,
      slots: { default: 'status' },
    },
    {
      field: 'createTime',
      title: '创建时间',
      width: 160,
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

/** 参训对象筛选条件（部门 + 岗位），存储于 training_class.target_condition */
export interface TargetScope {
  deptIds: number[];
  includeChildDept: boolean;
  postIds: number[];
}

/** 解析 targetCondition（兼容旧的手工 JSON：解析失败视为未配置） */
export function parseTargetCondition(condition?: string): TargetScope {
  const empty: TargetScope = {
    deptIds: [],
    includeChildDept: true,
    postIds: [],
  };
  if (!condition) {
    return empty;
  }
  try {
    const parsed = JSON.parse(condition) as Partial<TargetScope>;
    return {
      deptIds: Array.isArray(parsed?.deptIds) ? parsed.deptIds : [],
      includeChildDept: parsed?.includeChildDept !== false,
      postIds: Array.isArray(parsed?.postIds) ? parsed.postIds : [],
    };
  } catch {
    return empty;
  }
}

/** 组装 targetCondition（部门与岗位均未选择时不落库） */
export function buildTargetCondition(
  raw: Record<string, any>,
): string | undefined {
  const deptIds: number[] = Array.isArray(raw?.scopeDeptIds)
    ? raw.scopeDeptIds
    : [];
  const postIds: number[] = Array.isArray(raw?.scopePostIds)
    ? raw.scopePostIds
    : [];
  if (deptIds.length === 0 && postIds.length === 0) {
    return undefined;
  }
  return JSON.stringify({
    deptIds,
    includeChildDept: raw.scopeIncludeChildDept !== false,
    postIds,
  });
}

/** 参训对象筛选条件摘要（详情展示用） */
export function targetConditionSummary(condition?: string): string {
  const scope = parseTargetCondition(condition);
  const parts: string[] = [];
  if (scope.deptIds.length > 0) {
    parts.push(
      `部门 ${scope.deptIds.length} 个（${scope.includeChildDept ? '含下级' : '不含下级'}）`,
    );
  }
  if (scope.postIds.length > 0) {
    parts.push(`岗位 ${scope.postIds.length} 个`);
  }
  return parts.length > 0 ? parts.join('，') : '-';
}
