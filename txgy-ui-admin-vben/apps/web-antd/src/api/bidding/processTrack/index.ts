import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

/** 契约来源：投标流程跟踪-前后端契约-2026-09-07（父仓，后端状态机待实现，v1 本地演示）+ 09-投标流程跟踪前端交付-业务口径说明；Track/TrackEvent/StateCode/EventCode 为前端本地模型，真实接口仅 fetchMyFollowPage（关注域，见 06 对照 §5）。
 *
 * 【2026-09-20 更新】后端状态机**已落地**（bidding_process_track / bidding_process_event 两张表 + 7 个端点），
 * 本文件按实际端点补齐封装：建档(create)/报名(register)/审核(review)/递交(upload)/取消(cancel)/我的(my)/总览(page)。
 * 【2026-09-28 追加】管理端总览另有 stats/options/detail 三个端点（09-18 契约 §5 接口 2/3/4），一并封装：
 * 统计卡走 stats（勿在前端对当前页 list 做 reduce，那样只反映当前页）、候选下拉走 options、
 * 行详情走 detail（含事件史 events[]，只读）。
 * 管理端 processTrack 页与门户「我的投标流程」页均改用真实接口，demoData / localStorage 退出。
 *
 * 【业务口径（用户裁定，务必勿混）】「关注」与「投标意向」是两个独立动作：
 *   · 关注：轻量、可撤销 —— 取关即从关注列表消失（bidding_follow_item，软删保留历史）；
 *   · 意向投标：正式建档 —— 由用户**显式点击**触发 create，建档后记录持久保留（仅做状态流转，永不删）；
 *   · 取关**不影响**已建立的投标流程档案。 */
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

  /** 我的关注行（后端 notice-follow 真实接口，作为“意向建档”数据源）  * 契约来源：FollowItemRespVO
   */
  export interface FollowItem {
    id?: number | string;
    followType?: string;
    followKey?: string;
    displayName?: string;
    issuerName?: string;
    stageCode?: string;
    stageName?: string;
    followTime?: string;
    /** 关注对象基础信息快照 JSON
   *  契约来源：FollowItemRespVO——本轮仅补类型，未接 UI（避免"页面拿不到数据"与"没做界面"被混为一谈）。 */
  baseJson?: string;
  /** 最近动态时间（LocalDateTime → epoch 毫秒）
   *  契约来源：FollowItemRespVO——本轮仅补类型，未接 UI（避免"页面拿不到数据"与"没做界面"被混为一谈）。 */
  latestDynamicTime?: null | number | string;
  /** 未读动态条数
   *  契约来源：FollowItemRespVO——本轮仅补类型，未接 UI（避免"页面拿不到数据"与"没做界面"被混为一谈）。 */
  unreadCount?: number;
  /** 公告状态（关注对象为公告时下发）
   *  契约来源：FollowItemRespVO——本轮仅补类型，未接 UI（避免"页面拿不到数据"与"没做界面"被混为一谈）。 */
  noticeStatus?: string;
  /** 对象是否仍可用/可访问
   *  契约来源：FollowItemRespVO——本轮仅补类型，未接 UI（避免"页面拿不到数据"与"没做界面"被混为一谈）。 */
  available?: boolean;
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
    /** 创建时间（epoch 毫秒；后端 LocalDateTime → 数字，类型兼容 number|string） */
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

// ==================== 投标流程跟踪（bidding_process_track，7 个端点；管理端总览另见文末 stats/options/detail） ====================

/** 投标流程跟踪行（契约来源：ProcessTrackItemVO；雪花 ID 与用户 ID 一律字符串传输） */
export interface ProcessTrackItem {
  id: string;
  tenantId?: string;
  tenantName?: string;
  /** 关联关注行ID（意向来源，可追溯；关注与流程的关联点，非依赖） */
  followItemId?: string;
  followKey?: string;
  followType?: string;
  projectName?: string;
  projectCode?: string;
  issuerName?: string;
  region?: string;
  amount?: string;
  /** 投标截止（epoch 毫秒） */
  deadline?: null | number;
  state?: ProcessTrackApi.StateCode | string;
  /** 阶段中文名（前端优先取字典，回退用此列） */
  stateName?: string;
  ownerUserId?: string;
  ownerName?: string;
  ownerPost?: string;
  ownerPhone?: string;
  intentionTime?: null | number | string;
  registerTime?: null | number | string;
  reviewTime?: null | number | string;
  pendingBidTime?: null | number | string;
  biddedTime?: null | number | string;
  terminatedTime?: null | number | string;
  reviewResult?: string;
  reviewReason?: string;
  documentFileId?: string;
  documentName?: string;
  documentSize?: number;
  documentHash?: string;
  /** 投标文件集附件清单（契约多文件化 R2：bizType=BID_TRACK&bizCode=trackId，可为空数组） */
  attachments?: any[];
  /** 备注（契约来源：ProcessTrackItemVO.remark） */
  remark?: string;
}

/** 单条流程出参（契约来源：**ProcessTrackRespVO**，app 端 /my、/get 及 register/review/upload 的返回体）
 *  ⚠️ 与列表行 ProcessTrackItem（ProcessTrackItemVO，/page）**不是同一个 VO**：本 VO 用 currentState。
 *  2026-09-20 已为门户补齐 followItemId/followKey/followType/intentionTime/reviewTime/pendingBidTime/documentFileId
 *  （控制器走 BeanUtils.toBean(DO→VO)，同名字段自动填充，无需改 Service）——**需后端重启后生效**；
 *  重启前这些字段回传 undefined，前端已按空值降级，不会报错。 */
export interface ProcessTrackResp {
  id: number | string;
  /** 标段编号（建档时即关注键 followKey，门户据此与关注列表对账） */
  bidSectionCode?: string;
  tenderProjectCode?: string;
  projectName?: string;
  issuerName?: string;
  /** 当前状态：INTENTION/QUALIFICATION_REVIEW/PENDING_BID/BIDDED/TERMINATED */
  currentState?: string;
  followItemId?: null | number | string;
  /** 维度代码（标段编号）；建档时即关注键 */
  followKey?: string;
  /** 维度类型（PROJECT/SECTION） */
  followType?: string;
  intentionTime?: null | number | string;
  registerTime?: null | number | string;
  reviewTime?: null | number | string;
  pendingBidTime?: null | number | string;
  documentFileId?: null | number | string;
  reviewResult?: string;
  reviewReason?: string;
  documentName?: string;
  documentSize?: number;
  documentHash?: string;
  documentHashAlgo?: string;
  documentSignature?: string;
  caCertNo?: string;
  biddedTime?: null | number | string;
  terminatedTime?: null | number | string;
  remark?: string;
}

/** 建档（意向投标）入参（契约来源：ProcessTrackCreateReqVO，app 端） */
export interface ProcessTrackCreateParams {
  userId: number | string;
  bidSectionCode: string;
  tenderProjectCode?: string;
  projectName?: string;
  issuerName?: string;
}

/** 报名 / 取消入参 */
export interface ProcessTrackIdParams {
  id: number | string;
  userId: number | string;
}

/** 递交投标文件入参（契约来源：ProcessTrackUploadReqVO；CA 加密 + SM3 指纹 + SM2 签名由外部 CA SDK 完成） */
export interface ProcessTrackUploadParams extends ProcessTrackIdParams {
  documentFileId: number | string;
  documentName?: string;
  documentSize: number;
  documentHash?: string;
  documentHashAlgo?: string;
  documentSignature?: string;
  caCertNo?: string;
  caPublicKey?: string;
}

/** 审核入参（契约来源：ProcessTrackReviewReqVO，平台方） */
export interface ProcessTrackReviewParams {
  id: number | string;
  reviewResult: 'APPROVED' | 'REJECTED' | string;
  reviewReason?: string;
  operatorId?: number | string;
}

/** 总览分页入参（契约来源：ProcessTrackPageReqVO）
 *  bidSectionCode 为 2026-10-02 契约 R5：标段详情页按标段聚合投标记录（后端已落地，见契约多文件化文档） */
export interface ProcessTrackPageParams extends PageParam {
  keyword?: string;
  state?: string;
  tenantId?: number | string;
  /** 标段编号（精确匹配；标段详情页按标段聚合投标记录） */
  bidSectionCode?: string;
  ownerUserId?: number | string;
}

/** 契约：POST /bidding/process-track/create —— **意向投标建档**（每用户每标段一条，幂等）
 *  触发时机：用户在「我的关注」或项目详情**显式点击「意向投标」**（不是关注时自动调用——关注与意向是两个独立动作）；权限 bidding:process-track:create */
export function createProcessTrack(data: ProcessTrackCreateParams) {
  return requestClient.post<number | string>('/bidding/process-track/create', data);
}

/** 契约：POST /bidding/process-track/register —— 报名投标（INTENTION→QUALIFICATION_REVIEW）；权限 bidding:process-track:register */
export function registerProcessTrack(data: ProcessTrackIdParams) {
  return requestClient.post<ProcessTrackResp>('/bidding/process-track/register', data);
}

/** 契约：POST /bidding/process-track/review —— 资质审核（平台方；QUALIFICATION_REVIEW→PENDING_BID 或 TERMINATED）；权限 bidding:process-track:review */
export function reviewProcessTrack(data: ProcessTrackReviewParams) {
  return requestClient.post<ProcessTrackResp>('/bidding/process-track/review', data);
}

/** 契约：POST /bidding/process-track/upload —— 递交加密投标文件（PENDING_BID→BIDDED；截止后禁止）；权限 bidding:process-track:upload */
export function uploadProcessTrackDocument(data: ProcessTrackUploadParams) {
  return requestClient.post<ProcessTrackResp>('/bidding/process-track/upload', data);
}

/** 契约：POST /bidding/process-track/cancel?id=&userId= —— 取消投标（非终态→TERMINATED，**档案保留**）；权限 bidding:process-track:cancel */
export function cancelProcessTrack(id: number | string, userId: number | string) {
  return requestClient.post<boolean>('/bidding/process-track/cancel', null, { params: { id, userId } });
}

/** 契约：GET /bidding/process-track/my?userId= —— **我的投标流程**（已建档的流程，与关注列表区分）；权限 bidding:process-track:query */
export function fetchMyProcessTracks(userId: number | string) {
  return requestClient.get<ProcessTrackResp[]>('/bidding/process-track/my', { params: { userId } });
}

/** 契约：GET /bidding/process-track/page —— 流程总览分页（超管跨租户；租户视角只看本公司）；权限 bidding:process-track:query */
export function fetchProcessTrackPage(params: ProcessTrackPageParams) {
  return requestClient.get<PageResult<ProcessTrackItem>>('/bidding/process-track/page', { params });
}

// ==================== 管理端总览补充（09-18 契约 §5 接口 2/3/4：stats / options / detail） ====================

/** KPI 统计（契约来源：ProcessTrackStatsRespVO；ongoing = 意向投标 + 资质审核 + 待投标 三态之和） */
export interface ProcessTrackStats {
  /** 公司数（租户数） */
  tenantCount?: number;
  /** 项目总数（跟踪行数） */
  projectCount?: number;
  /** 进行中（意向 + 审核 + 待投标） */
  ongoing?: number;
  bidded?: number;
  terminated?: number;
  /** 负责人数（去重） */
  ownerCount?: number;
  /** 按阶段分布 {INTENTION: n, ...} */
  byState?: Record<string, number>;
}

/** 候选形状（契约来源：ProcessTrackOptionsRespVO.Option，同 doc 24 OptionItemRespVO） */
export interface ProcessTrackOption {
  /** 值（String；负责人=ownerUserId、公司=tenantId） */
  value?: string;
  label?: string;
  /** 附加说明（公司名，用于跨租户同名区分） */
  extra?: string;
}

/** 筛选项（契约来源：ProcessTrackOptionsRespVO；tenants 仅超管下发） */
export interface ProcessTrackOptions {
  owners?: ProcessTrackOption[];
  tenants?: ProcessTrackOption[];
}

/** 事件史项（契约来源：ProcessTrackEventVO；occurredAt 为 LocalDateTime → 兼容 number|string） */
export interface ProcessTrackEvent {
  /** 事件类型（CREATE/REGISTER/APPROVE/REJECT/UPLOAD_DOCUMENT/CANCEL） */
  eventType?: string;
  /** 事件中文名（前端优先用字典，回退用此列） */
  eventName?: string;
  fromState?: string;
  toState?: string;
  /** 操作人名称快照（RPC 降级时为空） */
  operatorName?: string;
  operationDesc?: string;
  occurredAt?: null | number | string;
}

/** 详情（契约来源：ProcessTrackDetailRespVO；= 总览项 + 事件史，只读） */
export interface ProcessTrackDetail {
  item?: ProcessTrackItem;
  /** 操作记录事件史（后端按时间升序下发） */
  events?: ProcessTrackEvent[];
}

/** 契约：GET /bidding/process-track/stats —— KPI 统计（公司数/项目数/进行中/已投标/已结束/负责人数/按阶段分布；超管跨租户、租户仅本公司）；权限 bidding:process-track:query */
export function fetchProcessTrackStats() {
  return requestClient.get<ProcessTrackStats>('/bidding/process-track/stats');
}

/** 契约：GET /bidding/process-track/options —— 筛选项（负责人候选全量去重；公司候选仅超管下发）；权限 bidding:process-track:query */
export function fetchProcessTrackOptions() {
  return requestClient.get<ProcessTrackOptions>('/bidding/process-track/options');
}

/** 契约：GET /bidding/process-track/detail?id= —— 详情（总览项 + 事件史 events[]，只读；越权访问由后端按租户拦截返回不存在）；权限 bidding:process-track:query */
export function fetchProcessTrackDetail(id: number | string) {
  return requestClient.get<ProcessTrackDetail>('/bidding/process-track/detail', { params: { id } });
}
