import type { PageParam, PageResult } from '@vben/request';

import { createDemoStore } from '../demo-page';

/**
 * VR 虚拟仿真实操训练（模块七，挂载于 41008「VR 虚拟仿真实操训练」目录下）数据层
 *
 * 说明：后端 VR 实训接口尚未交付，本文件基于 demo-page.ts 提供本地内存演示数据。
 * 页面只依赖本文件导出函数即可运行、操作、演示。后端接口就绪后，
 * 仅需将各函数实现替换为 requestClient 请求（签名与返回值保持不变），页面无需改动。
 */

export namespace TrainingVrApi {
  /** VR 实操项目资源 */
  export interface VrProject {
    id?: number;
    code?: string;
    name?: string;
    /** 工种标签 */
    techTag?: string;
    /** 工艺标签 */
    processTag?: string;
    /** 技能等级标签（多选：1 学徒 2 初级技工 3 中级技工 4 高级技师） */
    levelTags?: number[];
    /** 完成可获得学时（小时） */
    durationHours?: number;
    /** 合格分数线（0-100） */
    passScore?: number;
    /** 当前资源包版本号 */
    currentVersion?: string;
    /** 累计实操人次 */
    playCount?: number;
    /** 0 草稿 1 已上架 2 已下架 3 已归档 */
    status?: number;
    /** 适用岗位/工序说明 */
    scopeDesc?: string;
    publishTime?: string;
    createTime?: string;
  }

  /** VR 资源包版本 */
  export interface VrProjectVersion {
    id?: number;
    projectId?: number;
    version?: string;
    size?: string;
    /** 1 开发中 2 已发布 3 已停用 */
    status?: number;
    releaseNote?: string;
    releaseTime?: string;
  }

  /** VR 分类标签 */
  export interface VrTag {
    id?: number;
    /** 1 技能等级 2 工种 3 工艺 */
    type?: number;
    name?: string;
    sort?: number;
    usageCount?: number;
    createTime?: string;
  }

  /** VR 实操任务 */
  export interface VrTask {
    id?: number;
    taskNo?: string;
    projectId?: number;
    projectName?: string;
    projectStatus?: number;
    /** 绑定对象类型：1 课程 2 培训班 */
    bindType?: number;
    bindName?: string;
    /** 要求完成次数 */
    requireTimes?: number;
    /** 合格评分标准（引用项目合格线，可覆盖） */
    passScore?: number;
    /** 任务截止日期 */
    deadline?: string;
    /** 0 未开始 1 进行中 2 已完成 3 已逾期 4 已停用 */
    status?: number;
    studentCount?: number;
    doneCount?: number;
    creatorName?: string;
    createTime?: string;
  }

  /** VR 实操记录（训练练习 / 正式考核通用） */
  export interface VrPracticeRecord {
    id?: number;
    recordNo?: string;
    studentNo?: string;
    studentName?: string;
    taskId?: number;
    taskName?: string;
    projectId?: number;
    projectName?: string;
    /** 1 训练练习 2 正式考核 */
    source?: number;
    /** 判定方式：1 自动评分 2 人工复核 */
    method?: number;
    score?: number;
    /** 是否合格 */
    qualified?: boolean;
    /** 本次获得学时（小时） */
    gainedHours?: number;
    costMinutes?: number;
    /** 开始时间 */
    startedAt?: string;
    /** 完成时间 */
    finishedAt?: string;
    /** 人工复核人 */
    reviewer?: string;
    /** 复核前得分 */
    beforeScore?: number;
    /** 复核意见 */
    reviewRemark?: string;
    /** 复核时间 */
    reviewTime?: string;
    /** 是否支持重练重考 */
    retryable?: boolean;
    createTime?: string;
  }
}

// ==================== 演示种子数据 ====================

const PROJECT_SEED: TrainingVrApi.VrProject[] = [
  {
    id: 1,
    code: 'VR-FG-001',
    name: '喷砂除锈虚拟仿真实操',
    techTag: '防腐作业',
    processTag: '喷砂除锈',
    levelTags: [1, 2],
    durationHours: 2,
    passScore: 80,
    currentVersion: 'v2.1.0',
    playCount: 186,
    status: 1,
    scopeDesc: '适用于喷砂除锈工序上岗前的沉浸式安全操作训练。',
    publishTime: '2026-07-02 09:30:00',
    createTime: '2026-06-20 10:00:00',
  },
  {
    id: 2,
    code: 'VR-FG-002',
    name: '涂料防腐滚涂仿真实操',
    techTag: '防腐作业',
    processTag: '涂料防腐',
    levelTags: [2, 3],
    durationHours: 3,
    passScore: 75,
    currentVersion: 'v1.8.0',
    playCount: 132,
    status: 1,
    scopeDesc: '覆盖涂料配比、滚涂手法与漆膜厚度控制等关键操作点。',
    publishTime: '2026-07-15 14:00:00',
    createTime: '2026-07-01 16:20:00',
  },
  {
    id: 3,
    code: 'VR-BW-001',
    name: '岩棉保温层敷设仿真实操',
    techTag: '保温作业',
    processTag: '岩棉保温',
    levelTags: [1, 2, 3],
    durationHours: 2.5,
    passScore: 80,
    currentVersion: 'v1.2.0',
    playCount: 78,
    status: 1,
    scopeDesc: '管壳保温敷设、错缝与捆扎工艺的虚拟实操练习。',
    publishTime: '2026-08-01 10:00:00',
    createTime: '2026-07-20 09:00:00',
  },
  {
    id: 4,
    code: 'VR-PE-001',
    name: '聚乙烯包覆缺陷识别仿真实操',
    techTag: '防腐作业',
    processTag: '聚乙烯包覆',
    levelTags: [3, 4],
    durationHours: 4,
    passScore: 85,
    currentVersion: 'v0.9.0',
    playCount: 0,
    status: 0,
    scopeDesc: '包覆缺陷识别与处理（内测版本，待审核上架）。',
    createTime: '2026-08-25 11:00:00',
  },
  {
    id: 5,
    code: 'VR-DH-001',
    name: '高温烘烤工艺仿真实操',
    techTag: '保温作业',
    processTag: '高温烘烤',
    levelTags: [3, 4],
    durationHours: 3,
    passScore: 80,
    currentVersion: 'v2.0.0',
    playCount: 64,
    status: 2,
    scopeDesc: '高温烘烤温控操作仿真（历史版本资源，已下架维护）。',
    publishTime: '2026-06-10 09:00:00',
    createTime: '2026-05-18 15:00:00',
  },
  {
    id: 6,
    code: 'VR-GL-001',
    name: '玻璃钢衬里手糊仿真实操',
    techTag: '防腐作业',
    processTag: '玻璃钢衬里',
    levelTags: [2, 3],
    durationHours: 2,
    passScore: 75,
    currentVersion: 'v1.5.0',
    playCount: 210,
    status: 3,
    scopeDesc: '已归档项目：历史任务记录保留，不再允许新建任务引用。',
    publishTime: '2026-05-20 10:00:00',
    createTime: '2026-04-15 14:00:00',
  },
];

const VERSION_SEED: TrainingVrApi.VrProjectVersion[] = [
  { id: 1, projectId: 1, version: 'v1.0.0', size: '1.2 GB', status: 2, releaseNote: '首版场景与基础操作点。', releaseTime: '2026-06-22 10:00:00' },
  { id: 2, projectId: 1, version: 'v2.0.0', size: '1.6 GB', status: 2, releaseNote: '新增安全帽识别与气瓶压力联锁。', releaseTime: '2026-06-30 15:00:00' },
  { id: 3, projectId: 1, version: 'v2.1.0', size: '1.7 GB', status: 2, releaseNote: '修复评分点采集延迟问题。', releaseTime: '2026-07-02 09:30:00' },
  { id: 4, projectId: 2, version: 'v1.8.0', size: '2.1 GB', status: 2, releaseNote: '漆膜厚度检测点标定更新。', releaseTime: '2026-07-15 14:00:00' },
  { id: 5, projectId: 3, version: 'v1.2.0', size: '980 MB', status: 2, releaseNote: '优化捆扎工艺操作反馈。', releaseTime: '2026-08-01 10:00:00' },
  { id: 6, projectId: 4, version: 'v0.9.0', size: '1.3 GB', status: 1, releaseNote: '内测版本，缺陷样本待扩充。', releaseTime: '2026-08-25 11:00:00' },
  { id: 7, projectId: 5, version: 'v2.0.0', size: '1.5 GB', status: 2, releaseNote: '温控曲线重制。', releaseTime: '2026-06-10 09:00:00' },
  { id: 8, projectId: 6, version: 'v1.5.0', size: '1.1 GB', status: 3, releaseNote: '历史版本（项目已归档）。', releaseTime: '2026-05-20 10:00:00' },
];

const TAG_SEED: TrainingVrApi.VrTag[] = [
  { id: 1, type: 1, name: '学徒', sort: 1, usageCount: 3 },
  { id: 2, type: 1, name: '初级技工', sort: 2, usageCount: 4 },
  { id: 3, type: 1, name: '中级技工', sort: 3, usageCount: 4 },
  { id: 4, type: 1, name: '高级技师', sort: 4, usageCount: 3 },
  { id: 5, type: 2, name: '防腐作业', sort: 10, usageCount: 4 },
  { id: 6, type: 2, name: '保温作业', sort: 11, usageCount: 2 },
  { id: 7, type: 2, name: '涂装作业', sort: 12, usageCount: 0 },
  { id: 8, type: 2, name: '电焊作业', sort: 13, usageCount: 0 },
  { id: 9, type: 3, name: '喷砂除锈', sort: 20, usageCount: 1 },
  { id: 10, type: 3, name: '涂料防腐', sort: 21, usageCount: 1 },
  { id: 11, type: 3, name: '聚乙烯包覆', sort: 22, usageCount: 1 },
  { id: 12, type: 3, name: '岩棉保温', sort: 23, usageCount: 1 },
  { id: 13, type: 3, name: '玻璃钢衬里', sort: 24, usageCount: 1 },
  { id: 14, type: 3, name: '高温烘烤', sort: 25, usageCount: 1 },
];

const TASK_SEED: TrainingVrApi.VrTask[] = [
  {
    id: 1,
    taskNo: 'VR-T-202609-001',
    projectId: 1,
    projectName: '喷砂除锈虚拟仿真实操',
    projectStatus: 1,
    bindType: 2,
    bindName: '2026 防腐作业技能提升班（三期）',
    requireTimes: 2,
    passScore: 80,
    deadline: '2026-09-20',
    status: 1,
    studentCount: 24,
    doneCount: 9,
    creatorName: '王工',
    createTime: '2026-09-01 09:00:00',
  },
  {
    id: 2,
    taskNo: 'VR-T-202609-002',
    projectId: 2,
    projectName: '涂料防腐滚涂仿真实操',
    projectStatus: 1,
    bindType: 1,
    bindName: '涂料防腐工艺课（课程编号 K-PT-002）',
    requireTimes: 1,
    passScore: 75,
    deadline: '2026-09-15',
    status: 3,
    studentCount: 18,
    doneCount: 5,
    creatorName: '李讲师',
    createTime: '2026-08-28 14:00:00',
  },
  {
    id: 3,
    taskNo: 'VR-T-202609-003',
    projectId: 3,
    projectName: '岩棉保温层敷设仿真实操',
    projectStatus: 1,
    bindType: 2,
    bindName: '保温作业新员工带教班',
    requireTimes: 3,
    passScore: 80,
    deadline: '2026-10-10',
    status: 0,
    studentCount: 12,
    doneCount: 0,
    creatorName: '王工',
    createTime: '2026-09-03 10:00:00',
  },
  {
    id: 4,
    taskNo: 'VR-T-202608-012',
    projectId: 6,
    projectName: '玻璃钢衬里手糊仿真实操',
    projectStatus: 3,
    bindType: 1,
    bindName: '玻璃钢衬里工艺课（课程编号 K-GL-001）',
    requireTimes: 1,
    passScore: 75,
    deadline: '2026-08-20',
    status: 2,
    studentCount: 20,
    doneCount: 20,
    creatorName: '李讲师',
    createTime: '2026-08-01 09:00:00',
  },
  {
    id: 5,
    taskNo: 'VR-T-202608-008',
    projectId: 5,
    projectName: '高温烘烤工艺仿真实操',
    projectStatus: 2,
    bindType: 2,
    bindName: '保温作业技能强化班',
    requireTimes: 2,
    passScore: 80,
    deadline: '2026-08-15',
    status: 4,
    studentCount: 15,
    doneCount: 15,
    creatorName: '王工',
    createTime: '2026-07-25 16:00:00',
  },
];

const RECORD_SEED: TrainingVrApi.VrPracticeRecord[] = [
  {
    id: 1, recordNo: 'VR-R-20260902-001', studentNo: 'T001', studentName: '张伟', taskId: 1,
    taskName: '喷砂除锈虚拟仿真实操（2026 防腐作业技能提升班·三期）', projectId: 1, projectName: '喷砂除锈虚拟仿真实操',
    source: 1, method: 1, score: 92, qualified: true, gainedHours: 2, costMinutes: 25,
    startedAt: '2026-09-02 09:10:00', finishedAt: '2026-09-02 09:35:00', retryable: false,
  },
  {
    id: 2, recordNo: 'VR-R-20260902-002', studentNo: 'T002', studentName: '刘洋', taskId: 1,
    taskName: '喷砂除锈虚拟仿真实操（2026 防腐作业技能提升班·三期）', projectId: 1, projectName: '喷砂除锈虚拟仿真实操',
    source: 1, method: 1, score: 74, qualified: false, gainedHours: 0, costMinutes: 30,
    startedAt: '2026-09-02 10:05:00', finishedAt: '2026-09-02 10:35:00', retryable: true,
  },
  {
    id: 3, recordNo: 'VR-R-20260902-003', studentNo: 'T003', studentName: '陈静', taskId: 1,
    taskName: '喷砂除锈虚拟仿真实操（2026 防腐作业技能提升班·三期）', projectId: 1, projectName: '喷砂除锈虚拟仿真实操',
    source: 2, method: 2, score: 88, qualified: true, gainedHours: 2, costMinutes: 28,
    startedAt: '2026-09-02 14:00:00', finishedAt: '2026-09-02 14:28:00', retryable: false,
    reviewer: '王工', beforeScore: 82, reviewRemark: '安全阀操作顺序存疑，人工复核扣除动作分。', reviewTime: '2026-09-03 09:00:00',
  },
  {
    id: 4, recordNo: 'VR-R-20260901-011', studentNo: 'T004', studentName: '赵磊', taskId: 2,
    taskName: '涂料防腐滚涂仿真实操（涂料防腐工艺课）', projectId: 2, projectName: '涂料防腐滚涂仿真实操',
    source: 1, method: 1, score: 95, qualified: true, gainedHours: 3, costMinutes: 40,
    startedAt: '2026-09-01 15:00:00', finishedAt: '2026-09-01 15:40:00', retryable: false,
  },
  {
    id: 5, recordNo: 'VR-R-20260830-021', studentNo: 'T005', studentName: '孙丽', taskId: 4,
    taskName: '玻璃钢衬里手糊仿真实操（玻璃钢衬里工艺课）', projectId: 6, projectName: '玻璃钢衬里手糊仿真实操',
    source: 2, method: 1, score: 90, qualified: true, gainedHours: 2, costMinutes: 22,
    startedAt: '2026-08-30 10:00:00', finishedAt: '2026-08-30 10:22:00', retryable: false,
  },
];

// ==================== Store 实例 ====================

const projectStore = createDemoStore<TrainingVrApi.VrProject>(PROJECT_SEED);
const versionStore = createDemoStore<TrainingVrApi.VrProjectVersion>(VERSION_SEED);
const tagStore = createDemoStore<TrainingVrApi.VrTag>(TAG_SEED);
const taskStore = createDemoStore<TrainingVrApi.VrTask>(TASK_SEED);
const recordStore = createDemoStore<TrainingVrApi.VrPracticeRecord>(RECORD_SEED);

function matchText(value: any, keyword?: string) {
  if (!keyword) return true;
  return String(value ?? '').toLowerCase().includes(String(keyword).toLowerCase());
}

/** 通用分页响应（与后端接口约定保持一致，便于后续切换） */
function toPage<T>(list: T[], params: PageParam): PageResult<T> {
  const pageNo = params.pageNo ?? 1;
  const pageSize = params.pageSize ?? 10;
  return {
    list: list.slice((pageNo - 1) * pageSize, pageNo * pageSize),
    total: list.length,
  };
}

// ==================== 71 VR 项目资源管理 ====================

export async function getProjectPage(params: PageParam & Record<string, any>) {
  const { keyword, status, techTag } = params;
  const rows = projectStore
    .list()
    .filter((row) => matchText(row.name, keyword) || matchText(row.code, keyword))
    .filter((row) => (status == null ? true : row.status === status))
    .filter((row) => (techTag ? row.techTag === techTag : true));
  return toPage(rows, params);
}

export async function createProject(data: Partial<TrainingVrApi.VrProject>) {
  const row = projectStore.add({
    ...data,
    code: data.code || `VR-${String(Math.floor(Math.random() * 9000) + 1000)}`,
    playCount: 0,
    status: data.status ?? 0,
    createTime: formatNow(),
  });
  return row;
}

export async function updateProject(data: Partial<TrainingVrApi.VrProject>) {
  projectStore.update(data as TrainingVrApi.VrProject);
}

export async function deleteProject(id: number) {
  projectStore.remove(id);
}

export async function transitionProject(id: number, action: 'publish' | 'off' | 'archive') {
  const target = action === 'publish' ? 1 : action === 'off' ? 2 : 3;
  projectStore.patch(id, { status: target, publishTime: action === 'publish' ? formatNow() : undefined });
}

/** 供任务创建选择的已上架项目 */
export async function getPublishProjectOptions() {
  return projectStore.list().filter((row) => row.status === 1);
}

/** 版本历史 */
export async function getProjectVersionList(projectId: number) {
  return versionStore.list().filter((row) => row.projectId === projectId);
}

export async function addProjectVersion(
  projectId: number,
  data: Partial<TrainingVrApi.VrProjectVersion>,
) {
  const list = versionStore.list().filter((row) => row.projectId === projectId);
  const nextVersion = nextVersionText(data.version);
  const version = versionStore.add({
    projectId,
    version: nextVersion,
    size: data.size || '—',
    status: 1,
    releaseNote: data.releaseNote || '',
    releaseTime: formatNow(),
  });
  projectStore.patch(projectId, { currentVersion: nextVersion });
  return { ...version, historyCount: list.length + 1 };
}

function nextVersionText(current?: string) {
  if (!current || !/^v?\d+\.\d+\.\d+$/.test(current)) return 'v1.0.0';
  const [major, minor, patch] = current.replace('v', '').split('.').map(Number);
  return `v${major}.${minor}.${patch + 1}`;
}

// ==================== 71 分类标签维护 ====================

export async function getTagPage(params: PageParam & Record<string, any>) {
  const { keyword, type } = params;
  const rows = tagStore
    .list()
    .filter((row) => matchText(row.name, keyword))
    .filter((row) => (type == null ? true : row.type === type))
    .sort((a, b) => (a.type ?? 0) - (b.type ?? 0) || (a.sort ?? 0) - (b.sort ?? 0));
  return toPage(rows, params);
}

export async function createTag(data: Partial<TrainingVrApi.VrTag>) {
  return tagStore.add({ ...data, usageCount: 0, createTime: formatNow() });
}

export async function updateTag(data: Partial<TrainingVrApi.VrTag>) {
  tagStore.update(data as TrainingVrApi.VrTag);
}

export async function deleteTag(id: number) {
  tagStore.remove(id);
}

// ==================== 72 VR 任务管理 ====================

export async function getTaskPage(params: PageParam & Record<string, any>) {
  const { keyword, status, bindType } = params;
  const rows = taskStore
    .list()
    .filter((row) => matchText(row.taskNo, keyword) || matchText(row.projectName, keyword))
    .filter((row) => (status == null ? true : row.status === status))
    .filter((row) => (bindType == null ? true : row.bindType === bindType));
  return toPage(rows, params);
}

export async function createTask(data: Partial<TrainingVrApi.VrTask>) {
  const project = projectStore.list().find((row) => row.id === data.projectId);
  if (!project) throw new Error('请选择 VR 实操项目');
  if (project.status === 3) throw new Error('该项目已归档，不允许新建任务引用');
  if (project.status === 0 || project.status === 2) {
    throw new Error('项目未上架，请先上架后再创建任务');
  }
  const row = taskStore.add({
    ...data,
    taskNo: `VR-T-${formatCompactNow()}-${String(Math.floor(Math.random() * 900) + 100)}`,
    projectName: project.name,
    projectStatus: project.status,
    status: data.status ?? 0,
    studentCount: data.studentCount ?? 0,
    doneCount: 0,
    creatorName: '当前用户',
    createTime: formatNow(),
  });
  return row;
}

export async function updateTask(data: Partial<TrainingVrApi.VrTask>) {
  taskStore.update(data as TrainingVrApi.VrTask);
}

export async function disableTask(id: number) {
  taskStore.patch(id, { status: 4 });
}

export async function deleteTask(id: number) {
  taskStore.remove(id);
}

// ==================== 73/74 实操记录（训练练习 / 考核） ====================

export async function getRecordPage(params: PageParam & Record<string, any>) {
  const { keyword, source, qualified, method } = params;
  const rows = recordStore
    .list()
    .filter(
      (row) =>
        matchText(row.studentName, keyword) ||
        matchText(row.recordNo, keyword) ||
        matchText(row.projectName, keyword),
    )
    .filter((row) => (source == null ? true : row.source === source))
    .filter((row) => (qualified == null ? true : row.qualified === qualified))
    .filter((row) => (method == null ? true : row.method === method))
    .sort((a, b) => String(b.finishedAt || '').localeCompare(String(a.finishedAt || '')));
  return toPage(rows, params);
}

/** 学员我的任务列表：pending / finished / overdue */
export async function getMyTaskList(scope: 'pending' | 'finished' | 'overdue') {
  const now = new Date();
  const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(
    now.getDate(),
  ).padStart(2, '0')}`;
  return taskStore.list().filter((task) => {
    const finished = (task.doneCount ?? 0) >= (task.requireTimes ?? 1) || task.status === 2;
    const overdue = !finished && task.deadline != null && task.deadline < today;
    if (scope === 'pending') return !finished && !overdue && task.status !== 4;
    if (scope === 'finished') return finished;
    return overdue;
  });
}

/** 提交一次实操（练习/考核），自动判定得分与合格、累加学时 */
export async function submitPractice(data: {
  taskId?: number;
  studentNo: string;
  studentName: string;
  source: 1 | 2;
  score: number;
  costMinutes: number;
}) {
  const task = taskStore.list().find((row) => row.id === data.taskId);
  const project = projectStore.list().find((row) => row.id === task?.projectId);
  const passScore = task?.passScore ?? project?.passScore ?? 80;
  const qualified = data.score >= (passScore ?? 80);
  const durationHours = qualified ? project?.durationHours ?? 0 : 0;
  const record = recordStore.add({
    recordNo: `VR-R-${formatCompactNow()}-${String(Math.floor(Math.random() * 9000) + 1000)}`,
    studentNo: data.studentNo,
    studentName: data.studentName,
    taskId: data.taskId,
    taskName: task ? `${task.projectName}（${task.bindName}）` : undefined,
    projectId: task?.projectId,
    projectName: task?.projectName,
    source: data.source,
    method: 1,
    score: data.score,
    qualified,
    gainedHours: durationHours,
    costMinutes: data.costMinutes,
    startedAt: formatNow(),
    finishedAt: formatNow(),
    retryable: !qualified,
  });
  if (task) {
    const doneCount = (task.doneCount ?? 0) + 1;
    const needCount = task.requireTimes ?? 1;
    const status = doneCount >= needCount ? 2 : task.status;
    taskStore.patch(task.id!, { doneCount, status });
  }
  projectStore.patch(project?.id ?? -1, {
    playCount: (project?.playCount ?? 0) + 1,
  });
  return record;
}

/** 人工复核修改得分（记录复核人、复核时间、修改前后分数） */
export async function reviewRecord(id: number, data: { score: number; remark: string }) {
  const row = recordStore.list().find((item) => item.id === id);
  if (!row) return;
  const before = row.score;
  const passScore = 80;
  const qualified = data.score >= passScore;
  recordStore.patch(id, {
    score: data.score,
    beforeScore: before,
    qualified,
    gainedHours: qualified && !row.gainedHours ? 2 : row.gainedHours,
    method: 2,
    reviewer: '当前用户',
    reviewRemark: data.remark,
    reviewTime: formatNow(),
  });
}

/** 支持重练的记录重新判定（模拟一次重练通过） */
export async function retryRecord(id: number) {
  const row = recordStore.list().find((item) => item.id === id);
  if (!row) return;
  const project = projectStore.list().find((p) => p.id === row.projectId);
  recordStore.patch(id, {
    score: 90,
    beforeScore: row.score,
    qualified: true,
    gainedHours: project?.durationHours ?? 2,
    method: 1,
    retryable: false,
    reviewRemark: '学员重练重考通过，成绩刷新。',
    reviewTime: formatNow(),
  });
}

// ==================== 75 数据看板 ====================

export interface DashboardStat {
  taskTotal: number;
  taskFinished: number;
  studentTotal: number;
  recordTotal: number;
  qualifiedRate: number;
  totalHours: number;
  pendingStudentList: { studentName: string; taskName: string; deadline: string }[];
  scoreBuckets: { label: string; count: number }[];
}

export async function getDashboardStat(): Promise<DashboardStat> {
  const tasks = taskStore.list().filter((row) => row.status !== 4);
  const records = recordStore.list();
  const qualified = records.filter((row) => row.qualified).length;
  const buckets = [
    { label: '<60', count: records.filter((r) => (r.score ?? 0) < 60).length },
    { label: '60-69', count: records.filter((r) => (r.score ?? 0) >= 60 && (r.score ?? 0) < 70).length },
    { label: '70-79', count: records.filter((r) => (r.score ?? 0) >= 70 && (r.score ?? 0) < 80).length },
    { label: '80-89', count: records.filter((r) => (r.score ?? 0) >= 80 && (r.score ?? 0) < 90).length },
    { label: '90-100', count: records.filter((r) => (r.score ?? 0) >= 90).length },
  ];
  const pendingStudentList: DashboardStat['pendingStudentList'] = [];
  for (const task of tasks) {
    if ((task.status ?? 0) === 1 || (task.status ?? 0) === 0) {
      for (let i = (task.doneCount ?? 0) + 1; i <= (task.studentCount ?? 0); i++) {
        if (pendingStudentList.length >= 10) break;
        pendingStudentList.push({
          studentName: `学员-${String(i).padStart(3, '0')}`,
          taskName: task.projectName ?? '',
          deadline: task.deadline ?? '-',
        });
      }
    }
  }
  const finishedTasks = tasks.filter((row) => row.status === 2).length;
  return {
    taskTotal: tasks.length,
    taskFinished: finishedTasks,
    studentTotal: tasks.reduce((sum, row) => sum + (row.studentCount ?? 0), 0),
    recordTotal: records.length,
    qualifiedRate: records.length ? Math.round((qualified / records.length) * 100) : 0,
    totalHours: records.reduce((sum, row) => sum + (row.gainedHours ?? 0), 0),
    pendingStudentList,
    scoreBuckets: buckets,
  };
}

/** 学员维度汇总（学员实操完成情况） */
export async function getStudentStatList() {
  const groups = new Map<string, { total: number; finished: number; qualifiedCount: number; hours: number }>();
  for (const task of taskStore.list()) {
    const count = task.studentCount ?? 0;
    const done = task.doneCount ?? 0;
    for (let i = 1; i <= count; i++) {
      const name = `学员-${String(i).padStart(3, '0')}`;
      const current = groups.get(name) ?? { total: 0, finished: 0, qualifiedCount: 0, hours: 0 };
      current.total += 1;
      const finishedList = recordStore.list().filter((r) => r.studentName === name);
      if (done > 0 && finishedList.length > 0) {
        current.finished += finishedList.some((r) => r.taskId === task.id) ? 1 : 0;
        current.qualifiedCount += finishedList.filter((r) => r.taskId === task.id && r.qualified).length;
        current.hours += finishedList
          .filter((r) => r.taskId === task.id && r.qualified)
          .reduce((sum, r) => sum + (r.gainedHours ?? 0), 0);
      }
      groups.set(name, current);
    }
  }
  const rows = [...groups.entries()].map(([studentName, value]) => ({
    studentNo: 'T' + String(Math.floor(Math.random() * 9000) + 1000),
    studentName,
    taskTotal: value.total,
    finishedCount: value.finished,
    qualifiedCount: value.qualifiedCount,
    totalHours: value.hours,
    rate: value.total ? Math.round((value.finished / value.total) * 100) : 0,
  }));
  return rows;
}

function pad2(value: number) {
  return String(value).padStart(2, '0');
}

function formatNow() {
  const d = new Date();
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())} ${pad2(d.getHours())}:${pad2(
    d.getMinutes(),
  )}:${pad2(d.getSeconds())}`;
}

function formatCompactNow() {
  const d = new Date();
  return `${d.getFullYear()}${pad2(d.getMonth() + 1)}${pad2(d.getDate())}`;
}
