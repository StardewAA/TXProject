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
    /** 成长等级：APPRENTICE/JUNIOR/MIDDLE/SENIOR（四级成长体系） */
    courseLevel?: string;
    credit?: number;
    durationMinutes?: number;
    industry?: string;
    /** 适用岗位/工种（JSON 数组） */
    applicablePosts?: string;
    /** 资源归属标识 */
    ownerFlag?: string;
    bizTags?: string;
    isRequired?: boolean;
    /** 是否复合课程 */
    isComposite?: boolean;
    /** 复合课程子模块标签（JSON 数组） */
    compositeTags?: string;
    examId?: number;
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

  /** 课程分类 */
  export interface CourseCategory {
    id?: number;
    name?: string;
    code?: string;
    sort?: number;
    remark?: string;
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

/** 课程分类全量列表（下拉） */
export function getCourseCategoryList() {
  return requestClient.get<TrainingCourseApi.CourseCategory[]>(
    '/training/course-category/list',
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

/** 结课考试试卷下拉（占位） */
export function getExamOptions() {
  return requestClient.get<any[]>('/training/course/exam-options');
}
