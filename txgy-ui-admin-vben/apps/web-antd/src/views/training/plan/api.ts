import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace TrainingPlanApi {
  /** 培训班 */
  export interface Class {
    id?: number;
    code?: string;
    name?: string;
    type?: number;
    projectId?: string;
    projectName?: string;
    targetCondition?: string;
    targetPosts?: string;
    startTime?: string;
    deadline?: string;
    courseCount?: number;
    studentCount?: number;
    status?: number;
    remark?: string;
    createTime?: string;
    /** 详情返回：绑定课程 ID 列表 */
    courseIds?: number[];
  }

  /** 培训班课程快照 */
  export interface ClassCourse {
    courseId?: number;
    courseCode?: string;
    courseTitle?: string;
    durationMinutes?: number;
  }

  /** 在班学员 */
  export interface ClassStudent {
    id?: number;
    classId?: number;
    userId?: number;
    userName?: string;
    deptName?: string;
    source?: number;
    joinTime?: string;
    status?: number;
  }

  /** 学员学习任务 */
  export interface StudentTask {
    id?: number;
    classId?: number;
    userId?: number;
    courseId?: number;
    courseTitle?: string;
    studyStatus?: number;
    courseStatus?: number;
    examStatus?: number;
    certStatus?: number;
    status?: number;
    finishTime?: string;
  }

  /** 班级进度（按学员聚合） */
  export interface ClassProgress {
    userId?: number;
    userName?: string;
    deptName?: string;
    totalTasks?: number;
    finishedTasks?: number;
    studyFinished?: boolean;
    courseFinished?: boolean;
    examStatus?: number;
    certFinished?: boolean;
    overallStatus?: number;
    progressPercent?: number;
  }

  /** 督促记录 */
  export interface RemindRecord {
    id?: number;
    classId?: number;
    className?: string;
    userId?: number;
    userName?: string;
    messageId?: number;
    content?: string;
    status?: number;
    errorMsg?: string;
    createTime?: string;
  }

  /** 上岗核查明细 */
  export interface QualificationItem {
    classId?: number;
    className?: string;
    type?: number;
    typeName?: string;
    classStatus?: number;
    deadline?: string;
    totalTasks?: number;
    finishedTasks?: number;
    qualified?: boolean;
  }

  export interface QualificationResult {
    userId?: number;
    userName?: string;
    projectId?: string;
    qualified?: boolean;
    items?: QualificationItem[];
    reason?: string;
  }

  /** Excel 批量导入学员结果 */
  export interface ImportStudentResult {
    successCount?: number;
    /** 自动创建的系统账号数（仅勾选自动建号时可能大于 0） */
    createdUserCount?: number;
    failRows?: {
      mobile?: string;
      reason?: string;
      rowNumber?: number;
    }[];
  }

}

/** 培训班分页 */
export function getClassPage(params: PageParam & Record<string, any>) {
  return requestClient.get<PageResult<TrainingPlanApi.Class>>(
    '/training/plan/class/page',
    { params },
  );
}

/** 培训班详情 */
export function getClass(id: number) {
  return requestClient.get<TrainingPlanApi.Class>(
    `/training/plan/class/get?id=${id}`,
  );
}

/** 新增培训班 */
export function createClass(data: Partial<TrainingPlanApi.Class>) {
  return requestClient.post('/training/plan/class/create', data);
}

/** 修改培训班 */
export function updateClass(data: Partial<TrainingPlanApi.Class>) {
  return requestClient.put('/training/plan/class/update', data);
}

/** 删除培训班 */
export function deleteClass(id: number) {
  return requestClient.delete(`/training/plan/class/delete?id=${id}`);
}

/** 开班 */
export function startClass(id: number) {
  return requestClient.post(`/training/plan/class/start?id=${id}`);
}

/** 结业 */
export function finishClass(id: number) {
  return requestClient.post(`/training/plan/class/finish?id=${id}`);
}

/** 取消培训班 */
export function cancelClass(id: number) {
  return requestClient.post(`/training/plan/class/cancel?id=${id}`);
}

/** 培训班课程清单 */
export function getClassCourseList(classId: number) {
  return requestClient.get<TrainingPlanApi.ClassCourse[]>(
    `/training/plan/class/course-list?classId=${classId}`,
  );
}

/** 添加学员（自动生成学习任务） */
export function addClassStudents(classId: number, userIds: number[]) {
  return requestClient.post('/training/plan/class/student/add', {
    classId,
    userIds,
  });
}

/** 移出学员 */
export function removeClassStudent(classId: number, userId: number) {
  return requestClient.delete(
    `/training/plan/class/student/remove?classId=${classId}&userId=${userId}`,
  );
}

/** 在班学员分页 */
export function getClassStudentPage(params: PageParam & Record<string, any>) {
  return requestClient.get<PageResult<TrainingPlanApi.ClassStudent>>(
    '/training/plan/class/student/page',
    { params },
  );
}

/** 学员任务列表（进度登记） */
export function getStudentTaskList(classId: number, userId: number) {
  return requestClient.get<TrainingPlanApi.StudentTask[]>(
    `/training/plan/class/student/task-list?classId=${classId}&userId=${userId}`,
  );
}

/** 班级进度分页 */
export function getProgressPage(params: PageParam & Record<string, any>) {
  return requestClient.get<PageResult<TrainingPlanApi.ClassProgress>>(
    '/training/plan/class/progress/page',
    { params },
  );
}

/** 后端分页每页条数上限为 200（超出会报「每页条数最大值为 200」），故按上限循环拉取全量数据 */
const MAX_PAGE_SIZE = 200;

async function fetchAllPages<T>(
  fetcher: (params: PageParam & Record<string, any>) => Promise<PageResult<T>>,
  params: Record<string, any>,
): Promise<T[]> {
  const list: T[] = [];
  for (let pageNo = 1; ; pageNo++) {
    const data = await fetcher({ ...params, pageNo, pageSize: MAX_PAGE_SIZE });
    const rows = data?.list ?? [];
    list.push(...rows);
    if (rows.length < MAX_PAGE_SIZE) {
      break;
    }
  }
  return list;
}

/** 在班学员全量（用于去重、下拉等需要完整数据的场景） */
export function getAllClassStudents(classId: number) {
  return fetchAllPages<TrainingPlanApi.ClassStudent>(getClassStudentPage, {
    classId,
  });
}

/** 班级进度全量（用于进度看板、未完成统计等需要完整数据的场景） */
export function getAllProgress(classId: number) {
  return fetchAllPages<TrainingPlanApi.ClassProgress>(getProgressPage, {
    classId,
  });
}

/** 登记学习进度 */
export function registerProgress(data: Record<string, any>) {
  return requestClient.post('/training/plan/class/progress/register', data);
}

/** 一键督促（批量站内信） */
export function sendRemind(classId: number, userIds?: number[]) {
  return requestClient.post('/training/plan/class/remind/send', {
    classId,
    userIds,
  });
}

/** 督促记录分页 */
export function getRemindRecordPage(params: PageParam & Record<string, any>) {
  return requestClient.get<PageResult<TrainingPlanApi.RemindRecord>>(
    '/training/plan/class/remind/record-page',
    { params },
  );
}

/** 上岗资格核查 */
export function checkQualification(userId: number, projectId: string) {
  return requestClient.post<TrainingPlanApi.QualificationResult>(
    '/training/plan/class/qualification/check',
    { userId, projectId },
  );
}

/** 下载学员导入模板 */
export function getStudentImportTemplate() {
  return requestClient.download(
    '/training/plan/class/student/import-template',
  );
}

/**
 * Excel 批量导入学员（按手机号匹配系统账号）
 *
 * @param autoCreateUser 未匹配到系统账号时，是否按「登录名=手机号、昵称=姓名」自动创建学员账号
 */
export function importClassStudents(
  classId: number,
  file: File,
  autoCreateUser = false,
) {
  return requestClient.upload<TrainingPlanApi.ImportStudentResult>(
    `/training/plan/class/student/import?classId=${classId}&autoCreateUser=${autoCreateUser}`,
    { file },
  );
}

/** 培训班全量（班级下拉使用，由前端按状态筛选可入班班级） */
export function getAllClasses() {
  return fetchAllPages<TrainingPlanApi.Class>(getClassPage, {});
}
