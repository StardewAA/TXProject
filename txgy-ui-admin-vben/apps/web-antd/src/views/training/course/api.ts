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
    credit?: number;
    durationMinutes?: number;
    industry?: string;
    bizTags?: string;
    isRequired?: boolean;
    examId?: number;
    lecturer?: string;
    lecturerBrief?: string;
    mediaType?: number;
    mediaCount?: number;
    commentCount?: number;
    auditCount?: number;
    status?: number;
    rejectReason?: string;
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
