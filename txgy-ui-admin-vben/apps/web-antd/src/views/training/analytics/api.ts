import { requestClient } from '#/api/request';

export namespace TrainingAnalyticsApi {
  /** 课程规模统计 */
  export interface CourseStats {
    approved?: number;
    draft?: number;
    mediaTotal?: number;
    offShelf?: number;
    onShelf?: number;
    pendingReview?: number;
    total?: number;
  }

  /** 资源规模统计 */
  export interface ResourceStats {
    documentOnline?: number;
    documentTotal?: number;
    questionTotal?: number;
    videoOnline?: number;
    videoTotal?: number;
  }

  /** 互动与日志统计 */
  export interface ActivityStats {
    auditLogTotal?: number;
    commentTotal?: number;
    operationLogLast7d?: number;
    operationLogTotal?: number;
  }

  /** 近 7 天操作趋势项 */
  export interface TrendItem {
    count: number;
    date: string;
  }

  /** 分类分布项 */
  export interface CategoryDistributionItem {
    category: string;
    count: number;
  }

  /** 最近操作日志项 */
  export interface RecentLogItem {
    action: string;
    createTime: string;
    id: number;
    targetType: string;
    userName: string;
  }

  /** 统计看板数据 */
  export interface Dashboard {
    activity: ActivityStats;
    course: CourseStats;
    documentCategoryDistribution: CategoryDistributionItem[];
    operationTrend7d: TrendItem[];
    recentLogs: RecentLogItem[];
    resource: ResourceStats;
  }
}

/** 获取数据统计看板 */
export function getAnalyticsDashboard() {
  return requestClient.get<TrainingAnalyticsApi.Dashboard>(
    '/training/analytics/dashboard',
  );
}

export namespace PromotionApi {
  export interface Path { fromLevel: string; name: string; toLevel: string }
  export interface Context {
    levels: Array<{ label: string; value: string }>;
    paths: Path[];
    sources: Record<string, string>;
  }
  export interface Employee { id: number; nickname: string; username: string }
  export interface Profile { currentLevel: string; jobType?: string; levelSince: string; userId: number }
  export interface Rule {
    actualValue?: number;
    available?: boolean;
    description?: string;
    gap?: string;
    id: number;
    isHard: boolean | number;
    metricSource?: string;
    operator: string;
    passed?: boolean;
    progress?: number;
    ruleKey: string;
    ruleName: string;
    ruleValue: number;
    sourceModule: string;
    status: number;
    unit?: string;
  }
  export interface Evaluation {
    currentLevel: string;
    currentLevelName: string;
    levelSince: string;
    message?: string;
    nextLevel?: string;
    nextLevelName?: string;
    passedCount: number;
    progress: number;
    qualified: boolean;
    rules: Rule[];
    totalCount: number;
    userId: number;
  }
}

const promotionBase = '/training/analytics/promotion';
export const getPromotionContext = () => requestClient.get<PromotionApi.Context>(`${promotionBase}/context`);
export const getPromotionRules = (params: { fromLevel: string; toLevel: string }) =>
  requestClient.get<PromotionApi.Rule[]>(`${promotionBase}/rules`, { params });
export const getPromotionEmployees = (keyword = '') =>
  requestClient.get<PromotionApi.Employee[]>(`${promotionBase}/employees`, { params: { keyword } });
export const getPromotionProfile = (userId?: number) =>
  requestClient.get<PromotionApi.Profile>(`${promotionBase}/profile`, { params: { userId } });
export const savePromotionProfile = (data: PromotionApi.Profile) => requestClient.post(`${promotionBase}/profile/save`, data);
export const reportPromotionMetric = (data: Record<string, any>) => requestClient.post(`${promotionBase}/metric/report`, data);
export const updatePromotionRule = (data: Record<string, any>) => requestClient.post(`${promotionBase}/rule/update`, data);
export const getPromotionEvaluation = (userId?: number) =>
  requestClient.get<PromotionApi.Evaluation>(`${promotionBase}/evaluation`, { params: { userId } });
export const runPromotionEvaluation = (userId?: number) =>
  requestClient.post<PromotionApi.Evaluation>(`${promotionBase}/evaluate`, undefined, { params: { userId } });
export const getPersonalArchive = () => requestClient.get<Record<string, any>>('/training/analytics/personal-archive/overview');
