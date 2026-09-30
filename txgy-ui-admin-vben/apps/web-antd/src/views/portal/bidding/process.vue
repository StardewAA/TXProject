<!--
  招投标前台 · 投标流程管理（个人工作台）
  业务：「全部」展示当前登录人关注的项目/标段（**公告级关注已下线**，过滤见 useBiddingProcess.syncFromFollow；
        点「意向投标」进入意向环节；点「取消关注」调真实接口取关并移出）；
        流程：意向投标 → 报名投标 → 资质审核 →（审核通过）待投标 → 上传标书 → 已投标（流程结束）；审核不通过/取消 → 流程结束。
  视觉（2026-09-17 改版）：状态改「浅底深字」实心标签、标题做主视觉、主操作实心按钮固定右侧、
        审核结论用图标+色条提示、顶部改选项卡（选中主色下划线 + 计数圆标）；色彩/阴影取自 bid-ui.css 共享令牌。
  口径（2026-09-20 起**后端为准**，localStorage 已退场）：
    「关注项」= GET /bidding/notice-follow/item/page；「取消关注」= POST /bidding/notice-follow/unfollow；
    「我的投标流程」= GET /bidding/process-track/my（已建档记录，服务端权威）；
    「意向投标」= POST /bidding/process-track/create（**必须显式点击**，关注时不会自动建档）；
    报名/审核/取消 = /process-track/{register,review,cancel}；
    「上传标书」= 真实接口 POST /bidding/file/upload（返回 fileId，文件落 infra_file），
      确认递交 = /process-track/upload（需 CA 国密签章，见 composables/useBiddingProcess.ts）。
    关注 ≠ 投标意向：取关只移出关注列表，**不影响已建立的投标流程档案**（反之取消投标会连带取关）。
  依据：电子招标投标办法 第9/13条（部门规章）；招标投标法 第26条（法律）。
  【2026-09-22 本轮新增两项能力】
    ① 关联公告入口：关注项行与已建档流程行（能拿到 followKey 时）都可打开
       components/NoticeLinkModal.vue —— 按该行编号列出外部公告（**复用门户服务层 fetchNoticePage**，
       三个编号参数后端是 AND 关系故一次只传一个），点标题 router.push 到门户公告详情
       /portal/bidding/detail/:id（本页此前没有任何公告详情入口，也没引入 router —— router 现由该组件持有）。
    ② 每步「该看的信息」信息卡：stepCards() 只产出**已发生**的步骤，把该步要看的数据摊开
       （时间 / 审核结论+审核意见 / 标书与预览 / 签章摘要 / 结束原因 / 合规结论），
       卡片下再给 trailOf() 的「操作留痕」（events[] 倒序；服务端 /my 未下发事件史时按节点时间推导并注明）。
       文案一律取自既有映射（STATE_TEXT / EVENT_TEXT / COMPLIANCE_TEXT），配色沿用 bid-ui.css 令牌。
-->
<script lang="ts" setup>
import type { FollowItem } from './types';

import type { CaApi } from '#/api/bidding/ca';

import { computed, onMounted, ref, watch } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { useUserStore } from '@vben/stores';

import { Button, message, Modal, Radio } from 'ant-design-vue';

import { uploadBiddingFile } from '#/api/bidding/file';
import {
  applyPrequal,
  getPrequalApplyList,
  getPrequalPage,
} from '#/api/bidding/prequal';
import { listAttachments, saveAttachment } from '#/api/bidding/attachment';
import { getBidOpeningList, OPENING_STATUS_TEXT } from '#/api/bidding/opening';

import CaSealAuth from '../../bidding/ca/CaSealAuth.vue';
import HeroMods from './components/HeroMods.vue';
import NoticeLinkModal from './components/NoticeLinkModal.vue';
import {
  COMPLIANCE_TEXT,
  EVENT_TEXT,
  type PortalTrack,
  type ProcessState,
  STATE_TEXT,
  useBiddingProcess,
} from './composables/useBiddingProcess';

import './styles/bid-ui.css';

/* 合并说明（2026-09-21 rebase 冲突）：上游那一侧是
 *   `const router = useRouter();` + `{ advance, syncFromFollow, …, stageDocument, stageSeal }`。
 * · `router` 丢掉：本页的「返回公告大厅」按钮早已按用户口径删除，页面里已无 `router.` 取值，
 *   而 `import { useRouter } from 'vue-router'` 也在同一批里删掉了 —— 再留着这一行就是未定义引用。
 * · `stageSeal` 保留：它是上游新加的 CA 电子签章能力，下面的 `onSealed` 要用（不保留会编译不过）。 */
const { advance, syncFromFollow, tracks, follows, intentFromFollow, unfollow, unfollowTrack, stageDocument, stageSeal } = useBiddingProcess();

const stateFilter = ref<'ALL' | ProcessState>('ALL');

const FILTERS: Array<{ label: string; value: 'ALL' | ProcessState }> = [
  { label: '全部', value: 'ALL' },
  { label: '意向投标', value: 'INTENTION' },
  { label: '资质审核', value: 'QUALIFICATION_REVIEW' },
  { label: '待投标', value: 'PENDING_BID' },
  { label: '已投标', value: 'BIDDED' },
  { label: '流程结束', value: 'TERMINATED' },
];

/** 状态 → 实心标签配色（浅底深字，扫一眼可辨进度） */
const STATE_TAG: Record<string, string> = {
  INTENTION: 'bid-tag--blue',
  QUALIFICATION_REVIEW: 'bid-tag--orange',
  PENDING_BID: 'bid-tag--purple',
  BIDDED: 'bid-tag--green',
  TERMINATED: 'bid-tag--gray',
};

const filtered = computed(() =>
  tracks.value.filter((t) => stateFilter.value === 'ALL' || t.currentState === stateFilter.value),
);
const statCounts = computed(() => {
  const c: Record<string, number> = {};
  for (const t of tracks.value) c[t.currentState] = (c[t.currentState] || 0) + 1;
  return c;
});
/** 选项卡计数（「全部」= 关注项 + 已建档流程） */
function countOf(v: 'ALL' | ProcessState): number {
  return v === 'ALL' ? follows.value.length + tracks.value.length : statCounts.value[v] || 0;
}
/** 当前筛选下是否有内容 */
const isEmpty = computed(() =>
  stateFilter.value === 'ALL'
    ? follows.value.length + tracks.value.length === 0
    : filtered.value.length === 0,
);

/* ============ 报名 / 取消 ============ */
function registerBid(t: PortalTrack): void {
  Modal.confirm({
    title: '报名投标「' + t.projectName + '」？',
    content: '报名后进入「资质审核」，请手动标记审核结果。',
    okText: '报名投标',
    cancelText: '再想想',
    onOk: async () => {
      const r = await advance(t, 'REGISTER');
      // 服务端失败时 r.error 为空：提示已由 requestClient 统一弹出，此处不再重复弹窗
      if (!r.ok) {
        if (r.error) message.warning(r.error);
        return;
      }
      message.success('已报名，进入资质审核：' + t.projectName);
    },
  });
}
function cancelTrack(t: PortalTrack): void {
  Modal.confirm({
    title: '取消投标「' + t.projectName + '」？',
    content: '取消后流程结束（档案保留在「流程结束」，不会删除），并取消对该公告的关注（公告大厅详情将变回「关注」）。',
    okText: '取消投标',
    okButtonProps: { danger: true },
    cancelText: '保留',
    onOk: async () => {
      const r = await advance(t, 'CANCEL', { cancelReason: '用户取消投标' });
      if (!r.ok) {
        if (r.error) message.warning(r.error);
        return;
      }
      // 流程已终止后再取关（反向不成立：取关不会终止流程）
      try {
        await unfollowTrack(t);
        message.success('已取消投标并取消关注，流程结束');
      } catch {
        message.warning('流程已结束，但取消关注失败，请稍后重试');
      }
    },
  });
}

/* ============ 关注项 → 意向投标 / 取消关注 ============ */
/** 意向投标：**显式建档**（POST /process-track/create）。与「关注」是两个独立动作，建档后持久保留。 */
async function doIntent(f: FollowItem): Promise<void> {
  const r = await intentFromFollow(f);
  if (!r.ok) {
    if (r.error) message.warning(r.error);
    return;
  }
  message.success('已建立投标流程（意向投标）：' + (r.track?.projectName || f.displayName || ''));
}
function doUnfollow(f: FollowItem): void {
  Modal.confirm({
    title: '取消关注「' + (f.displayName || f.followKey || '') + '」？',
    content: '取消后该公告将从此处消失，如需再次关注请返回公告大厅。取关只影响关注列表，不影响已建立的投标流程档案。',
    okText: '取消关注',
    okButtonProps: { danger: true },
    cancelText: '保留',
    onOk: async () => {
      try {
        await unfollow(f);
        message.success('已取消关注');
      } catch {
        /* 失败提示由请求层统一弹出 */
      }
    },
  });
}

/* ============ 资质审核 ============ */
const reviewOpen = ref(false);
const reviewTarget = ref<null | PortalTrack>(null);
const reviewResult = ref<'APPROVED' | 'REJECTED'>('APPROVED');
const reviewReason = ref('');
const reviewSubmitting = ref(false);

function openReview(t: PortalTrack, preset: 'APPROVED' | 'REJECTED'): void {
  reviewTarget.value = t;
  reviewResult.value = preset;
  reviewReason.value = '';
  reviewOpen.value = true;
}
async function submitReview(): Promise<void> {
  const t = reviewTarget.value;
  if (!t) return;
  if (reviewResult.value === 'REJECTED' && !reviewReason.value.trim()) {
    message.warning('审核不通过请填写原因');
    return;
  }
  reviewSubmitting.value = true;
  try {
    const r = await advance(t, reviewResult.value === 'APPROVED' ? 'APPROVE' : 'REJECT', {
      reviewResult: reviewResult.value,
      reviewReason: reviewReason.value.trim() || undefined,
    });
    if (!r.ok) {
      if (r.error) message.warning(r.error);
      return;
    }
    reviewOpen.value = false;
    message.success(reviewResult.value === 'APPROVED' ? '审核通过，进入待投标' : '审核不通过，流程结束');
  } finally {
    reviewSubmitting.value = false;
  }
}

/* ============ 标书上传（真实接口） ============ */
const uploadOpen = ref(false);
const uploadTarget = ref<null | PortalTrack>(null);
const pickedFiles = ref<File[]>([]);
const uploading = ref(false);
const fileInput = ref<HTMLInputElement | null>(null);

/* 前端预校验：只为了省掉一次注定失败的往返，后端校验仍是权威。
 * 上限 16MB 与后端 spring.servlet.multipart.max-file-size 对齐（超限后端会回「上传文件过大，请调整后重试」）。 */
const DOC_ACCEPT = '.pdf,.doc,.docx,.xls,.xlsx,.zip,.rar,.7z';
const DOC_MAX_MB = 16;
const DOC_EXTS = DOC_ACCEPT.split(',');

/** 清空已选文件，并重置 input 自身——否则清空后再选同一个文件不会触发 change */
function resetPicker(): void {
  pickedFiles.value = [];
  if (fileInput.value) fileInput.value.value = '';
}
function openUpload(t: PortalTrack): void {
  uploadTarget.value = t;
  resetPicker();
  uploadOpen.value = true;
}
function onFilePicked(e: Event): void {
  const input = e.target as HTMLInputElement;
  const files = Array.from(input.files ?? []);
  if (files.length === 0) return;
  const bad = files.find((fl) => {
    const ext = '.' + (fl.name.split('.').pop() ?? '').toLowerCase();
    return !DOC_EXTS.includes(ext) || fl.size > DOC_MAX_MB * 1024 * 1024;
  });
  if (bad) {
    resetPicker();
    message.warning(
      '标书仅支持 PDF / Word / Excel / 压缩包，单个文件不超过 ' + DOC_MAX_MB + ' MB（' + bad.name + '）',
    );
    return;
  }
  pickedFiles.value = [...pickedFiles.value, ...files];
  if (fileInput.value) fileInput.value.value = '';
}
function removePickedFile(i: number): void {
  pickedFiles.value.splice(i, 1);
}
async function doUpload(): Promise<void> {
  const t = uploadTarget.value;
  if (!t || pickedFiles.value.length === 0) {
    message.warning('请先选择标书文件');
    return;
  }
  uploading.value = true;
  try {
    const uploaded: Array<{ fileId: number | string; name: string }> = [];
    for (const fl of pickedFiles.value) {
      const resp = await uploadBiddingFile(fl, 'bidding-document');
      uploaded.push({ fileId: resp.fileId, name: resp.name || fl.name });
    }
    // 主件 = 第一个文件（CA 签章 + 落 process_track.document_file_id）；其余绑 BID_TRACK 附件集（契约多文件化 R2）
    const main = uploaded[0];
    stageDocument(t, {
      docId: main.fileId,
      docName: main.name,
      docSize: undefined,
      file: pickedFiles.value[0],
      sha256: undefined,
      url: undefined,
    });
    for (let i = 1; i < uploaded.length; i++) {
      await saveAttachment({
        bizType: 'BID_TRACK',
        bizCode: String(t.id),
        attachmentType: 'TENDER_FILE',
        attachmentName: uploaded[i].name,
        fileId: uploaded[i].fileId,
      });
    }
    uploadOpen.value = false;
    resetPicker();
    message.success(
      uploaded.length > 1
        ? '标书已上传（含 ' + uploaded.length + ' 份文件，主件待 CA 签章）；下一步点「CA 认证」，再点「确认上传」递交'
        : '标书已上传（暂存）；下一步点「CA 认证」完成电子签章，再点「确认上传」递交',
    );
  } catch {
    /* 失败提示由请求层统一弹出 */
  } finally {
    uploading.value = false;
  }
}

/** 确认上传：暂存的标书 → 已投标（流程结束） */
function confirmUpload(t: PortalTrack): void {
  if (!t.documentName && !t.documentId) {
    message.warning('请先上传标书文件');
    return;
  }
  Modal.confirm({
    title: '确认上传「' + t.projectName + '」的标书？',
    content:
      '确认后流程进入「已投标」并结束，不可再修改。递交需 CA 数字证书签章（SM3 指纹 + SM2 签名 + 证书序列号）。',
    okText: '确认上传',
    cancelText: '再检查一下',
    onOk: async () => {
      const r = await advance(t, 'UPLOAD_DOCUMENT');
      if (!r.ok) {
        if (r.error) message.warning(r.error);
        return;
      }
      message.success('已确认上传，流程进入「已投标」并结束');
    },
  });
}

/* ============ 资格预审申请（2026-10-02 用户拍板：门户投标流程页·该标段流程行） ============
 * 前审标段：投标人在流程行提交申请文件（applyPrequal；招标方在管理端资格预审抽屉审查）。
 * 入口显示：该流程行标段（followKey）存在「已发布」资格预审时才出现（只有前审招标项目才会创建资格预审）。 */
const preqOpen = ref(false);
const preqTarget = ref<null | PortalTrack>(null);
const preqInfo = ref<null | Record<string, any>>(null);
const preqMyApply = ref<null | Record<string, any>>(null);
const preqFiles = ref<File[]>([]);
const preqFileInput = ref<HTMLInputElement | null>(null);
const preqLoading = ref(false);
const preqSubmitting = ref(false);
/** 标段编号（followKey）→ 该标段存在已发布资格预审（按钮据此出现） */
const prequalFlag = ref<Record<string, boolean>>({});
/** 流程行 id → 投标文件集附件份数（契约 R2：BID_TRACK 附件，与标段详情「材料」列对称） */
const trackAttachCount = ref<Record<string, number>>({});
async function loadTrackAttachCounts(): Promise<void> {
  const next: Record<string, number> = {};
  for (const t of tracks.value) {
    try {
      const list = await listAttachments('BID_TRACK', String(t.id));
      next[t.id] = list?.length ?? 0;
    } catch {
      next[t.id] = 0;
    }
  }
  trackAttachCount.value = next;
}

/** 收集当前所有流程行的标段，逐个探测已发布资格预审（数量少，直查一次） */
async function loadPrequalFlags(): Promise<void> {
  const codes = Array.from(new Set(tracks.value.map((t) => t.followKey ?? '').filter(Boolean)));
  if (codes.length === 0) return;
  const next: Record<string, boolean> = {};
  for (const c of codes) {
    try {
      const page = await getPrequalPage({ pageNo: 1, pageSize: 1, bidSectionCode: c, status: 'PUBLISHED' });
      next[c] = (page?.list ?? []).length > 0;
    } catch {
      next[c] = false;
    }
  }
  prequalFlag.value = next;
}
watch(tracks, () => void loadPrequalFlags(), { immediate: true });
watch(tracks, () => void loadTrackAttachCounts(), { immediate: true });
/** 流程行 id → 该标段开标记录（投标人视角：开标后看时间/状态/唱标，契约 opening page 按标段） */
const trackOpenings = ref<Record<string, any[]>>({});
/* [2026-10-02 修复] 唱标明细（openList）归一化 + 按标段归属（用户报障：唱标明细 190 条、190 行全空）
 * 实测后端两处问题（契约已同步后端）：
 *   ① GET /bidding/opening/page 的 openList 下发的是**原始 JSON 字符串**（形如 '[{"price":…}]'），
 *      前端按数组使用 → `openList.length` 拿到的是**字符串长度 190** → 模板 v-for 遍历字符，
 *      渲染出 190 行全「—」。此处统一 JSON.parse 归一化为数组。
 *   ② 该接口的 bidSectionCode 过滤**未生效**（带参/不带参实测返回同一批记录），且记录自身
 *      bid_section_code 可能为 NULL（标段号只在 openList 里）→ 前端按「记录标段号 ∪ openList 内标段号」
 *      二次过滤，避免每条流程行都显示同一场开标。 */
function normalizeOpenList(v: unknown): any[] {
  if (Array.isArray(v)) return v;
  if (typeof v === 'string' && v.trim()) {
    try {
      const parsed = JSON.parse(v);
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  }
  return [];
}
async function loadTrackOpenings(): Promise<void> {
  const next: Record<string, any[]> = {};
  for (const t of tracks.value) {
    // PortalTrack 只有 followKey（旧代码多写的 t.bidSectionCode 并不存在于该类型，已剔除）
    const key = String(t.followKey ?? '');
    if (!key) continue;
    try {
      const rows = ((await getBidOpeningList(key)) ?? []) as any[];
      const normalized = rows.map((o) => ({ ...o, openList: normalizeOpenList(o.openList) }));
      next[t.id] = normalized.filter((o) =>
        [o.bidSectionCode, ...(o.openList as any[]).map((x) => x?.bidSectionCode)]
          .filter(Boolean)
          .some((c) => String(c) === key),
      );
    } catch {
      next[t.id] = [];
    }
  }
  trackOpenings.value = next;
}
watch(tracks, () => void loadTrackOpenings(), { immediate: true });
/** 唱标明细解密状态文案（open_list[].decryptStatus，原码兜底） */
function openDecryptText(s?: string): string {
  return s === 'PENDING' ? '未解密' : s === 'DECRYPTED' || s === 'SUCCESS' ? '已解密' : s === 'FAILED' ? '解密失败' : s || '—';
}

function resetPreqPicker(): void {
  preqFiles.value = [];
  if (preqFileInput.value) preqFileInput.value.value = '';
}
function onPreqFilePicked(e: Event): void {
  const input = e.target as HTMLInputElement;
  const files = Array.from(input.files ?? []);
  if (files.length === 0) return;
  const bad = files.find((f) => {
    const ext = '.' + (f.name.split('.').pop() ?? '').toLowerCase();
    return !DOC_EXTS.includes(ext) || f.size > DOC_MAX_MB * 1024 * 1024;
  });
  if (bad) {
    resetPreqPicker();
    message.warning(
      '资格预审申请仅支持 PDF / Word / Excel / 压缩包，单个文件不超过 ' + DOC_MAX_MB + ' MB（' + bad.name + '）',
    );
    return;
  }
  preqFiles.value = [...preqFiles.value, ...files];
  if (preqFileInput.value) preqFileInput.value.value = '';
}
function removePreqFile(i: number): void {
  preqFiles.value.splice(i, 1);
}

/** 打开申请弹窗：定位该标段的已发布资格预审 + 当前登录人已有申请（若有） */
async function openPrequalApply(t: PortalTrack): Promise<void> {
  const code = t.followKey ?? '';
  if (!code) {
    message.info('该流程行缺少标段编号，无法定位资格预审');
    return;
  }
  preqOpen.value = true;
  preqTarget.value = t;
  preqInfo.value = null;
  preqMyApply.value = null;
  resetPreqPicker();
  preqLoading.value = true;
  try {
    const page = await getPrequalPage({ pageNo: 1, pageSize: 1, bidSectionCode: code, status: 'PUBLISHED' });
    const hit = (page?.list ?? [])[0] ?? null;
    preqInfo.value = hit;
    if (!hit) {
      message.info('该标段暂无已发布的资格预审');
      return;
    }
    const uid = useUserStore().userInfo?.userId;
    if (uid !== undefined && uid !== null) {
      try {
        const applies = await getPrequalApplyList(String(hit.id));
        preqMyApply.value =
          (applies ?? []).find((a) => String(a.userId ?? '') === String(uid)) ?? null;
      } catch {
        preqMyApply.value = null;
      }
    }
  } catch {
    message.error('加载资格预审失败，请稍后重试');
  } finally {
    preqLoading.value = false;
  }
}

/** 提交申请：上传文件 → applyPrequal（申请人=当前登录人） */
async function submitPrequalApply(): Promise<void> {
  if (!preqInfo.value) return;
  if (preqFiles.value.length === 0) {
    message.warning('请先选择资格预审申请文件');
    return;
  }
  preqSubmitting.value = true;
  try {
    const uploaded: Array<{ fileId: number | string; name: string }> = [];
    for (const fl of preqFiles.value) {
      const resp = await uploadBiddingFile(fl, 'bidding-document');
      uploaded.push({ fileId: resp.fileId, name: resp.name || fl.name });
    }
    // 主件 = 第一个文件（落 prequal_apply.file_id）；其余多份材料绑 PREQUAL_APPLY 附件集（契约多文件化 R3）
    const applyId = await applyPrequal({ prequalId: String(preqInfo.value.id), fileId: uploaded[0].fileId });
    for (let i = 1; i < uploaded.length; i++) {
      await saveAttachment({
        bizType: 'PREQUAL_APPLY',
        bizCode: String(applyId),
        attachmentType: 'QUALIFICATION_DOC',
        attachmentName: uploaded[i].name,
        fileId: uploaded[i].fileId,
      });
    }
    resetPreqPicker();
    message.success(
      uploaded.length > 1
        ? '资格预审申请已提交（含 ' + uploaded.length + ' 份材料），等待招标方审查'
        : '资格预审申请已提交，等待招标方审查',
    );
    const uid = useUserStore().userInfo?.userId;
    if (uid !== undefined && uid !== null) {
      try {
        const applies = await getPrequalApplyList(String(preqInfo.value.id));
        preqMyApply.value =
          (applies ?? []).find((a) => String(a.userId ?? '') === String(uid)) ?? null;
      } catch {
        preqMyApply.value = null;
      }
    }
  } catch {
    /* 失败提示由请求层统一弹出 */
  } finally {
    preqSubmitting.value = false;
  }
}

/** 申请状态文案（状态走字典 prequal_apply_status，缺字典回显原码；审查结论优先） */
function preqStateText(a: Record<string, any>): string {
  if (a.reviewResult) return String(a.reviewResult);
  const s = String(a.status ?? '');
  const map: Record<string, string> = {
    SUBMITTED: '已提交，待审查',
    PENDING: '已提交，待审查',
    APPROVED: '已通过',
    PASSED: '已通过',
    REJECTED: '未通过',
    FAILED: '未通过',
    CLOSED: '已关闭',
  };
  return map[s] ?? s;
}

/* ============ CA 认证（电子签章：二维码 → 模拟已扫码 → 盖章） ============ */
const sealOpen = ref(false);
const sealTarget = ref<null | PortalTrack>(null);

/** CaSealAuth 的 file 入参（fileId/url/sha256 来自上传响应，供 check 检测与预览） */
function sealFileOf(t: PortalTrack) {
  return {
    fileId: t.documentId,
    fileName: t.documentName,
    name: t.documentName,
    sha256: t.uploadSha256,
    url: t.uploadUrl,
  };
}

function openSeal(t: PortalTrack): void {
  if (t.documentId === undefined) {
    message.warning('请先上传标书文件，再做 CA 认证');
    return;
  }
  sealTarget.value = t;
  sealOpen.value = true;
}

/** 盖章完成：暂存签章产物（SM3 指纹/SM2 签名/证书号/红章文件），递交时作为证据链提交 */
async function onSealed(doc: CaApi.SealDocument): Promise<void> {
  const t = sealTarget.value;
  if (!t) return;
  // 证据链三件套缺一不可（递交接口对三者均 @NotBlank）：宁可在这里拦住，也不提交空签名
  if (!doc.signature || !doc.certSn || !doc.hash) {
    message.error('签章响应缺少签名值 / 证书号 / SM3 指纹，无法作为递交证据链，请重试');
    return;
  }
  const { sizeFrom } = await stageSeal(t, {
    certSn: doc.certSn,
    hash: doc.hash,
    orgName: doc.orgName,
    sealFileId: doc.sealFileId,
    sealFileName: doc.sealFileName,
    sealFileUrl: doc.sealFileUrl,
    sealTime: doc.sealTime,
    signature: doc.signature,
  });
  sealOpen.value = false;
  message.success(
    sizeFrom === 'sealed'
      ? 'CA 签章完成：红章文件与证据链已就绪，可点「确认上传」递交'
      : 'CA 签章完成（签章后文件大小回取失败，递交将按原文件大小提交）',
  );
}

/* ============ 展示工具 ============ */
const p2 = (n: number) => String(n).padStart(2, '0');
function fmtT(v?: null | number | string): string {
  if (!v) return '—';
  const d = new Date(v);
  if (Number.isNaN(+d)) return '—';
  return d.getFullYear() + '-' + p2(d.getMonth() + 1) + '-' + p2(d.getDate()) + ' ' + p2(d.getHours()) + ':' + p2(d.getMinutes());
}
function fmtSize(n?: number): string {
  if (n == null) return '';
  if (n >= 1024 * 1024) return (n / 1024 / 1024).toFixed(1) + ' MB';
  if (n >= 1024) return (n / 1024).toFixed(1) + ' KB';
  return n + ' B';
}
/** 状态 / 事件中文：取自 composable 既有的 STATE_TEXT / EVENT_TEXT（不另造一份枚举文案）。
 *  两个映射都是 Record<string, string>，在 tsconfig 的 noUncheckedIndexedAccess 下取值类型是
 *  string | undefined —— 这里统一收口：正常必然命中，只有**确实缺键**时回落状态码 / 事件码本身。 */
function stateLabel(s: ProcessState): string {
  return STATE_TEXT[s] ?? s;
}
function eventLabel(ev: string): string {
  return EVENT_TEXT[ev] ?? ev;
}

/** 审核结论中文：同样复用 EVENT_TEXT 的两个既有条目 */
const REVIEW_TEXT: Record<string, string> = {
  APPROVED: eventLabel('APPROVE'),
  REJECTED: eventLabel('REJECT'),
};

/** 信息卡的一行（label + 值；href 非空时给「打开」链接，用于标书 / 签章件的预览） */
interface StepLine {
  href: string;
  label: string;
  value: string;
}

/** 步骤信息卡（**只产出已发生的步骤**；time/footnote 为空串表示不显示） */
interface StepCard {
  /** 口径等补充说明（如合规结论是前端本地标记） */
  footnote: string;
  key: string;
  label: string;
  lines: StepLine[];
  time: string;
  tone: 'bad' | 'info' | 'ok' | 'plain';
}

/** 签章摘要：单位 · 证书序列号（+ SM3 前 16 位），待投标与已投标两处共用同一句 */
function sealSummary(t: PortalTrack): string {
  const seal = t.seal;
  if (!seal) return '';
  const parts = [seal.orgName || '本地CA测试单位', seal.certSn];
  if (seal.hash) parts.push(`SM3 ${String(seal.hash).slice(0, 16)}…`);
  return parts.filter(Boolean).join(' · ');
}

/** 步骤 → 信息卡：按流程顺序只追加**已发生**的步骤（有时间戳，或该步正是当前状态） */
function stepCards(t: PortalTrack): StepCard[] {
  const cards: StepCard[] = [];
  const line = (label: string, value: string, href = ''): StepLine => ({ href, label, value });

  // ① 意向投标：建档即发生（老后端未回填 intentionTime 时仍显示步骤名，不假装没这一步）
  if (t.intentionTime || t.currentState === 'INTENTION') {
    cards.push({
      key: 'INTENTION',
      label: stateLabel('INTENTION'),
      time: t.intentionTime ? fmtT(t.intentionTime) : '',
      tone: 'plain',
      lines: [],
      footnote: '',
    });
  }

  // ② 报名投标
  if (t.registerTime || t.currentState === 'QUALIFICATION_REVIEW') {
    cards.push({
      key: 'REGISTER',
      label: eventLabel('REGISTER'),
      time: t.registerTime ? fmtT(t.registerTime) : '',
      tone: 'plain',
      lines: [],
      footnote: '',
    });
  }

  // ③ 资质审核：结论 + **审核意见必须显示**（不通过时卡片整体转红）
  if (t.reviewTime || t.reviewResult || t.currentState === 'QUALIFICATION_REVIEW') {
    const lines: StepLine[] = [];
    if (t.reviewResult) {
      lines.push(line('审核结论', REVIEW_TEXT[t.reviewResult] || t.reviewResult));
    }
    if (t.reviewReason) lines.push(line('审核意见', t.reviewReason));
    cards.push({
      key: 'REVIEW',
      label: stateLabel('QUALIFICATION_REVIEW'),
      time: t.reviewTime ? fmtT(t.reviewTime) : '',
      tone: t.reviewResult === 'REJECTED' ? 'bad' : t.reviewResult === 'APPROVED' ? 'ok' : 'info',
      lines,
      footnote: '',
    });
  }

  // ④ 待投标：标书（名称 / 大小 / 预览下载）+ 若已盖章则给签章摘要
  if (t.pendingBidTime || t.currentState === 'PENDING_BID' || t.currentState === 'BIDDED') {
    const lines: StepLine[] = [];
    if (t.documentName) {
      lines.push(
        line('标书', t.documentSize ? `${t.documentName}（${fmtSize(t.documentSize)}）` : t.documentName),
      );
    } else if (t.documentId !== undefined) {
      lines.push(line('标书', '已上传（服务端未下发文件名）'));
    }
    // 预览/下载沿用门户既有方式：直接吃文件地址（本框架失败包体只在 fetchFileBlob 那条链路才需要判）
    if (t.uploadUrl) lines.push(line('原文件', '预览 / 下载', t.uploadUrl));
    if (t.seal) lines.push(line('电子签章', sealSummary(t)));
    cards.push({
      key: 'PENDING_BID',
      label: stateLabel('PENDING_BID'),
      time: t.pendingBidTime ? fmtT(t.pendingBidTime) : '',
      tone: 'info',
      lines,
      footnote: t.uploadUrl
        ? ''
        : '标书文件地址仅本次会话内暂存（上传后未刷新页面时可用），刷新后以服务端文件 id 为准。',
    });
  }

  // ⑤ 已投标：递交时间 + 签章证据链（证书号 / SM3 指纹 / 签章件）
  if (t.biddedTime || t.currentState === 'BIDDED') {
    const lines: StepLine[] = [];
    if (t.seal) {
      lines.push(line('签章单位', t.seal.orgName || '本地CA测试单位'));
      lines.push(line('证书序列号', t.seal.certSn || '—'));
      if (t.seal.hash) lines.push(line('SM3 指纹', `${String(t.seal.hash).slice(0, 32)}…`));
      if (t.seal.sealFileName) {
        lines.push(line('签章文件', t.seal.sealFileName, t.seal.sealFileUrl || ''));
      }
    } else {
      lines.push(line('电子签章', '未盖章（签章证据链由服务端 CA 台账承载）'));
    }
    cards.push({
      key: 'BIDDED',
      label: stateLabel('BIDDED'),
      time: t.biddedTime ? fmtT(t.biddedTime) : '',
      tone: 'ok',
      lines,
      footnote: '',
    });
  }

  // ⑥ 流程结束：结束时间 + （取消原因 或 审核不通过意见）
  if (t.terminatedTime || t.currentState === 'TERMINATED') {
    const lines: StepLine[] = [];
    if (t.cancelReason) lines.push(line('取消原因', t.cancelReason));
    else if (t.reviewReason) lines.push(line('审核意见', t.reviewReason));
    cards.push({
      key: 'TERMINATED',
      label: stateLabel('TERMINATED'),
      time: t.terminatedTime ? fmtT(t.terminatedTime) : '',
      tone: 'bad',
      lines,
      footnote: '',
    });
  }

  // ⑦ 合规结论（**前端本地标记**：后端合规接口未上线，刷新后不保留 —— 卡片里注明口径）
  if (t.complianceStatus) {
    const lines: StepLine[] = [line('结论', COMPLIANCE_TEXT[t.complianceStatus] || t.complianceStatus)];
    if (t.complianceRemark) lines.push(line('备注', t.complianceRemark));
    if (t.complianceAt) lines.push(line('标记时间', fmtT(t.complianceAt)));
    cards.push({
      key: 'COMPLIANCE',
      label: '合规审查结论',
      time: '',
      tone: t.complianceStatus === 'REJECT' ? 'bad' : t.complianceStatus === 'PASS' ? 'ok' : 'info',
      lines,
      footnote: '口径：前端本地标记（后端合规接口未上线），刷新后不保留。',
    });
  }

  return cards;
}

/** 操作留痕的一行 */
interface TrailRow {
  desc: string;
  key: string;
  text: string;
  time: string;
}

/**
 * 全程操作留痕（**倒序**）。
 * 优先用服务端事件史 events[]；/my 当前不下发该数组（见 useBiddingProcess.toPortalTrack 注释），
 * 此时按节点时间推导一份并在界面上**注明来源**——不把推导出来的东西伪装成服务端记录。
 */
function trailOf(t: PortalTrack): TrailRow[] {
  if (t.events.length > 0) {
    return [...t.events]
      .sort((a, b) => (b.occurredAt || 0) - (a.occurredAt || 0))
      .map((e, i) => ({
        desc: e.desc || '',
        key: `${e.eventType}-${e.occurredAt}-${i}`,
        text: eventLabel(e.eventType),
        time: fmtT(e.occurredAt),
      }));
  }
  const out: TrailRow[] = [];
  const add = (time: number | undefined, text: string, desc = '') => {
    if (!time) return;
    out.push({ desc, key: `${text}-${time}`, text, time: fmtT(time) });
  };
  // 倒序追加：结束 → 递交 → 待投标 → 审核 → 报名 → 建档
  add(
    t.terminatedTime,
    t.cancelReason
      ? eventLabel('CANCEL')
      : t.reviewResult === 'REJECTED'
        ? eventLabel('REJECT')
        : stateLabel('TERMINATED'),
    t.cancelReason || t.reviewReason || '',
  );
  add(t.biddedTime, eventLabel('UPLOAD_DOCUMENT'), t.documentName || '');
  add(t.pendingBidTime, stateLabel('PENDING_BID'));
  add(
    t.reviewTime,
    (t.reviewResult ? REVIEW_TEXT[t.reviewResult] : '') || stateLabel('QUALIFICATION_REVIEW'),
    t.reviewReason || '',
  );
  add(t.registerTime, eventLabel('REGISTER'));
  add(t.intentionTime, eventLabel('CREATE'));
  return out;
}

/* ============ 关联公告（门户服务层 fetchNoticePage，见 components/NoticeLinkModal.vue） ============ */
const noticeOpen = ref(false);
const noticeKey = ref('');
const noticeType = ref('');
const noticeSubject = ref('');

/** 打开「关联公告」：关注项与已建档流程共用同一个弹窗（编号 + 维度决定传哪个编号参数） */
function openNoticeLink(key?: null | string, type?: null | string, subject?: string): void {
  const k = String(key ?? '').trim();
  if (!k) {
    message.warning('该行没有业务编号，无法关联公告');
    return;
  }
  noticeKey.value = k;
  noticeType.value = type ?? '';
  noticeSubject.value = subject ?? '';
  noticeOpen.value = true;
}

onMounted(() => {
  void syncFromFollow();
});
</script>

<template>
  <div class="module-page">
    <!-- Banner：与其它三页同一套底纹。2026-09-21 起它自己**不再画背景** ——
         整页底图已上移到 layouts/portal.vue 的 .portal-shell--bidhero（钉在视口上的一层），
         本元素只负责占位与排版。这样照片整页连续，滚动时蓝条下沿不会切出接缝。 -->
    <section class="hero">
      <!-- 上：平台名 + 小字。.hero-text 是共享类（styles/bid-ui.css），2026-09-21 下午起居中、宽度自适应。 -->
      <div class="hero-text">
        <!-- 标题与下面那行小字（2026-09-21 用户口径）：四个页面统一用平台名「防腐保温招投标智慧平台」
             和公告大厅那一行小字 —— 于是四页的标题段逐字相同、且逐字同样大小，
             本页是哪个模块只由下方高亮的那张入口卡体现。
             （此前这里放的是组长 2026-09-20 给本模块写的价值句「从关注到投标…」，
               现按用户要求换掉，句子已不再使用。） -->
        <h1 class="hero-title">防腐保温招投标<span class="hl">智慧</span>平台</h1>
        <div class="hero-sub">《公告大厅》《免费查阅 · 依法公开》《智慧标书》</div>
      </div>

      <!-- 下：四个模块入口那整行（共享组件 components/HeroMods.vue）。
           2026-09-21 用户口径：四个页面的蓝色背景部分都要与公告大厅一致、都显示这四个按钮；
           active 传本页的 key，让「投标流程管理」这张卡保持被点击时的高亮色。
           下午起它从标题右侧搬到标题下方，并压到下方玻璃卡顶边下 12px。 -->
      <HeroMods active="process" />
    </section>

    <!-- 2026-09-21 用户口径：下面的内容部分底下加一张「略大一些的半透明底圆角卡片」——
         共享类 .bid-glass-shell（见 styles/bid-ui.css），本页直接加在已有的 .content 上。 -->
    <section class="content bid-glass-shell">
      <!-- 原来这里有一行工具栏（「返回公告大厅」按钮）。2026-09-21 用户口径：去掉 ——
           顶部蓝条里那排模块入口卡已经承担了页面间跳转，这行按钮是重复入口。
           整块 `.toolbar` 连它的样式一起删除；`.content` 的 `margin-top: -32px`
           （内容压在蓝条上）也已一并取消 —— 2026-09-21 用户口径：下面的卡片不要压在蓝色背景上，
           不过那条口径当天又被「内容往上压，在屏幕页面 1/2 的位置」取代 ——
           现在整块内容是往上压进蓝条，顶边落在屏幕纵向正中再往上抬 `--bid-glass-lift`
           （`margin-top: calc(-50vh - 64px - var(--bid-glass-lift))`）。 -->

      <div class="panel bid-card">
        <!-- 顶部状态选项卡（选中主色下划线 + 计数圆标） -->
        <div class="bid-tabs">
          <button
            v-for="f in FILTERS"
            :key="f.value"
            class="bid-tab"
            :class="{ on: stateFilter === f.value }"
            type="button"
            @click="stateFilter = f.value"
          >
            {{ f.label }}<span class="cnt">{{ countOf(f.value) }}</span>
          </button>
        </div>

        <!-- 空态 -->
        <div v-if="isEmpty" class="bid-empty">
          <IconifyIcon class="bid-empty-icon" icon="lucide:inbox" />
          <div class="bid-empty-text">
            {{ stateFilter === 'ALL'
              ? '还没有关注的项目 — 到「公告大厅」的公告详情点「＋ 跟踪该项目」（或关注某个标段）后，会出现在这里'
              : '当前筛选条件下暂无流程' }}
          </div>
        </div>

        <template v-else>
          <!-- 分组标题：让「关注」与「已建档流程」一眼可分 —— 关注可撤销、建档持久保留 -->
          <div v-if="stateFilter === 'ALL' && follows.length > 0" class="group-head">
            <span class="group-title">关注中</span>
            <span class="group-hint">尚未建档 · 取关不影响已建立的投标流程</span>
          </div>

          <!-- 关注项（尚未进入意向投标） -->
          <article
            v-for="f in (stateFilter === 'ALL' ? follows : [])"
            :key="`F${ String(f.id)}`"
            class="bid-item"
          >
            <div class="bid-item-main">
              <div class="bid-item-head">
                <h3 class="bid-item-title">{{ f.displayName || f.followKey || '—' }}</h3>
                <span class="bid-tag bid-tag--gold">已关注</span>
                <span v-if="f.issuerName" class="bid-item-issuer">{{ f.issuerName }}</span>
              </div>
              <div class="bid-item-meta">
                <span class="bid-chip"><b>跟踪编号</b>{{ f.followKey || '—' }}</span>
                <span class="bid-chip"><b>关注</b>{{ fmtT(f.followTime) }}</span>
              </div>
            </div>
            <div class="bid-item-actions">
              <!-- 关联公告：该关注项编号下有哪些公告（此前本页没有任何公告详情入口） -->
              <button
                class="bid-btn bid-btn--ghost bid-btn--sm"
                type="button"
                @click="openNoticeLink(f.followKey, f.followType, f.displayName || f.followKey || '')"
              >
                <IconifyIcon icon="lucide:link" /> 关联公告
              </button>
              <button class="bid-btn bid-btn--primary bid-btn--sm" type="button" @click="doIntent(f)">
                <IconifyIcon icon="lucide:flag" /> 意向投标
              </button>
              <button class="bid-btn bid-btn--danger-ghost bid-btn--sm" type="button" @click="doUnfollow(f)">
                取消关注
              </button>
            </div>
          </article>

          <div v-if="stateFilter === 'ALL' && tracks.length > 0" class="group-head">
            <span class="group-title">我的投标流程</span>
            <span class="group-hint">已建档 · 记录持久保留</span>
          </div>

          <!-- 投标流程 -->
          <article
            v-for="t in (stateFilter === 'ALL' ? tracks : filtered)"
            :key="t.id"
            class="bid-item"
          >
            <div class="bid-item-main">
              <div class="bid-item-head">
                <h3 class="bid-item-title">{{ t.projectName }}</h3>
                <span class="bid-tag" :class="STATE_TAG[t.currentState] || 'bid-tag--gray'">
                  {{ STATE_TEXT[t.currentState] || t.currentState }}
                </span>
                <span v-if="t.issuerName" class="bid-item-issuer">{{ t.issuerName }}</span>
                <span v-if="trackAttachCount[t.id]" class="bid-chip"><b>材料</b>{{ trackAttachCount[t.id] }} 份</span>
              </div>

              <!-- 每步「该看的信息」：只列**已发生**的步骤（时间 + 该步要看的数据），见 stepCards()。
                   原来这里是一排时间胶囊 + 若干 bid-note 提示条，信息散在四处；
                   现在按步骤成卡，审核意见 / 标书 / 签章 / 结束原因各归其位（文案仍取既有映射）。 -->
              <ol v-if="stepCards(t).length > 0" class="step-list">
                <li
                  v-for="s in stepCards(t)"
                  :key="s.key"
                  class="step-card"
                  :class="`step-card--${s.tone}`"
                >
                  <div class="step-head">
                    <span class="step-name">{{ s.label }}</span>
                    <span v-if="s.time" class="step-time">{{ s.time }}</span>
                  </div>
                  <div v-for="l in s.lines" :key="l.label" class="step-line">
                    <b>{{ l.label }}</b>
                    <span class="step-val">{{ l.value }}</span>
                    <a
                      v-if="l.href"
                      class="bid-link"
                      :href="l.href"
                      rel="noopener noreferrer"
                      target="_blank"
                    >
                      预览 / 下载 ↗
                    </a>
                  </div>
                  <div v-if="s.footnote" class="step-foot">{{ s.footnote }}</div>
                </li>
              </ol>

              <!-- 全程操作留痕（倒序）：events[] 优先；服务端 /my 未下发事件史时按节点时间推导并**注明来源** -->
              <div class="trace">
                <div class="trace-head">
                  操作留痕
                  <span class="trace-src">
                    {{ t.events.length > 0 ? '服务端事件史' : '（服务端未下发事件史，按节点时间推导）' }}
                  </span>
                </div>
                <div v-if="trailOf(t).length === 0" class="bid-muted">暂无留痕</div>
                <ol v-else class="trace-list">
                  <li v-for="r in trailOf(t)" :key="r.key" class="trace-row">
                    <span class="trace-time">{{ r.time }}</span>
                    <span class="trace-text">{{ r.text }}</span>
                    <span v-if="r.desc" class="trace-desc">{{ r.desc }}</span>
                  </li>
                </ol>
              </div>

              <!-- 开标信息（投标人视角）：该标段有开标记录时展示；details 原生展开唱标明细（契约 opening page 按标段） -->
              <details v-if="trackOpenings[t.id]?.length" class="bid-open-note">
                <summary>
                  <b>开标信息</b>：{{ fmtT(trackOpenings[t.id][0].openingTime) }} ·
                  {{ OPENING_STATUS_TEXT[trackOpenings[t.id][0].openingStatus] || trackOpenings[t.id][0].openingStatus || '—' }}
                  · 投标 {{ trackOpenings[t.id][0].bidderCount ?? '—' }} 家 ·
                  唱标明细 {{ (trackOpenings[t.id][0].openList || []).length }} 条
                </summary>
                <table v-if="(trackOpenings[t.id][0].openList || []).length" class="bid-open-list">
                  <thead>
                    <tr><th>投标人</th><th>报价</th><th>解密状态</th></tr>
                  </thead>
                  <tbody>
                    <tr v-for="(o, i) in trackOpenings[t.id][0].openList" :key="i">
                      <td>{{ o.bidderName || '—' }}</td>
                      <td>{{ o.price ?? '—' }}</td>
                      <td>{{ openDecryptText(o.decryptStatus) }}</td>
                    </tr>
                  </tbody>
                </table>
                <div v-else class="bid-muted">该场开标暂无唱标明细</div>
              </details>
            </div>

            <!-- 主操作固定在卡片最右侧 -->
            <div class="bid-item-actions">
              <!-- 关联公告：档案能拿到 followKey 时始终可用（流程结束后也留着，便于回查公告） -->
              <button
                v-if="t.followKey"
                class="bid-btn bid-btn--ghost bid-btn--sm"
                type="button"
                @click="openNoticeLink(t.followKey, t.followType, t.projectName)"
              >
                <IconifyIcon icon="lucide:link" /> 关联公告
              </button>
              <!-- 资格预审申请：仅当该标段存在已发布资格预审（= 前审招标项目）时出现 -->
              <button
                v-if="prequalFlag[t.followKey ?? '']"
                class="bid-btn bid-btn--ghost bid-btn--sm"
                type="button"
                @click="openPrequalApply(t)"
              >
                <IconifyIcon icon="lucide:file-check-2" /> 资格预审申请
              </button>
              <template v-if="t.currentState === 'INTENTION'">
                <button class="bid-btn bid-btn--primary bid-btn--sm" type="button" @click="registerBid(t)">报名投标</button>
                <button class="bid-btn bid-btn--danger-ghost bid-btn--sm" type="button" @click="cancelTrack(t)">取消</button>
              </template>
              <template v-else-if="t.currentState === 'QUALIFICATION_REVIEW'">
                <button class="bid-btn bid-btn--primary bid-btn--sm" type="button" @click="openReview(t, 'APPROVED')">审核通过</button>
                <button class="bid-btn bid-btn--danger-ghost bid-btn--sm" type="button" @click="openReview(t, 'REJECTED')">审核不通过</button>
                <button class="bid-btn bid-btn--danger-ghost bid-btn--sm" type="button" @click="cancelTrack(t)">取消</button>
              </template>
              <template v-else-if="t.currentState === 'PENDING_BID'">
                <button class="bid-btn bid-btn--ghost bid-btn--sm" type="button" @click="openUpload(t)">上传标书</button>
                <button class="bid-btn bid-btn--ghost bid-btn--sm" type="button" @click="openSeal(t)">
                  {{ t.seal ? 'CA 签章 ✓' : 'CA 认证' }}
                </button>
                <button class="bid-btn bid-btn--primary bid-btn--sm" type="button" @click="confirmUpload(t)">确认上传</button>
                <button class="bid-btn bid-btn--danger-ghost bid-btn--sm" type="button" @click="cancelTrack(t)">取消</button>
              </template>
            </div>
          </article>
        </template>
      </div>
    </section>

    <!-- 资质审核弹窗 -->
    <!-- width 必须传数字（:width="400"）：写成 width="400" 会得到无单位行内样式 width:400，浏览器丢弃后回落到 antd 默认 520px -->
    <Modal v-model:open="reviewOpen" title="资质审核（手动标记）" :footer="null" :width="550" wrap-class-name="white-modal">
      <div class="modal-body">
        <div class="modal-hint">对「{{ reviewTarget?.projectName }}」标记资质审核结果。</div>
        <Radio.Group v-model:value="reviewResult" option-type="button" :options="[{ label: '审核通过', value: 'APPROVED' }, { label: '审核不通过', value: 'REJECTED' }]" />
        <div>
          <div class="modal-label">{{ reviewResult === 'REJECTED' ? '不通过原因 *' : '审核说明（可选）' }}</div>
          <textarea v-model="reviewReason" class="ta" rows="2" placeholder="如 资料齐全，通过 / 社保记录缺失，不通过"></textarea>
        </div>
        <div class="modal-footer">
          <Button @click="reviewOpen = false">取消</Button>
          <Button type="primary" :loading="reviewSubmitting" @click="submitReview">确认</Button>
        </div>
      </div>
    </Modal>

    <!-- 上传标书弹窗 -->
    <Modal v-model:open="uploadOpen" title="上传标书" :footer="null" :width="550" wrap-class-name="white-modal">
      <div class="modal-body">
        <div class="modal-hint">上传「{{ uploadTarget?.projectName }}」的投标文件（暂存，可反复重新上传），需在列表点「确认上传」后才进入「已投标」。</div>
        <label class="file-pick">
          选择文件（可多选）
          <input ref="fileInput" type="file" :accept="DOC_ACCEPT" multiple @change="onFilePicked" />
        </label>
        <div v-if="pickedFiles.length" class="modal-hint">
          <div v-for="(fl, i) in pickedFiles" :key="i">
            {{ fl.name }}（{{ fmtSize(fl.size) }}）<a class="bid-link" @click="removePickedFile(i)">移除</a>
          </div>
          <div class="bid-muted">首个文件为主件（CA 签章递交），其余为投标文件集附件</div>
        </div>
        <div v-else class="modal-hint">支持 PDF / Word / Excel / 压缩包，单个文件不超过 {{ DOC_MAX_MB }} MB，可一次选择多份材料。</div>
        <div class="modal-footer">
          <Button @click="uploadOpen = false">取消</Button>
          <Button type="primary" :loading="uploading" @click="doUpload">上传</Button>
        </div>
      </div>
    </Modal>

    <!-- 资格预审申请弹窗（2026-10-02：前审标段投标人提交申请文件；招标方在管理端资格预审抽屉审查） -->
    <Modal v-model:open="preqOpen" title="资格预审申请" :footer="null" :width="560" wrap-class-name="white-modal">
      <div class="modal-body">
        <div v-if="preqLoading" class="modal-hint">加载中…</div>
        <template v-else>
          <template v-if="preqInfo">
            <div class="modal-hint">
              对「{{ preqTarget?.projectName }}」提交资格预审申请：{{ preqInfo.title }}
              <template v-if="preqInfo.applyDeadline">（申请截止 {{ fmtT(preqInfo.applyDeadline) }}）</template>
            </div>
            <div v-if="preqMyApply" class="modal-hint">
              你已提交申请：{{ fmtT(preqMyApply.applyTime) }} · 状态：<b>{{ preqStateText(preqMyApply) }}</b>
              <template v-if="preqMyApply.attachments?.length"> · 材料 {{ preqMyApply.attachments.length }} 份</template>
              <template v-if="preqMyApply.reviewReason">（{{ preqMyApply.reviewReason }}）</template>
            </div>
            <template v-else>
              <label class="file-pick">
                选择文件（可多选）
                <input ref="preqFileInput" type="file" :accept="DOC_ACCEPT" multiple @change="onPreqFilePicked" />
              </label>
              <div v-if="preqFiles.length" class="modal-hint">
                <div v-for="(fl, i) in preqFiles" :key="i">
                  {{ fl.name }}（{{ fmtSize(fl.size) }}）<a class="bid-link" @click="removePreqFile(i)">移除</a>
                </div>
              </div>
              <div v-else class="modal-hint">支持 PDF / Word / Excel / 压缩包，单个文件不超过 {{ DOC_MAX_MB }} MB，可一次选择多份材料。</div>
              <div class="modal-footer">
                <Button @click="preqOpen = false">取消</Button>
                <Button type="primary" :loading="preqSubmitting" @click="submitPrequalApply">提交申请</Button>
              </div>
            </template>
          </template>
          <div v-else class="modal-hint">该标段暂无已发布的资格预审。</div>
        </template>
      </div>
    </Modal>

    <!-- 关联公告：按该行编号列出外部公告（门户服务层 fetchNoticePage，一次只传一个编号参数），
         点标题 router.push 到门户公告详情 /portal/bidding/detail/:id（组件见 components/NoticeLinkModal.vue） -->
    <NoticeLinkModal
      v-model:open="noticeOpen"
      :follow-key="noticeKey"
      :follow-type="noticeType"
      :subject-name="noticeSubject"
    />

    <!-- 合并说明（2026-09-21 rebase 冲突）：上游这一段是「CA 认证工作台 + 服务导航 FloatBar」。
         前者是上游新加的功能（本页的 onSealed / stageSeal 都服务它），**整段保留**；
         `FloatBar` 按用户口径已彻底不显示（另两页也已无引用），而且上游那行所依赖的
         `import FloatBar` 在合并中已被本分支的删除带走 —— 留着就是个解析不到的组件，故删掉。 -->

    <!-- CA 认证工作台：二维码（测试态本地渲染，可真实扫出）+「模拟已扫码」→ 红章 PDF + SM2 签名 + 落库台账 -->
    <CaSealAuth
      v-if="sealTarget"
      :open="sealOpen"
      :file="sealFileOf(sealTarget)"
      :uploaded="sealTarget.uploadFile"
      :business="sealTarget.id"
      hide-trigger
      @update:open="(v: boolean) => { sealOpen = v }"
      @sealed="onSealed"
    />
</div>
</template>

<style scoped>
.module-page {
  min-height: 100%;
  /* 底色透明（2026-09-21）：整页的底不归本页管 —— 底图钉在 layouts/portal.vue 的
     .portal-shell--bidhero 上。原先这里是浅灰 --bid-bg，滚动时蓝条下沿会露出它，
     形成一道"截断"的浅色带。 */
  background: transparent;
  color: var(--bid-text-1);
}

/* 分组标题：「关注中」/「我的投标流程」两组的分界（2026-09-20 关注与建档分离后新增） */
.group-head {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 4px;
  padding: 12px 4px 6px;
  border-bottom: 1px solid var(--bid-border-soft);
}

.group-title {
  font-family: var(--bid-font);
  font-size: 14px;
  font-weight: 600;
  color: var(--bid-text-1);
}

.group-title::before {
  content: '';
  display: inline-block;
  width: 3px;
  height: 12px;
  margin-right: 6px;
  border-radius: 2px;
  background: var(--bid-primary);
  vertical-align: -1px;
}

.group-hint {
  font-size: 12px;
  color: var(--bid-text-3);
}

/* Banner：底纹与**尺寸规格**均与公告大厅 .hero 一致
 *   （2026-09-21 用户口径：四页蓝色背景先统一压到 160px 窄条、文字改左上角并缩小，
 *    当天又统一回到 200px，见下）。
 * 高度：**min-height: 100vh（满屏，2026-09-21 用户口径「加高背景部分高度，铺满试试」）**。
 *   · 沿革：固定内边距 40/20/66（≈190px）→ 1/3 屏高（33.333vh ≈ 360px）→ 160px → 本次回到 200px；
 *     本轮是配合公告大厅「四个模块入口移进蓝条 + 按钮放大」一起加的，四页同时改，
 *     否则切页时蓝条高度会跳。（当时本页蓝条里还没有那排入口卡；后来四页都放了 HeroMods，已一致。）
 *   · 仍用 min-height 而非 height —— 屏幕特别矮时不至于把文字挤出容器。
 * 文字位置（2026-09-21 下午口径）：标题段**水平居中**、标题 26 → 44px、副标题 14 → 20px
 *   （沿革：曾居中 → 左上角 40/16 → 左上角 26/14 → 本次居中 44/20）。
 *   左右内边距取 --bid-side-inset 的一半 —— 于是内容盒宽 = 100% − inset，
 *   与下方 .content（`width: calc(100% - var(--bid-side-inset))`）同宽同左右边缘
 *   （两者包含块同为 .module-page，百分比同源，必然对齐）；
 *   标题段居中后，这条列宽只由下方那排按钮体现 —— 而且它还要再往里收一圈去对齐玻璃卡里的**白卡**
 *   （用户 2026-09-21 口径「四个按钮的宽度与白卡对齐而不是玻璃卡」），见 HeroMods.vue。
 * 内容区与 Banner 的关系：**压在蓝条上、顶边落在屏幕纵向正中**
 *   （2026-09-21 最新口径「内容往上压，在屏幕页面 1/2 的位置」），
 *   `.content` 上外边距 `calc(-50vh - 64px - var(--bid-glass-lift))`，见其注释。
 *   （同一天早些时候还要求过「不压在蓝条上」，那是蓝条只有 200px 时的口径，已被取代。） */
.hero {
  position: relative;
  /* **一列两段、水平居中**（2026-09-21 下午用户口径：标题与小字移到正中间、放大字号；
     四个模块入口从标题右侧搬到标题下方那**整行**），与公告大厅 `.hero` 逐项一致。
     `align-items: center` 管的是交叉轴（＝水平）对齐 —— 标题段与下方那排按钮都居中
     （按钮排宽度比内容盒窄一圈，靠这条居中）。
     沿革（别再绕回去）：曾是「一行两段」（左标题 + 右按钮）+ `align-items: flex-start` +
     `justify-content: space-between` + `flex-wrap: nowrap` —— 那套全是「按钮在标题右边」时的口径。 */
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-start;
  /* **`gap: 0`**：标题段的纵向位置交给它自己的 `margin-top: auto`（见 styles/bid-ui.css 里
     `.hero-text` 的注释，算式也在那儿）。这里若还留 16px 行间距，标题与按钮之间就比上方多 16px，
     标题会被顶到中点上方约 8px —— 用户口径要的是「导航栏与按钮之间的正中间」。 */
  gap: 0;
  /* 满屏（2026-09-21 用户口径「加高背景部分高度，铺满试试」）：200px → min-height: 100vh。
     蓝条占满整个首屏（顶上门户导航条 64px 后，下缘落到首屏之外一点点，下面内容要往下滚一点）。
     仍用 min-height 而非 height：屏幕特别矮、或入口按钮降列数后内容变高时，条会跟着变高。
     想「门户导航条 + 蓝条」正好铺满首屏，改成 `calc(100vh - 64px)` 即可。 */
  min-height: 100vh;
  /* **上内边距 0**（原 26px）：标题段靠 `margin-top: auto` 定位，「导航栏与按钮之间的正中间」
     是拿本元素的内容盒顶边当上界的 —— 内容盒顶边必须正好等于门户导航栏下沿，留 26px 就会
     让标题比中点低 13px。左右仍是 `--bid-side-inset / 2`（决定内容盒宽＝内容列宽），下 22px 不变。 */
  padding: 0 calc(var(--bid-side-inset, 64px) / 2) 22px;
  overflow: hidden;
  color: #fff;
  /* 居中（2026-09-21 下午用户口径）。原来这里是 left（标题在左上角的口径）。 */
  text-align: center;
  /* 背景**透明**（2026-09-21 用户口径「照片铺满整页、钉住不滚」）：底图与蒙版已上移到
     layouts/portal.vue 的 .portal-shell--bidhero（钉在视口上的一层），取值一字未改 ——
     11 47 99 / 82% → 23 72 154 / 68%、裁切 `center top`，故首屏观感与改动前一致。
     本元素只留 `overflow: hidden` 与 `color: #fff`：白字仍压在那层照片上。 */
  background: transparent;
}

/* 标题与下面那行小字：2026-09-21 下午放大（用户口径「都移到正中间并放大字号」），
 *   26px → 44px、14px → 20px；**四页必须同值**（与公告大厅 .hero-title / .hero-sub 一致）。 */
.hero-title {
  margin: 0;
  font-size: 44px;
  font-weight: 800;
  /* 字距随字号收一档：字号 26px 时 1px 约 3.8%，44px 时 1px 只有 2.3%、显得松散 */
  letter-spacing: 2px;
  line-height: 1.25;
}

.hero-title .hl {
  color: var(--bid-accent-on-dark);
}

/* Banner 小字：浅白字；句子内容 2026-09-21 起与公告大厅统一
   （原来是组长给本模块单独写的那句价值话，已按用户口径换掉） */
.hero-sub {
  color: rgb(255 255 255 / 82%);
  font-size: 20px;
  line-height: 1.7;
  letter-spacing: 0.8px;
}

/* 内容区（选项卡 + 流程列表面板）：
 * 上外边距：**压在蓝条上、顶边落在屏幕纵向正中再往上抬 `--bid-glass-lift`**（2026-09-21 用户口径
 *   「内容往上压，在屏幕页面 1/2 的位置」＋同天下午「增加玻璃卡的高度，让四个按钮上移后可以压到
 *   玻璃卡」）—— `margin-top: calc(-50vh - 64px - var(--bid-glass-lift))`，算式见下面那段注释。
 *   沿革（别再绕回去）：-44px → -32px（压进蓝条）→ 20px（蓝条下方，当天的口径）→ 现在压在蓝条上、顶边在屏幕 1/2 处。
 * position/z-index 是**必需的**：Banner 是 position:relative（叠了几何纹理与底图），
 *   带定位的兄弟会盖在普通块之上；不给自己定位就会被蓝条盖住。
 * 宽度：左右对称收窄，两侧边距取共享变量 --bid-side-inset（随窗口缩放，每边 32~240px）。 */
.content {
  position: relative;
  z-index: 1;
  width: calc(100% - var(--bid-side-inset));
  /* 顶上位置（2026-09-21 用户口径「内容往上压，在屏幕页面 1/2 的位置」）：
     `margin-top: calc(-50vh - 64px)` —— 让本区的**顶边落在屏幕纵向正中**。
     算式：文档流里它的自然位置在蓝条下方 = 顶栏 64px + 蓝条 100vh，要把它提到 50vh 处，
     需要回压 `-(64px + 50vh)`。64px 是门户顶栏的高度（layouts/portal.vue 的 min-height）。
     2026-09-21 下午又**往上抬了 `--bid-glass-lift`（20px）**，所以末项是 `- var(--bid-glass-lift)` ——
     抬手同时把 `.bid-glass-shell` 的上内边距加同样的量，于是卡是**往上长高**、里面的白卡原地不动；
     按钮排那边也同步上移（见 components/HeroMods.vue）。**要调高度只改 --bid-glass-lift 那一处。**
     想微调落点：想落在 40% 处就把 `-50vh` 换成 `-60vh`（绝对值更大＝更往上）。
     （早先的「内容不要压在蓝条上」是蓝条只有 200px 时的口径，已被本条取代。）
     position/z-index 一直是必需的：Banner 是 position:relative，不给自己定位就会被蓝色盖住。 */
  margin: calc(-50vh - 64px - var(--bid-glass-lift, 0px)) auto 40px;
}

/* `.toolbar` 的样式已在 2026-09-21 随那行按钮一起删除（用户口径：
   顶部蓝条里那排模块入口卡已承担页面间跳转，「返回公告大厅」是重复入口）。 */

.panel {
  padding: 8px 22px 20px;
}

/* 流程条目 / 提示条 / 空态的样式已收敛到 styles/bid-ui.css
 * （.bid-item* / .bid-chip / .bid-note* / .bid-empty*），三页共用，此处不再重复定义。 */

/* ============ 步骤信息卡（每步「该看的信息」，2026-09-22 新增） ============
 * 配色全部取自 styles/bid-ui.css 的 --bid-* 令牌（不引新视觉体系）：
 * 白底卡 + 左侧 3px 色条表示该步结论（绿=通过 / 红=不通过或结束 / 蓝=进行中），
 * 步骤名沿用条目标题的字重口径，时间用等宽字体便于纵向对齐。 */
.step-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 0;
  margin: 10px 0 0;
  list-style: none;
}

.step-card {
  padding: 8px 12px 8px 14px;
  border: 1px solid var(--bid-border-soft);
  border-left: 3px solid var(--bid-border);
  border-radius: var(--bid-radius-sm);
  background: var(--bid-surface-2);
}

.step-card--ok {
  border-left-color: var(--bid-green);
}

.step-card--bad {
  border-left-color: var(--bid-red);
}

.step-card--info {
  border-left-color: var(--bid-primary);
}

.step-head {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: baseline;
}

.step-name {
  color: var(--bid-text-1);
  font-size: 13.5px;
  font-weight: 600;
}

.step-time {
  color: var(--bid-text-3);
  font-family: var(--bid-font-mono);
  font-size: 12px;
}

.step-line {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 8px;
  align-items: baseline;
  margin-top: 4px;
  color: var(--bid-text-2);
  font-size: 12.5px;
  line-height: 1.7;
}

.step-line b {
  flex: none;
  color: var(--bid-text-3);
  font-weight: 500;
}

.step-val {
  word-break: break-all;
}

/* 口径补充（如合规结论是「前端本地标记」）：小字弱化，不抢主信息 */
.step-foot {
  margin-top: 4px;
  color: var(--bid-text-4);
  font-size: 12px;
  line-height: 1.6;
}

/* ============ 操作留痕（倒序） ============ */
.trace {
  margin-top: 10px;
}

.trace-head {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: baseline;
  padding-bottom: 4px;
  border-bottom: 1px dashed var(--bid-border-soft);
  color: var(--bid-text-2);
  font-size: 12.5px;
  font-weight: 600;
}

.trace-src {
  color: var(--bid-text-4);
  font-size: 11.5px;
  font-weight: 400;
}

.trace-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 6px 0 0;
  margin: 0;
  list-style: none;
}

.trace-row {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 8px;
  align-items: baseline;
  color: var(--bid-text-2);
  font-size: 12.5px;
  line-height: 1.7;
}

.trace-time {
  flex: none;
  color: var(--bid-text-3);
  font-family: var(--bid-font-mono);
  font-size: 12px;
}

.trace-desc {
  color: var(--bid-text-3);
}

/* 弹窗内部（antd Modal 由 white-modal 全局类强制白底） */
.modal-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
  background: #fff;
}

.modal-hint {
  color: var(--bid-text-1);
  font-size: 12.5px;
  line-height: 1.7;
}

.modal-label {
  margin-bottom: 6px;
  color: var(--bid-text-1);
  font-size: 12.5px;
}

.ta {
  width: 100%;
  padding: 8px 10px;
  border: 1px solid var(--bid-border);
  border-radius: var(--bid-radius-sm);
  background: #fff;
  color: var(--bid-text-1);
  font-family: inherit;
  font-size: 13px;
  outline: none;
  resize: vertical;
}

.ta::placeholder {
  color: var(--bid-text-4);
}

.file-pick {
  display: inline-block;
  align-self: flex-start;
  padding: 8px 16px;
  border: 1px solid var(--bid-border);
  border-radius: var(--bid-radius-sm);
  background: #fff;
  color: var(--bid-primary-500);
  font-size: 13px;
  cursor: pointer;
}

.file-pick:hover {
  border-color: var(--bid-primary);
}

.file-pick input {
  display: none;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

/* 弹窗的白底与深色字统一在 styles/bid-ui.css 的 .white-modal 段处理
 * （antd Modal 挂到 body，令牌需在弹窗容器上重新定义，页面内不再重复写）。 */

/* 窄屏降级（条目纵向堆叠）已在 styles/bid-ui.css 的共享媒体查询里处理；
 * 两侧边距由 --bid-side-inset 自动收敛，无需额外媒体查询。 */

/* ============ 开标信息（投标人视角，<details> 原生展开） ============ */
.bid-open-note {
  margin-top: 10px;
  padding: 8px 12px;
  border: 1px dashed var(--bid-border);
  border-radius: var(--bid-radius-sm);
  background: var(--bid-surface-2);
  color: var(--bid-text-2);
  font-size: 12.5px;
  line-height: 1.7;
}

.bid-open-note summary {
  cursor: pointer;
  color: var(--bid-text-1);
}

.bid-open-note summary b {
  color: var(--bid-text-1);
  font-weight: 600;
}

.bid-open-list {
  width: 100%;
  margin-top: 8px;
  border-collapse: collapse;
  font-size: 12px;
}

.bid-open-list th,
.bid-open-list td {
  padding: 4px 8px;
  border: 1px solid var(--bid-border-soft);
  text-align: left;
}

.bid-open-list th {
  background: #fff;
  color: var(--bid-text-3);
  font-weight: 600;
}
</style>
