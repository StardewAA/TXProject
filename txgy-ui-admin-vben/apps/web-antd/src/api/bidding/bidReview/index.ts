import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

/**
 * 智慧标书审查（前台智慧标书审查）接口封装。
 *
 * 后端实现：txgy-module-bidding / controller/admin/bidreview/**
 * 方案文档：txgy-module-bidding/docs/模块设计与规范/智慧标书审查-设计方案.md（V2.0 · DeepSeek 单通道）
 *
 * 两组端点：
 *  - 个人端（门户，只做"上传 + 一键生成"）：/bidding/bid-review/portal/**，权限 bidding:bid-review:self:*
 *    后端强制按 getLoginUserId() 归属，**入参不含 userId**，前端也不要试图传。
 *  - 管理端（后台）：/bidding/bid-review/{task,issue,rule-set,rule-item,report,config}/**
 *
 * 口径提醒：状态/级别/结论等标签一律走字典（bid_review_*）显示，别在页面硬编码中文。
 */
export namespace BidReviewApi {
  /* ===================== 通用 ===================== */
  export type TaskStatus = 'CANCELED' | 'FAILED' | 'QUEUED' | 'RUNNING' | 'SUCCESS';
  export type Verdict = 'FAIL' | 'PASS' | 'RISK';
  export type Severity = 'HIGH' | 'LOW' | 'MEDIUM';
  export type IssueStatus = 'CONFIRMED' | 'FIXED' | 'IGNORED' | 'OPEN';

  /* ===================== 字典（bidding_dict_data 的 bid_review_* 段） ===================== */
  export type DictTagType = 'default' | 'error' | 'info' | 'success' | 'warning';
  export interface DictItem {
    value: string;
    label: string;
    /** 标签色（前端直接用，勿自造映射） */
    tagType: DictTagType;
    /** 是否终态（终态不可再流转） */
    terminal: boolean;
    sort: number;
  }
  /** key = 字典名，如 bid_review_status */
  export type DictMap = Record<string, DictItem[]>;

  export interface Task {
    id: number;
    taskNo: string;
    taskName: string;
    sourceScene: string;
    ownerUserId?: number;
    ownerName?: string;
    ruleSetId?: number;
    ruleSetName?: string;
    bidFileId?: number;
    bidFileName?: string;
    tenderFileId?: number;
    parseMode?: string;
    status: TaskStatus;
    currentStep?: string;
    progress: number;
    verdict?: Verdict;
    scoreTotal?: number;
    ruleCnt?: number;
    issueCnt?: number;
    highIssueCnt?: number;
    aiCalled?: boolean;
    errorCode?: string;
    errorMsg?: string;
    /** 是否废标（命中高危项即 true） */
    rejected?: boolean;
    /** 废标原因（命中高危的规则名称） */
    rejectReasons?: string[];
    retryCount?: number;
    costMs?: number;
    remark?: string;
    reportReady?: boolean;
    scoreItems?: { category: string; score: number; scoreMax?: number }[];
    startedTime?: string;
    createTime?: string;
    finishedTime?: string;
  }

  export interface TaskPageReq extends PageParam {
    taskName?: string;
    taskNo?: string;
    ownerUserId?: number;
    /** 提交人姓名（模糊，管理端） */
    ownerName?: string;
    sourceScene?: string;
    status?: TaskStatus;
    verdict?: Verdict;
    beginTime?: string;
    endTime?: string;
  }

  /** 管理端建单入参（POST /task/create）：替他人建单时 ownerUserId 必传 */
  export interface TaskCreateReq {
    taskName: string;
    ownerUserId?: number;
    ruleSetId?: number;
    bidFileId: number;
    bidFileUrl?: string;
    bidFileName?: string;
    tenderFileId?: number;
    tenderFileUrl?: string;
    tenderFileName?: string;
    projectId?: number;
    tenderProjectId?: number;
    remark?: string;
    /** 保存后立即排队执行，默认 true */
    generate?: boolean;
  }

  export interface TaskProgress {
    id: number;
    status: TaskStatus;
    currentStep?: string;
    progress: number;
    verdict?: Verdict;
    issueCnt?: number;
    highIssueCnt?: number;
    errorCode?: string;
    errorMsg?: string;
    /** 是否废标 + 废标原因（一键审查后据此展示结论页） */
    rejected?: boolean;
    rejectReasons?: string[];
  }

  export interface Issue {
    id: number;
    taskId: number;
    /** 所属任务编号/名称（跨任务「问题复核」页直接展示） */
    taskNo?: string;
    taskName?: string;
    ruleItemId?: number;
    ruleCode?: string;
    category?: string;
    severity: Severity;
    issueTitle: string;
    issueDesc?: string;
    hitLocation?: string;
    evidenceText?: string;
    evidenceLack?: boolean;
    suggestion?: string;
    source?: string;
    aiConfidence?: number;
    status: IssueStatus;
    reviewerId?: number;
    /** 复核人名称（后端批量解析） */
    reviewerName?: string;
    reviewRemark?: string;
    reviewTime?: string;
  }

  export interface IssuePageReq extends PageParam {
    taskId?: number;
    severity?: Severity;
    category?: string;
    status?: IssueStatus;
    source?: string;
    /** 只看"证据不足"（AI 给不出标书原文证据，仍按原级别计入结论） */
    evidenceLack?: boolean;
  }

  /** 问题项复核进度（GET /issue/stats） */
  export interface IssueStats {
    openCount: number;
    confirmedCount: number;
    ignoredCount: number;
    fixedCount: number;
    totalCount: number;
    evidenceLackCount: number;
  }

  /** 报告台账行（GET /report/page）：报告 + 它属于哪个任务的扁平行 */
  export interface ReportLedgerRow {
    id: number;
    taskId: number;
    taskNo?: string;
    taskName?: string;
    reportNo: string;
    verdict: Verdict;
    rejected?: boolean;
    scoreTotal?: number;
    aiGenerated?: boolean;
    createTime?: string;
    docxReady?: boolean;
  }

  export interface ReportPageReq extends PageParam {
    reportNo?: string;
    verdict?: Verdict;
  }

  export interface Report {
    id: number;
    taskId: number;
    reportNo: string;
    verdict: Verdict;
    scoreTotal?: number;
    conclusion?: string;
    htmlContent?: string;
    aiGenerated?: boolean;
    disclaimer?: string;
    createTime?: string;
  }

  /** 报告 Word 文档（下载用；generated=false 表示复用了已生成的文件） */
  export interface ReportDoc {
    fileId?: number;
    url?: string;
    fileName?: string;
    generated?: boolean;
  }

  export interface RuleSet {
    id: number;
    ruleSetCode: string;
    ruleSetName: string;
    source: string;
    parseStatus?: string;
    ruleCnt?: number;
    enable?: boolean;
    defaultFlag?: boolean;
    remark?: string;
    createTime?: string;
    updateTime?: string;
    items?: RuleItem[];
  }

  export interface RuleSetPageReq extends PageParam {
    ruleSetName?: string;
    source?: string;
    enable?: boolean;
  }

  export interface RuleItem {
    id: number;
    ruleSetId: number;
    ruleCode: string;
    ruleName: string;
    category: string;
    ruleType: string;
    checkMode: string;
    severity: Severity;
    scoreMax?: number;
    requirement?: string;
    evidenceHint?: string;
    sourceRef?: string;
    sort?: number;
    enable?: boolean;
  }

  export interface Overview {
    totalCount: number;
    runningCount: number;
    successCount: number;
    failedCount: number;
    verdictStats: { name: string; count: number }[];
    severityStats: { name: string; count: number }[];
    topRules: { name: string; count: number }[];
    aiCallsLast24h?: number;
    aiFailureRate?: number;
  }

  export interface AiConfig {
    enabled?: boolean;
    baseUrl?: string;
    model?: string;
    jsonMode?: boolean;
    temperature?: number;
    maxTokens?: number;
    timeoutSeconds?: number;
    retryTimes?: number;
    concurrency?: number;
    chunkSize?: number;
    apiKeyMasked?: string;
    available?: boolean;
  }

  export interface AiProbeResp {
    ok: boolean;
    model?: string;
    costMs?: number;
    errorMsg?: string;
  }

  /** 个人端「一键生成」入参：只传文件信息，不传 userId（后端按登录人归属）。 */
  export interface PortalCreateReq {
    bidFileId: number;
    bidFileUrl?: string;
    bidFileName?: string;
    tenderFileId?: number;
    tenderFileUrl?: string;
    tenderFileName?: string;
    projectId?: number;
    tenderProjectId?: number;
    remark?: string;
    /**
     * 归属企业（租户 ID，可选）。
     *
     * 传了：任务/问题项/报告全部落在**该企业租户**下，企业端即可看到（用于"个人端提交、企业端可见"）。
     * 后端会校验"本人已被该企业授权为经办人"（bidding_subject_user），否则报 1_060_113_016。
     * 不传：保持原行为，落到当前登录租户。
     */
    enterpriseTenantId?: number;
  }

  /** 个人端可选的「归属企业」项（来源：bidding_subject_user 里本人被授权为经办人的企业）。 */
  export interface EnterpriseOption {
    tenantId: number;
    subjectType?: string;
    userRole?: string;
  }
}

const PORTAL = '/bidding/bid-review/portal';
const ADMIN = '/bidding/bid-review';

/**
 * 标书审查用到的全部字典类型（bidding_dict_data 里的 bid_review_* 段）。
 *
 * 口径：页面里的状态/级别/结论/类别/来源等中文一律走字典，不在页面硬编码——
 * 与《招投标模块开发规范》§4.4 及本模块 API 头注释一致。
 */
export const BID_REVIEW_DICT_TYPES = [
  'bid_review_status',
  'bid_review_verdict',
  'bid_review_severity',
  'bid_review_step',
  'bid_review_scene',
  'bid_review_issue_status',
  'bid_review_issue_source',
  'bid_review_rule_category',
  'bid_review_rule_type',
  'bid_review_check_mode',
] as const;

/** 取本模块字典（登录即可，失败静默——调用方降级为"只显示 code"，不弹全局错误、不白屏）。 */
export function getBidReviewDicts() {
  return requestClient.get<BidReviewApi.DictMap>('/bidding/meta/dicts', {
    params: { types: BID_REVIEW_DICT_TYPES.join(',') },
    headers: { 'X-Silent-Error': 'true' },
  });
}

/**
 * 触发浏览器「直接下载」平台文件（不跳转到文件地址页）。
 *
 * 背景：文件服务回传的是绝对地址（如 http://127.0.0.1:48080/admin-api/infra/file/...），
 * 与页面不同源 → `<a download>` 的 download 属性会被忽略，点击只会"跳到文件地址页"。
 * 处理：取 `/admin-api/...` 起的同源路径（dev 走 Vite 代理、生产同源网关），
 * 这样 `<a download>` 才会真正触发下载，并带上后端返回的文件名。
 */
export function downloadPlatformFile(url?: string, fileName?: string): boolean {
  if (!url) {
    return false;
  }
  const idx = url.indexOf('/admin-api/');
  const href = idx >= 0 ? url.slice(idx) : url;
  const link = document.createElement('a');
  link.href = href;
  if (fileName) {
    link.download = fileName;
  }
  link.style.display = 'none';
  document.body.appendChild(link);
  link.click();
  link.remove();
  return true;
}

/* ===================== 个人端（门户） ===================== */

/** 一键生成：创建审查任务并立即执行（幂等：同人同文件只产生一个任务）。 */
export function createAndGenerate(data: BidReviewApi.PortalCreateReq) {
  return requestClient.post<number>(`${PORTAL}/create-and-generate`, data, {
    headers: { 'X-Silent-Error': 'true' },
  });
}

/**
 * 我已被授权为经办人的企业列表（个人端上传页「所属企业」下拉用）。
 *
 * 只返回 tenantId/subjectType/userRole —— 企业名称由页面用系统租户列表按 tenantId 映射，
 * 避免标书审查模块去依赖 system 模块的租户详情接口。
 */
export function getMyEnterprises() {
  return requestClient.get<BidReviewApi.EnterpriseOption[]>(`${PORTAL}/my-enterprises`, {
    headers: { 'X-Silent-Error': 'true' },
  });
}

export function getMyReviewPage(params: BidReviewApi.TaskPageReq) {
  return requestClient.get<PageResult<BidReviewApi.Task>>(`${PORTAL}/my/page`, { params });
}

export function getMyReviewTask(id: number | string) {
  return requestClient.get<BidReviewApi.Task>(`${PORTAL}/get`, { params: { id } });
}

export function getMyReviewProgress(id: number | string) {
  return requestClient.get<BidReviewApi.TaskProgress>(`${PORTAL}/progress`, {
    params: { id },
    headers: { 'X-Silent-Error': 'true' },
  });
}

export function getMyReviewIssues(taskId: number | string) {
  return requestClient.get<BidReviewApi.Issue[]>(`${PORTAL}/issue/list`, { params: { taskId } });
}

export function getMyReviewReport(taskId: number | string) {
  return requestClient.get<BidReviewApi.Report>(`${PORTAL}/report`, { params: { taskId } });
}

/** 生成/获取审查报告 Word 文档（同一任务只生成一次，返回可下载地址） */
export function getMyReviewReportDoc(taskId: number | string) {
  return requestClient.get<BidReviewApi.ReportDoc>(`${PORTAL}/report/doc`, {
    params: { taskId },
    headers: { 'X-Silent-Error': 'true' },
  });
}

export function retryMyReview(id: number | string) {
  return requestClient.post<boolean>(`${PORTAL}/retry`, null, {
    params: { id },
    headers: { 'X-Silent-Error': 'true' },
  });
}

export function cancelMyReview(id: number | string) {
  return requestClient.post<boolean>(`${PORTAL}/cancel`, null, {
    params: { id },
    headers: { 'X-Silent-Error': 'true' },
  });
}

export function deleteMyReview(id: number | string) {
  return requestClient.delete<boolean>(`${PORTAL}/delete`, {
    params: { id },
    headers: { 'X-Silent-Error': 'true' },
  });
}

/* ===================== 管理端（后台） ===================== */

export function getReviewTaskPage(params: BidReviewApi.TaskPageReq) {
  return requestClient.get<PageResult<BidReviewApi.Task>>(`${ADMIN}/task/page`, { params });
}

export function getReviewTask(id: number | string) {
  return requestClient.get<BidReviewApi.Task>(`${ADMIN}/task/get`, { params: { id } });
}

/**
 * 管理端建单（替经办人补建一次审查）。
 *
 * 后端 POST /task/create 需要 bidding:bid-review:create；幂等键由后端按
 * 归属人 + 标书 + 招标文件生成，重复点击只会产生一个任务。
 */
export function createReviewTask(data: BidReviewApi.TaskCreateReq) {
  return requestClient.post<number>(`${ADMIN}/task/create`, data, {
    headers: { 'X-Silent-Error': 'true' },
  });
}

export function retryReviewTask(id: number | string) {
  return requestClient.post<boolean>(`${ADMIN}/task/retry`, null, {
    params: { id },
    headers: { 'X-Silent-Error': 'true' },
  });
}

export function cancelReviewTask(id: number | string) {
  return requestClient.post<boolean>(`${ADMIN}/task/cancel`, null, {
    params: { id },
    headers: { 'X-Silent-Error': 'true' },
  });
}

export function getReviewOverview() {
  return requestClient.get<BidReviewApi.Overview>(`${ADMIN}/task/overview`);
}

export function getReviewIssuePage(params: BidReviewApi.IssuePageReq) {
  return requestClient.get<PageResult<BidReviewApi.Issue>>(`${ADMIN}/issue/page`, { params });
}

/** 问题项复核进度（待复核/已确认/已忽略/已整改/证据不足） */
export function getReviewIssueStats() {
  return requestClient.get<BidReviewApi.IssueStats>(`${ADMIN}/issue/stats`);
}

export function reviewIssue(data: { id: number; status: string; reviewRemark?: string }) {
  return requestClient.put<boolean>(`${ADMIN}/issue/review`, data, {
    headers: { 'X-Silent-Error': 'true' },
  });
}

export function getReviewReport(taskId: number | string) {
  return requestClient.get<BidReviewApi.Report>(`${ADMIN}/report/get`, { params: { taskId } });
}

/** 管理端：生成/获取报告 Word 文档 */
export function getReviewReportDoc(taskId: number | string) {
  return requestClient.get<BidReviewApi.ReportDoc>(`${ADMIN}/report/doc`, {
    params: { taskId },
    headers: { 'X-Silent-Error': 'true' },
  });
}

/** 报告台账分页（报告与统计页） */
export function getReviewReportPage(params: BidReviewApi.ReportPageReq) {
  return requestClient.get<PageResult<BidReviewApi.ReportLedgerRow>>(`${ADMIN}/report/page`, {
    params,
  });
}

/**
 * 导出报告正文 HTML（后端 report/export）。
 *
 * 用途：管理端"打印 / 另存为 HTML"；下载走 downloadPlatformFile 的同源规则，
 * 因此这里只负责取内容，落盘/预览交给调用方（避免直接新开一个需要鉴权的地址页拿不到内容）。
 */
export function exportReviewReportHtml(taskId: number | string) {
  return requestClient.get<string>(`${ADMIN}/report/export`, {
    params: { taskId },
    headers: { 'X-Silent-Error': 'true' },
  });
}

export function getRuleSetPage(params: BidReviewApi.RuleSetPageReq) {
  return requestClient.get<PageResult<BidReviewApi.RuleSet>>(`${ADMIN}/rule-set/page`, { params });
}

export function getRuleSet(id: number | string) {
  return requestClient.get<BidReviewApi.RuleSet>(`${ADMIN}/rule-set/get`, { params: { id } });
}

export function createRuleSet(data: Partial<BidReviewApi.RuleSet>) {
  return requestClient.post<number>(`${ADMIN}/rule-set/create`, data, {
    headers: { 'X-Silent-Error': 'true' },
  });
}

export function updateRuleSet(data: Partial<BidReviewApi.RuleSet>) {
  return requestClient.put<boolean>(`${ADMIN}/rule-set/update`, data, {
    headers: { 'X-Silent-Error': 'true' },
  });
}

export function deleteRuleSet(id: number | string) {
  return requestClient.delete<boolean>(`${ADMIN}/rule-set/delete`, {
    params: { id },
    headers: { 'X-Silent-Error': 'true' },
  });
}

export function getRuleItemPage(params: Record<string, any>) {
  return requestClient.get<PageResult<BidReviewApi.RuleItem>>(`${ADMIN}/rule-item/page`, { params });
}

export function createRuleItem(data: Partial<BidReviewApi.RuleItem>) {
  return requestClient.post<number>(`${ADMIN}/rule-item/create`, data, {
    headers: { 'X-Silent-Error': 'true' },
  });
}

export function updateRuleItem(data: Partial<BidReviewApi.RuleItem>) {
  return requestClient.put<boolean>(`${ADMIN}/rule-item/update`, data, {
    headers: { 'X-Silent-Error': 'true' },
  });
}

export function deleteRuleItem(id: number | string) {
  return requestClient.delete<boolean>(`${ADMIN}/rule-item/delete`, {
    params: { id },
    headers: { 'X-Silent-Error': 'true' },
  });
}

export function getAiConfig() {
  return requestClient.get<BidReviewApi.AiConfig>(`${ADMIN}/config/get`);
}

export function updateAiConfig(data: Partial<BidReviewApi.AiConfig> & { changeReason?: string }) {
  return requestClient.put<boolean>(`${ADMIN}/config/update`, data, {
    headers: { 'X-Silent-Error': 'true' },
  });
}

export function probeAi() {
  return requestClient.post<BidReviewApi.AiProbeResp>(`${ADMIN}/config/ai-probe`, null, {
    headers: { 'X-Silent-Error': 'true' },
  });
}
