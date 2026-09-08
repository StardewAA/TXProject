import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace ProcessTrackApi {
  /** 流程状态（契约 §1） */
  export type StateCode =
    | 'INTENTION'
    | 'QUALIFICATION_REVIEW'
    | 'PENDING_BID'
    | 'BIDDED'
    | 'TERMINATED';
  export type EventCode =
    | 'CREATE'
    | 'REGISTER'
    | 'APPROVE'
    | 'REJECT'
    | 'UPLOAD_DOCUMENT'
    | 'CANCEL';

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

/** 我的关注列表（真实接口，作为意向建档数据源；失败可降级手填） */
export function fetchMyFollowPage(params: ProcessTrackApi.FollowPageParams) {
  return requestClient.get<PageResult<ProcessTrackApi.FollowItem>>('/bidding/notice-follow/item/page', { params });
}
