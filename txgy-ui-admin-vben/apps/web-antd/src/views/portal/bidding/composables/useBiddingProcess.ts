import type { FollowItem } from '../types';

/**
 * 招投标前台 · 投标流程状态机（**后端为准**，2026-09-20 从 localStorage 切换）
 *
 * 职责：为「投标流程管理 / 标书制作」页面提供**共享**的流程数据与状态流转。
 * 模块级单例：页面同时引用同一份 tracks，切页不丢内存状态。
 *
 * 【数据源】bidding_process_track（服务端权威，本文件不再读写 localStorage）
 *   读：GET  /bidding/process-track/my?userId=   → 我的投标流程（= 已建档记录）
 *   写：POST /bidding/process-track/{create,register,review,upload,cancel}
 *   每次写成功后**重新拉取**，以服务端状态为准（本地不做乐观流转，避免与服务端分叉）。
 *   权限：bidding:process-track:{query,create,register,review,upload,cancel}
 *
 * 【状态机（对齐 BiddingProcessStateEnum）】
 *   意向投标(INTENTION) --报名投标(REGISTER)--> 资质审核(QUALIFICATION_REVIEW)
 *     --审核通过(APPROVE)--> 待投标(PENDING_BID) --递交标书(UPLOAD_DOCUMENT)--> 已投标(BIDDED)
 *     --审核不通过(REJECT) / 取消(CANCEL)--> 流程结束(TERMINATED)
 *
 * 【关注 ≠ 投标意向（用户裁定，务必勿混）——本文件的核心口径】
 *   · 关注（follows，数据源 notice-follow）：**轻量、可撤销**。取关后该行从关注列表消失，仅此而已；
 *   · 意向投标（tracks，数据源 process-track）：**正式建档**，由用户在关注列表显式点击「意向投标」触发，
 *     建档后持久保留，只做状态流转、**永不删除**；
 *   · 取关**不会**影响已建立的投标流程档案（unfollow 只调 notice-follow，绝不触碰 tracks）。
 *
 * 【出参说明】/my 返回 ProcessTrackRespVO，2026-09-20 已补齐 intentionTime / reviewTime / pendingBidTime /
 *   documentFileId / followKey / followType（见 ProcessTrackRespVO），**需后端重启后生效**；
 *   重启前这些字段为 undefined，映射时按空值降级（时间线少节点、标书需重新上传），不报错。
 */
import type { ProcessTrackResp } from '#/api/bidding/processTrack';

import { ref } from 'vue';

import { useUserStore } from '@vben/stores';

import {
  cancelProcessTrack,
  createProcessTrack,
  fetchMyProcessTracks,
  registerProcessTrack,
  reviewProcessTrack,
  uploadProcessTrackDocument,
} from '#/api/bidding/processTrack';
import { fetchFileBlob } from '#/utils/fetchFile';

import { fetchMyFollowItems, unfollowTarget } from '../api';

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
  /** 标书 fileId（服务端 documentFileId 回填；老后端未下发时为本次会话暂存值） */
  documentId?: number | string;
  /** 交付截止时间（2026-10-02 契约：ProcessTrackRespVO.deadline；截止前可多次交付，过期后禁止） */
  deadline?: number;
  /** 已上传原文件（浏览器 File；CA 盖章需 multipart 原文件，刷新后丢失需重传） */
  uploadFile?: File;
  /** 原文件 sha256（上传响应带回，作签名摘要占位；真实文件 SM3 由平台文件服务计算） */
  uploadSha256?: string;
  /** 原文件访问地址（CA 认证工作台预览/回取用） */
  uploadUrl?: string;
  documentName?: string;
  documentSize?: number;
  /** 合规审查结论（前端本地标记；后端合规接口未上线） */
  complianceStatus?: 'PASS' | 'PENDING' | 'REJECT';
  complianceRemark?: string;
  complianceAt?: number;
  cancelReason?: string;
  events: PortalEvent[];
  /** CA 签章产物（待投标环节盖章后暂存；递交时作为签章证据链提交） */
  seal?: SealedDocument;
}

export const STATE_TEXT: Record<string, string> = {
  INTENTION: '意向投标',
  QUALIFICATION_REVIEW: '资质审核',
  PENDING_BID: '待投标',
  BIDDED: '已投标',
  TERMINATED: '流程结束',
};
// 注：曾在此导出 STATE_COLOR（状态→antd Tag 颜色）。2026-09-17 视觉改版后，状态一律用
// bid-ui.css 的实心标签类（bid-tag--blue/green/...），颜色不再由 JS 常量决定，故删除该导出。
export const EVENT_TEXT: Record<string, string> = {
  CREATE: '意向投标（建档）',
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

/** 状态转移表（事件 → 目标状态）；仅用于**本地前置校验**，服务端为最终权威 */
const TRANSITIONS: Record<string, Partial<Record<ProcessEvent, ProcessState>>> = {
  INTENTION: { REGISTER: 'QUALIFICATION_REVIEW', CANCEL: 'TERMINATED' },
  QUALIFICATION_REVIEW: { APPROVE: 'PENDING_BID', REJECT: 'TERMINATED', CANCEL: 'TERMINATED' },
  PENDING_BID: { UPLOAD_DOCUMENT: 'BIDDED', CANCEL: 'TERMINATED' },
  // 2026-10-02 用户口径「标书可多次交付，直到截止之前」：已投标（BIDDED）在截止前允许再次递交
  // （重新上传 → 再次交付，覆盖 documentFileId）；截止/过期由调用方按 deadline 拦，服务端为最终权威。
  BIDDED: { UPLOAD_DOCUMENT: 'BIDDED' },
};

/* ============ 模块级单例状态 ============ */
const tracks = ref<PortalTrack[]>([]);
const follows = ref<FollowItem[]>([]);
let loaded = false;
let loading = false;
let syncing = false;

/** 当前登录人 userId（后端按 userId 过滤，必传；未登录返回 undefined） */
function currentUserId(): number | string | undefined {
  const userStore = useUserStore();
  return userStore.userInfo?.userId ?? undefined;
}

/** 时间归一：后端 LocalDateTime 可能下发 epoch 数字或 'yyyy-MM-dd HH:mm:ss' 字符串 */
function toMs(v: null | number | string | undefined): number | undefined {
  if (v === null || v === undefined || v === '') return undefined;
  if (typeof v === 'number') return v;
  const t = Date.parse(String(v).replace(' ', 'T'));
  return Number.isNaN(t) ? undefined : t;
}

/** 状态归一：未知值一律降级为 INTENTION（避免模板取不到 STATE_TEXT 而显示空白） */
function normalizeState(s?: string): ProcessState {
  return s && s in STATE_TEXT ? (s as ProcessState) : 'INTENTION';
}

/** 服务端行 → 页面视图模型（ProcessTrackRespVO → PortalTrack） */
function toPortalTrack(r: ProcessTrackResp): PortalTrack {
  return {
    id: String(r.id),
    // followKey 优先取服务端下发值；老后端未下发时回退 bidSectionCode（建档时传的就是关注键，
    // 见 intentFromFollow），保证「关注列表排除已建档项」与取关目标同源一致。
    followKey: r.followKey ?? r.bidSectionCode ?? undefined,
    followType: r.followType ?? 'ANNOUNCEMENT',
    projectName: r.projectName || r.bidSectionCode || String(r.id),
    issuerName: r.issuerName ?? undefined,
    currentState: normalizeState(r.currentState),
    intentionTime: toMs(r.intentionTime),
    registerTime: toMs(r.registerTime),
    reviewTime: toMs(r.reviewTime),
    pendingBidTime: toMs(r.pendingBidTime),
    documentId: r.documentFileId ?? undefined,
    reviewResult:
      r.reviewResult === 'APPROVED' || r.reviewResult === 'REJECTED' ? r.reviewResult : undefined,
    reviewReason: r.reviewReason ?? undefined,
    documentName: r.documentName ?? undefined,
    documentSize: r.documentSize ?? undefined,
    biddedTime: toMs(r.biddedTime),
    deadline: toMs(r.deadline),
    terminatedTime: toMs(r.terminatedTime),
    // 事件时间线由后端 bidding_process_event 承载，但 /my 不下发 events[]；
    // 门户模板当前未渲染该数组，故留空（需要时改调管理端 /detail，见 api/bidding/processTrack）。
    events: [],
  };
}

/** 拉取「我的投标流程」并覆盖本地 tracks（保留本次会话内暂存但未提交的标书信息）。 */
async function refreshTracks(): Promise<void> {
  const userId = currentUserId();
  if (userId === undefined) {
    tracks.value = [];
    return;
  }
  const rows = (await fetchMyProcessTracks(userId)) || [];
  const staged = new Map(tracks.value.map((t) => [t.id, t]));
  tracks.value = rows.map((r) => {
    const mapped = toPortalTrack(r);
    const prev = staged.get(mapped.id);
    if (prev) {
      // 仅回填服务端未下发的本地暂存标书信息；服务端已下发的字段以本次响应为准
      if (mapped.documentId === undefined) mapped.documentId = prev.documentId;
      if (!mapped.documentName && prev.documentName) mapped.documentName = prev.documentName;
      if (mapped.documentSize === undefined && prev.documentSize !== undefined) {
        mapped.documentSize = prev.documentSize;
      }
      mapped.complianceStatus = prev.complianceStatus;
      mapped.complianceRemark = prev.complianceRemark;
      mapped.complianceAt = prev.complianceAt;
    }
    return mapped;
  });
}

/** 首次进入时加载（幂等；失败静默，由请求层统一提示） */
async function ensureLoaded(): Promise<void> {
  if (loaded || loading) return;
  loading = true;
  try {
    await refreshTracks();
    loaded = true;
  } catch {
    /* 拉取失败：保持空列表，requestClient 已统一弹错 */
  } finally {
    loading = false;
  }
}

function followKeyOf(f: FollowItem): string {
  return String(f.followKey ?? f.id ?? '');
}

/** 拉「我的关注」填充 follows（**排除已建档**的关注项——关注与投标意向是两件事，已建档的归 tracks）。 */
async function syncFromFollow(): Promise<void> {
  if (syncing) return;
  syncing = true;
  try {
    await ensureLoaded();
    const res = await fetchMyFollowItems({ pageNo: 1, pageSize: 100 });
    const list = (res.list || []) as FollowItem[];
    // 过滤一：公告级关注（ANNOUNCEMENT）已按用户口径**下线** —— 门户「关注」只保留「按编号/代码的项目跟踪」
    //   （PROJECT / TENDER_PROJECT / SECTION），公告详情页也不再创建该维度关注行；
    //   后端不会自动生成公告级行，这里只对存量行兜底剔除。
    // 过滤二：只保留「未进入流程」的关注项；TERMINATED（取消投标）不再占用 followKey，
    //   因而重新关注后会重新出现在「全部」里（取消投标时已连带取关，见 unfollowTrack）。
    follows.value = list.filter((f) => {
      if (f.followType === 'ANNOUNCEMENT') return false;
      const key = followKeyOf(f);
      return key && !tracks.value.some((t) => t.followKey === key && t.currentState !== 'TERMINATED');
    });
  } catch {
    /* 关注拉取失败静默：已有流程照常展示 */
  } finally {
    syncing = false;
  }
}

/**
 * 【意向投标】关注项 → 建档（INTENTION）。**必须由用户显式点击触发**，不在关注时自动调用。
 * 契约：POST /bidding/process-track/create（每用户每标段一条，服务端幂等）。
 * 注意：后端以 bidSectionCode 为业务键，此处传关注键（标段类关注即标段编号；
 *       公告类关注的关注键是公告 id，一期外部公告无标段数据源，按现状沿用）。
 */
async function intentFromFollow(f: FollowItem): Promise<{ error?: string; ok: boolean; track?: PortalTrack }> {
  const key = followKeyOf(f);
  if (!key) return { ok: false, error: '该关注项缺少业务键，无法建档' };
  const userId = currentUserId();
  if (userId === undefined) return { ok: false, error: '请先登录后再进行意向投标' };
  try {
    await createProcessTrack({
      userId,
      bidSectionCode: key,
      projectName: f.displayName || key,
      issuerName: f.issuerName ?? undefined,
    });
  } catch {
    // 失败提示由请求层统一弹出（含 403 无权限、401 未登录）
    return { ok: false };
  }
  await refreshTracks();
  follows.value = follows.value.filter((x) => followKeyOf(x) !== key);
  return { ok: true, track: tracks.value.find((t) => t.followKey === key) };
}

/**
 * 取消关注（真实接口），成功后从 follows 移除。
 * **只动关注，不动投标流程档案**——已建档的记录继续留在 tracks 中，这是「关注≠意向」的落点。
 * 注意 targetKey 必须用 followKey（= 公告主键 id 字符串），与详情页关注时的 targetKey 一致；
 * 不能用关注行 id 兜底，否则取关目标与详情页不一致、取消后详情页仍显示「已关注」。
 */
async function unfollow(f: FollowItem): Promise<void> {
  const key = String(f.followKey ?? '');
  await unfollowTarget({ targetType: f.followType ?? 'ANNOUNCEMENT', targetKey: key });
  follows.value = follows.value.filter((x) => followKeyOf(x) !== followKeyOf(f));
}

/** 终止流程时连带取消关注（取消投标 = 不再跟进该公告，公告大厅详情页关注状态随之变回「关注」）。
 *  反向不成立：取关**不会**终止流程（见 unfollow）。 */
async function unfollowTrack(t: PortalTrack): Promise<void> {
  const key = String(t.followKey ?? '');
  if (!key) return;
  await unfollowTarget({ targetType: t.followType ?? 'ANNOUNCEMENT', targetKey: key });
}

/** 递交标书所需的 CA 签章产物（契约来源：CA 模块 **SealDocumentRespVO**，
 *  由 POST /bidding/ca/seal/document 产出；映射关系见
 *  docs/电子证照前前端契约/后端变更请求-投标文件CA签章递交衔接-2026-09-20.md §3）。
 *  ⚠️ 该响应**没有**签章后文件字节数（契约缺口 A1），故 sealFileSize 常缺；详见 advance 分支注释。 */
export interface SealedDocument {
  /** CA 证书序列号 → documentSignature 对应 caCertNo */
  certSn: string;
  /** 文件 SM3 指纹（hex）→ documentHash */
  hash: string;
  /** SM2 签名值（base64）→ documentSignature */
  signature: string;
  /** 已签章文件 fileId（PDF=带红章 PDF；其他=签名信息 .sig.txt）→ documentFileId 候选 */
  sealFileId?: number | string;
  /** 已签章文件名 → documentName */
  sealFileName?: string;
  /** 已签章文件字节数 → documentSize（⚠️ 后端暂缺，见契约 A1） */
  sealFileSize?: number;
  /** 签章台账 id（契约 §5 建议：递交接口改收 sealId，由后端从台账回填证据链） */
  sealId?: number | string;
  /** 已签章文件访问地址（预览/下载；并用于回算签章后文件真实字节数） */
  sealFileUrl?: string;
  /** 签章单位（测试态固定为「本地CA测试单位」） */
  orgName?: string;
  /** 签章时间 */
  sealTime?: string;
}

export interface AdvanceOpts {
  cancelReason?: string;
  docId?: number | string;
  docName?: string;
  docSize?: number;
  reviewReason?: string;
  reviewResult?: 'APPROVED' | 'REJECTED';
  /** CA 签章产物（由网守调用 POST /bidding/ca/seal/document 后传入；不传时取 t.seal） */
  sealed?: SealedDocument;
}

export interface AdvanceResult {
  /** 本地前置校验失败时的原因；**服务端失败不设该字段**（提示已由 requestClient 统一弹出，避免重复弹窗） */
  error?: string;
  ok: boolean;
  to?: ProcessState;
}

/**
 * 流转流程状态。**以服务端为权威**：先本地校验转移合法性（快速反馈），再调真实接口，
 * 成功后重新拉取 /my 覆盖本地，绝不本地乐观改状态。
 */
async function advance(
  t: PortalTrack,
  ev: ProcessEvent,
  opts: AdvanceOpts = {},
): Promise<AdvanceResult> {
  const cur = t.currentState;
  const to = TRANSITIONS[cur]?.[ev];
  if (!to) return { ok: false, error: `当前状态（${STATE_TEXT[cur]}）不允许该操作` };
  const userId = currentUserId();
  if (userId === undefined) return { ok: false, error: '请先登录后再操作' };

  try {
    if (ev === 'REGISTER') {
      await registerProcessTrack({ id: t.id, userId });
    } else if (ev === 'APPROVE' || ev === 'REJECT') {
      await reviewProcessTrack({
        id: t.id,
        operatorId: userId,
        reviewResult: opts.reviewResult ?? (ev === 'APPROVE' ? 'APPROVED' : 'REJECTED'),
        reviewReason: opts.reviewReason,
      });
    } else if (ev === 'CANCEL') {
      await cancelProcessTrack(t.id, userId);
    } else if (ev === 'UPLOAD_DOCUMENT') {
      if (t.documentId === undefined || t.documentSize === undefined) {
        return { ok: false, error: '请先上传标书文件' };
      }
      const sealed = opts.sealed ?? t.seal;
      if (!sealed) {
        // ProcessTrackUploadReqVO 对 documentHash(SM3) / documentSignature(SM2) / caCertNo 三者均加了
        // @NotBlank，而这三件套**只能由服务端 CA 签章产出**（POST /bidding/ca/seal/document，
        // 前端不引入本地国密实现、不伪造签名）。缺产物时不发请求，直接给出可执行的下一步。
        return {
          ok: false,
          error: '请先完成 CA 数字证书签章（SM3 指纹 + SM2 签名 + 证书序列号）后再递交。',
        };
      }
      // document_file_id 语义待后端确认（契约缺口 A3），两种取法后果不同：
      //   · 取已签章文件 sealFileId（法律证据应以已签章文件为准）→ 需要"签章后文件字节数"，
      //     而 SealDocumentRespVO / CaSealRespVO 都**不下发**该字段（缺口 A1）→ 无法在不编造数值的前提下递交；
      //   · 取原文件 fileId → 大小用上传返回的原文件大小，可立即跑通，但证据链指向的是未签章原文件。
      const usingSealedFile = sealed.sealFileId !== undefined;
      if (usingSealedFile && sealed.sealFileSize === undefined) {
        return {
          ok: false,
          error:
            '递交缺少签章后文件大小：签章接口未下发该字段（契约缺口 A1），' +
            '请后端补 sealFileSize，或采纳「递交改收 sealId、由后端从台账回填」方案后再试。',
        };
      }
      await uploadProcessTrackDocument({
        id: t.id,
        userId,
        documentFileId: sealed.sealFileId ?? t.documentId,
        documentName: sealed.sealFileName ?? t.documentName,
        documentSize: sealed.sealFileSize ?? t.documentSize,
        documentHash: sealed.hash,
        documentHashAlgo: 'SM3',
        documentSignature: sealed.signature,
        caCertNo: sealed.certSn,
      });
    }
  } catch {
    // 失败提示由请求层统一弹出（401/403 已去重）
    return { ok: false };
  }

  await refreshTracks();
  if (ev === 'CANCEL') {
    t.cancelReason = opts.cancelReason;
  }
  return { ok: true, to };
}

/** 暂存标书文件（不流转状态、不落库）：待投标环节可反复重新上传覆盖，需再点「确认上传」才进入已投标。 */
function stageDocument(
  t: PortalTrack,
  opts: {
    docId?: number | string;
    docName?: string;
    docSize?: number;
    file?: File;
    sha256?: string;
    url?: string;
  },
): void {
  if (opts.docId !== undefined) t.documentId = opts.docId;
  if (opts.docName !== undefined) t.documentName = opts.docName;
  if (opts.docSize !== undefined) t.documentSize = opts.docSize;
  if (opts.file !== undefined) t.uploadFile = opts.file;
  if (opts.sha256 !== undefined) t.uploadSha256 = opts.sha256;
  if (opts.url !== undefined) t.uploadUrl = opts.url;
}

/**
 * 暂存 CA 签章产物（待投标环节盖章后调用）。
 * 契约缺口 A1（签章接口不下发"签章后文件字节数"）：此处按已签章文件地址回取一次，
 * 用 blob.size 拿到**真实字节数**，避免递交时用原文件大小冒充签章后文件大小。
 * 回取失败则退回原文件大小，并在返回里标明（调用方可提示）。
 */
async function stageSeal(t: PortalTrack, doc: SealedDocument): Promise<{ sizeFrom: 'origin' | 'sealed' }> {
  const sealed: SealedDocument = { ...doc };
  let sizeFrom: 'origin' | 'sealed' = 'origin';
  if (sealed.sealFileSize === undefined && sealed.sealFileUrl) {
    try {
      // 必须用 fetchFileBlob：本框架失败响应是 HTTP 200 + {code,msg}，
      // 裸 fetch + res.ok 会把「账号未登录」的 JSON 当成签章件、按它的字节数落库（大小假得离谱）
      const blob = await fetchFileBlob(sealed.sealFileUrl);
      sealed.sealFileSize = blob.size;
      sizeFrom = 'sealed';
    } catch {
      /* 回取失败：保留 undefined，递交时退回原文件大小 */
    }
  } else if (sealed.sealFileSize !== undefined) {
    sizeFrom = 'sealed';
  }
  t.seal = sealed;
  return { sizeFrom };
}

/** 合规审查结论标记（本地；后端合规接口未上线，刷新后不保留） */
function setCompliance(t: PortalTrack, status: 'PASS' | 'REJECT', remark?: string): void {
  t.complianceStatus = status;
  t.complianceRemark = remark;
  t.complianceAt = Date.now();
}

/** 兼容旧调用点：原为「写 localStorage」，现语义变为「重新拉取服务端」。 */
async function save(): Promise<void> {
  await refreshTracks();
}

export function useBiddingProcess() {
  void ensureLoaded();
  return {
    tracks,
    follows,
    advance,
    save,
    setCompliance,
    syncFromFollow,
    intentFromFollow,
    unfollow,
    unfollowTrack,
    stageDocument,
    stageSeal,
    refreshTracks,
  };
}
