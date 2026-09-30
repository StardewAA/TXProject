/* ============================================================================
  全流程跟踪：状态字典 + 业务枚举 → 中文映射（契约 26 §8 / 28 §八-7）。
  硬约束（用户口径 + 契约）：**状态枚举一律取字典**（bidding_dict_data 四类状态字典，
  /bidding/meta/dicts 回显，运营可配置中文名/标签色/终态），前端不硬编码枚举；
  label 即中文（DictItemRespVO.label），前端只做 value → label 的读取与兜底。
  本文件静态映射仅作**字典不可用时的离线兜底**（对齐 certificate/useCertMeta 的 CONTRACT_LABELS 模式，
  避免界面直接显示英文 code；字典优先，静态兜底，未知值才回显原值）。
  法规：《电子招标投标办法》第三章 电子招标 / 第五章 电子开标、评标和中标（状态阶段对应法条见页面注释）。
============================================================================ */
import { ref } from 'vue';

import { getFlowDicts, type FlowApi } from './index';

export const BIZ_TYPE_TEXT: Record<string, string> = {
  PROJECT: '项目',
  TENDER_PROJECT: '招标项目',
  BID_SECTION: '标段',
};

export const PHASE_TEXT: Record<string, string> = {
  PREPARE: '前期准备',
  BID: '投标阶段',
  EVALUATE: '开标评标',
  DECIDE: '定标阶段',
  FINISH: '收尾归档',
  ABNORMAL: '异常终态',
};

/* ============ 状态字典加载（模块级单例 + 并发去重，对齐 useCertMeta.loadCertMeta） ============ */
/** bizType → 字典类型键（契约 26 §8 登记的四类状态字典） */
const DICT_KEY_BY_BIZ: Record<string, string> = {
  PROJECT: 'project_status',
  TENDER_PROJECT: 'tender_project_status',
  BID_SECTION: 'bid_section_status',
};
const FLOW_DICT_TYPES = 'project_status,tender_project_status,bid_section_status,announcement_status';

const flowDicts = ref<FlowApi.DictMap>({});
let dictLoaded = false;
let dictInflight: null | Promise<void> = null;

/** 加载四类状态字典（幂等；force=true 强制刷新；失败静默，由静态兜底承接） */
export function loadFlowDicts(force = false): Promise<void> {
  if (!force && dictLoaded) return Promise.resolve();
  if (dictInflight) return dictInflight;
  dictInflight = getFlowDicts(FLOW_DICT_TYPES)
    .then((d) => {
      flowDicts.value = d ?? {};
      dictLoaded = Object.keys(flowDicts.value).length > 0;
    })
    .catch(() => {
      flowDicts.value = {};
      dictLoaded = false;
    })
    .finally(() => {
      dictInflight = null;
    });
  return dictInflight;
}

/** 字典项（按 sort 升序；静态兜底 CONTRACT_STATUS_TEXT 缺项也返回空数组） */
function dictItems(key: string): FlowApi.DictItem[] {
  return [...(flowDicts.value[key] ?? [])].sort((a, b) => (a.sort ?? 0) - (b.sort ?? 0));
}

/* ============ 静态兜底（仅字典不可用时使用；label/desc=契约 26 中文名，phase=业务阶段，非字典概念） ============ */
const CONTRACT_STATUS_TEXT: Record<string, Record<string, { label: string; desc: string; phase?: string }>> = {
  project_status: {
    DRAFT: { label: '草稿', desc: '项目信息编制中' },
    APPROVING: { label: '审批中', desc: '提交立项审批，等待审批' },
    ACTIVE: { label: '已立项', desc: '审批通过，可创建招标项目' },
    COMPLETED: { label: '已完结', desc: '所有下属招标项目均已归档' },
    CANCELLED: { label: '已取消', desc: '项目取消' },
  },
  tender_project_status: {
    DRAFT: { label: '草稿', phase: 'PREPARE', desc: '招标活动信息编制中' },
    APPROVING: { label: '审批中', phase: 'PREPARE', desc: '招标方案提交审批，等待审批' },
    PREPARING: { label: '待发公告', phase: 'PREPARE', desc: '审批通过，可发布招标公告' },
    ANNOUNCED: { label: '公告中', phase: 'BID', desc: '招标公告已发布，获取文件/报名' },
    BIDDING: { label: '投标中', phase: 'BID', desc: '投标人递交投标文件' },
    PENDING_OPEN: { label: '待开标', phase: 'EVALUATE', desc: '投标截止，等待开标' },
    EVALUATING: { label: '评标中', phase: 'EVALUATE', desc: '开标完成，专家评审中' },
    PUBLICIZING: { label: '公示中', phase: 'DECIDE', desc: '中标候选人公示中' },
    AWARDED: { label: '已定标', phase: 'DECIDE', desc: '确定中标人' },
    CONTRACTING: { label: '合同签订中', phase: 'FINISH', desc: '发出中标通知书，签订合同' },
    COMPLETED: { label: '已归档', phase: 'FINISH', desc: '合同签订并归档' },
    TERMINATED: { label: '已终止/流标', phase: 'ABNORMAL', desc: '终止或流标' },
  },
  bid_section_status: {
    DRAFT: { label: '草稿', phase: 'PREPARE', desc: '标段信息编制中' },
    BIDDING: { label: '招标中', phase: 'BID', desc: '公告/投标/开标阶段' },
    EVALUATING: { label: '评标中', phase: 'EVALUATE', desc: '专家评审中' },
    AWARDED: { label: '已定标', phase: 'DECIDE', desc: '已确定中标人' },
    FAILED: { label: '流标', phase: 'ABNORMAL', desc: '投标不足3家或全部废标' },
    COMPLETED: { label: '已完结', phase: 'FINISH', desc: '合同签订并归档' },
  },
};

/* ============ 状态 → 展示（字典优先 / 静态兜底 / 未知回显原值） ============ */
/** 状态 → 中文名（label）。字典优先；静态兜底；未知值回显英文 code。 */
export function statusTextOf(bizType: string, state?: null | string): string {
  if (!state) return '—';
  const key = DICT_KEY_BY_BIZ[bizType];
  const hit = key ? dictItems(key).find((i) => i.value === state) : undefined;
  if (hit) return hit.label;
  return key ? CONTRACT_STATUS_TEXT[key]?.[state]?.label ?? state : state;
}

/** 状态 → 描述（description；业务语义，静态兜底；找不到回 ''） */
export function statusDescOf(bizType: string, state?: null | string): string {
  if (!state) return '';
  const key = DICT_KEY_BY_BIZ[bizType];
  return key ? CONTRACT_STATUS_TEXT[key]?.[state]?.desc ?? '' : '';
}

/** 状态 → 阶段中文（phase；业务维度分组，非字典概念；找不到回 ''） */
export function phaseOf(bizType: string, state?: null | string): string {
  if (!state) return '';
  const key = DICT_KEY_BY_BIZ[bizType];
  if (!key) return '';
  const phase = CONTRACT_STATUS_TEXT[key]?.[state]?.phase;
  return phase ? PHASE_TEXT[phase] ?? '' : '';
}

/** 字典 tagType → antd Tag color（展示层映射，非业务枚举；对齐 certificate/useCertMeta TAG_COLOR） */
const TAG_COLOR: Record<string, string> = {
  success: 'green',
  warning: 'orange',
  error: 'red',
  info: 'blue',
  default: 'default',
};
/** 静态兜底状态色（仅字典不可用时用；草稿灰/进行中橙/终态红…） */
const CONTRACT_STATUS_COLOR: Record<string, string> = {
  COMPLETED: 'green', ACTIVE: 'green', AWARDED: 'green',
  DRAFT: 'default', PREPARING: 'default', PENDING_OPEN: 'default',
  APPROVING: 'blue',
  ANNOUNCED: 'cyan', BIDDING: 'cyan',
  EVALUATING: 'orange',
  PUBLICIZING: 'purple',
  CONTRACTING: 'geekblue',
  TERMINATED: 'red', CANCELLED: 'red', FAILED: 'red',
};

/** 状态 → Tag 颜色。字典 tagType 优先（运营可配），静态兜底。 */
export function statusColorOf(bizType: string, state?: null | string): string {
  if (!state) return 'default';
  const key = DICT_KEY_BY_BIZ[bizType];
  const hit = key ? dictItems(key).find((i) => i.value === state) : undefined;
  if (hit && hit.tagType) return TAG_COLOR[hit.tagType] ?? 'default';
  return CONTRACT_STATUS_COLOR[state] ?? 'default';
}

/** 状态下拉选项（筛选/回显用：字典 label/value 优先，静态兜底，避免英文 code 直接裸露）。 */
export function statusOptionsOf(bizType: string): Array<{ label: string; value: string }> {
  const key = DICT_KEY_BY_BIZ[bizType];
  const items = key ? dictItems(key) : [];
  if (items.length > 0) return items.map((i) => ({ label: i.label, value: i.value }));
  const fallback = key ? CONTRACT_STATUS_TEXT[key] ?? {} : {};
  return Object.entries(fallback).map(([value, it]) => ({ label: it.label, value }));
}

/** 是否终态（字典 terminal 字段；终态禁止再执行状态流转）。字典缺失时按静态终态集兜底。 */
const CONTRACT_TERMINAL: Record<string, string[]> = {
  project_status: ['COMPLETED', 'CANCELLED'],
  tender_project_status: ['COMPLETED', 'TERMINATED'],
  bid_section_status: ['COMPLETED', 'FAILED'],
};
export function isTerminalState(bizType: string, state?: null | string): boolean {
  if (!state) return false;
  const key = DICT_KEY_BY_BIZ[bizType];
  if (!key) return false;
  if (flowDicts.value[key]) {
    return flowDicts.value[key]?.some((i) => i.value === state && i.terminal === true) === true;
  }
  return (CONTRACT_TERMINAL[key] ?? []).includes(state);
}

/* ============ 公告状态（AnnouncementStatusEnum 5 态；契约 26 §3.4 / 28：announcement_status 字典 + 静态兜底） ============ */
const CONTRACT_ANNOUNCEMENT_TEXT: Record<string, string> = {
  DRAFT: '草稿',
  REVIEWING: '审核中',
  PUBLISHED: '已发布',
  WITHDRAWN: '已撤回',
  EXPIRED: '已过期',
};
/** 公告状态 → 中文名（announcement_status 字典优先；静态兜底；未知值回显原值） */
export function announcementStatusTextOf(state?: null | string): string {
  if (!state) return '—';
  const hit = dictItems('announcement_status').find((i) => i.value === state);
  if (hit) return hit.label;
  return CONTRACT_ANNOUNCEMENT_TEXT[state] ?? state;
}
/** 公告状态 → Tag 颜色（字典 tagType 优先；静态兜底） */
export function announcementStatusColorOf(state?: null | string): string {
  if (!state) return 'default';
  const hit = dictItems('announcement_status').find((i) => i.value === state);
  if (hit && hit.tagType) return TAG_COLOR[hit.tagType] ?? 'default';
  return CONTRACT_STATUS_COLOR[state] ?? 'default';
}

/* ============ 时间驱动调度记录状态（FlowTimerStatusEnum；无字典，静态） ============ */
export const TIMER_STATUS_TEXT: Record<string, string> = {
  WAITING: '待触发',
  DONE: '已执行',
  SKIPPED: '已跳过',
};
export const TIMER_STATUS_COLOR: Record<string, string> = {
  WAITING: 'orange',
  DONE: 'green',
  SKIPPED: 'default',
};

/* ============ 流转事件中文（FlowTransitionEventEnum；事件非状态枚举，无字典，静态） ============ */
export const EVENT_TEXT: Record<string, { label: string; timeDriven: boolean }> = {
  SUBMIT_APPROVAL: { label: '提交审批', timeDriven: false },
  APPROVE_PASS: { label: '审批通过', timeDriven: false },
  APPROVE_REJECT: { label: '审批驳回', timeDriven: false },
  PUBLISH_TENDER_NOTICE: { label: '发布招标公告', timeDriven: false },
  BIDDING_STARTED: { label: '到达投标开始时间', timeDriven: true },
  BIDDING_ENDED: { label: '投标截止', timeDriven: true },
  BID_OPENED: { label: '开标完成', timeDriven: false },
  EVALUATION_FINISHED: { label: '评标完成', timeDriven: false },
  PUBLISH_CANDIDATE_PUBLICITY: { label: '发布中标候选人公示', timeDriven: false },
  PUBLISH_RESULT_NOTICE: { label: '发布中标结果公告', timeDriven: false },
  ISSUE_AWARD_NOTICE: { label: '发出中标通知书', timeDriven: false },
  CONTRACT_SIGNED: { label: '合同签订并归档', timeDriven: false },
  OBJECTION_RECONSIDER: { label: '异议成立组织复核', timeDriven: false },
  TERMINATE: { label: '终止/流标', timeDriven: false },
  CANCEL: { label: '取消', timeDriven: false },
  WITHDRAW: { label: '撤回', timeDriven: false },
  EXPIRE: { label: '过期', timeDriven: true },
};

/** 事件中文（找不到回原码）；timeDriven 事件加「（时间驱动）」 */
export function eventTextOf(event?: null | string): string {
  if (!event) return '—';
  const it = EVENT_TEXT[event];
  if (!it) return event;
  return it.timeDriven ? it.label + '（时间驱动）' : it.label;
}

/** 政府审批动作（GovernmentApprovalActionEnum） */
export const APPROVAL_ACTION_TEXT: Record<string, string> = {
  APPROVE: '通过',
  REJECT: '驳回',
};

/** 政府审批对象类型（GovernmentApprovalBizTypeEnum：项目立项/招标方案/终止/公告审核 四类审批点） */
export const APPROVAL_BIZ_TYPE_TEXT: Record<string, string> = {
  PROJECT: '项目立项审批',
  TENDER_PROJECT: '招标方案审批',
  TERMINATION: '终止/流标审批',
  ANNOUNCEMENT: '公告审核',
};
