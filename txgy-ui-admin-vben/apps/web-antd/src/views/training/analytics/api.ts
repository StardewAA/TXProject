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
