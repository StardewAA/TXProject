import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

/** 契约来源：投标流程跟踪-前后端契约-2026-09-07（父仓，后端状态机待实现，v1 本地演示）+ 09-投标流程跟踪前端交付-业务口径说明；Track/TrackEvent/StateCode/EventCode 为前端本地模型，真实接口仅 fetchMyFollowPage（关注域，见 06 对照 §5）。 */
export namespace ProcessTrackApi {
  /** 流程状态（契约 §1） */
  export type StateCode =
    | 'BIDDED'
    | 'INTENTION'
    | 'PENDING_BID'
    | 'QUALIFICATION_REVIEW'
    | 'TERMINATED';
  export type EventCode =
    | 'APPROVE'
    | 'CANCEL'
    | 'CREATE'
    | 'REGISTER'
    | 'REJECT'
    | 'UPLOAD_DOCUMENT';

  /** 我的关注行（后端 notice-follow 真实接口，作为“意向建档”数据源） */
  export interface FollowItem {
    id?: number | string;
    followType?: string;
    followKey?: string;
    displayName?: string;
    issuerName?: string;
    stageCode?: string;
    stageName?: string;
    followTime?: string;
  }

  /** 页面行/本地跟踪（契约 §3 未来字段对齐） */
  export interface Track {
    id: string;
    currentState: StateCode;
    previousState?: StateCode;
    projectName: string;
    projectCode?: string;
    issuerName?: string;
    followItemId?: number | string;
    followKey?: string;
    followType?: string;
    reviewResult?: string;
    reviewReason?: string;
    documentName?: string;
    documentSize?: number;
    remark?: string;
    intentionTime?: number;
    registerTime?: number;
    reviewTime?: number;
    pendingBidTime?: number;
    biddedTime?: number;
    terminatedTime?: number;
    events: TrackEvent[];
    createTime: number;
  }

  export interface TrackEvent {
    id: string;
    eventType: EventCode;
    fromState?: StateCode;
    toState: StateCode;
    operatorName?: string;
    operationDesc?: string;
    occurredAt: number;
  }

  export interface FollowPageParams extends PageParam {
    followType?: string;
  }
}

/** 契约：GET /bidding/notice-follow/item/page —— 我的关注分页（后端按当前登录人隔离，作为意向建档数据源，失败可降级手填）；权限 bidding:notice-external:query；依据 04-全链路 §3.3 / 投标流程跟踪-前后端契约 §4 */
export function fetchMyFollowPage(params: ProcessTrackApi.FollowPageParams) {
  return requestClient.get<PageResult<ProcessTrackApi.FollowItem>>('/bidding/notice-follow/item/page', { params });
}
