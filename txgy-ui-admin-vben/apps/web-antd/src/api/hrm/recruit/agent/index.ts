import { requestClient } from '#/api/request';

/**
 * 管理端招聘 Agent 接口（A07 / A08 / A09）。
 *
 * 三个场景都以「应聘记录」为入口：服务端会重新校验应聘记录是否属于当前租户，
 * 简历取应聘记录关联的候选人快照，不接受前端传入简历内容。
 */
export namespace HrmRecruitAgentApi {
  export interface ApplicationReq {
    applicationId: number;
    /** 幂等键；重新分析请换新键，否则会复用上一次的任务 */
    idempotencyKey: string;
  }

  /** 候选人摘要（A07） */
  export interface CandidateSummaryResult {
    coreSkills: string[];
    workYears: string;
    experienceSummary: string;
    achievements: string[];
    /** 简历事实，field 一定来自负载白名单 */
    facts: Array<{ field: string; note: string }>;
    missingInfo: string[];
    /** AI 推断，需要 HR 核实 */
    inferences: string[];
  }

  export interface MatchDimension {
    key:
      | 'EDUCATION'
      | 'EXPERIENCE'
      | 'KEYWORD'
      | 'RESPONSIBILITY'
      | 'SKILL'
      | string;
    score: number;
    comment: string;
    evidence: Array<{ field: string; note: string }>;
  }

  /** 候选人与职位匹配（A08） */
  export interface ApplicationMatchResult {
    overallScore: number;
    dimensions: MatchDimension[];
    matched: Array<{ evidence: string; point: string }>;
    missing: Array<{ evidence: string; point: string }>;
    toConfirm: Array<{ point: string; reason: string }>;
    /** 管理端额外要求的风险项 */
    risks: string[];
  }

  /** 面试问题建议（A09） */
  export interface InterviewQuestion {
    category:
      | 'COMPETENCY'
      | 'PROFESSIONAL'
      | 'PROJECT'
      | 'VERIFICATION'
      | string;
    question: string;
    purpose: string;
    focus: string;
  }

  export interface InterviewQuestionResult {
    questions: InterviewQuestion[];
  }

  export interface TaskResp {
    taskId: number;
    scene:
      | 'APPLICATION_MATCH'
      | 'CANDIDATE_SUMMARY'
      | 'INTERVIEW_QUESTION'
      | string;
    status: 'FAILED' | 'PENDING' | 'RUNNING' | 'SUCCEEDED';
    applicationId?: number;
    resumeId?: number;
    postId?: number;
    postFingerprint?: string;
    createdAt?: string;
    overallScore?: number;
    result?:
      | ApplicationMatchResult
      | CandidateSummaryResult
      | InterviewQuestionResult;
    model?: string;
    promptVersion?: string;
    promptTokens?: number;
    completionTokens?: number;
    totalTokens?: number;
    errorCode?: string;
    errorMessage?: string;
    disclaimer?: string;
  }
}

/** 生成候选人摘要（A07），返回任务编号 */
export function submitCandidateSummary(data: HrmRecruitAgentApi.ApplicationReq) {
  return requestClient.post<number>('/hrm/recruit/agent/candidate/summary', data);
}

/** 生成候选人与职位匹配报告（A08），返回任务编号 */
export function submitApplicationMatch(data: HrmRecruitAgentApi.ApplicationReq) {
  return requestClient.post<number>('/hrm/recruit/agent/application/match', data);
}

/** 生成面试问题建议（A09），返回任务编号 */
export function submitInterviewQuestion(
  data: HrmRecruitAgentApi.ApplicationReq,
) {
  return requestClient.post<number>(
    '/hrm/recruit/agent/application/interview-question',
    data,
  );
}

/** 查询 Agent 任务与结果，仅限当前租户 */
export function getRecruitAgentTask(taskId: number) {
  return requestClient.request<HrmRecruitAgentApi.TaskResp>(
    '/hrm/recruit/agent/task/get',
    { method: 'GET', params: { taskId } },
  );
}

/**
 * 生成幂等键。
 *
 * 每次「重新分析」都要用新键，否则服务端会命中上一次的任务而不重新调用模型。
 */
export function buildAgentIdempotencyKey() {
  const random =
    typeof crypto !== 'undefined' && 'randomUUID' in crypto
      ? crypto.randomUUID()
      : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
  return `agent-${random}`.slice(0, 64);
}
