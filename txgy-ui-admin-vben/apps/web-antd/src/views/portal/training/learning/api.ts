import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace TrainingLearnerApi {
  /** 我的课程卡片 */
  export interface CourseCard {
    courseId?: number;
    title?: string;
    coverUrl?: string;
    lecturer?: string;
    credit?: number;
    durationMinutes?: number;
    status?: number;
    progressPercent?: number;
    creditTotal?: number;
    creditEarned?: number;
    doneCount?: number;
    totalCount?: number;
    startedAt?: string;
    completedAt?: string;
  }

  /** 我的课程分组 */
  export interface MyCoursesGroup {
    todoCourses?: CourseCard[];
    learningCourses?: CourseCard[];
    completedCourses?: CourseCard[];
    totalCount?: number;
    completedCount?: number;
    learningCount?: number;
    todoCount?: number;
    creditTotal?: number;
    creditEarned?: number;
  }

  /** 课程学习详情-媒资项 */
  export interface MediaItem {
    mediaId?: number;
    mediaType?: string;
    mediaTypeName?: string;
    name?: string;
    url?: string;
    durationSeconds?: number;
    sort?: number;
    completed?: boolean;
    ratioPercent?: number;
    validSeconds?: number;
    docCoveredPages?: number;
    docTotalPages?: number;
  }

  /** 课程学习详情 */
  export interface CourseDetail {
    courseId?: number;
    title?: string;
    coverUrl?: string;
    lecturer?: string;
    credit?: number;
    durationMinutes?: number;
    taskStatus?: number;
    progressPercent?: number;
    creditTotal?: number;
    creditEarned?: number;
    doneCount?: number;
    totalCount?: number;
    medias?: MediaItem[];
  }

  /** 会话配置 */
  export interface SessionConfig {
    heartbeat_interval_seconds?: number;
    max_delta_seconds?: number;
    session_timeout_seconds?: number;
    verify_interval_min?: number;
    min_valid_seconds_checkin?: number;
  }

  /** 开启会话返回 */
  export interface SessionStart {
    sessionId?: number;
    status?: string;
    mediaType?: string;
    courseId?: number;
    mediaId?: number;
    taskId?: number;
    heartbeatIntervalSeconds?: number;
    validSeconds?: number;
  }

  /** 心跳返回 */
  export interface HeartbeatResult {
    sessionId?: number;
    status?: string;
    reason?: string;
    validSeconds?: number;
    needVerify?: boolean;
    challengeQuestion?: string;
    warnInvalid?: boolean;
    canCheckin?: boolean;
    docCoveredPages?: number;
    docTotalPages?: number;
  }

  /** 结束/打卡返回 */
  export interface SessionEnd {
    sessionId?: number;
    status?: string;
    reason?: string;
    validSeconds?: number;
    taskId?: number;
    courseId?: number;
    mediaCompleted?: boolean;
    taskCompleted?: boolean;
    progressPercent?: number;
    creditTotal?: number;
    creditEarned?: number;
    doneCount?: number;
    totalCount?: number;
    checkedIn?: boolean;
  }

  /** 直播房间 */
  export interface LiveRoom {
    id?: number;
    title?: string;
    coverUrl?: string;
    lecturerName?: string;
    status?: number;
    statusText?: string;
    planStartAt?: string;
    planEndAt?: string;
    streamUrl?: string;
    replayVideoUrl?: string;
    creditHours?: number;
    viewerCount?: number;
    canJoin?: boolean;
    canReplay?: boolean;
    remark?: string;
  }

  export interface LiveRoomGroup {
    upcoming?: LiveRoom[];
    live?: LiveRoom[];
    ended?: LiveRoom[];
  }

  /** 弹幕 */
  export interface Danmaku {
    id?: number;
    roomId?: number;
    userId?: number;
    userName?: string;
    content?: string;
    createAt?: string;
  }

  /** 学习轨迹行 */
  export interface TrackRow {
    sessionId?: number;
    courseId?: number;
    courseTitle?: string;
    mediaId?: number;
    mediaType?: string;
    mediaTypeName?: string;
    mediaName?: string;
    startAt?: string;
    endAt?: string;
    validSeconds?: number;
    endReason?: string;
    creditEarned?: number;
    completed?: boolean;
  }

  /** 学习轨迹汇总 */
  export interface TrackSummary {
    totalValidSeconds?: number;
    totalCreditEarned?: number;
    creditTotal?: number;
    taskTotalCount?: number;
    taskCompletedCount?: number;
    checkinCount?: number;
    courseRows?: {
      courseId?: number;
      courseTitle?: string;
      taskStatus?: number;
      progressPercent?: number;
      creditTotal?: number;
      creditEarned?: number;
    }[];
    typeRows?: {
      mediaType?: string;
      mediaTypeName?: string;
      sessionCount?: number;
      totalValidSeconds?: number;
    }[];
  }

  /** 打卡记录 */
  export interface CheckinRecord {
    id?: number;
    sessionId?: number;
    courseId?: number;
    mediaId?: number;
    mediaType?: string;
    validSecondsBefore?: number;
    checkinAt?: string;
  }

  /** 文档学习状态 */
  export interface DocStatus {
    mediaId?: number;
    completed?: boolean;
    validSeconds?: number;
    coveredPages?: number;
    totalPages?: number;
    progressPercent?: number;
  }
}

/** 我的课程（分组） */
export function getMyCourses() {
  return requestClient.get<TrainingLearnerApi.MyCoursesGroup>(
    '/training/portal/learner/course/my-list',
  );
}

/** 课程学习详情 */
export function getCourseDetail(id: number) {
  return requestClient.get<TrainingLearnerApi.CourseDetail>(
    `/training/portal/learner/course/detail?id=${id}`,
  );
}

/** 自主选学 */
export function enrollSelf(id: number) {
  return requestClient.post<boolean>(
    `/training/portal/learner/course/enroll-self?id=${id}`,
  );
}

/** 会话参数 */
export function getSessionConfig() {
  return requestClient.get<TrainingLearnerApi.SessionConfig>(
    '/training/portal/learner/session/config',
  );
}

/** 开启学习会话 */
export function startSession(data: {
  courseId?: number;
  mediaId: number;
  mediaType: string;
}) {
  return requestClient.post<TrainingLearnerApi.SessionStart>(
    '/training/portal/learner/session/start',
    data,
  );
}

/** 学习心跳 */
export function heartbeat(params: {
  sessionId: number;
  pageNo?: number;
  totalPages?: number;
}) {
  return requestClient.post<TrainingLearnerApi.HeartbeatResult>(
    '/training/portal/learner/session/heartbeat',
    undefined,
    { params },
  );
}

/** 提交人机验证 */
export function verifySession(data: { sessionId: number; answer: string }) {
  return requestClient.post<boolean>(
    '/training/portal/learner/session/verify',
    data,
  );
}

/** 结束学习会话 */
export function endSession(sessionId: number) {
  return requestClient.post<TrainingLearnerApi.SessionEnd>(
    `/training/portal/learner/session/end?sessionId=${sessionId}`,
  );
}

/** 学习打卡（打卡即结算本次会话） */
export function checkinSession(sessionId: number) {
  return requestClient.post<TrainingLearnerApi.SessionEnd>(
    `/training/portal/learner/session/checkin?sessionId=${sessionId}`,
  );
}

/** 文档翻页/停留上报 */
export function reportDocPage(data: {
  sessionId: number;
  pageNo: number;
  totalPages: number;
}) {
  return requestClient.post<TrainingLearnerApi.HeartbeatResult>(
    '/training/portal/learner/doc/page',
    data,
  );
}

/** 文档学习状态 */
export function getDocStatus(courseId: number, mediaId: number) {
  return requestClient.get<TrainingLearnerApi.DocStatus>(
    `/training/portal/learner/doc/status?courseId=${courseId}&mediaId=${mediaId}`,
  );
}

/** 直播大厅 */
export function getLiveRooms() {
  return requestClient.get<TrainingLearnerApi.LiveRoomGroup>(
    '/training/portal/learner/live/rooms',
  );
}

/** 直播房间详情 */
export function getLiveRoom(id: number) {
  return requestClient.get<TrainingLearnerApi.LiveRoom>(
    `/training/portal/learner/live/room?id=${id}`,
  );
}

/** 进入直播间 */
export function joinLive(id: number) {
  return requestClient.post<TrainingLearnerApi.SessionStart>(
    `/training/portal/learner/live/join?id=${id}`,
  );
}

/** 发送弹幕 */
export function sendDanmaku(data: { roomId: number; content: string }) {
  return requestClient.post<boolean>(
    '/training/portal/learner/live/danmaku',
    data,
  );
}

/** 拉取弹幕（增量） */
export function getDanmaku(roomId: number, afterId?: number) {
  return requestClient.get<TrainingLearnerApi.Danmaku[]>(
    `/training/portal/learner/live/danmaku?roomId=${roomId}${
      afterId ? `&afterId=${afterId}` : ''
    }`,
  );
}

/** 我的学习轨迹 */
export function getMyTracks(params: PageParam & { mediaType?: string }) {
  return requestClient.get<PageResult<TrainingLearnerApi.TrackRow>>(
    '/training/portal/learner/track/list',
    { params },
  );
}

/** 我的学习轨迹汇总 */
export function getMySummary() {
  return requestClient.get<TrainingLearnerApi.TrackSummary>(
    '/training/portal/learner/track/summary',
  );
}

/** 我的打卡记录 */
export function getMyCheckins(courseId?: number) {
  return requestClient.get<TrainingLearnerApi.CheckinRecord[]>(
    `/training/portal/learner/track/checkins${
      courseId ? `?courseId=${courseId}` : ''
    }`,
  );
}
