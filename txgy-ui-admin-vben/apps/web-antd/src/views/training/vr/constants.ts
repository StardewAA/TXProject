/**
 * VR 虚拟仿真实操训练（模块七）展示字典与状态文案
 * 业务依据：《模块七 VR 虚拟仿真实操训练初步搭建方案》
 *   71 VR 资源项目管理 / 72 VR 任务管理 / 73 学员 VR 实操训练 / 74 VR 实操考核 / 75 VR 实操数据看板
 */

// ============ 71 VR 资源项目管理 ============

/** VR 项目状态：0 草稿 1 已上架 2 已下架 3 已归档 */
export const VR_PROJECT_STATUS_OPTIONS = [
  { label: '草稿', value: 0, color: 'default' },
  { label: '已上架', value: 1, color: 'success' },
  { label: '已下架', value: 2, color: 'warning' },
  { label: '已归档', value: 3, color: 'error' },
];

export function vrProjectStatusText(status?: number) {
  return VR_PROJECT_STATUS_OPTIONS.find((item) => item.value === status)?.label;
}

export function vrProjectStatusColor(status?: number) {
  return VR_PROJECT_STATUS_OPTIONS.find((item) => item.value === status)?.color;
}

/** 资源包版本状态：1 开发中 2 已发布 3 已停用 */
export const VR_VERSION_STATUS_OPTIONS = [
  { label: '开发中', value: 1, color: 'default' },
  { label: '已发布', value: 2, color: 'success' },
  { label: '已停用', value: 3, color: 'warning' },
];

export function vrVersionStatusText(status?: number) {
  return VR_VERSION_STATUS_OPTIONS.find((item) => item.value === status)?.label;
}

export function vrVersionStatusColor(status?: number) {
  return VR_VERSION_STATUS_OPTIONS.find((item) => item.value === status)?.color;
}

/** 技能等级标签（分类标签·等级维度） */
export const VR_LEVEL_TAG_OPTIONS = [
  { label: '学徒', value: 1 },
  { label: '初级技工', value: 2 },
  { label: '中级技工', value: 3 },
  { label: '高级技师', value: 4 },
];

export const VR_LEVEL_TAG_TEXT: Record<number, string> = {
  1: '学徒',
  2: '初级技工',
  3: '中级技工',
  4: '高级技师',
};

/** 标签类型：1 技能等级 2 工种 3 工艺 */
export const VR_TAG_TYPE_OPTIONS = [
  { label: '技能等级', value: 1 },
  { label: '工种', value: 2 },
  { label: '工艺', value: 3 },
];

export const VR_TAG_TYPE_TEXT: Record<number, string> = {
  1: '技能等级',
  2: '工种',
  3: '工艺',
};

/** 预置工种标签 */
export const VR_TECH_TAG_PRESET = ['防腐作业', '保温作业', '涂装作业', '电焊作业', '管道安装'];
/** 预置工艺标签 */
export const VR_PROCESS_TAG_PRESET = ['喷砂除锈', '涂料防腐', '聚乙烯包覆', '岩棉保温', '玻璃钢衬里', '高温烘烤'];

// ============ 72 VR 任务管理 ============

/** 任务绑定对象类型：1 课程 2 培训班 */
export const VR_TASK_BIND_OPTIONS = [
  { label: '课程', value: 1 },
  { label: '培训班', value: 2 },
];

export const VR_TASK_BIND_TEXT: Record<number, string> = {
  1: '课程',
  2: '培训班',
};

/** 任务状态：0 未开始 1 进行中 2 已完成 3 已逾期 4 已停用 */
export const VR_TASK_STATUS_OPTIONS = [
  { label: '未开始', value: 0, color: 'default' },
  { label: '进行中', value: 1, color: 'processing' },
  { label: '已完成', value: 2, color: 'success' },
  { label: '已逾期', value: 3, color: 'error' },
  { label: '已停用', value: 4, color: 'warning' },
];

export function vrTaskStatusText(status?: number) {
  return VR_TASK_STATUS_OPTIONS.find((item) => item.value === status)?.label;
}

export function vrTaskStatusColor(status?: number) {
  return VR_TASK_STATUS_OPTIONS.find((item) => item.value === status)?.color;
}

// ============ 73/74 实操训练与考核 ============

/** 实操记录来源：1 训练练习 2 正式考核 */
export const VR_PRACTICE_SOURCE_OPTIONS = [
  { label: '训练练习', value: 1 },
  { label: '正式考核', value: 2 },
];

/** 考核判定方式：1 自动评分 2 人工复核 */
export const VR_ASSESS_METHOD_OPTIONS = [
  { label: '自动评分', value: 1 },
  { label: '人工复核', value: 2 },
];

/** 学员任务（我的任务）分组状态 */
export const VR_MY_TASK_TAB_OPTIONS = [
  { label: '待完成', value: 'pending' },
  { label: '已完成', value: 'finished' },
  { label: '已逾期', value: 'overdue' },
];

export const VR_ASSESS_METHOD_TEXT: Record<number, string> = {
  1: '自动评分',
  2: '人工复核',
};
