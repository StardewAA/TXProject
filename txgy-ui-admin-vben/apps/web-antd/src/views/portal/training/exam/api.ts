/**
 * 考试引擎 API（独立的 Python 考试服务，经 nginx 反代到 /training-exam/api）
 *
 * 服务使用平台 token + tenant-id 鉴权（与平台同一套登录态），
 * 因此走原生 fetch 并显式带上 Authorization / tenant-id，不复用 admin-api 的 requestClient。
 */
import { useAccessStore } from '@vben/stores';

export namespace TrainingExamApi {
  /** 考试题目（学员视角，不含正确答案） */
  export interface Question {
    id: number;
    q_type: string;
    stem: string;
    options?: { key: string; text: string }[];
    sort_no: number;
    score: number;
  }

  /** 答卷（取卷/开考返回） */
  export interface Attempt {
    attempt_id: number;
    deadline_at: string;
    anti_switch?: boolean;
    switch_count?: number;
    server_now: string;
    answers: Record<string, unknown>;
    questions: Question[];
    paper?: string;
    work_type?: string;
    level?: string;
    total_score?: number;
    pass_score?: number;
    duration_min?: number;
  }

  /** 成绩 */
  export interface Result {
    id: number;
    status: string;
    objective_score?: number;
    subjective_score?: number;
    total_score?: number;
    is_pass?: boolean;
    switch_count?: number;
    answers?: {
      question_id: number;
      is_correct?: boolean;
      score_awarded?: number;
      comment?: string;
    }[];
  }

  /** 我的工种资格认证考试记录 */
  export interface QualificationRecord {
    attempt_id: number;
    work_type: string;
    level: string;
    exam: string;
    paper: string;
    status: string;
    score?: number;
    objective_score?: number;
    subjective_score?: number;
    is_pass?: boolean;
    pass_score?: number;
    submitted_at?: string;
    started_at?: string;
  }
}

const EXAM_BASE = '/training-exam/api';

function authHeaders(): Record<string, string> {
  const accessStore = useAccessStore();
  return {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${accessStore.accessToken}`,
    'tenant-id': String(accessStore.tenantId ?? 1),
  };
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${EXAM_BASE}${path}`, {
    ...init,
    headers: { ...authHeaders(), ...(init?.headers as Record<string, string>) },
  });
  const json = await res.json().catch(() => ({}));
  if (!res.ok || (json.code !== undefined && json.code !== 0)) {
    throw new Error(
      json?.detail ||
        json?.message ||
        json?.msg ||
        `考试服务请求失败（${res.status}）`,
    );
  }
  return json.data as T;
}

/** 工种资格认证考试：后端自动随机组卷并立即开考 */
export function startQualificationExam(data: {
  work_type: string;
  level: string;
  duration_min?: number;
}) {
  return request<TrainingExamApi.Attempt>('/portal/qualification/start', {
    method: 'POST',
    body: JSON.stringify(data),
  });
}

/** 保存单题答案 */
export function saveExamAnswer(
  attemptId: number | string,
  questionId: number | string,
  answer: unknown,
) {
  return request<null>(`/my/exams/attempts/${attemptId}/answers`, {
    method: 'POST',
    body: JSON.stringify({ question_id: Number(questionId), user_answer: answer }),
  });
}

/** 交卷（客观题自动阅卷出分，含主观题则进入待批阅） */
export function submitExam(attemptId: number | string) {
  return request<{ status: string; objective_score?: number }>(
    `/my/exams/attempts/${attemptId}/submit`,
    { method: 'POST', body: '{}' },
  );
}

/** 查看成绩 */
export function getExamResult(attemptId: number | string) {
  return request<TrainingExamApi.Result>(
    `/my/exams/attempts/${attemptId}/result`,
  );
}

/** 我的考试任务 */
export function getMyExams() {
  return request<any[]>('/my/exams');
}

/** 我的工种资格认证成绩（个人档案 / 成长等级） */
export function getMyQualifications() {
  return request<TrainingExamApi.QualificationRecord[]>(
    '/portal/qualification/my',
  );
}
