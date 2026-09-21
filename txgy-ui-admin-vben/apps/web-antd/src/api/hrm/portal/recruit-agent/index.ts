import { requestClient } from '#/api/request';

/**
 * 员工端招聘 Agent 接口。
 *
 * 诊断基于「本人已固化的简历版本 + 已发布职位」，服务端会重新校验归属与职位有效性。
 */
export namespace HrmPortalRecruitAgentApi {
  /** 诊断结果里的单个分项 */
  export interface AnalyzeDimension {
    key: 'CLARITY' | 'COMPLETENESS' | 'JOB_MATCH' | 'PROFESSIONALISM' | string;
    score: number;
    comment: string;
    evidence: Array<{ field: string; note: string }>;
  }

  export interface AnalyzeSuggestion {
    module: string;
    fieldPath: string;
    advice: string;
    reason: string;
  }

  export interface AnalyzeResult {
    overallScore: number;
    dimensions: AnalyzeDimension[];
    strengths: string[];
    missingInfo: string[];
    risks: string[];
    suggestions: AnalyzeSuggestion[];
  }

  /** 匹配结果里的单个分项 */
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

  export interface MatchPoint {
    point: string;
    evidence: string;
  }

  export interface MatchToConfirm {
    point: string;
    reason: string;
  }

  /** 职位匹配结果（A05） */
  export interface MatchResult {
    overallScore: number;
    dimensions: MatchDimension[];
    matched: MatchPoint[];
    missing: MatchPoint[];
    toConfirm: MatchToConfirm[];
  }

  /** 优化建议项：以「建议项」为最小确认单位 */
  export interface OptimizeItem {
    module: string;
    fieldPath: string;
    operation: 'REPLACE';
    /** 简历中的原文片段，用于定位与对比 */
    originalValue: string;
    suggestedValue: string;
    reason: string;
  }

  /** 简历优化结果（A02） */
  export interface OptimizeResult {
    items: OptimizeItem[];
  }

  /** 投递前检查里的提醒项：requiredMissing 用 item/note，conflicts 与 gaps 用 point/evidence */
  export interface PreCheckItem {
    item?: string;
    point?: string;
    note?: string;
    evidence?: string;
  }

  /** 投递前检查结果（A06）：只作提醒，不含分数，也不阻断投递 */
  export interface PreCheckResult {
    summary: string;
    requiredMissing: PreCheckItem[];
    conflicts: PreCheckItem[];
    gaps: PreCheckItem[];
  }

  /** 任务结果可能是诊断、匹配、优化或投递前检查，按 scene 区分 */
  export type TaskResult =
    | AnalyzeResult
    | MatchResult
    | OptimizeResult
    | PreCheckResult;

  export interface AnalyzeReq {
    resumeVersionId: number;
    postId: number;
    idempotencyKey: string;
  }

  /** 表达方向 */
  export type OptimizeStyle =
    | 'CONCISE'
    | 'MANAGEMENT'
    | 'PROFESSIONAL'
    | 'TECHNICAL';

  export interface OptimizeReq extends AnalyzeReq {
    style: OptimizeStyle;
    /** 优化模块；为空表示整份优化 */
    modules?: string[];
  }
  /** 采纳状态 */
  export type OptimizeDecision = 'ACCEPT' | 'ADJUST' | 'REJECT';

  /** 单项确认结果：以建议项为最小确认单位 */
  export interface OptimizeDecisionItem {
    module?: string;
    fieldPath: string;
    originalValue: string;
    suggestedValue?: string;
    finalValue: string;
    decision: OptimizeDecision;
  }

  export interface OptimizeApplyReq {
    sourceVersionId: number;
    taskId: number;
    changeSummary?: string;
    idempotencyKey: string;
    decisions: OptimizeDecisionItem[];
  }


  export interface TaskResp {
    taskId: number;
    /** RESUME_ANALYZE 简历诊断、JOB_MATCH 职位匹配、PRE_SUBMIT_CHECK 投递前检查 */
    scene: 'JOB_MATCH' | 'PRE_SUBMIT_CHECK' | 'RESUME_ANALYZE' | string;
    status: 'FAILED' | 'PENDING' | 'RUNNING' | 'SUCCEEDED';
    resumeVersionId?: number;
    postId?: number;
    postFingerprint?: string;
    createdAt?: string;
    overallScore?: number;
    result?: TaskResult;
    model?: string;
    promptVersion?: string;
    errorCode?: string;
    errorMessage?: string;
    disclaimer?: string;
  }
}

/** 提交简历诊断，返回任务编号 */
export function submitResumeAnalyze(data: HrmPortalRecruitAgentApi.AnalyzeReq) {
  return requestClient.post<number>(
    '/hrm/portal/recruit-agent/resume/analyze',
    data,
  );
}

/** 提交职位匹配，返回任务编号 */
export function submitJobMatch(data: HrmPortalRecruitAgentApi.AnalyzeReq) {
  return requestClient.post<number>(
    '/hrm/portal/recruit-agent/job/match',
    data,
  );
}

/**
 * 提交投递前检查，返回任务编号。
 *
 * 结论只作提醒：服务端不会给出通过与否的判定，前端也不得据此阻断投递。
 */
export function submitPreSubmitCheck(data: HrmPortalRecruitAgentApi.AnalyzeReq) {
  return requestClient.post<number>(
    '/hrm/portal/recruit-agent/application/precheck',
    data,
  );
}

/** 提交简历优化，返回任务编号；产出建议项供用户逐项确认，不会直接改动简历 */
export function submitResumeOptimize(
  data: HrmPortalRecruitAgentApi.OptimizeReq,
) {
  return requestClient.post<number>(
    '/hrm/portal/recruit-agent/resume/optimize',
    data,
  );
}

/** 确认优化建议并生成新的简历版本，返回新版本编号；不会改动源版本 */
export function applyResumeOptimize(
  data: HrmPortalRecruitAgentApi.OptimizeApplyReq,
) {
  return requestClient.post<number>(
    '/hrm/portal/recruit-agent/resume/optimize/apply',
    data,
  );
}

/** 生成确认提交的幂等键 */
export function buildOptimizeApplyKey() {
  const random =
    typeof crypto !== 'undefined' && 'randomUUID' in crypto
      ? crypto.randomUUID()
      : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
  return `apply-${random}`.slice(0, 64);
}

/** 查询 Agent 任务状态与结果 */
export function getRecruitAgentTask(taskId: number) {
  return requestClient.request<HrmPortalRecruitAgentApi.TaskResp>(
    '/hrm/portal/recruit-agent/task/get',
    { method: 'GET', params: { taskId } },
  );
}

/** 生成幂等键：同一次用户操作复用同一个键，重复提交不会重复调用模型 */
export function buildAnalyzeIdempotencyKey() {
  const random =
    typeof crypto !== 'undefined' && 'randomUUID' in crypto
      ? crypto.randomUUID()
      : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
  return `analyze-${random}`.slice(0, 64);
}
