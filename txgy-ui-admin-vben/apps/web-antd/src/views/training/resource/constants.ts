/** 资源中心展示字典：状态/题型/难度/转码/标签维度等 */

/** 文档/视频资源状态 */
export const RESOURCE_STATUS_OPTIONS = [
  { label: '草稿', value: 1, color: 'default' },
  { label: '上线', value: 2, color: 'success' },
  { label: '下架', value: 3, color: 'warning' },
  { label: '归档', value: 4, color: 'purple' },
] as const;

export function statusText(status?: number) {
  return RESOURCE_STATUS_OPTIONS.find((item) => item.value === status)?.label;
}

export function statusColor(status?: number) {
  return RESOURCE_STATUS_OPTIONS.find((item) => item.value === status)?.color;
}

/** 题型 */
export const QUESTION_TYPE_OPTIONS = [
  { label: '单选', value: 1 },
  { label: '多选', value: 2 },
  { label: '判断', value: 3 },
  { label: '实操问答', value: 4 },
  { label: '案例分析', value: 5 },
  { label: '填空', value: 6 },
  { label: '简答', value: 7 },
] as const;

export const QUESTION_TYPE_TEXT: Record<number, string> = {
  1: '单选',
  2: '多选',
  3: '判断',
  4: '实操问答',
  5: '案例分析',
  6: '填空',
  7: '简答',
};

export const QUESTION_TYPE_COLOR: Record<number, string> = {
  1: 'blue',
  2: 'cyan',
  3: 'green',
  4: 'orange',
  5: 'volcano',
  6: 'purple',
  7: 'magenta',
};

/** 难度 */
export const QUESTION_DIFFICULTY_OPTIONS = [
  { label: '易', value: 1 },
  { label: '中', value: 2 },
  { label: '难', value: 3 },
] as const;

export function difficultyText(difficulty?: number) {
  return QUESTION_DIFFICULTY_OPTIONS.find((item) => item.value === difficulty)
    ?.label;
}

/** 视频转码状态 */
export const TRANSCODE_STATUS_OPTIONS = [
  { label: '未转码', value: 0, color: 'default' },
  { label: '排队中', value: 1, color: 'processing' },
  { label: '转码中', value: 2, color: 'processing' },
  { label: '转码成功', value: 3, color: 'success' },
  { label: '转码失败', value: 4, color: 'error' },
] as const;

export function transcodeText(status?: number) {
  return TRANSCODE_STATUS_OPTIONS.find((item) => item.value === status)?.label;
}

export function transcodeColor(status?: number) {
  return TRANSCODE_STATUS_OPTIONS.find((item) => item.value === status)?.color;
}

/** 转码任务状态（0待转码 1转码中 2成功 3失败 4跳过） */
export const TRANSCODE_TASK_STATUS_OPTIONS = [
  { label: '待转码', value: 0, color: 'default' },
  { label: '转码中', value: 1, color: 'processing' },
  { label: '成功', value: 2, color: 'success' },
  { label: '失败', value: 3, color: 'error' },
  { label: '跳过', value: 4, color: 'warning' },
] as const;

export function transcodeTaskText(status?: number) {
  return TRANSCODE_TASK_STATUS_OPTIONS.find((item) => item.value === status)
    ?.label;
}

export function transcodeTaskColor(status?: number) {
  return TRANSCODE_TASK_STATUS_OPTIONS.find((item) => item.value === status)
    ?.color;
}

/** 标签维度 */
export const TAG_DIMENSION_OPTIONS = [
  { label: '工种', value: 'work_type' },
  { label: '课程', value: 'course' },
] as const;

/** 资源分类类型 */
export const CATEGORY_TYPE_OPTIONS = [
  { label: '文档分类', value: 'doc' },
  { label: '视频分类', value: 'video' },
  { label: '试题分类', value: 'question' },
] as const;

/** 文件大小展示 */
export function formatFileSize(bytes?: number) {
  if (bytes == null || bytes <= 0) {
    return '-';
  }
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`;
  }
  if (bytes < 1024 * 1024 * 1024) {
    return `${(bytes / 1024 / 1024).toFixed(2)} MB`;
  }
  return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`;
}

/** 秒数 → 分钟:秒 展示 */
export function formatDuration(seconds?: number) {
  if (seconds == null || seconds <= 0) {
    return '-';
  }
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return `${m}分${String(s).padStart(2, '0')}秒`;
}
