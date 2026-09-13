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

import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { useAppConfig } from '@vben/hooks';

import CopyText from '../../bidding/components/CopyText.vue';
import { buildExternalNoticePdfUrl } from '../../bidding/noticeExternal/pdfUrl';
import { fetchNoticeDetail, followTarget, isFollowed, openOrigUrl, unfollowTarget } from './api';
import { biddingConfig } from './config';
import { blocksToHtml, cleanT, fmtDate, fmtDT, prettyNoticeHtml, toCleanList } from './format';

const route = useRoute();
const router = useRouter();

const loading = ref(true);
const errorMsg = ref('');
const detail = ref<NoticeExternalDetail | null>(null);

/** 关注状态：idle=探测中 / followed / unfollowed / unavailable（未登录或无权限时不展示按钮并给提示） */
const followState = ref<'followed' | 'idle' | 'unavailable' | 'unfollowed'>('idle');
const followBusy = ref(false);
const followHint = ref('');

async function probeFollow(): Promise<void> {
  try {
    const followed = await isFollowed('ANNOUNCEMENT', id.value);
    followState.value = followed ? 'followed' : 'unfollowed';
  } catch (e) {
    // 未登录/无权限等：隐藏关注按钮，给一句说明（详情仍可看）
    followState.value = 'unavailable';
    followHint.value = e instanceof Error ? e.message : String(e);
  }
}

function hintOf(e: unknown): string {
  const m = e instanceof Error ? e.message : String(e);
  return /账号未登录|未登录/.test(m) ? `请先登录后关注：${m}` : m;
}
/** 登录态错误判定：ApiError.code === 401/403（api.ts authedFetch 统一抛出；其余业务/网络错误不在此列） */
function isAuthError(e: unknown): boolean {
  const code = (e as null | undefined | { code?: number })?.code;
  return code === 401 || code === 403;
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

async function toggleFollow(): Promise<void> {
  if (followBusy.value) return;
  if (followState.value === 'unavailable') {
    goLogin();
    return;
  }
  followBusy.value = true;
  try {
    if (followState.value === 'followed') {
      await unfollowTarget({ targetType: 'ANNOUNCEMENT', targetKey: id.value });
      followState.value = 'unfollowed';
    } else {
      await followTarget({ targetType: 'ANNOUNCEMENT', targetKey: id.value });
      followState.value = 'followed';
    }
  } catch (e) {
    // 仅登录态错误（401/403）才切“需登录”；其余失败保留原状态，只给重试提示
    if (isAuthError(e)) {
      followState.value = 'unavailable';
      followHint.value = hintOf(e);
    } else {
      followHint.value = '操作失败，请稍后重试';
    }
  } finally {
    followBusy.value = false;
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
    // 未登录/无权限等：隐藏按钮，给一句说明（浏览与公告级关注不受影响）
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
    // 仅登录态错误（401/403）才切“需登录”；其余失败保留原状态，只给重试提示
    if (isAuthError(e)) {
      projFollowState.value = 'unavailable';
      projFollowHint.value = hintOf(e);
    } else {
      projFollowHint.value = '操作失败，请稍后重试';
    }
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
      hint: isAuthError(e) ? hintOf(e) : '操作失败，请稍后重试',
      state: isAuthError(e) ? 'unavailable' : st.state,
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
/* PDF 预览地址（两级）：
   ① 后端字节流代理 GET /bidding/notice-external/pdf?id= —— 公开免登（@PermitAll @TenantIgnore）、
      直接转发平台归档 PDF 字节流，前端 iframe src 直接用；后端注释明确"避免前端直连 infra 文件 URL（内网 host 不可达）"；
   ② detail.pdfDownloadUrl（FileApi 签名地址，回填后可用）优先于①。
   理由：此前只等 pdfDownloadUrl 回填，导致 contentType=PDF 的公告长期显示"源文件整理中"、无法预览。 */
const { apiURL } = useAppConfig(import.meta.env, import.meta.env.PROD);
// 解析口径见 views/bidding/noticeExternal/pdfUrl.ts（单一真源，后台审核详情共用，避免再次分叉）
const pdfFrameUrl = computed(() => buildExternalNoticePdfUrl(apiURL, id.value, detail.value?.pdfDownloadUrl));
const canPreviewPdf = computed(() => !!pdfFrameUrl.value);
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
    : n >= 1024 * 1024
      ? `${(n / 1024 / 1024).toFixed(1)} MB`
      : n >= 1024
        ? `${Math.round(n / 1024)} KB`
        : `${n} B`;

const virusText = (v?: string) =>
  v === 'CLEAN' ? { text: '已扫描 CLEAN', bad: false } : v === 'PENDING' ? { text: '扫描中 PENDING', bad: false } : v === 'INFECTED' ? { text: '文件含病毒 · 已拦截', bad: true } : { text: '', bad: false };

async function load(): Promise<void> {
  loading.value = true;
  errorMsg.value = '';
  try {
    detail.value = await fetchNoticeDetail(id.value);
    if (detail.value) {
      void probeFollow();
      void probeProjFollow();
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
  门户 · 外部公告详情（公开只读 + 关注/外链）
  业务：完整展示一条外部公告：结构化字段+净化正文+PDF/附件（可见性分层 L1/L2）+外链白名单中转(/go?url=)；
        登录用户可“关注”该项目（意向起点），未登录仅浏览。
  理由：完整信息免费及时查阅（10号令第12条）；HTML 净化防存储型XSS；外链走白名单 302 防开放重定向（安全评审）。
    依据（法规/政府文件/法律）：
      · 招标公告和公示信息发布管理办法（发改委令第10号，部门规章/政府文件）第12/13条——免费、及时查阅完整公告公示信息；免费归类与检索、新公告醒目标识；
      · 电子招标投标办法（八部委令第20号，部门规章/政府文件）第9条——市场主体免费注册登录、获取依法公开的招标投标信息；
      · 招标投标法 第16条（法律）——公开招标应当发布招标公告（公告公开性的上位依据）。
    依据（技术安全）：前端净化（DOMPurify/白名单）——安全评审要求存储型 XSS 载荷不下发（01 接口文档 §2.4）。
    后端契约：01-接口逐一说明与设计依据.md §2（detail 三形态/附件 virusStatus、go 白名单 302）；外部公告-前端对接/01-接口契约.md §1；
      follow/unfollow（bidding:notice-external:update，按登录人 user_id，T 轨）；GET /bidding/notice-external/detail、/go、/follow。
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
          <button
            v-if="followState !== 'idle'"
            class="go-orig"
            :class="{ active: followState === 'followed', need: followState === 'unavailable' }"
            :disabled="followBusy"
            @click="toggleFollow()"
          >
{{ followState === 'followed' ? '已关注 ✓' : followBusy ? '处理中…' : followState === 'unavailable' ? '＋ 关注 · 需登录' : '＋ 关注' }}
</button>
          <button
            v-if="followState === 'unavailable'"
            class="go-orig ghost"
            @click="goLogin()"
          >
去登录
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
            <button v-if="secStateOf(s).state === 'unavailable'" class="go-orig ghost" @click="goLogin()">去登录</button>
            <span v-if="secStateOf(s).hint" class="follow-note">标段：{{ secStateOf(s).hint }}</span>
          </div>
        </div>
      </div>
      <p v-if="followHint" class="follow-note">关注：{{ followHint }}（公告浏览不受影响）</p>
    </div>

    <!-- 正文：结构化块 优先 → contentHtmlSafe 回退（统一公文样式渲染） -->
    <div v-if="hasBodyHtml" class="body-area">
      <div class="docx" v-dompurify-html="bodyHtml"></div>
    </div>

    <!-- 形态 · PDF：内嵌预览（后端字节流代理 /bidding/notice-external/pdf 公开免登，签名地址优先） -->
    <div v-else-if="isPdf" class="body-area">
      <div class="pdf-panel">
        <div v-if="canPreviewPdf" class="pdf-frame-wrap">
          <iframe class="pdf-frame" :src="pdfFrameUrl" title="公告 PDF 预览"></iframe>
          <div class="sub pdf-actions">
            <a :href="pdfFrameUrl" target="_blank" rel="noopener noreferrer">新窗口打开</a>
            <a :href="pdfFrameUrl" download>下载</a>
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
.detail-page {
  background: #f3f5f7;
  min-height: 100%;
  padding: 0 0 30px;
}
.err-banner {
  background: #fef2f0;
  border-bottom: 1px solid #f2b8b0;
  color: #b42318;
  font-size: 12.5px;
  padding: 8px 22px;
}
.top-bar {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 10px 22px;
  background: #fff;
  border-bottom: 1px solid #e2e6eb;
}
.back {
  font-size: 13px;
  color: #17539a;
  font-weight: 700;
  cursor: pointer;
  border: 1px solid #c4d7ec;
  background: #eef4fb;
  border-radius: 4px;
  padding: 4px 12px;
}
.back:hover {
  background: #e2ecf8;
}
.sep {
  color: #cfd6dd;
}
.crumb {
  font-size: 12.5px;
  color: #5f6b7a;
}
.head-card {
  background: #fff;
  border-bottom: 1px solid #e2e6eb;
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
  background: #e6eef8;
  color: #174a86;
}
.head-card h1 {
  margin: 8px 0 10px;
  font-size: 19px;
  line-height: 1.5;
  color: #1e2832;
}
.meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 22px;
  font-size: 12.5px;
  color: #44505c;
  border-top: 1px solid #eceff3;
  padding-top: 9px;
}
.meta b {
  font-weight: 400;
  color: #5f6b7a;
  margin-right: 4px;
}
.body-area {
  margin: 14px 22px;
}
.sec {
  font-size: 14px;
  margin: 0 0 8px;
  color: #1e2832;
  padding-left: 8px;
  border-left: 3px solid #17539a;
}
/* ===== 公文渲染容器：正文样式 ===== */
.docx {
  background: #fff;
  border: 1px solid #d9d3c4;
  width: min(900px, 100%);
  margin: 0 auto;
  padding: 56px 80px 50px;
  font-family: '仿宋_GB2312', 'FangSong_GB2312', '仿宋', 'FangSong', 'STFangsong', '华文仿宋', 'SimSun', serif;
  font-size: 18px;
  line-height: 2;
  color: #1c1c1c;
}
.docx :deep(h1),
.docx :deep(h2),
.docx :deep(h3),
.docx :deep(h4),
.docx :deep(h5),
.docx :deep(h6) {
  font-family: '黑体', 'SimHei', 'Microsoft YaHei', sans-serif;
  font-weight: 700;
  color: #161616;
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
  border: 1px solid #333;
  padding: 5px 12px;
  text-align: center;
  font-family: inherit;
}
.docx :deep(th) {
  font-family: '黑体', 'SimHei', sans-serif;
  font-weight: 700;
  background: #f7f5ef;
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
  color: #17539a;
}
/* ===== PDF 组件 ===== */
.pdf-panel {
  background: #fff;
  border: 1px solid #d9dfe5;
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
  color: #5f6b7a;
  font-size: 13px;
}
.pdf-empty .sub {
  margin-top: 6px;
  color: #5f6b7a;
  font-size: 12px;
}
/* ===== 仅简略信息 ===== */
.slim-box {
  background: #fff;
  border: 1px solid #f0dcb8;
  border-radius: 6px;
  padding: 28px 24px;
  text-align: center;
}
.slim-box p {
  color: #8a5a00;
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
  background: #17539a;
  color: #fff;
}
/* ===== 下载区：仅下划线文字 ===== */
.dl-card {
  background: #fff;
  border: 1px solid #e0e4e9;
  border-radius: 6px;
  padding: 2px 14px;
}
.dl-row {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 9px 2px;
  border-bottom: 1px solid #eceff3;
  font-size: 13px;
}
.dl-row:last-child {
  border-bottom: 0;
}
.fn {
  font-weight: 600;
  color: #1e2832;
}
.fs {
  color: #5f6b7a;
  font-size: 12px;
}
.st {
  font-size: 12px;
  color: #176b44;
}
.st.bad {
  color: #b42318;
}
.spacer {
  flex: 1;
}
.dl-link {
  color: #17539a;
  text-decoration: underline;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}
.dl-link.dim {
  color: #5f6b7a;
  cursor: not-allowed;
}
.blocked {
  color: #b42318;
  font-size: 12px;
}
.foot-note {
  margin: 4px 22px 0;
  font-size: 11.5px;
  color: #5f6b7a;
}
.state-card {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 60px 20px;
  text-align: center;
  color: #5f6b7a;
  font-size: 13.5px;
  margin: 40px auto;
  max-width: 560px;
}
.state-card .err {
  color: #b42318;
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
  color: #17539a;
  border: 1px solid #c4d7ec;
  background: #eef4fb;
  border-radius: 4px;
  padding: 4px 12px;
  cursor: pointer;
  text-decoration: none;
  white-space: nowrap;
}
.go-orig:hover {
  background: #e2ecf8;
}
.go-orig.need {
  border-style: dashed;
  color: #5f6b7a;
  background: #fafbfc;
  border-color: #d3dae1;
}
.go-orig.need:hover {
  background: #eef1f4;
}
.go-orig.ghost {
  border: 1px solid #d3dae1;
  background: #fff;
  color: #5f6b7a;
}
.go-orig.ghost:hover {
  background: #f3f5f7;
}


.head-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  flex: none;
}
.go-orig.active {
  background: #e5f2ea;
  border-color: #a9d6bd;
  color: #176b44;
}
.go-orig:disabled {
  opacity: 0.6;
  cursor: wait;
}
.follow-note {
  margin: 8px 0 0;
  font-size: 12px;
  color: #5f6b7a;
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
  border-top: 1px dashed #e3e8ee;
  font-size: 12.5px;
  color: #44505c;
}
.track-label {
  color: #5f6b7a;
}
.track-key {
  font-weight: 700;
  color: #17539a;
  font-family: Consolas, Menlo, monospace;
}

</style>
