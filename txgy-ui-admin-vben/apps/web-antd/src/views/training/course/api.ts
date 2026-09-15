import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace TrainingCourseApi {
  /** 课程 */
  export interface Course {
    id?: number;
    rootId?: number;
    version?: number;
    isCurrent?: boolean;
    categoryId?: number;
    code?: string;
    title?: string;
    subtitle?: string;
    summary?: string;
    coverUrl?: string;
    level?: number;
    /** 成长等级标记：APPRENTICE/JUNIOR/INTERMEDIATE/SENIOR/TECHNICIAN/SENIOR_TECHNICIAN（对应 training_growth_level 六级） */
    courseLevel?: string;
    credit?: number;
    durationMinutes?: number;
    industry?: string;
    /** 适用岗位/工种（JSON 数组） */
    applicablePosts?: string;
    /** 学习域/课程组编码（按适用岗位工种自动匹配，逗号分隔；* 表示全域） */
    learnDomainCodes?: string;
    /** 资源归属标识 */
    ownerFlag?: string;
    bizTags?: string;
    isRequired?: boolean;
    /** 是否复合课程 */
    isComposite?: boolean;
    /** 复合课程子模块标签（JSON 数组） */
    compositeTags?: string;
    /** 结课考试试卷 ID（模块三「考试与认证」） */
    examId?: number;
    /** 结课考试开始时间 */
    examStartTime?: string;
    /** 结课考试结束时间 */
    examEndTime?: string;
    lecturer?: string;
    lecturerBrief?: string;
    mediaType?: number;
    mediaCount?: number;
    commentCount?: number;
    auditCount?: number;
    status?: number;
    rejectReason?: string;
    reviewerId?: number;
    reviewedAt?: string;
    sort?: number;
    remark?: string;
    submitterId?: number;
    submittedAt?: string;
    createTime?: string;
  }

  /** 课程媒资 */
  export interface CourseMedia {
    id?: number;
    courseId?: number;
    mediaType?: number;
    name?: string;
    url?: string;
    /** 外部资源引用 ID（资源中心媒资 ID / VR 项目编号） */
    mediaRefId?: string;
    sizeBytes?: number;
    durationSeconds?: number;
    sort?: number;
  }

  /** 课程评论 */
  export interface CourseComment {
    id?: number;
    courseId?: number;
    parentId?: number;
    content?: string;
    userId?: number;
    userName?: string;
    /** 状态：1 正常 2 已删除（治理软删留痕） */
    status?: number;
    deletedBy?: number;
    deletedAt?: string;
    deleteReason?: string;
    createTime?: string;
  }

  /** 课程分类（三级树：1 域 / 2 组 / 3 课） */
  export interface CourseCategory {
    id?: number;
    parentId?: number;
    level?: number;
    name?: string;
    code?: string;
    bizCategory?: string;
    stdHours?: number;
    isRequired?: boolean;
    status?: string;
    sort?: number;
    remark?: string;
    children?: CourseCategory[];
  }

  /** 成长等级（六级人才成长体系字典 training_growth_level） */
  export interface GrowthLevel {
    id?: number;
    /** 等级编码：APPRENTICE/JUNIOR/INTERMEDIATE/SENIOR/TECHNICIAN/SENIOR_TECHNICIAN */
    code?: string;
    /** 等级序号：1学徒 2初级工 3中级工 4高级工 5技师 6高级技师 */
    levelNo?: number;
    name?: string;
    /** 定位 */
    positionDesc?: string;
    /** 独立作业权限 */
    workPermission?: string;
    /** 带徒上限（人） */
    maxApprentice?: number;
    /** 是否需人工评审 */
    needReview?: boolean;
    /** 参考对标 */
    reference?: string;
    sort?: number;
    remark?: string;
    createTime?: string;
  }
}

/** 课程分页列表 */
export function getCoursePage(params: PageParam & Record<string, any>) {
  return requestClient.get<PageResult<TrainingCourseApi.Course>>(
    '/training/course/page',
    { params },
  );
}

/** 课程详情 */
export function getCourse(id: number) {
  return requestClient.get<TrainingCourseApi.Course>(
    `/training/course/get?id=${id}`,
  );
}

/** 新增课程 */
export function createCourse(data: Partial<TrainingCourseApi.Course>) {
  return requestClient.post('/training/course/create', data);
}

/** 修改课程 */
export function updateCourse(data: Partial<TrainingCourseApi.Course>) {
  return requestClient.put('/training/course/update', data);
}

/** 删除课程 */
export function deleteCourse(id: number) {
  return requestClient.delete(`/training/course/delete?id=${id}`);
}

/** 提交审核 */
export function submitCourse(courseId: number) {
  return requestClient.post(`/training/course-audit/submit?courseId=${courseId}`);
}

/** 撤审 */
export function withdrawCourse(courseId: number) {
  return requestClient.post(
    `/training/course-audit/withdraw?courseId=${courseId}`,
  );
}

/** 审核通过 */
export function approveCourse(courseId: number) {
  return requestClient.post('/training/course-audit/approve', { courseId });
}

/** 驳回 */
export function rejectCourse(courseId: number, reason: string) {
  return requestClient.post('/training/course-audit/reject', {
    courseId,
    reason,
  });
}

/** 上架 */
export function onlineCourse(courseId: number) {
  return requestClient.post(`/training/course-audit/online?courseId=${courseId}`);
}

/** 下架 */
export function offlineCourse(courseId: number) {
  return requestClient.post(
    `/training/course-audit/offline?courseId=${courseId}`,
  );
}

/** 发布新版本 */
export function publishCourseVersion(courseId: number) {
  return requestClient.post(
    `/training/course/publish-version?courseId=${courseId}`,
  );
}

/** 版本历史 */
export function getCourseVersionList(courseId: number) {
  return requestClient.get<TrainingCourseApi.Course[]>(
    `/training/course/version-list?courseId=${courseId}`,
  );
}

/** 课程媒资列表 */
export function getCourseMediaList(courseId: number) {
  return requestClient.get<TrainingCourseApi.CourseMedia[]>(
    `/training/course-media/list?courseId=${courseId}`,
  );
}

/** 绑定媒资 */
export function createCourseMedia(data: Partial<TrainingCourseApi.CourseMedia>) {
  return requestClient.post('/training/course-media/create', data);
}

/** 删除媒资 */
export function deleteCourseMedia(id: number) {
  return requestClient.delete(`/training/course-media/delete?id=${id}`);
}

/** 评论分页 */
export function getCourseCommentPage(params: PageParam & Record<string, any>) {
  return requestClient.get<PageResult<TrainingCourseApi.CourseComment>>(
    '/training/course-comment/page',
    { params },
  );
}

/** 评论治理删除（软删留痕，可选原因） */
export function deleteCourseComment(id: number, reason?: string) {
  const query = reason
    ? `id=${id}&reason=${encodeURIComponent(reason)}`
    : `id=${id}`;
  return requestClient.delete(`/training/course-comment/delete?${query}`);
}

/** 课程媒资排序（按传入顺序重排） */
export function sortCourseMedia(courseId: number, ids: number[]) {
  return requestClient.put(`/training/course-media/sort?courseId=${courseId}`, ids);
}

/** 课程分类全量列表（扁平，下拉） */
export function getCourseCategoryList() {
  return requestClient.get<TrainingCourseApi.CourseCategory[]>(
    '/training/course-category/list',
  );
}

/** 课程分类树（域-组-课 三级） */
export function getCourseCategoryTree() {
  return requestClient.get<TrainingCourseApi.CourseCategory[]>(
    '/training/course-category/list-tree',
  );
}

/** 新增课程分类 */
export function createCourseCategory(data: Partial<TrainingCourseApi.CourseCategory>) {
  return requestClient.post('/training/course-category/create', data);
}

/** 配置课程业务属性（成长等级/岗位/必修/复合/结课考试等） */
export function updateCourseBusinessAttributes(
  id: number,
  data: Partial<TrainingCourseApi.Course>,
) {
  return requestClient.put(`/training/course/business-attributes?id=${id}`, data);
}

/** 结课考试试卷下拉（对接模块三「考试与认证」试卷中心） */
export function getExamOptions() {
  return requestClient.get<CourseExamOption[]>(
    '/training/course/exam-options',
  );
}

/** 课程结课考试计划（模块三「考试与认证」展示课程考试） */
export function getCourseExamPlanList() {
  return requestClient.get<CourseExamPlan[]>(
    '/training/course/exam-plan-list',
  );
}

/** 结课考试试卷选项 */
export interface CourseExamOption {
  id?: number;
  name?: string;
  /** 试卷状态：draft 草稿 / published 已发布 */
  status?: string;
  examType?: string;
  totalScore?: number;
  passScore?: number;
  durationMin?: number;
  questionCount?: number;
  courseName?: string;
  courseId?: number;
}

/** 课程结课考试计划 */
export interface CourseExamPlan {
  courseId?: number;
  courseName?: string;
  categoryId?: number;
  examId?: number;
  examStartTime?: string;
  examEndTime?: string;
  status?: number;
}

/** 修改课程分类 */
export function updateCourseCategory(
  data: Partial<TrainingCourseApi.CourseCategory>,
) {
  return requestClient.put('/training/course-category/update', data);
}

/** 删除课程分类（已被课程引用时后端禁止删除） */
export function deleteCourseCategory(id: number) {
  return requestClient.delete(`/training/course-category/delete?id=${id}`);
}

/** 课程分类分页 */
export function getCourseCategoryPage(
  params: PageParam & Record<string, any>,
) {
  return requestClient.get<PageResult<TrainingCourseApi.CourseCategory>>(
    '/training/course-category/page',
    { params },
  );
}

/** 待审核课程队列 */
export function getAuditQueue(params: PageParam & Record<string, any>) {
  return requestClient.get<PageResult<TrainingCourseApi.Course>>(
    '/training/course-audit/queue',
    { params },
  );
}

/** 课程审核日志（永久留痕，不可删除） */
export function getAuditLogPage(params: PageParam & Record<string, any>) {
  return requestClient.get<PageResult<CourseAuditLog>>(
    '/training/course-audit/logs/page',
    { params },
  );
}

/** 修改课程媒资 */
export function updateCourseMedia(
  data: Partial<TrainingCourseApi.CourseMedia>,
) {
  return requestClient.put('/training/course-media/update', data);
}

/** 课程审核日志 */
export interface CourseAuditLog {
  id?: number;
  courseId?: number;
  /** 动作：SUBMIT / WITHDRAW / APPROVE / REJECT / ONLINE / OFFLINE / PUBLISH */
  action?: string;
  fromStatus?: number;
  toStatus?: number;
  operatorId?: number;
  operatorName?: string;
  /** 备注 / 驳回原因 */
  reason?: string;
  createTime?: string;
}

/** 成长等级分页 */
export function getGrowthLevelPage(params: PageParam & Record<string, any>) {
  return requestClient.get<PageResult<TrainingCourseApi.GrowthLevel>>(
    '/training/growth-level/page',
    { params },
  );
}

/** 成长等级全量列表（下拉：课程成长等级标记等引用） */
export function getGrowthLevelList() {
  return requestClient.get<TrainingCourseApi.GrowthLevel[]>(
    '/training/growth-level/list',
  );
}

/** 新增成长等级 */
export function createGrowthLevel(data: Partial<TrainingCourseApi.GrowthLevel>) {
  return requestClient.post('/training/growth-level/create', data);
}

/** 修改成长等级 */
export function updateGrowthLevel(data: Partial<TrainingCourseApi.GrowthLevel>) {
  return requestClient.put('/training/growth-level/update', data);
}

/** 删除成长等级（已被课程标记引用时后端禁止删除） */
export function deleteGrowthLevel(id: number) {
  return requestClient.delete(`/training/growth-level/delete?id=${id}`);
}

/** 成长等级详情 */
export function getGrowthLevel(id: number) {
  return requestClient.get<TrainingCourseApi.GrowthLevel>(
    `/training/growth-level/get?id=${id}`,
  );
}
