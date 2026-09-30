import type { PageParam } from '@vben/request';

import { requestClient } from '#/api/request';

/** 契约来源：txgy-module-bidding flow 域（2026-09-20-flow-domain / 2026-09-21-flow-transition / 2026-09-23-flow-timer SQL + FlowTransitionController/GovernmentApprovalController）。
 *  流转实体 /bidding/flow/*（权限：bidding:flow:query|transition|approval，菜单 6800 招标全流程 下 6803/6802/6801）。
 *  时间口径：LocalDateTime → epoch 毫秒（平台统一序列化，前端 dayjs(Number(v))）。
 *  流转语义：状态变更一律走动作方法（transition），不在业务代码直接 setStatus；
 *    timeDriven 事件（BIDDING_STARTED/BIDDING_ENDED/EXPIRE）由定时任务触发，手动执行受 manual-advance-enabled 控制（PROD 默认禁）。
 *  法规依据：《电子招标投标办法》（八部委令 20 号）第三章电子招标 第十七~二十二条（公告/文件加载、澄清修改醒目告知）、
 *    第五章电子开标评标中标 第二十九~三十六条（按时公开开标、评标报告、候选人公示、中标通知书与合同签订）。 */
export namespace FlowApi {
  /** 流转实体类型（FlowBizTypeEnum）—— 前端只在下拉中选择，不允许手输 */
  export type BizType = 'PROJECT' | 'TENDER_PROJECT' | 'BID_SECTION';

  /** 流转请求（FlowTransitionReqVO：bizType + bizCode + event + remark；无 id 直填） */
  export interface TransitionReq {
    bizType: BizType;
    bizCode: string;
    event: string;
    remark?: string;
  }
  /** 流转结果（FlowTransitionRespVO） */
  export interface TransitionResp {
    bizType?: string;
    bizCode?: string;
    fromState?: string;
    toState?: string;
    event?: string;
    remark?: string;
  }

  /** 可执行动作（FlowNextActionRespVO：event 事件 code + label 中文名 + toState 目标状态） */
  export interface NextAction {
    event: string;
    label: string;
    toState: string;
  }

  /** 时间驱动调度记录（FlowTimerRespVO） */
  export interface TimerRecord {
    id?: number;
    bizType?: string;
    bizCode?: string;
    event?: string;
    /** 触发时间（epoch 毫秒） */
    triggerTime?: null | number | string;
    status?: string;
    /** 实际执行时间（epoch 毫秒） */
    executedTime?: null | number | string;
    remark?: string;
  }

  /** 流转留痕（FlowLogRespVO） */
  export interface LogRecord {
    bizType?: string;
    bizCode?: string;
    fromState?: string;
    toState?: string;
    event?: string;
    operator?: string;
    operatorIp?: string;
    remark?: string;
    /** 流转时间（epoch 毫秒） */
    createTime?: null | number | string;
  }

  /** 政府审批回调（GovernmentApprovalReqVO；预留 stub） */
  export interface ApprovalReq {
    bizType: string;
    bizId: string;
    action: 'APPROVE' | 'REJECT';
    operator?: string;
    remark?: string;
  }
  /** 政府审批回调结果（GovernmentApprovalRespVO） */
  export interface ApprovalResp {
    approved?: boolean;
    approvalNo?: string;
    approvedTime?: string;
    message?: string;
    stub?: boolean;
  }

  /** 实体选择项（下拉候选：项目/招标项目/标段 列表行的统一投影） */
  export interface EntityOption {
    bizType: BizType;
    bizCode: string;
    label: string;
    sub?: string;
    /** 台账管理态的 state（用于无流转记录时展示当前状态兜底） */
    state?: string;
  }

  /* ================= 状态字典（契约 26 §8 / 28 §八-7：四类状态字典已登记 bidding_dict_data，前端禁止硬编码枚举） ================= */
  /** 字典标签色 code（DictItemRespVO.tagType；antd Tag 色映射见 labels.ts TAG_COLOR） */
  export type DictTagType = 'default' | 'error' | 'info' | 'success' | 'warning';
  /** 字典项（DictItemRespVO：value 提交值 / label 中文名 / tagType 标签色 / terminal 是否终态） */
  export interface DictItem {
    value: string;
    label: string;
    tagType: DictTagType;
    terminal: boolean;
    sort: number;
  }
  /** key = 字典类型（project_status / tender_project_status / bid_section_status / announcement_status …） */
  export type DictMap = Record<string, DictItem[]>;
}

/** 查询当前状态可执行动作（GET /bidding/flow/next-actions；权限 bidding:flow:query） */
export function getFlowNextActions(params: { bizType: FlowApi.BizType; bizCode: string }) {
  return requestClient.get<FlowApi.NextAction[]>('/bidding/flow/next-actions', { params });
}

/** 状态流转执行（POST /bidding/flow/transition；权限 bidding:flow:transition；幂等） */
export function flowTransition(data: FlowApi.TransitionReq) {
  return requestClient.post<FlowApi.TransitionResp>('/bidding/flow/transition', data);
}

/** 查询时间驱动调度记录（GET /bidding/flow/timers；权限 bidding:flow:query） */
export function getFlowTimers(params: { bizType: FlowApi.BizType; bizCode: string }) {
  return requestClient.get<FlowApi.TimerRecord[]>('/bidding/flow/timers', { params });
}

/** 查询流转留痕（GET /bidding/flow/logs；权限 bidding:flow:query） */
export function getFlowLogs(params: { bizType: FlowApi.BizType; bizCode: string }) {
  return requestClient.get<FlowApi.LogRecord[]>('/bidding/flow/logs', { params });
}

/** 政府审批回调（POST /bidding/flow/approval；权限 bidding:flow:approval；预留 stub） */
export function flowApproval(data: FlowApi.ApprovalReq) {
  return requestClient.post<FlowApi.ApprovalResp>('/bidding/flow/approval', data);
}

/** 状态字典（GET /bidding/meta/dicts；契约 26 §8 四类状态字典 project_status/tender_project_status/bid_section_status/announcement_status。
 *  静默失败：字典不可用时由调用方降级为静态中文兜底（labels.ts CONTRACT_STATUS_TEXT），不弹全局错误、不白屏）。 */
export function getFlowDicts(types?: string) {
  return requestClient.get<FlowApi.DictMap>('/bidding/meta/dicts', {
    params: { types },
    headers: { 'X-Silent-Error': 'true' },
  });
}

/** 分页参数复用导出（供实体列表下拉候选分页使用） */
export type { PageParam };
