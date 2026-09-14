import { requestClient } from '#/api/request';

export namespace MesHomeApi {
  /** MES 首页汇总统计 */
  export interface Summary {
    workOrderActiveCount: number; // 进行中工单数
    workOrderPrepareCount: number; // 待排产工单数
    workOrderFinishedCount: number; // 已完成工单数
    todayOutput: number; // 今日产量
    yesterdayOutput: number; // 昨日产量
    todayQualifiedQuantity: number; // 今日合格品数
    todayUnqualifiedQuantity: number; // 今日不良品数
    machineryTotal: number; // 设备总数
    machineryProducing: number; // 生产中设备数
    machineryStop: number; // 停机设备数
    machineryMaintenance: number; // 维护中设备数
    andonActiveCount: number; // 未处置安灯呼叫数
    repairActiveCount: number; // 待处理维修工单数
    milestoneAlertCount: number; // 里程碑预警数（3 天内到期/已超期且未完成）
  }

  /** MES 工单状态分布 */
  export interface WorkOrderStatus {
    status: number; // 工单状态
    statusName: string; // 工单状态名称
    count: number; // 数量
  }

  /** MES 设备状态分布 */
  export interface MachineryStatus {
    status: number; // 设备状态
    statusName: string; // 设备状态名称
    count: number; // 设备台数
  }

  /** MES 质检合格率 */
  export interface QcPassRate {
    qcType: number; // 检验类型值
    qcTypeName: string; // 检验类型名
    docCount: number; // 检验单数
    checkQuantity: number; // 检测数量
    qualifiedQuantity: number; // 合格数量
    passRate: number; // 数量合格率（%）
    docPassRate: number; // 单据合格率（%）
  }

  /** MES 安灯级别分布 */
  export interface AndonLevelStat {
    level: number; // 级别值
    levelName: string; // 级别名
    count: number; // 呼叫数量
  }

  /** MES 安灯呼叫原因统计 */
  export interface AndonReasonStat {
    reason: string; // 呼叫原因
    count: number; // 呼叫数量
  }

  /** MES 安灯呼叫分析 */
  export interface AndonAnalysis {
    totalCount: number; // 呼叫总数
    activeCount: number; // 未处置数
    handledCount: number; // 已处置数
    handleRate: number; // 处置率（%）
    avgHandleMinutes: number; // 平均处置时长（分钟）
    levelStats: AndonLevelStat[]; // 按级别分布
    topReasons: AndonReasonStat[]; // 呼叫原因 TOP N
  }

  /** MES 维修工单状态分布 */
  export interface RepairStatusStat {
    status: number; // 状态值
    statusName: string; // 状态名
    count: number; // 工单数量
  }

  /** MES 维修工单分析 */
  export interface RepairAnalysis {
    totalCount: number; // 维修工单总数
    pendingCount: number; // 待处理数
    finishedCount: number; // 已完成数
    avgRepairHours: number; // 平均维修时长（小时）
    passCount: number; // 维修通过数
    failCount: number; // 维修不通过数
    statusStats: RepairStatusStat[]; // 按状态分布
  }

  /** MES 点检保养执行 */
  export interface CheckMainten {
    planTotalCount: number; // 点检方案总数
    planEnabledCount: number; // 已启用方案数
    checkTotalCount: number; // 点检记录总数
    checkFinishedCount: number; // 点检已完成数
    checkFinishRate: number; // 点检完成率（%）
    maintenTotalCount: number; // 保养记录总数
    maintenSubmittedCount: number; // 保养已提交数
    maintenSubmitRate: number; // 保养提交率（%）
  }

  /** MES 不良缺陷分析 */
  export interface DefectAnalysis {
    recordCount: number; // 缺陷记录条数
    totalQuantity: number; // 缺陷总数量
    criticalQuantity: number; // 致命缺陷数量
    majorQuantity: number; // 严重缺陷数量
    minorQuantity: number; // 轻微缺陷数量
    defectTopList: { name: string; quantity: number }[]; // 缺陷 TOP N 列表
  }

  /** MES 生产趋势 */
  export interface ProductionTrend {
    date: string; // 日期
    quantity: number; // 产量
    qualifiedQuantity: number; // 合格品数
    unqualifiedQuantity: number; // 不良品数
  }
}

/** 获得首页汇总统计 */
export function getHomeSummary() {
  return requestClient.get<MesHomeApi.Summary>('/mes/home-statistics/summary');
}

/** 获得工单状态分布 */
export function getWorkOrderStatusDistribution() {
  return requestClient.get<MesHomeApi.WorkOrderStatus[]>(
    '/mes/home-statistics/work-order-status',
  );
}

/** 获得设备状态分布 */
export function getMachineryStatusDistribution() {
  return requestClient.get<MesHomeApi.MachineryStatus[]>(
    '/mes/home-statistics/machinery-status',
  );
}

/** 获得质检合格率 */
export function getQcPassRate(days?: number) {
  return requestClient.get<MesHomeApi.QcPassRate[]>(
    '/mes/home-statistics/qc-pass-rate',
    { params: { days } },
  );
}

/** 获得安灯呼叫分析 */
export function getAndonAnalysis(days?: number) {
  return requestClient.get<MesHomeApi.AndonAnalysis>(
    '/mes/home-statistics/andon-analysis',
    { params: { days } },
  );
}

/** 获得维修工单分析 */
export function getRepairAnalysis(days?: number) {
  return requestClient.get<MesHomeApi.RepairAnalysis>(
    '/mes/home-statistics/repair-analysis',
    { params: { days } },
  );
}

/** 获得点检保养执行情况 */
export function getCheckMainten(days?: number) {
  return requestClient.get<MesHomeApi.CheckMainten>(
    '/mes/home-statistics/check-mainten',
    { params: { days } },
  );
}

/** 获得不良缺陷分析 */
export function getDefectAnalysis(days?: number) {
  return requestClient.get<MesHomeApi.DefectAnalysis>(
    '/mes/home-statistics/defect-analysis',
    { params: { days } },
  );
}

/** 获得生产趋势 */
export function getProductionTrend(days?: number) {
  return requestClient.get<MesHomeApi.ProductionTrend[]>(
    '/mes/home-statistics/production-trend',
    { params: { days } },
  );
}
