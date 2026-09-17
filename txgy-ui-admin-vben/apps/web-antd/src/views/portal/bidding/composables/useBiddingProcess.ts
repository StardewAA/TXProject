/**
 * 招投标前台 · 投标流程本地状态机（前端为主，2026-09-15 用户裁定）
 *
 * 职责：为「投标流程管理 / 标书制作 / 合规审查」三个页面提供**共享**的流程数据与状态流转。
 * 模块级单例：三个页面同时引用同一份 tracks，切页不丢内存状态；变更写回 localStorage 持久化。
 *
 * 状态机（恢复资质审核，对齐《投标流程跟踪功能设计方案.md》§1.3）：
 *   意向投标(INTENTION) --报名投标(REGISTER)--> 资质审核(QUALIFICATION_REVIEW)
 *      --审核通过(APPROVE)--> 待投标(PENDING_BID) --上传标书(UPLOAD_DOCUMENT)--> 已投标(BIDDED)
 *      --审核不通过(REJECT)/取消(CANCEL)--> 流程结束(TERMINATED)
 *
 * 「关注自动触发意向投标」= syncFromFollow()：拉「我的关注」为新关注项补建 INTENTION 流程。
 * 后端状态机尚未实现，阶段仅本地维护（localStorage 按 租户:用户 隔离），正式接口上线后以服务端为准。
 */
import type { FollowItem } from '../types';

import { ref } from 'vue';

import { useAccessStore, useUserStore } from '@vben/stores';

import { fetchMyFollowItems } from '../api';

export type ProcessState = 'BIDDED' | 'INTENTION' | 'PENDING_BID' | 'QUALIFICATION_REVIEW' | 'TERMINATED';
export type ProcessEvent = 'APPROVE' | 'CANCEL' | 'REGISTER' | 'REJECT' | 'UPLOAD_DOCUMENT';

export interface PortalEvent {
  desc?: string;
  eventType: string;
  occurredAt: number;
  toState: string;
}

export interface PortalTrack {
  id: string;
  followKey?: string;
  followType?: string;
  projectName: string;
  issuerName?: string;
  currentState: ProcessState;
  intentionTime?: number;
  registerTime?: number;
  reviewTime?: number;
  reviewResult?: 'APPROVED' | 'REJECTED';
  reviewReason?: string;
  pendingBidTime?: number;
  biddedTime?: number;
  terminatedTime?: number;
  documentId?: number | string;
  documentName?: string;
  documentSize?: number;
  /** 合规审查结论（前端本地标记；后端合规接口未上线） */
  complianceStatus?: 'PASS' | 'PENDING' | 'REJECT';
  complianceRemark?: string;
  complianceAt?: number;
  cancelReason?: string;
  events: PortalEvent[];
}

export const STATE_TEXT: Record<string, string> = {
  INTENTION: '意向投标',
  QUALIFICATION_REVIEW: '资质审核',
  PENDING_BID: '待投标',
  BIDDED: '已投标',
  TERMINATED: '流程结束',
};
export const STATE_COLOR: Record<string, string> = {
  INTENTION: 'blue',
  QUALIFICATION_REVIEW: 'orange',
  PENDING_BID: 'purple',
  BIDDED: 'green',
  TERMINATED: 'default',
};
export const EVENT_TEXT: Record<string, string> = {
  CREATE: '关注建档（意向投标）',
  REGISTER: '报名投标',
  APPROVE: '资质审核通过',
  REJECT: '资质审核不通过',
  UPLOAD_DOCUMENT: '上传标书',
  CANCEL: '取消投标',
};
export const COMPLIANCE_TEXT: Record<string, string> = {
  PENDING: '待审查',
  PASS: '审查通过',
  REJECT: '审查不通过',
};

/** 状态转移表（事件 → 目标状态） */
const TRANSITIONS: Record<string, Partial<Record<ProcessEvent, ProcessState>>> = {
  INTENTION: { REGISTER: 'QUALIFICATION_REVIEW', CANCEL: 'TERMINATED' },
  QUALIFICATION_REVIEW: { APPROVE: 'PENDING_BID', REJECT: 'TERMINATED', CANCEL: 'TERMINATED' },
  PENDING_BID: { UPLOAD_DOCUMENT: 'BIDDED', CANCEL: 'TERMINATED' },
};

const LS_KEY_BASE = 'txgy.bidding.portalProcess.v1';
function lsKey(): string {
  const accessStore = useAccessStore();
  const userStore = useUserStore();
  const tenant = accessStore.tenantId ?? 'anon';
  const userId = userStore.userInfo?.userId ?? 'anon';
  return LS_KEY_BASE + '.' + tenant + ':' + userId;
}

/* ============ 模块级单例状态 ============ */
const tracks = ref<PortalTrack[]>([]);
let loaded = false;
let syncing = false;
let synced = false;

function load(): void {
  if (loaded) return;
  loaded = true;
  try {
    tracks.value = JSON.parse(localStorage.getItem(lsKey()) || '[]') as PortalTrack[];
  } catch {
    tracks.value = [];
  }
}
function save(): void {
  localStorage.setItem(lsKey(), JSON.stringify(tracks.value));
}
function pushEvent(t: PortalTrack, eventType: string, toState: string, desc?: string): void {
  if (!Array.isArray(t.events)) t.events = [];
  t.events.unshift({ eventType, toState, occurredAt: Date.now(), desc });
}

/** 关注自动建档：为新关注项补建「意向投标」流程（关注项目自动触发意向投标）。 */
async function syncFromFollow(): Promise<void> {
  if (syncing) return;
  syncing = true;
  try {
    const res = await fetchMyFollowItems({ pageNo: 1, pageSize: 100 });
    const list = (res.list || []) as FollowItem[];
    let added = 0;
    for (const f of list) {
      const key = String(f.followKey ?? f.id ?? '');
      if (!key) continue;
      if (tracks.value.some((t) => t.followKey === key)) continue;
      const now = Date.now();
      const t: PortalTrack = {
        id: 'PT' + String(now) + String(Math.floor(Math.random() * 1e4)),
        followKey: key,
        followType: f.followType ?? undefined,
        projectName: f.displayName || key,
        issuerName: f.issuerName ?? undefined,
        currentState: 'INTENTION',
        intentionTime: now,
        events: [],
      };
      pushEvent(t, 'CREATE', 'INTENTION', '关注项目，自动进入意向投标');
      tracks.value.unshift(t);
      added += 1;
    }
    if (added > 0) save();
  } catch {
    /* 关注拉取失败静默：已有本地流程照常展示 */
  } finally {
    syncing = false;
  }
}

export interface AdvanceOpts {
  cancelReason?: string;
  docId?: number | string;
  docName?: string;
  docSize?: number;
  reviewReason?: string;
  reviewResult?: 'APPROVED' | 'REJECTED';
}

function advance(t: PortalTrack, ev: ProcessEvent, opts: AdvanceOpts = {}): { error?: string; ok: boolean; to?: ProcessState } {
  const cur = t.currentState;
  const to = TRANSITIONS[cur]?.[ev];
  if (!to) return { ok: false, error: `当前状态（${STATE_TEXT[cur]}）不允许该操作` };
  const now = Date.now();
  t.currentState = to;
  if (ev === 'REGISTER') t.registerTime = now;
  if (ev === 'APPROVE' || ev === 'REJECT') {
    t.reviewTime = now;
    t.reviewResult = opts.reviewResult;
    t.reviewReason = opts.reviewReason;
  }
  if (ev === 'UPLOAD_DOCUMENT') {
    t.biddedTime = now;
    t.documentId = opts.docId;
    t.documentName = opts.docName;
    t.documentSize = opts.docSize;
  }
  if (to === 'TERMINATED') {
    t.terminatedTime = now;
    t.cancelReason = opts.cancelReason;
  }
  pushEvent(t, ev, to, opts.cancelReason ?? opts.reviewReason);
  save();
  return { ok: true, to };
}

/** 合规审查结论标记（本地；对已投标流程的标书做核验） */
function setCompliance(t: PortalTrack, status: 'PASS' | 'REJECT', remark?: string): void {
  t.complianceStatus = status;
  t.complianceRemark = remark;
  t.complianceAt = Date.now();
  save();
}

export function useBiddingProcess() {
  load();
  // 首次进入任一流程页时自动拉一次「我的关注」补建意向（幂等；后续可在页面手动点“同步关注”刷新）
  if (!synced) {
    synced = true;
    void syncFromFollow();
  }
  return { tracks, advance, save, setCompliance, syncFromFollow };
}
