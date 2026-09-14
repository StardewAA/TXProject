import type { MesHomeApi } from '#/api/mes/home';

import { MesProWorkOrderStatusEnum } from '@vben/constants';

/** 首页汇总统计默认值 */
export const defaultSummary: MesHomeApi.Summary = {
  andonActiveCount: 0,
  machineryMaintenance: 0,
  machineryProducing: 0,
  machineryStop: 0,
  machineryTotal: 0,
  repairActiveCount: 0,
  todayOutput: 0,
  todayQualifiedQuantity: 0,
  todayUnqualifiedQuantity: 0,
  workOrderActiveCount: 0,
  workOrderFinishedCount: 0,
  workOrderPrepareCount: 0,
  yesterdayOutput: 0,
};

/** 工单状态对应的颜色映射 */
export const WORK_ORDER_STATUS_COLOR_MAP: Record<number, string> = {
  [MesProWorkOrderStatusEnum.PREPARE]: '#909399', // 草稿
  [MesProWorkOrderStatusEnum.CONFIRMED]: '#409EFF', // 已确认
  [MesProWorkOrderStatusEnum.ISSUED]: '#13C2C2', // 已下发
  [MesProWorkOrderStatusEnum.IN_PRODUCTION]: '#FA8C16', // 生产中
  [MesProWorkOrderStatusEnum.FINISHED]: '#67C23A', // 已完成
  [MesProWorkOrderStatusEnum.CANCELED]: '#F56C6C', // 已取消
};

/** 设备状态对应的颜色映射（对应 MesDvMachineryStatusEnum） */
export const MACHINERY_STATUS_COLOR_MAP: Record<number, string> = {
  1: '#F56C6C', // 停机
  2: '#67C23A', // 生产中
  3: '#E6A23C', // 维护中
};

/** 维修工单状态对应的颜色映射（对应 MesDvRepairStatusEnum） */
export const REPAIR_STATUS_COLOR_MAP: Record<number, string> = {
  0: '#909399', // 草稿
  1: '#409EFF', // 维修中
  2: '#E6A23C', // 待验收
  4: '#67C23A', // 已确认
};
