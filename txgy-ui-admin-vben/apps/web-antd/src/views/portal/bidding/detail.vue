<!--
  公告详情页（notice-external 域 · 单页完整展示）
  正文渲染优先级（01 §7.1 / 02 §3.1）：
    ① bodyBlocksStatus=SUCCESS 且 bodyBlocksJson 非空 → 按结构化块渲染（heading/paragraph/table/list，02 §4）；
    ② 回退 contentHtmlSafe（后端 jsoup 白名单净化 HTML：p/h1~h6/table/… 保留，script/on*/style 剥除）；
      遗留“单一长段落拍平正文”由 prettyNoticeHtml 公文化切段兜底；
  - PDF → 内嵌预览（后端字节流代理 /bidding/notice-external/pdf 公开免登；pdfDownloadUrl 签名地址优先）；
  - 仅简略信息 → 简略信息 + “前往原地址查看”。
  外链：一律经后端 /go 中转（origUrl 白名单校验，02 §3.3），前端不直接 window.open 外站。
  附件：downloadUrl=FileApi 签名下载；INFECTED 后端不下发链接（页面显示已拦截），PENDING 显示扫描中。
-->
<script lang="ts" setup>
import type { NoticeBodyBlock, NoticeExternalDetail } from './types';

import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { useAppConfig } from '@vben/hooks';
import { useAccessStore } from '@vben/stores';

import { message, Modal } from 'ant-design-vue';

import { createProcessTrack, fetchMyProcessTracks } from '#/api/bidding/processTrack';
import { saveAttachment } from '#/api/bidding/attachment';
import { uploadBiddingFile } from '#/api/bidding/file';
import { applyPrequal, getPrequalPage } from '#/api/bidding/prequal';
import { fmtBytes } from '#/utils/format';

import CopyText from '../../bidding/components/CopyText.vue';
import { buildExternalNoticePdfDownloadUrl, buildExternalNoticePdfPreviewUrl } from '../../bidding/noticeExternal/pdfUrl';
import { fetchNoticeDetail, followTarget, isFollowed, openOrigUrl, unfollowTarget } from './api';
import { biddingConfig } from './config';
import { blocksToHtml, cleanT, fmtDate, fmtDT, prettyNoticeHtml, toCleanList } from './format';
import { ensurePortalAccount } from './session';

const route = useRoute();
const router = useRouter();

const loading = ref(true);
const errorMsg = ref('');
const detail = ref<NoticeExternalDetail | null>(null);

/** 错误 → 用户可读提示：**不把权限/系统问题写成「你没登录」**（用户没有做错什么） */
function hintOf(e: unknown): string {
  const code = (e as null | undefined | { code?: number })?.code;
  const m = e instanceof Error ? e.message : String(e);
  // 401：登录态确实缺失/失效 —— 引导登录是对的
  if (code === 401 || /账号未登录|未登录/.test(m)) {
    return `登录状态已失效，请重新登录：${m}`;
  }
  // 403：权限没开通（系统/管理侧配置），不是让用户再登录一次
  if (code === 403) {
    return `当前账号没有该操作权限（权限未开通，属系统配置问题，请联系管理员）：${m}`;
  }
  return m;
}
/** 错误码取值（api.ts authedFetch 统一抛 ApiError；其余业务/网络错误无 code） */
function codeOf(e: unknown): number | undefined {
  return (e as null | undefined | { code?: number })?.code;
}
const accessStore = useAccessStore();

/* ===== 门户投标：用户类型判据（2026-09-29 用户反馈："明明已登录却要求登录"） =====
 *
 * 背景：个人用户注册在保留租户 tenant_id=0（后端 TenantSecurityWebFilter.PERSONAL_TENANT_ID=0，
 *   前端登录时 accessStore.setTenantId(0)），**不可能持有企业角色的权限码**；
 *   企业员工则属于某个企业租户。两者被"不能投标"拦下的**原因不同、下一步动作也不同**：
 *     · 个人用户：本功能未对个人用户开放 —— 让他去"完善企业档案"是死路（他根本没有企业）；
 *     · 企业员工：请联系贵公司对企业档案进行完善 —— 员工个人无权维护企业档案，必须告知公司。
 *   把两者合成一句"需登录 / 无权限"就是本次用户看到的错误体验。
 *
 * ⚠️ tenantId===0 是**前端推断**。后端下发 userType 后应优先用它，见契约
 *   docs/电子证照前前端契约/后端变更请求-门户投标个人用户提示与资格门-2026-09-29.md §3.1。 */
/** 严格判等 0：tenantId 初值为 null（"租户未确定"，如未登录/未走带租户的登录路径），
 *  不能把它当成个人用户——否则企业员工会被误报成"本功能未对个人用户开放"。
 *  个人用户的 0 由登录流程显式写入（auth.ts：loginType=personal → setTenantId(0)）。 */
const isPersonalAccount = computed(() => accessStore.tenantId === 0);

/** 投标资格门被拒 → 用户可照做的两句话（优先按后端业务码判定，前端不解析中文文案）
 *  码值口径见上述契约 §3.2：
 *    1_060_106_042 = 个人用户未开放（后端待新增）
 *    1_060_109_037 = 本租户尚未建立企业主体档案
 *    1_060_106_034 = 主体信息未完善 */
function subjectGateHint(e?: unknown): string {
  const code = codeOf(e);
  if (code === 1_060_106_042) return '本功能未对个人用户开放';
  if (code === 1_060_109_037 || code === 1_060_106_034) {
    return '请联系贵公司对企业档案进行完善';
  }
  // 后端尚未落地区分码时的兜底分流：按当前账号是不是个人用户给对应的一句话
  return isPersonalAccount.value
    ? '本功能未对个人用户开放'
    : '请联系贵公司对企业档案进行完善';
}

/** 该错误是否属于"被投标资格门拦下"（而非登录态/系统问题）
 *  判据：非 401，且是 403 或契约 §3.2 的三个业务码。 */
function isSubjectGateError(e: unknown): boolean {
  const code = codeOf(e);
  if (code === 401) return false;
  if (code === 403) return true;
  return code === 1_060_106_042 || code === 1_060_109_037 || code === 1_060_106_034;
}

/** 是否未登录 —— 判据只有一个：**body.code === 401**（§33《前端错误码分流契约》）。
 *  · 权限数组为空（roles/permissions 均为 []）是**正常状态**（code 仍为 0），不代表未登录；
 *  · code === 403 是"没有该操作权限"，**不等于未登录**，不能把人赶去登录页；
 *  · 关注域是「仅登录」范式（@PreAuthorize("isAuthenticated()")），本不该出现 403。 */
function isNotLoggedIn(e: unknown): boolean {
  return codeOf(e) === 401;
}
/** 未登录/无权限：去管理端登录页（login.vue 的 resolveRedirectPath 支持 /portal/ 前缀回跳当前详情） */
function goLogin(): void {
  router
    .push({
      path: '/auth/login',
      query: { redirect: `/portal/bidding/detail/${id.value}` },
    })
    .catch(() => {
      /* 导航取消忽略 */
    });
}

/** “查看原文”锚点 href：与 api.openOrigUrl 同构 —— 经后端 /go 白名单中转（Mock 态无 go 服务，回退原地址） */
function origGoHref(origUrl: string): string {
  if (biddingConfig.useMock) return origUrl;
  const api = `${biddingConfig.baseUrl}${biddingConfig.apiPrefix}/notice-external`;
  return `${api}/go?url=${encodeURIComponent(origUrl)}`;
}

/* ============ 意向投标（与「关注」相互独立，用户裁定口径） ============
 *   · 关注：轻量、可撤销（点上方的「＋ 关注」，取关只移出关注列表）；
 *   · 意向投标：**正式建档**，必须由用户在这里显式点击，建档后记录持久保留、只做状态流转不删除；
 *   · 取关**不影响**已建立的投标流程档案。
 *  数据源：GET /bidding/process-track/my（判是否已建档）；POST /bidding/process-track/create（建档）。
 */
/** 意向投标按钮态：
 *  · idle/none         → 可点击
 *  · archived          → 已建档
 *  · unavailable       → 确实没登录（唯一会跳登录页的态）
 *  · blocked           → **被业务规则拦下**（个人用户未开放 / 企业档案待完善）：
 *                        登录态有效，只是这件事现在办不了 —— 给原因，不跳登录页
 *  · error             → 系统侧异常，可重试 */
const intentState = ref<
  'archived' | 'blocked' | 'error' | 'idle' | 'none' | 'unavailable'
>('idle');
const intentBusy = ref(false);
/** 系统侧失败原因（非用户过错）：按钮转「账号信息异常」态并允许重试，**不**把人赶去登录页 */
const intentHint = ref('');

/** 探测该公告是否已建档（按 bidSectionCode = 公告 id 匹配；/my 未下发 followKey 时回退 bidSectionCode） */
async function probeIntent(): Promise<void> {
  intentHint.value = '';
  // 关键（2026-09-29 修复）：公告详情是公开页(meta.ignoreAccess)，路由守卫不会预加载账号信息，
  // 这里必须先补一次 —— 否则**已登录**用户会因 userInfo 为空被误判成「未登录」。
  const account = await ensurePortalAccount();
  if (!account.ok) {
    if (account.reason === 'anonymous') {
      intentState.value = 'unavailable';
    } else {
      // 登录态有效但系统取不到账号：说系统问题，不说用户没登录
      intentState.value = 'error';
      intentHint.value = account.message;
    }
    return;
  }
  try {
    const rows = (await fetchMyProcessTracks(account.userId)) || [];
    intentState.value = rows.some(
      (r) => String(r.followKey ?? r.bidSectionCode ?? '') === id.value,
    )
      ? 'archived'
      : 'none';
  } catch (e) {
    // /my 失败不等于未登录：只有 401 才引导登录
    if (codeOf(e) === 401) {
      intentState.value = 'unavailable';
    } else if (isSubjectGateError(e)) {
      // 被投标资格门拦下：登录态有效，给"能不能办、该找谁"的一句话，不跳登录页
      intentState.value = 'blocked';
      intentHint.value = subjectGateHint(e);
    } else {
      intentState.value = 'error';
      intentHint.value = hintOf(e);
    }
  }
}

/** 意向投标：显式建档。关注 ≠ 意向 —— 此动作不会在关注时自动发生，也不要求先关注。 */
async function doIntent(): Promise<void> {
  if (intentBusy.value) return;
  if (intentState.value === 'archived') return;
  // 被业务规则拦下：只解释原因（个人用户未开放 / 企业档案待完善），**不**跳登录页
  if (intentState.value === 'blocked') {
    message.warning(intentHint.value || subjectGateHint());
    return;
  }
  // 系统侧失败态：重试探测，而不是把用户赶去登录页（他本来就是登录的）
  if (intentState.value === 'error') {
    await probeIntent();
    if (intentState.value === 'error' && intentHint.value) {
      message.warning(intentHint.value);
    }
    return;
  }
  // 用前先保证账号可识别：公开页不预加载账号信息，直接读 userInfo 会把已登录用户判成未登录
  const account = await ensurePortalAccount();
  if (!account.ok) {
    if (account.reason === 'anonymous') {
      goLogin();
    } else {
      intentState.value = 'error';
      intentHint.value = account.message;
      message.warning(account.message);
    }
    return;
  }
  const userId = account.userId;
  intentBusy.value = true;
  try {
    await createProcessTrack({
      userId,
      bidSectionCode: id.value,
      projectName: cleanT(detail.value?.title) || id.value,
      issuerName: detail.value?.issuerName ?? undefined,
    });
    intentState.value = 'archived';
    message.success('已建立投标流程（意向投标），可在「投标流程管理」查看');
  } catch (e) {
    // 失败提示由请求层统一弹出；此处只维护按钮态：401 才是"需登录"
    if (codeOf(e) === 401) {
      intentState.value = 'unavailable';
    } else if (isSubjectGateError(e)) {
      // 建档被资格门拒（后端接线后即走这里）：个人用户 / 企业档案待完善，两句话分开说
      intentState.value = 'blocked';
      intentHint.value = subjectGateHint(e);
      message.warning(intentHint.value);
    } else {
      intentState.value = 'error';
      intentHint.value = hintOf(e);
    }
  } finally {
    intentBusy.value = false;
  }
}

/** 项目级次级关注（跟踪该招标项目；targetType=PROJECT + targetKey=projectKey，BR-1/01 §3） */
const projFollowState = ref<'followed' | 'idle' | 'unavailable' | 'unfollowed'>('idle');
const projFollowBusy = ref(false);
const projFollowHint = ref('');

async function probeProjFollow(): Promise<void> {
  const k = projectKeyVal.value;
  if (!k) return;
  try {
    const followed = await isFollowed('PROJECT', k);
    projFollowState.value = followed ? 'followed' : 'unfollowed';
  } catch (e) {
    // 未登录/无权限等：隐藏按钮，给一句说明（浏览与项目跟踪不受影响）
    projFollowState.value = 'unavailable';
    projFollowHint.value = hintOf(e);
  }
}

async function toggleProjFollow(): Promise<void> {
  const k = projectKeyVal.value;
  if (!k || projFollowBusy.value) return;
  if (projFollowState.value === 'unavailable') {
    goLogin();
    return;
  }
  projFollowBusy.value = true;
  try {
    if (projFollowState.value === 'followed') {
      await unfollowTarget({ targetType: 'PROJECT', targetKey: k });
      projFollowState.value = 'unfollowed';
    } else {
      await followTarget({ targetType: 'PROJECT', targetKey: k });
      projFollowState.value = 'followed';
    }
  } catch (e) {
    // 只有 401 才切「需登录」；403 保持按钮可用
    if (isNotLoggedIn(e)) {
      projFollowState.value = 'unavailable';
    }
    projFollowHint.value =
      isNotLoggedIn(e) || codeOf(e) === 403 ? hintOf(e) : '操作失败，请稍后重试';
  } finally {
    projFollowBusy.value = false;
  }
}

/** 兜底：未捕获的运行时/异步错误显示到页面上，便于定位（开发辅助） */
const pageError = ref('');
function onUnhandled(e: ErrorEvent | PromiseRejectionEvent): void {
  const reason = e instanceof PromiseRejectionEvent ? e.reason : e.error;
  const msg =
    reason instanceof Error ? `${reason.name}: ${reason.message}` : String(reason ?? e);
  pageError.value = msg || '未知异常';
  console.error('[bidding-detail]', reason);
}

const id = computed(() => String(route.params.id ?? ''));
/** 项目跟踪键（BR-1：detail.projectKey 存在 → 详情提供“跟踪该项目”次级关注） */
const projectKeyVal = computed(() => cleanT(detail.value?.projectKey));

/** 标段跟踪列表（01 §3：bidSectionKeys/bidSectionNames 下发则优先，否则用 bidSectionCodes 原文；逐标段可关注 SECTION） */
interface SecTrack {
  key: string;
  code: string;
  name: string;
}
const sections = computed<SecTrack[]>(() => {
  const d2 = detail.value;
  if (!d2) return [];
  const codes = toCleanList(d2.bidSectionCodes);
  const keys = Array.isArray(d2.bidSectionKeys)
    ? d2.bidSectionKeys.map((k) => cleanT(k)).filter(Boolean)
    : [];
  const names = Array.isArray(d2.bidSectionNames) ? d2.bidSectionNames : [];
  if (!codes.length && !keys.length) return [];
  const base = keys.length ? keys : codes;
  return base
    .map((k, i) => ({
      key: keys[i] || codes[i] || k,
      code: codes[i] || k,
      name: cleanT(names[i] ?? ''),
    }))
    .filter((s) => !!s.key);
});
type SecState = 'followed' | 'idle' | 'unavailable' | 'unfollowed';
const sectionFollows = ref<Record<string, { busy: boolean; hint: string; state: SecState; }>>({});
function secStateOf(s: SecTrack): { busy: boolean; hint: string; state: SecState; } {
  return sectionFollows.value[s.key] || { state: 'idle', busy: false, hint: '' };
}
/* ============ 资格预审申请（2026-10-02：公告详情标段行直达；前审标段才显示按钮） ============ */
const PREQ_ACCEPT = '.pdf,.doc,.docx,.xls,.xlsx,.zip,.rar,.7z';
const PREQ_MAX_MB = 16;
/** 标段 key → 存在已发布资格预审（按钮据此出现） */
const preqFlag = ref<Record<string, boolean>>({});
const preqTried = ref<Record<string, boolean>>({});
const preqOpen = ref(false);
const preqLoading = ref(false);
const preqInfo = ref<null | Record<string, any>>(null);
const preqSectionKey = ref('');
const preqFiles = ref<File[]>([]);
const preqFileInput = ref<HTMLInputElement | null>(null);
const preqSubmitting = ref(false);

async function loadPrequalFlags(): Promise<void> {
  const keys = Array.from(new Set(sections.value.map((s) => s.key).filter(Boolean)));
  const next: Record<string, boolean> = {};
  for (const k of keys) {
    if (preqTried.value[k]) {
      next[k] = preqFlag.value[k] ?? false;
      continue;
    }
    try {
      const page = await getPrequalPage({ pageNo: 1, pageSize: 1, bidSectionCode: k, status: 'PUBLISHED' });
      next[k] = (page?.list ?? []).length > 0;
    } catch {
      next[k] = false;
    }
    preqTried.value[k] = true;
  }
  if (Object.keys(next).length) preqFlag.value = { ...preqFlag.value, ...next };
}
watch(sections, () => void loadPrequalFlags(), { immediate: true });

async function openPrequal(s: { key: string }): Promise<void> {
  preqSectionKey.value = s.key;
  preqOpen.value = true;
  preqLoading.value = true;
  preqInfo.value = null;
  preqFiles.value = [];
  try {
    const page = await getPrequalPage({ pageNo: 1, pageSize: 1, bidSectionCode: s.key, status: 'PUBLISHED' });
    preqInfo.value = (page?.list ?? [])[0] ?? null;
  } catch {
    preqInfo.value = null;
  } finally {
    preqLoading.value = false;
  }
}
function onPreqPick(e: Event): void {
  const input = e.target as HTMLInputElement;
  const files = Array.from(input.files ?? []);
  if (!files.length) return;
  const bad = files.find((fl) => {
    const ext = '.' + (fl.name.split('.').pop() ?? '').toLowerCase();
    return !PREQ_ACCEPT.split(',').includes(ext) || fl.size > PREQ_MAX_MB * 1024 * 1024;
  });
  if (bad) {
    preqFiles.value = [];
    message.warning('仅支持 PDF / Word / Excel / 压缩包，单个文件不超过 ' + PREQ_MAX_MB + ' MB（' + bad.name + '）');
    return;
  }
  preqFiles.value = [...preqFiles.value, ...files];
  if (preqFileInput.value) preqFileInput.value.value = '';
}
function removePreqFile(i: number): void {
  preqFiles.value.splice(i, 1);
}
async function submitPrequalApply(): Promise<void> {
  if (!preqInfo.value || preqFiles.value.length === 0) {
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
    // 主件 = 第一个文件（落 prequal_apply.file_id）；其余绑 PREQUAL_APPLY 附件集（契约多文件化 R3）
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
    preqOpen.value = false;
    preqFiles.value = [];
    message.success(
      uploaded.length > 1
        ? '资格预审申请已提交（含 ' + uploaded.length + ' 份材料），等待招标方审查'
        : '资格预审申请已提交，等待招标方审查',
    );
  } catch {
    /* 失败提示由请求层统一弹出 */
  } finally {
    preqSubmitting.value = false;
  }
}

async function probeSectionFollows(): Promise<void> {
  const secs = sections.value;
  await Promise.all(
    secs.map(async (s) => {
      try {
        const followed = await isFollowed('SECTION', s.key);
        sectionFollows.value[s.key] = { state: followed ? 'followed' : 'unfollowed', busy: false, hint: '' };
      } catch (e) {
        sectionFollows.value[s.key] = { state: 'unavailable', busy: false, hint: hintOf(e) };
      }
    }),
  );
}
async function toggleSectionFollow(s: SecTrack): Promise<void> {
  const st = secStateOf(s);
  if (st.busy) return;
  if (st.state === 'unavailable') {
    goLogin();
    return;
  }
  sectionFollows.value[s.key] = { ...st, busy: true };
  try {
    if (st.state === 'followed') {
      await unfollowTarget({ targetType: 'SECTION', targetKey: s.key });
      sectionFollows.value[s.key] = { state: 'unfollowed', busy: false, hint: '' };
    } else {
      await followTarget({ targetType: 'SECTION', targetKey: s.key });
      sectionFollows.value[s.key] = { state: 'followed', busy: false, hint: '' };
    }
  } catch (e) {
    // 仅登录态错误（401/403）才切“需登录”；其余失败保留原状态，只给重试提示
    sectionFollows.value[s.key] = {
      ...st,
      busy: false,
      hint: isNotLoggedIn(e) || codeOf(e) === 403 ? hintOf(e) : '操作失败，请稍后重试',
      state: isNotLoggedIn(e) ? 'unavailable' : st.state,
    };
  }
}

/** 结构化块：首个与标题完全重复的块级标题（外站正文常整句回显）裁掉，避免标题下再叠一遍 */
function trimLeadHeading(
  blocks: NoticeBodyBlock[] | undefined,
  title: string,
): NoticeBodyBlock[] {
  if (!blocks?.length) return [];
  const first = blocks[0];
  if (!first || first.type !== 'heading' || !first.text || first.level > 2) return blocks;
  const norm = (x: string) => x.replace(/\s+/g, '').replace(/[/／]/g, '');
  const tn = norm(cleanT(title));
  return tn && norm(first.text) === tn ? blocks.slice(1) : blocks;
}

const bodyHtml = computed(() => {
  const d = detail.value;
  if (!d) return '';
  if (d.bodyBlocksStatus === 'SUCCESS') {
    const blocks = blocksToHtml(trimLeadHeading(d.bodyBlocks, d.title));
    if (blocks) return blocks;
  }
  return prettyNoticeHtml(d.contentHtmlSafe, d.title);
});
const hasBodyHtml = computed(() => !!bodyHtml.value);
const isPdf = computed(() => !hasBodyHtml.value && detail.value?.contentType === 'PDF');
const { apiURL } = useAppConfig(import.meta.env, import.meta.env.PROD);
/* 解析口径见 views/bidding/noticeExternal/pdfUrl.ts（单一真源，后台审核详情共用，避免再次分叉）。
 * 预览 / 下载**两个地址**（拆分理由见该文件头）：
 *   iframe 必须用 inline 型地址（后端字节流代理），签名地址是下载型、塞进 iframe 会直接触发下载。 */
const pdfPreviewUrl = computed(() =>
  buildExternalNoticePdfPreviewUrl(apiURL, id.value, detail.value?.pdfDownloadUrl),
);
const pdfSaveUrl = computed(() =>
  buildExternalNoticePdfDownloadUrl(apiURL, id.value, detail.value?.pdfDownloadUrl),
);
const canPreviewPdf = computed(() => !!pdfPreviewUrl.value);
/** 附件区是否没有任何可下载链接（FileApi 签名地址尚未回填 / 数据源无附件样例，R6） */
const noRealDownload = computed(
  () => !!detail.value?.attachments?.length && !detail.value.attachments.some((a) => a.downloadUrl),
);

const metaPairs = computed(() => {
  const d = detail.value;
  if (!d) return [];
  const pairs: Array<{ copy?: boolean; k: string; v: string }> = [];
  const issuer = cleanT(d.issuerName);
  if (issuer) pairs.push({ k: '发布单位', v: issuer });
  if (fmtDT(d.publishTime)) pairs.push({ k: '发布时间', v: fmtDT(d.publishTime) });
  const eff = [fmtDate(d.effectiveFrom), fmtDate(d.effectiveTo)].filter(Boolean).join(' ~ ');
  if (eff) pairs.push({ k: '生效期', v: eff });
  const proj = cleanT(d.projectName);
  const pKey = cleanT(d.projectKey);
  if (proj) pairs.push({ k: '关联项目', v: proj });
  // 项目编号 / 投标人拿去搜索或对外引用时都要原样复制 → 带一键复制（CopyText 的失败提示口径见组件注释）
  if (pKey) pairs.push(proj ? { copy: true, k: '项目编号', v: pKey } : { copy: true, k: '关联项目', v: pKey }); // BR-1：键存在即展示（有名称时编号单列）
  const srcName = cleanT(d.sourceName);
  const srcCode = cleanT(d.sourceCode);
  if (srcCode || srcName) pairs.push({ copy: Boolean(srcCode), k: '来源', v: [srcCode, srcName].filter(Boolean).join(' · ') }); // 有来源编码时才可复制（只有名称时复制意义不大）
  const nType = cleanT(d.noticeTypeRaw);
  if (nType) pairs.push({ k: '公告性质', v: nType });
  if (d.legalFlag === 'Y') pairs.push({ k: '依法必招', v: '是' });
  const cType = cleanT(d.contentType);
  if (cType) pairs.push({ k: '内容形态', v: cType });
  // 结构化信息：仅当已有正文块时提示（HTML_STRUCTURE=常规解析 / AI_FALLBACK=AI 兜底排版，01 §7）
  if (d.bodyBlocksStatus === 'SUCCESS' && d.bodyParserCode) {
    pairs.push({ k: '正文结构', v: d.bodyParserCode === 'AI_FALLBACK' ? 'AI 兜底排版' : '原文结构化' });
  }
  return pairs;
});

const tagList = computed(() => {
  const d = detail.value;
  if (!d) return [] as string[];
  const tags: string[] = [];
  if (d.noticeTypeRaw) tags.push(d.noticeTypeRaw);
  if (d.contentType) tags.push(d.contentType);
  if (d.industryFlag === 'BELONGS') tags.push('行业相关');
  if (d.industryFlag === 'UNCERTAIN') tags.push('行业未知');
  if (d.legalFlag === 'Y') tags.push('依法必招');
  const src = cleanT(d.sourceName);
  if (src) tags.push(`来源：${src}`);
  return tags;
});

const fmtSize = (n?: number) =>
  n == null
    ? ''
    : fmtBytes(n);

const virusText = (v?: string) =>
  v === 'CLEAN' ? { text: '已扫描 CLEAN', bad: false } : v === 'PENDING' ? { text: '扫描中 PENDING', bad: false } : v === 'INFECTED' ? { text: '文件含病毒 · 已拦截', bad: true } : { text: '', bad: false };

async function load(): Promise<void> {
  loading.value = true;
  errorMsg.value = '';
  try {
    detail.value = await fetchNoticeDetail(id.value);
    if (detail.value) {
      void probeProjFollow();
      void probeIntent();
      void probeSectionFollows();
    }
  } catch (e) {
    errorMsg.value = e instanceof Error ? e.message : String(e);
    detail.value = null;
  } finally {
    loading.value = false;
  }
}

function goBack(): void {
  if (window.history.length > 1) {
    router.back();
  } else {
    router.push('/portal/bidding').catch(() => {
      /* 导航被取消/重复导航：忽略 */
    });
  }
}

onMounted(() => {
  window.addEventListener('unhandledrejection', onUnhandled);
  window.addEventListener('error', onUnhandled);
  void load();
});
onBeforeUnmount(() => {
  window.removeEventListener('unhandledrejection', onUnhandled);
  window.removeEventListener('error', onUnhandled);
});
</script>

<template>
<!--
  门户 · 外部公告详情（公开只读 + 项目/标段跟踪 + 外链）
  业务：完整展示一条外部公告：结构化字段+净化正文+PDF/附件（可见性分层 L1/L2）+外链白名单中转(/go?url=)；
        登录用户可「跟踪该项目」/「关注该标段」（意向起点），未登录仅浏览。
        **公告级关注（targetType=ANNOUNCEMENT）入口已按用户口径下线**：门户关注只保留「按编号/代码的项目跟踪」，
        本页不再创建公告级关注行（后端零改动；follow/unfollow 仍用于 PROJECT / SECTION 两维）。
  理由：完整信息免费及时查阅（10号令第12条）；HTML 净化防存储型XSS；外链走白名单 302 防开放重定向（安全评审）。
    依据（法规/政府文件/法律）：
      · 招标公告和公示信息发布管理办法（发改委令第10号，部门规章/政府文件）第12/13条——免费、及时查阅完整公告公示信息；免费归类与检索、新公告醒目标识；
      · 电子招标投标办法（八部委令第20号，部门规章/政府文件）第9条——市场主体免费注册登录、获取依法公开的招标投标信息；
      · 招标投标法 第16条（法律）——公开招标应当发布招标公告（公告公开性的上位依据）。
    依据（技术安全）：前端净化（DOMPurify/白名单）——安全评审要求存储型 XSS 载荷不下发（01 接口文档 §2.4）。
    后端契约：01-接口逐一说明与设计依据.md §2（detail 三形态/附件 virusStatus、go 白名单 302）；外部公告-前端对接/01-接口契约.md §1；
      follow/unfollow（bidding:notice-external:update，按登录人 user_id，T 轨）**仅用于项目/标段两维**；
      GET /bidding/notice-external/detail、/go（本页已不再调用公告级 /follow 入口）。
-->

  <div class="detail-page bid-ind" v-if="detail">
    <div v-if="pageError" class="err-banner">页面异常：{{ pageError }}（可在控制台查看详细堆栈）</div>
    <div class="top-bar">
      <button class="back" @click="goBack()">‹ 返回上一页</button>
      <span class="sep">|</span>
      <span class="crumb">公告详情</span>
    </div>

    <div class="head-card">
      <div class="head-top">
        <div class="tags">
          <span v-for="t in tagList" :key="t" class="tag">{{ t }}</span>
        </div>
        <div class="head-actions">
          <!-- 公告级「＋ 关注」按钮已按用户口径删除（门户关注只保留「跟踪该项目」PROJECT 与「关注该标段」SECTION）：
               本页不再创建 targetType=ANNOUNCEMENT 的关注行，后端零改动。 -->
          <!-- 意向投标：与「关注」相互独立（关注可撤销、建档持久保留），必须显式点击才建档 -->
          <button
            v-if="intentState !== 'idle'"
            class="go-orig"
            :class="{
              active: intentState === 'archived',
              need:
                intentState === 'unavailable' ||
                intentState === 'error' ||
                intentState === 'blocked',
            }"
            :disabled="intentBusy || intentState === 'archived'"
            @click="doIntent()"
          >
            <!-- blocked 态直接显示**被拦的原因摘要**（读屏与窄屏都能看到，不用点开才知道）：
                 个人用户 → 未开放；企业员工 → 找公司完善档案。两者都不引导登录。 -->
            {{
              intentState === 'archived'
                ? '已建档 ✓'
                : intentBusy
                  ? '处理中…'
                  : intentState === 'unavailable'
                    ? '意向投标 · 需登录'
                    : intentState === 'blocked'
                      ? isPersonalAccount
                        ? '意向投标 · 未对个人开放'
                        : '意向投标 · 需完善企业档案'
                      : intentState === 'error'
                        ? '意向投标 · 系统异常，点此重试'
                        : '意向投标'
            }}
          </button>
          <a
            v-if="detail.origUrl"
            class="go-orig"
            :href="origGoHref(detail.origUrl)"
            title="经平台白名单中转跳转原站（防止外链绕过管控）"
            target="_blank"
            rel="noopener noreferrer"
            @click.prevent="openOrigUrl(detail.origUrl)"
          >查看原文 ↗</a>
          <!-- 失败原因可见化：区分 401（登录态失效）与 403/系统故障，避免把权限/系统问题说成用户没登录 -->
          <span v-if="intentHint" class="act-hint">{{ intentHint }}</span>
        </div>
      </div>
      <h1>{{ detail.title }}</h1>
      <div class="meta">
        <span v-for="p in metaPairs" :key="p.k">
          <b>{{ p.k }}</b>
          <CopyText v-if="p.copy" :label="p.k" :text="p.v" />
          <template v-else>{{ p.v }}</template>
        </span>
      </div>
      <!-- BR-1：detail.projectKey 存在 → 项目级次级关注（跟踪该招标项目）；未登录/无权限时不展示按钮只给提示 -->
      <div v-if="projectKeyVal" class="track-row">
        <span class="track-label">项目跟踪</span>
        <span class="track-key">{{ projectKeyVal }}</span>
        <button
          v-if="projFollowState !== 'idle'"
          class="go-orig"
          :class="{ active: projFollowState === 'followed', need: projFollowState === 'unavailable' }"
          :disabled="projFollowBusy"
          @click="toggleProjFollow()"
        >
{{ projFollowState === 'followed' ? '已关注该项目 ✓' : projFollowBusy ? '处理中…' : projFollowState === 'unavailable' ? '＋ 跟踪该项目 · 需登录' : '＋ 跟踪该项目' }}
</button>
        <button v-if="projFollowState === 'unavailable'" class="go-orig ghost" @click="goLogin()">去登录</button>
        <span v-if="projFollowHint" class="follow-note">跟踪：{{ projFollowHint }}（浏览不受影响）</span>
      </div>

      <!-- 标段跟踪（01 §3：bidSectionCodes/Keys 存在 → 逐标段关注；订阅页“标段”维度行 = 这些关注） -->
      <div v-if="sections.length" class="track-row secs">
        <span class="track-label">标段跟踪</span>
        <div class="sec-list">
          <div v-for="s in sections" :key="s.key" class="sec-item">
            <span class="track-key" :title="s.code && s.code !== s.key ? s.code : ''">{{ s.name || s.code || s.key }}</span>
            <button
              v-if="secStateOf(s).state !== 'idle'"
              class="go-orig"
              :class="{ active: secStateOf(s).state === 'followed', need: secStateOf(s).state === 'unavailable' }"
              :disabled="secStateOf(s).busy"
              @click="toggleSectionFollow(s)"
            >
{{ secStateOf(s).state === 'followed' ? '已关注该标段 ✓' : secStateOf(s).busy ? '处理中…' : secStateOf(s).state === 'unavailable' ? '＋ 关注该标段 · 需登录' : '＋ 关注该标段' }}
</button>
            <button
              v-if="preqFlag[s.key]"
              class="go-orig ghost"
              type="button"
              @click="openPrequal(s)"
            >资格预审申请</button>
            <button v-if="secStateOf(s).state === 'unavailable'" class="go-orig ghost" @click="goLogin()">去登录</button>
            <span v-if="secStateOf(s).hint" class="follow-note">标段：{{ secStateOf(s).hint }}</span>
          </div>
        </div>
      </div>
      <p v-if="intentState === 'archived'" class="follow-note">
        已建立投标流程档案（建档后持久保留）；取消关注不会影响该档案。
      </p>
      <!-- 被业务规则拦下：写清楚"为什么"和"下一步找谁"，避免用户误以为是登录问题 -->
      <p v-if="intentState === 'blocked' && intentHint" class="follow-note">
        {{ intentHint }}
        <template v-if="isPersonalAccount">
          （当前账号为个人用户；企业投标需使用企业账号，由企业管理员在后台维护企业档案后开通。）
        </template>
        <template v-else>
          （企业档案由贵公司的管理员在后台「设置 · 主体档案」维护，员工个人无维护权限。）
        </template>
      </p>
    </div>

    <!-- 资格预审申请弹窗（公告详情标段行直达；多文件主件+附件集，契约多文件化 R3） -->
    <Modal v-model:open="preqOpen" title="资格预审申请" :footer="null" :width="560" wrap-class-name="white-modal">
      <div class="modal-body">
        <div v-if="preqLoading" class="follow-note">加载中…</div>
        <template v-else>
          <template v-if="preqInfo">
            <div class="follow-note">
              对「{{ preqInfo.title }}」提交资格预审申请
              <template v-if="preqInfo.applyDeadline">（申请截止 {{ fmtDT(preqInfo.applyDeadline) }}）</template>
            </div>
            <label style="cursor:pointer;color:#2b6cb0">
              选择文件（可多选）
              <input ref="preqFileInput" type="file" :accept="PREQ_ACCEPT" multiple style="display:none" @change="onPreqPick" />
            </label>
            <div v-if="preqFiles.length">
              <div v-for="(fl, i) in preqFiles" :key="i" style="font-size:12px;margin-top:4px">
                {{ fl.name }}（{{ fmtBytes(fl.size) }}）<a @click="removePreqFile(i)">移除</a>
              </div>
            </div>
            <div v-else style="font-size:12px;color:#718096;margin-top:6px">
              支持 PDF / Word / Excel / 压缩包，单个文件不超过 {{ PREQ_MAX_MB }} MB，可一次选择多份材料。
            </div>
            <div class="modal-footer">
              <button class="go-orig ghost" type="button" @click="preqOpen = false">取消</button>
              <button class="go-orig" type="button" :disabled="preqSubmitting" @click="submitPrequalApply">
                {{ preqSubmitting ? '提交中…' : '提交申请' }}
              </button>
            </div>
          </template>
          <div v-else class="follow-note">该标段暂无已发布的资格预审。</div>
        </template>
      </div>
    </Modal>

    <!-- 正文：结构化块 优先 → contentHtmlSafe 回退（统一公文样式渲染） -->
    <div v-if="hasBodyHtml" class="body-area">
      <div class="docx" v-dompurify-html="bodyHtml"></div>
    </div>

    <!-- 形态 · PDF：内嵌预览（后端字节流代理 /bidding/notice-external/pdf 公开免登，签名地址优先） -->
    <div v-else-if="isPdf" class="body-area">
      <div class="pdf-panel">
        <div v-if="canPreviewPdf" class="pdf-frame-wrap">
          <!-- 预览：进入详情即内嵌显示（与 HTML 公告的正文展示同级，无需再点按钮） -->
          <iframe class="pdf-frame" :src="pdfPreviewUrl" title="公告 PDF 预览"></iframe>
          <div class="sub pdf-actions">
            <a :href="pdfPreviewUrl" target="_blank" rel="noopener noreferrer">新窗口打开</a>
            <a v-if="pdfSaveUrl" :href="pdfSaveUrl" download rel="noopener">下载</a>
          </div>
        </div>
        <div v-else class="pdf-empty">
          无法定位该公告的 PDF：缺少公告 id（字节流代理需要 id）且签名地址未回填，暂不提供预览/下载。
          <div class="sub">
            说明：只要公告 id 存在，本页会自动走后端字节流代理 /bidding/notice-external/pdf；此处为两条路径都不可用。
          </div>
          <div v-if="detail.extractionStatus" class="sub">抽取状态：{{ detail.extractionStatus }}</div>
        </div>
      </div>
    </div>

    <!-- 仅简略信息（无正文 HTML / 无 PDF 预览） -->
    <div v-else class="body-area">
      <div class="slim-box">
        <p>该公告仅有简略信息（标题、发布单位、发布时间见上方），无正文、无文件。</p>
        <button v-if="detail.origUrl" class="btn primary" @click="openOrigUrl(detail.origUrl)">
          前往原地址查看
        </button>
      </div>
    </div>

    <!-- 附件区：downloadUrl=FileApi 签名下载；INFECTED 显示“已拦截”（后端不下发链接，R6） -->
    <div v-if="detail.attachments?.length" class="body-area">
      <h2 class="sec">附件下载</h2>
      <div class="dl-card">
        <div v-for="a in detail.attachments" :key="a.id" class="dl-row">
          <span class="fn">{{ a.name }}</span>
          <span v-if="a.size" class="fs">{{ fmtSize(a.size) }}<template v-if="a.ext"> · {{ a.ext.toUpperCase() }}</template></span>
          <span v-if="virusText(a.virusStatus).text" class="st" :class="{ bad: virusText(a.virusStatus).bad }">{{ virusText(a.virusStatus).text }}</span>
          <span class="spacer"></span>
          <a
            v-if="a.virusStatus === 'CLEAN' && a.downloadUrl"
            class="dl-link"
            :href="a.downloadUrl"
            target="_blank"
            rel="noopener noreferrer"
          >下载链接</a>
          <span v-else-if="a.virusStatus === 'PENDING'" class="dl-link dim">下载链接</span>
          <span v-else-if="a.virusStatus === 'INFECTED'" class="blocked">已拦截</span>
          <span v-else class="dl-link dim">下载链接</span>
        </div>
      </div>
      <p v-if="noRealDownload" class="foot-note">文件服务（FileApi）签名下载地址暂未回填（后端 R6 待签名能力接入），当前数据源暂无真实下载链接。</p>
    </div>

    <div class="foot-note" v-if="detail.contentType === 'HTML'">
      正文为后端净化版 contentHtmlSafe / 结构化正文块（02 §3.1：保留段落与表格结构，已剥除脚本与事件属性；如需源站原版观感请点“查看原文”）。
    </div>
  </div>

  <div class="state-card" v-else>
    <p v-if="loading">加载中…</p>
    <p v-else-if="errorMsg" class="err">{{ errorMsg }}</p>
    <p v-else>公告不存在或已下架</p>
    <button v-if="!loading" class="back" @click="goBack()">‹ 返回公告大厅</button>
  </div>
</template>

<style scoped>
/* 内容列宽口径（2026-09-20 用户口径）：
 *   ① 与公告大厅「公告部分」（.table-wrap）**同宽同左边缘** —— 都取「两侧对称让出 --bid-side-inset」；
 *   ② 页内所有区块（返回栏 / 标题卡 / 正文 / 附件 / 脚注）都落在这一列内，左右边缘**上下对齐**。
 * 前置条件：本路由已登记进 layouts/portal.vue 的通栏判定（/portal/bidding/detail/*），
 *   容器才不会被默认的 min(1180px, 100% - 48px) 限宽。
 * --bid-side-inset 的定义与 styles/bid-ui.css 逐字相同（本页未引该文件，故在此复刻一份；
 *   取值「20% 窗口宽，夹在 64~480px 之间」= 每边 32~240px）。 */
.detail-page {
  --bid-side-inset: min(480px, max(64px, 20%));

  width: calc(100% - var(--bid-side-inset));
  min-height: 100%;
  margin: 0 auto;
  padding: 0 0 30px;
  background: hsl(var(--background-deep));
}
.err-banner {
  background: hsl(var(--destructive) / 8%);
  border-bottom: 1px solid hsl(var(--destructive) / 35%);
  color: hsl(var(--destructive));
  font-size: 12.5px;
  padding: 8px 22px;
}
.top-bar {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 10px 22px;
  background: hsl(var(--card));
  border-bottom: 1px solid hsl(var(--border));
}
.back {
  font-size: 13px;
  color: hsl(var(--primary));
  font-weight: 700;
  cursor: pointer;
  border: 1px solid hsl(var(--primary) / 30%);
  background: hsl(var(--primary) / 8%);
  border-radius: 4px;
  padding: 4px 12px;
}
.back:hover {
  background: hsl(var(--primary) / 15%);
}
.sep {
  color: hsl(var(--border));
}
.crumb {
  font-size: 12.5px;
  color: hsl(var(--muted-foreground));
}
.head-card {
  background: hsl(var(--card));
  border-bottom: 1px solid hsl(var(--border));
  padding: 14px 22px;
}
.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}
.tag {
  display: inline-flex;
  padding: 0 8px;
  border-radius: 4px;
  font-size: 11.5px;
  line-height: 1.7;
  background: hsl(var(--primary) / 8%);
  color: hsl(var(--primary));
}
.head-card h1 {
  margin: 8px 0 10px;
  font-size: 19px;
  line-height: 1.5;
  color: hsl(var(--foreground));
}
.meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 22px;
  font-size: 12.5px;
  color: hsl(var(--muted-foreground));
  border-top: 1px solid hsl(var(--border));
  padding-top: 9px;
}
.meta b {
  font-weight: 400;
  color: hsl(var(--muted-foreground));
  margin-right: 4px;
}
/* 正文/附件区：左右不留内缩（此前是 22px），与上方返回栏、标题卡同左边缘 → 上下对齐 */
.body-area {
  margin: 14px 0;
}
.sec {
  font-size: 14px;
  margin: 0 0 8px;
  color: hsl(var(--foreground));
  padding-left: 8px;
  border-left: 3px solid hsl(var(--primary));
}
/* ===== 公文渲染容器：正文样式 ===== */
.docx {
  background: hsl(var(--card));
  border: 1px solid hsl(var(--border));
  /* 正文白纸随内容列铺满（此前收窄成 900px 居中，比上下区块窄一截，视觉上没对齐） */
  width: 100%;
  margin: 0 auto;
  padding: 56px 80px 50px;
  font-family: '仿宋_GB2312', 'FangSong_GB2312', '仿宋', 'FangSong', 'STFangsong', '华文仿宋', 'SimSun', serif;
  font-size: 18px;
  line-height: 2;
  color: hsl(var(--foreground));
}
.docx :deep(h1),
.docx :deep(h2),
.docx :deep(h3),
.docx :deep(h4),
.docx :deep(h5),
.docx :deep(h6) {
  font-family: '黑体', 'SimHei', 'Microsoft YaHei', sans-serif;
  font-weight: 700;
  color: hsl(var(--foreground));
  margin: 24px 0 2px;
  line-height: 1.8;
}
.docx :deep(h1) {
  font-size: 26px;
}
.docx :deep(h2) {
  font-size: 22px;
}
.docx :deep(p) {
  margin: 0;
  text-indent: 2em;
  text-align: justify;
}
/* 智能排版输出的公文段落：段间留白避免粘连 */
.docx :deep(p.doc-para) {
  margin: 0 0 2px;
}
/* 智能排版输出的公文标题：居中黑体，与正文区隔 */
.docx :deep(h1.doc-title) {
  text-align: center;
  font-size: 22px;
  margin: 0 0 18px;
}
.docx :deep(p[align='right']),
.docx :deep(p[style*='right']),
.docx :deep(.sign),
.docx :deep(.ql) {
  text-indent: 0;
}
.docx :deep(table) {
  border-collapse: collapse;
  width: 84%;
  margin: 12px auto;
  font-size: 15px;
  font-family: inherit;
}
.docx :deep(th),
.docx :deep(td) {
  border: 1px solid hsl(var(--border));
  padding: 5px 12px;
  text-align: center;
  font-family: inherit;
}
.docx :deep(th) {
  font-family: '黑体', 'SimHei', sans-serif;
  font-weight: 700;
  background: hsl(var(--muted) / 60%);
}
.docx :deep(img) {
  max-width: 100%;
  height: auto;
  display: block;
  margin: 10px auto;
}
.docx :deep(ol),
.docx :deep(ul) {
  padding-left: 3em;
}
.docx :deep(a) {
  color: hsl(var(--primary));
}
/* ===== PDF 组件 ===== */
.pdf-panel {
  background: hsl(var(--card));
  border: 1px solid hsl(var(--border));
  border-radius: 4px;
}
.pdf-frame-wrap {
  height: 720px;
}
.pdf-frame {
  width: 100%;
  height: 100%;
  border: 0;
}
.pdf-empty {
  padding: 60px 20px;
  text-align: center;
  color: hsl(var(--muted-foreground));
  font-size: 13px;
}
.pdf-empty .sub {
  margin-top: 6px;
  color: hsl(var(--muted-foreground));
  font-size: 12px;
}
/* ===== 仅简略信息 ===== */
.slim-box {
  background: hsl(var(--card));
  border: 1px solid hsl(var(--warning) / 40%);
  border-radius: 6px;
  padding: 28px 24px;
  text-align: center;
}
.slim-box p {
  color: var(--ind-warn);
  font-size: 13.5px;
  margin: 0 0 16px;
}
.btn {
  border-radius: 4px;
  padding: 8px 26px;
  font-size: 13.5px;
  font-weight: 600;
  cursor: pointer;
  border: 1px solid transparent;
  background: hsl(var(--primary));
  color: hsl(var(--primary-foreground));
}
/* ===== 下载区：仅下划线文字 ===== */
.dl-card {
  background: hsl(var(--card));
  border: 1px solid hsl(var(--border));
  border-radius: 6px;
  padding: 2px 14px;
}
.dl-row {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 9px 2px;
  border-bottom: 1px solid hsl(var(--border));
  font-size: 13px;
}
.dl-row:last-child {
  border-bottom: 0;
}
.fn {
  font-weight: 600;
  color: hsl(var(--foreground));
}
.fs {
  color: hsl(var(--muted-foreground));
  font-size: 12px;
}
.st {
  font-size: 12px;
  color: var(--ind-ok);
}
.st.bad {
  color: hsl(var(--destructive));
}
.spacer {
  flex: 1;
}
.dl-link {
  color: hsl(var(--primary));
  text-decoration: underline;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}
.dl-link.dim {
  color: hsl(var(--muted-foreground));
  cursor: not-allowed;
}
.blocked {
  color: hsl(var(--destructive));
  font-size: 12px;
}
.foot-note {
  margin: 4px 0 0;
  font-size: 11.5px;
  color: hsl(var(--muted-foreground));
}
.state-card {
  background: hsl(var(--card));
  border: 1px solid hsl(var(--border));
  border-radius: 8px;
  padding: 60px 20px;
  text-align: center;
  color: hsl(var(--muted-foreground));
  font-size: 13.5px;
  margin: 40px auto;
  max-width: 560px;
}
.state-card .err {
  color: hsl(var(--destructive));
}
.state-card .back {
  margin-top: 14px;
}

.docx {
  overflow-x: auto;
}
.docx :deep(caption) {
  font-family: '黑体', 'SimHei', sans-serif;
  font-size: 15px;
  font-weight: 700;
  padding: 6px 0;
  caption-side: top;
}
.docx :deep(ol),
.docx :deep(ul) {
  padding-left: 2.6em;
}
.docx :deep(ol) li,
.docx :deep(ul) li {
  margin: 4px 0;
  text-indent: 0;
}
.docx :deep(table) {
  width: auto;
  min-width: 60%;
  max-width: 100%;
}


.head-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}
.go-orig {
  flex: none;
  margin-top: 2px;
  font-size: 12.5px;
  font-weight: 700;
  color: hsl(var(--primary));
  border: 1px solid hsl(var(--primary) / 30%);
  background: hsl(var(--primary) / 8%);
  border-radius: 4px;
  padding: 4px 12px;
  cursor: pointer;
  text-decoration: none;
  white-space: nowrap;
}
.go-orig:hover {
  background: hsl(var(--primary) / 15%);
}
.go-orig.need {
  border-style: dashed;
  color: hsl(var(--muted-foreground));
  background: hsl(var(--muted) / 40%);
  border-color: hsl(var(--border));
}
.go-orig.need:hover {
  background: hsl(var(--muted) / 60%);
}
.go-orig.ghost {
  border: 1px solid hsl(var(--border));
  background: hsl(var(--card));
  color: hsl(var(--muted-foreground));
}
.go-orig.ghost:hover {
  background: hsl(var(--background-deep));
}


.head-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  flex: none;
}
/* 动作失败原因（跟随按钮换行整行展示）：401=登录态失效 / 403=权限未开通 / 系统故障 */
.act-hint {
  flex-basis: 100%;
  font-size: 12px;
  line-height: 1.6;
  color: hsl(var(--destructive));
}
.go-orig.active {
  background: hsl(var(--success) / 12%);
  border-color: hsl(var(--success) / 45%);
  color: var(--ind-ok);
}
.go-orig:disabled {
  opacity: 0.6;
  cursor: wait;
}
.follow-note {
  margin: 8px 0 0;
  font-size: 12px;
  color: hsl(var(--muted-foreground));
}
.track-row.secs {
  align-items: flex-start;
}
.sec-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.sec-item {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px 12px;
  margin: 2px 0;
}
.track-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed hsl(var(--border));
  font-size: 12.5px;
  color: hsl(var(--muted-foreground));
}
.track-label {
  color: hsl(var(--muted-foreground));
}
.track-key {
  font-weight: 700;
  color: hsl(var(--primary));
  font-family: Consolas, Menlo, monospace;
}

</style>
