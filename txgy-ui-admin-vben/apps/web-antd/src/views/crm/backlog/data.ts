import type { Ref } from 'vue';

export interface LeftSideItem {
  name: string;
  menu: string;
  count: Ref<number>;
}

/** 跟进状态 */
export const FOLLOWUP_STATUS = [
  { label: '待跟进', value: false },
  { label: '已跟进', value: true },
];

/** 归属范围 */
export const SCENE_TYPES = [
  { label: '我负责的', value: 1 },
  { label: '我参与的', value: 2 },
  { label: '下属负责的', value: 3 },
];

/** 联系状态 */
export const CONTACT_STATUS = [
  { label: '今日需联系', value: 1 },
  { label: '已逾期', value: 2 },
  { label: '已联系', value: 3 },
];

/** 审批状态 */
export const AUDIT_STATUS = [
  { label: '待审批', value: 10 },
  { label: '审核通过', value: 20 },
  { label: '审核不通过', value: 30 },
];

/** 回款提醒类型 */
export const RECEIVABLE_REMIND_TYPE = [
  { label: '待回款', value: 1 },
  { label: '已逾期', value: 2 },
  { label: '已回款', value: 3 },
];

/** 合同过期状态 */
export const CONTRACT_EXPIRY_TYPE = [
  { label: '即将过期', value: 1 },
  { label: '已过期', value: 2 },
];

/**
 * 待办来源枚举
 *
 * 说明：现有待办的各来源分别对应各业务模块的**实时查询口径**（不落库、不预生成待办记录）；
 * 本次新增 WARRANTY = 售后质保「质保即将到期」。
 */
export const BACKLOG_SOURCE = {
  /** 售后质保：质保即将到期（质保状态=质保中，且质保到期日 ≤ 今天 + 提前提醒天数） */
  WARRANTY: 'warrantyExpiry',
  /**
   * 回访任务：任务提醒（含临期与已逾期）
   * 任务状态 ∈ {待处理, 已逾期}；其中待处理要求 计划回访日 ≤ 今天 + 临期提醒提前天数，已逾期无条件纳入
   */
  VISIT: 'visitTaskRemind',
  /** 售后工单：工单超时预警（工单未闭环，且创建日期距今 ≥ 超时预警天数） */
  WORK_ORDER: 'ticketOverdue',
} as const;

/** 左侧菜单 */
export const useLeftSides = (
  customerTodayContactCount: Ref<number>,
  clueFollowCount: Ref<number>,
  customerFollowCount: Ref<number>,
  customerPutPoolRemindCount: Ref<number>,
  contractAuditCount: Ref<number>,
  contractRemindCount: Ref<number>,
  receivableAuditCount: Ref<number>,
  receivablePlanRemindCount: Ref<number>,
  warrantyExpiryCount: Ref<number>,
  visitTaskRemindCount: Ref<number>,
  ticketOverdueCount: Ref<number>,
): LeftSideItem[] => {
  return [
    {
      name: '今日需联系客户',
      menu: 'customerTodayContact',
      count: customerTodayContactCount,
    },
    {
      name: '分配给我的线索',
      menu: 'clueFollow',
      count: clueFollowCount,
    },
    {
      name: '分配给我的客户',
      menu: 'customerFollow',
      count: customerFollowCount,
    },
    {
      name: '待进入公海的客户',
      menu: 'customerPutPoolRemind',
      count: customerPutPoolRemindCount,
    },
    {
      name: '待审核合同',
      menu: 'contractAudit',
      count: contractAuditCount,
    },
    {
      name: '待审核回款',
      menu: 'receivableAudit',
      count: receivableAuditCount,
    },
    {
      name: '待回款提醒',
      menu: 'receivablePlanRemind',
      count: receivablePlanRemindCount,
    },
    {
      name: '即将到期的合同',
      menu: 'contractRemind',
      count: contractRemindCount,
    },
    {
      name: '质保即将到期',
      menu: BACKLOG_SOURCE.WARRANTY,
      count: warrantyExpiryCount,
    },
    {
      name: '任务提醒',
      menu: BACKLOG_SOURCE.VISIT,
      count: visitTaskRemindCount,
    },
    {
      name: '工单超时预警',
      menu: BACKLOG_SOURCE.WORK_ORDER,
      count: ticketOverdueCount,
    },
  ];
};
