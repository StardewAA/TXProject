import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace TrainingLearningApi {
  /** 学习任务 */
  export interface LearnerTask {
    id?: number;
    userId?: number;
    courseId?: number;
    sourceType?: string;
    status?: number;
    progressPercent?: number;
    creditTotal?: number;
    creditEarned?: number;
    startedAt?: string;
    completedAt?: string;
    remark?: string;
    createTime?: string;
    /** 课程信息（由任务接口补齐） */
    courseTitle?: string;
    coverUrl?: string;
    lecturer?: string;
    courseCredit?: number;
    courseMediaType?: number;
    courseMediaCount?: number;
    courseDurationMinutes?: number;
  }

  /** 指派候选 */
  export interface AssignCandidate {
    courses?: CourseItem[];
    users?: UserItem[];
    userCount?: number;
  }

  export interface CourseItem {
    id?: number;
    title?: string;
    credit?: number;
    durationMinutes?: number;
    coverUrl?: string;
    lecturer?: string;
  }

  export interface UserItem {
    id?: number;
    nickname?: string;
  }

  /** 直播房间 */
  export interface LiveRoom {
    id?: number;
    title?: string;
    coverUrl?: string;
    lecturerName?: string;
    streamUrl?: string;
    status?: number;
    planStartAt?: string;
    planEndAt?: string;
    replayVideoUrl?: string;
    creditHours?: number;
    sort?: number;
    remark?: string;
    viewerCount?: number;
    createTime?: string;
  }

  /** 学习统计（按课程） */
  export interface LearningStat {
    courseId?: number;
    courseTitle?: string;
    coverUrl?: string;
    taskTotal?: number;
    todoCount?: number;
    learningCount?: number;
    completedCount?: number;
    avgProgressPercent?: number;
    creditTotal?: number;
    creditEarned?: number;
    completedRate?: number;
  }

  /** 课程学习轨迹 */
  export interface CourseTrack {
    userId?: number;
    nickname?: string;
    courseId?: number;
    courseTitle?: string;
    creditTotal?: number;
    totalValidSeconds?: number;
    creditEarned?: number;
    taskStatus?: number;
    mediaTracks?: MediaTrack[];
  }

  export interface MediaTrack {
    mediaId?: number;
    mediaType?: string;
    mediaTypeName?: string;
    name?: string;
    sessionCount?: number;
    totalValidSeconds?: number;
    completed?: boolean;
    ratioPercent?: number;
    creditEarned?: number;
    sessions?: SessionTrack[];
  }

  export interface SessionTrack {
    sessionId?: number;
    startAt?: string;
    endAt?: string;
    validSeconds?: number;
    endReason?: string;
    creditEarned?: number;
  }
}

/** 学习任务分页 */
export function getLearningTaskPage(params: PageParam & Record<string, any>) {
  return requestClient.get<PageResult<TrainingLearningApi.LearnerTask>>(
    '/training/learning/task/page',
    { params },
  );
}

/** 课程指派 */
export function assignCourse(data: {
  courseId?: number;
  remark?: string;
  userIds?: number[];
}) {
  return requestClient.post<number>('/training/learning/task/assign', data);
}

/** 指派候选（已上架课程 + 历史学员） */
export function getAssignCandidates() {
  return requestClient.get<TrainingLearningApi.AssignCandidate>(
    '/training/learning/task/assign-candidates',
  );
}

/** 直播房间分页 */
export function getLiveRoomPage(params: PageParam & Record<string, any>) {
  return requestClient.get<PageResult<TrainingLearningApi.LiveRoom>>(
    '/training/learning/live-room/page',
    { params },
  );
}

/** 直播房间详情 */
export function getLiveRoom(id: number) {
  return requestClient.get<TrainingLearningApi.LiveRoom>(
    `/training/learning/live-room/get?id=${id}`,
  );
}

/** 新增直播房间 */
export function createLiveRoom(data: Partial<TrainingLearningApi.LiveRoom>) {
  return requestClient.post<number>('/training/learning/live-room/create', data);
}

/** 修改直播房间 */
export function updateLiveRoom(data: Partial<TrainingLearningApi.LiveRoom>) {
  return requestClient.put<boolean>('/training/learning/live-room/update', data);
}

/** 删除直播房间 */
export function deleteLiveRoom(id: number) {
  return requestClient.delete<boolean>(
    `/training/learning/live-room/delete?id=${id}`,
  );
}

/** 学习数据统计 */
export function getLearningStats() {
  return requestClient.get<TrainingLearningApi.LearningStat[]>(
    '/training/learning/track/stats',
  );
}

/** 单课程学习轨迹 */
export function getCourseTrack(userId: number, courseId: number) {
  return requestClient.get<TrainingLearningApi.CourseTrack>(
    `/training/learning/track/course?userId=${userId}&courseId=${courseId}`,
  );
}

/** 学习全局配置 */
export function getLearningConfig() {
  return requestClient.get<Record<string, string>>(
    '/training/learning/config/list',
  );
}

/** 更新学习全局配置 */
export function updateLearningConfig(data: { key: string; value: string }) {
  return requestClient.put<boolean>('/training/learning/config/update', data);
}
