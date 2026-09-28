<!--
  招投标门户 · 本站公告详情（notice public/detail）
  数据源：公开详情（@PermitAll，仅 PUBLISHED + 社会公开；payload 已 SOCIAL 裁剪）

  排版口径（2026-09-23 用户口径）：**整页模仿「内容形态 = HTML」的外部公告详情页**（detail.vue），
  三处对齐 ——
    ① 页头：白底带下边框的返回栏（返回上一页 | 公告详情），下方白底标题卡（标签行 + 大标题 + 元信息 + 跟踪行）；
    ② 正文：公文白纸 `.docx`（仿宋 18px / 行高 2 / 首行缩进 2em / 黑体居中大标题），
       与外部 HTML 公告**逐字相同**的排版规则；正文经 prettyNoticeHtml() 做公文化切段（同一个函数）；
    ③ 分节：`.body-area > .sec` 的小节标题与白底区块。
  与外部页的唯一差别：本站详情**保留权威字段分节表**（noticeSections.ts 四类公告文书分节，与打印版式 PDF 同源）
  —— 外部 HTML 形态没有这张表，用户裁定本站保留，位置在公文白纸正文**之下**、并改成同一套白卡+描边表样式。
  依据（法规/政府文件/法律）：10号令 第12/13条（免费查阅、归类检索）；电子办法 第9条；招标投标法 第16条。
  后端契约：08 口径 §4；docs/公告发布-字段对齐与自研渲染器-附件公开-2026-09-10.md §二/§三；NoticeDetailRespVO。
-->
<script lang="ts" setup>
import type { InternalNoticeDetail } from './types';

import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { buildTrackTiers, type NoticeTrackTier } from '#/views/bidding/notice/noticeCodes';
import { buildNoticeSections } from '#/views/bidding/notice/noticeSections';
import { fmtDateTime } from '#/utils/datetime';
import { fmtBytes } from '#/utils/format';

import { message, Modal } from 'ant-design-vue';

import { saveAttachment } from '#/api/bidding/attachment';
import { uploadBiddingFile } from '#/api/bidding/file';
import { applyPrequal, getPrequalPage } from '#/api/bidding/prequal';

import { fetchInternalNoticeDetail, followTarget, isFollowed, unfollowTarget } from './api';
import { escapeHtml, prettyNoticeHtml } from './format';

const route = useRoute();
const router = useRouter();
const id = String(route.params.id || '');
const loading = ref(true);
const errorMsg = ref('');
const detail = ref<InternalNoticeDetail>();
const fmtT = (v?: number | string): string => fmtDateTime(v, '—');

/** 返回上一页：与外部公告详情（detail.vue 的 goBack）同口径 —— 有浏览历史就 router.back()，
 *  否则兜底回公告大厅。原来这里跳「本平台发布」大厅（/portal/bidding/internal-hall），该页已废弃删除。 */
function goBack(): void {
  if (window.history.length > 1) {
    router.back();
  } else {
    router.push('/portal/bidding').catch(() => {
      /* 导航被取消/重复导航：忽略 */
    });
  }
}

/** 权威字段分节（四类公告文书分节，noticeSections.ts 单一数据源；与打印 HTML/版式 PDF 同源） */
const docSections = computed(() =>
  buildNoticeSections(detail.value?.noticeType, detail.value?.payload, {
    projectCode: detail.value?.projectCode,
    projectName: detail.value?.projectName,
  }),
);

/** 正文 → 公文化 HTML：与外部 HTML 公告走**同一个** prettyNoticeHtml()（首行缩进/黑体大标题/长段自动切分）。
 *  外部页喂的是后端净化版 contentHtmlSafe；本站正文是 bidding_notice_content 的**纯文本**，
 *  故先按「有无真实 HTML 标签」分流：真富文本原样传，纯文本先 escapeHtml —— 否则 prettyNoticeHtml
 *  的去标签分支会把正文里字面出现的 < > 当标签整段吃掉（如「桩径<800mm>」会丢掉 800mm）。
 *  转义不影响观感：该函数内部 decodeEntities 会把 &lt;/&gt;/&amp; 还原，输出前再由它自己的 esc() 重新转义。
 *  产物再由 v-dompurify-html 二次净化。 */
const bodyHtml = computed(() => {
  const raw = String(detail.value?.content ?? '').trim();
  if (!raw) return '';
  const isHtml = /<\/?[a-z][\w-]*(\s[^>]*)?>/i.test(raw);
  return prettyNoticeHtml(isHtml ? raw : escapeHtml(raw), detail.value?.title);
});

async function load(): Promise<void> {
  loading.value = true;
  errorMsg.value = '';
  try {
    detail.value = await fetchInternalNoticeDetail(id);
  } catch (e) {
    errorMsg.value = (e as Error).message || String(e);
  } finally {
    loading.value = false;
  }
}
/* ===== 三级编号跟踪（项目 / 招标项目 / 标段）=====
 * 口径：noticeCodes.buildTrackTiers —— 与发布工作台、外部公告详情同一套编号键（notice-follow targetType）。
 * 为什么必须在这里给入口：门户此前只有**外部公告**详情页有关注按钮，而关注键就是编号本身；
 * 内部公告没有关注入口 = 本平台发布的公告**没人能建立跟踪**（发几条都不会有人收到动态）。
 * 交互与外部公告详情页一致：仅登录态可关注（401 → 引导登录），403 不当作未登录。 */
const tiers = computed(() =>
  buildTrackTiers({ payload: detail.value?.payload, projectCode: detail.value?.projectCode }),
);
type FollowState = 'followed' | 'unavailable' | 'unfollowed';
const followState = ref<Record<string, FollowState>>({});
const followBusy = ref<Record<string, boolean>>({});
const followHint = ref<Record<string, string>>({});

function codeOf(e: unknown): number | undefined {
  return (e as null | undefined | { code?: number })?.code;
}
function goLogin(): void {
  router
    .push({ path: '/auth/login', query: { redirect: `/portal/bidding/internal-detail/${id}` } })
    .catch(() => {});
}
/** 拉三级关注状态（未登录时后端回 401 → 按「需登录」展示，不隐藏按钮） */
async function probeFollow(): Promise<void> {
  const list = tiers.value.filter((t) => t.trackable);
  await Promise.all(
    list.map(async (t) => {
      try {
        const followed = await isFollowed(t.level, t.code);
        followState.value = { ...followState.value, [t.level]: followed ? 'followed' : 'unfollowed' };
      } catch (e) {
        followState.value = { ...followState.value, [t.level]: 'unavailable' };
        followHint.value = {
          ...followHint.value,
          [t.level]: codeOf(e) === 401 ? '需登录后跟踪' : '关注状态查询失败',
        };
      }
    }),
  );
}
async function toggleFollow(t: NoticeTrackTier): Promise<void> {
  if (followBusy.value[t.level] || !t.code) return;
  if (followState.value[t.level] === 'unavailable') {
    goLogin();
    return;
  }
  followBusy.value = { ...followBusy.value, [t.level]: true };
  try {
    if (followState.value[t.level] === 'followed') {
      await unfollowTarget({ targetType: t.level, targetKey: t.code });
      followState.value = { ...followState.value, [t.level]: 'unfollowed' };
    } else {
      await followTarget({ targetType: t.level, targetKey: t.code });
      followState.value = { ...followState.value, [t.level]: 'followed' };
    }
    followHint.value = { ...followHint.value, [t.level]: '' };
  } catch (e) {
    // 只有 401 才是"没登录"；403 是权限问题，不把人赶去登录页
    if (codeOf(e) === 401) {
      followState.value = { ...followState.value, [t.level]: 'unavailable' };
      followHint.value = { ...followHint.value, [t.level]: '需登录后跟踪' };
    } else {
      followHint.value = {
        ...followHint.value,
        [t.level]: (e as Error).message || '操作失败，请稍后重试',
      };
    }
  } finally {
    followBusy.value = { ...followBusy.value, [t.level]: false };
  }
}

/* ============ 资格预审申请（2026-10-02：本站公告详情标段行直达；前审标段才显示按钮） ============ */
const PREQ_ACCEPT = '.pdf,.doc,.docx,.xls,.xlsx,.zip,.rar,.7z';
const PREQ_MAX_MB = 16;
/** 本公告 payload 标出的标段编号（完整编号；buildTrackTiers 对无 sections 的公告有 code='' 空档，过滤掉） */
const sectionKeys = computed(() =>
  tiers.value.filter((t) => t.level === 'SECTION' && t.code).map((t) => t.code as string),
);
/** 标段编号 → 存在已发布资格预审（按钮据此出现） */
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
  const keys = Array.from(new Set(sectionKeys.value));
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
watch(sectionKeys, () => void loadPrequalFlags(), { immediate: true });

async function openPrequal(k: string): Promise<void> {
  preqSectionKey.value = k;
  preqOpen.value = true;
  preqLoading.value = true;
  preqInfo.value = null;
  preqFiles.value = [];
  try {
    const page = await getPrequalPage({ pageNo: 1, pageSize: 1, bidSectionCode: k, status: 'PUBLISHED' });
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
    message.success('资格预审申请已提交');
  } catch (e) {
    message.error((e as Error).message || '提交失败，请稍后重试');
  } finally {
    preqSubmitting.value = false;
  }
}

onMounted(async () => {
  await load();
  void probeFollow();
});
</script>

<template>
  <div class="bidding-page bid-ind">
    <!-- 返回栏：与外部公告详情 .top-bar 同口径（白底 + 下边框 + 面包屑） -->
    <div class="top-bar">
      <button class="back" @click="goBack()">‹ 返回上一页</button>
      <span class="sep">|</span>
      <span class="crumb">公告详情</span>
    </div>

    <div v-if="loading" class="state-card"><p>加载中…</p></div>
    <div v-else-if="errorMsg" class="state-card">
      <p class="err">{{ errorMsg }}</p>
      <button class="back" @click="goBack()">‹ 返回上一页</button>
    </div>

    <template v-else-if="detail && detail.title">
      <!-- 标题卡：标签行 + 大标题 + 元信息 + 三级编号跟踪行（照外部详情 .head-card） -->
      <div class="head-card">
        <div class="head-top">
          <div class="tags">
            <span v-if="detail.noticeTypeName" class="tag">{{ detail.noticeTypeName }}</span>
            <span v-if="detail.publicLevel" class="tag">
              {{ detail.publicLevel === 'GOVERNMENT' ? '政务公开' : '社会公开' }}<template v-if="detail.internalFlag">（企业内部）</template>
            </span>
          </div>
        </div>
        <h1>{{ detail.title }}</h1>
        <div class="meta">
          <span v-if="detail.noticeCode"><b>公告编号</b>{{ detail.noticeCode }}</span>
          <span v-if="detail.publisherName"><b>发布单位</b>{{ detail.publisherName }}</span>
          <span v-if="detail.publishTime"><b>发布时间</b>{{ fmtT(detail.publishTime) }}</span>
          <span v-if="detail.contentVersion"><b>正文版本</b>v{{ detail.contentVersion }}</span>
        </div>
        <!-- 三级编号跟踪（项目/招标项目/标段）：与门户「我的关注」同一套编号键；
             发布端把同三级编号写进公告后，这里跟踪过的编号会被扇出匹配 -->
        <div v-if="tiers.some((t) => t.trackable)" class="track-row">
          <span class="track-label">跟踪编号（同编号新公告发布时会推到「我的关注」）：</span>
          <span v-for="t in tiers" :key="t.level" class="track-item">
            <span class="track-key">{{ t.levelLabel }}</span>
            <em>{{ t.codeShort || t.code || '未关联' }}</em>
            <button
              v-if="t.trackable"
              class="go-orig"
              :class="{ active: followState[t.level] === 'followed' }"
              :disabled="followBusy[t.level]"
              @click="toggleFollow(t)"
            >
              {{ followState[t.level] === 'followed' ? '已跟踪 ✓' : followBusy[t.level] ? '处理中…' : followState[t.level] === 'unavailable' ? '＋ 跟踪 · 需登录' : '＋ 跟踪' }}
            </button>
          </span>
          <span v-if="Object.values(followHint).some(Boolean)" class="follow-note">
            {{ Object.values(followHint).filter(Boolean).join('；') }}
          </span>
        </div>
      </div>

      <!-- 正文：公文白纸（与外部 HTML 公告同一套 .docx 排版规则） -->
      <div class="body-area">
        <h2 class="sec">公告正文<template v-if="detail.contentVersion">（v{{ detail.contentVersion }}）</template></h2>
        <div v-if="bodyHtml" class="docx" v-dompurify-html="bodyHtml"></div>
        <div v-else class="docx docx--empty">（暂无正文——发布前须录入正文）</div>
      </div>

      <!-- 权威字段分节表：与打印版式 PDF 同源（noticeSections.ts）。外部 HTML 形态本无此表，
           用户裁定本站保留，位置在公文白纸正文**之下**。
           样式**直接套用正文的公文白纸类 .docx**（同一张纸的字体/字号/行高/内边距 → 不割裂）；
           键值表需要的三点差异由 .docx--fields 限定覆盖（不影响正文里的表格）。 -->
      <div v-if="docSections.length" class="body-area">
        <h2 class="sec">公告信息</h2>
        <div class="docx docx--fields">
          <section v-for="sec in docSections" :key="sec.title" class="fd-sec">
            <h3>{{ sec.title }}</h3>
            <table>
              <tbody>
                <tr v-for="it in sec.items" :key="it.key">
                  <td class="fd-k">{{ it.label }}</td>
                  <td class="fd-v">{{ it.value }}</td>
                </tr>
              </tbody>
            </table>
          </section>
        </div>
      </div>

      <!-- 版式文件（PDF 对外最终件）：本站公告暂无后端字节流代理，有 file_id 时给提示位 -->
      <div v-if="detail.pdfFileId" class="body-area">
        <h2 class="sec">版式文件</h2>
        <div class="doc-sec">
          <div class="pdf-line">
            <span class="doc">公告版式文件（PDF）</span>
            <span class="mono">file_id #{{ detail.pdfFileId }}<template v-if="detail.pdfSize"> · {{ detail.pdfSize }} B</template></span>
            <span class="tip">下载地址由业务系统按公开性签发后开放</span>
          </div>
        </div>
      </div>
    </template>

  </div>
</template>

<style scoped>
/* ============================================================================
 * 本页排版 = 外部公告详情（detail.vue）「内容形态 = HTML」那一套：白底页头 + 标题卡 + 公文白纸正文。
 * 下列 .top-bar / .back / .sep / .crumb / .head-card / .tags / .tag / .meta / .head-top /
 * .go-orig / .track-row / .body-area / .sec / .docx 的取值均与 detail.vue 逐字一致
 * —— 那里是 scoped 样式，无法复用，故按同一套数值复刻（与 --bid-side-inset 复刻同一处理方式）。
 * 内容列宽口径（2026-09-23 用户口径）：与外部公告详情同宽同左右边缘，取「两侧对称让出 --bid-side-inset」，
 * 故弃用原来的 max-width: 900px。前置条件：本路由已登记进 layouts/portal.vue 的通栏判定。
 * --bid-side-inset 的定义与 styles/bid-ui.css 逐字相同（本页未引该文件，故在此复刻一份）。
 * ========================================================================= */
.bidding-page {
  --bid-side-inset: min(480px, max(64px, 20%));

  width: calc(100% - var(--bid-side-inset));
  min-height: 100%;
  margin: 0 auto;
  padding: 0 0 30px;
  background: hsl(var(--background-deep));
}

/* ---- 返回栏（= detail.vue 的 .top-bar） ---- */
.top-bar {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 10px 22px;
  background: hsl(var(--card));
  border-bottom: 1px solid hsl(var(--border));
}
/* 返回按钮与外部公告详情同口径：描边 + 浅蓝底 + 加粗 + 4px 圆角 + 4px 12px 内边距 */
.back { font-size: 13px; color: hsl(var(--primary)); font-weight: 700; cursor: pointer; border: 1px solid hsl(var(--primary) / 30%); background: hsl(var(--primary) / 8%); border-radius: 4px; padding: 4px 12px; }
.back:hover { background: hsl(var(--primary) / 15%); }
.sep { color: hsl(var(--border)); }
.crumb { font-size: 12.5px; color: hsl(var(--muted-foreground)); }

/* ---- 标题卡（= detail.vue 的 .head-card） ---- */
.head-card {
  background: hsl(var(--card));
  border-bottom: 1px solid hsl(var(--border));
  padding: 14px 22px;
}
.head-top { display: flex; align-items: flex-start; justify-content: space-between; gap: 14px; }
.tags { display: flex; flex-wrap: wrap; gap: 5px; }
.tag {
  display: inline-flex;
  padding: 0 8px;
  border-radius: 4px;
  font-size: 11.5px;
  line-height: 1.7;
  background: hsl(var(--primary) / 8%);
  color: hsl(var(--primary));
}
.head-card h1 { margin: 8px 0 10px; font-size: 19px; line-height: 1.5; color: hsl(var(--foreground)); }
.meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 22px;
  font-size: 12.5px;
  color: hsl(var(--muted-foreground));
  border-top: 1px solid hsl(var(--border));
  padding-top: 9px;
}
.meta b { font-weight: 400; color: hsl(var(--muted-foreground)); margin-right: 4px; }

/* ---- 次要动作按钮（= detail.vue 的 .go-orig；本页现在只剩跟踪行的「＋ 跟踪」用它） ---- */
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
.go-orig:hover { background: hsl(var(--primary) / 15%); }
.go-orig:disabled { opacity: 0.6; cursor: wait; }
.go-orig.active { background: hsl(var(--success) / 12%); border-color: hsl(var(--success) / 45%); color: var(--ind-ok); }

/* ---- 三级编号跟踪行（= detail.vue 的 .track-row） ---- */
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
.track-label { color: hsl(var(--muted-foreground)); }
.track-item { display: inline-flex; align-items: center; gap: 6px; }
.track-key { font-weight: 700; color: hsl(var(--primary)); font-family: Consolas, Menlo, monospace; }
.track-item em { font-style: normal; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; color: hsl(var(--muted-foreground)); }
.follow-note { margin: 8px 0 0; font-size: 12px; color: hsl(var(--muted-foreground)); }

/* ---- 正文/附件区（= detail.vue 的 .body-area / .sec） ---- */
.body-area { margin: 14px 0; }
.sec {
  font-size: 14px;
  margin: 0 0 8px;
  color: hsl(var(--foreground));
  padding-left: 8px;
  border-left: 3px solid hsl(var(--primary));
}

/* ---- 版式文件行的白卡（仅这一行用；字段表已改走 .docx--fields，样式见 .docx 规则之后） ---- */
.doc-sec {
  background: hsl(var(--card));
  border: 1px solid hsl(var(--border));
  border-radius: 8px;
  padding: 14px 18px;
}

/* ---- 公文白纸正文（= detail.vue 的 .docx，逐字复刻） ---- */
.docx {
  background: hsl(var(--card));
  border: 1px solid hsl(var(--border));
  width: 100%;
  margin: 0 auto;
  padding: 56px 80px 50px;
  font-family: '仿宋_GB2312', 'FangSong_GB2312', '仿宋', 'FangSong', 'STFangsong', '华文仿宋', 'SimSun', serif;
  font-size: 18px;
  line-height: 2;
  color: hsl(var(--foreground));
}
.docx--empty { font-family: inherit; font-size: 13px; color: hsl(var(--muted-foreground)); }
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
.docx :deep(h1) { font-size: 26px; }
.docx :deep(h2) { font-size: 22px; }
.docx :deep(p) { margin: 0; text-indent: 2em; text-align: justify; }
/* 智能排版输出的公文段落：段间留白避免粘连 */
.docx :deep(p.doc-para) { margin: 0 0 2px; }
/* 智能排版输出的公文标题：居中黑体，与正文区隔 */
.docx :deep(h1.doc-title) { text-align: center; font-size: 22px; margin: 0 0 18px; }
.docx :deep(p[align='right']),
.docx :deep(p[style*='right']),
.docx :deep(.sign),
.docx :deep(.ql) { text-indent: 0; }
.docx :deep(table) { border-collapse: collapse; width: 84%; margin: 12px auto; font-size: 15px; font-family: inherit; }
.docx :deep(th),
.docx :deep(td) { border: 1px solid hsl(var(--border)); padding: 5px 12px; text-align: center; font-family: inherit; }
.docx :deep(th) { font-family: '黑体', 'SimHei', sans-serif; font-weight: 700; background: hsl(var(--muted) / 60%); }
.docx :deep(img) { max-width: 100%; height: auto; display: block; margin: 10px auto; }
.docx :deep(ol),
.docx :deep(ul) { padding-left: 3em; }
.docx :deep(a) { color: hsl(var(--primary)); }
/* detail.vue 的第二段 .docx 覆盖（表格/列表/标题说明的细化）：为保证两页排版逐字一致，一并复刻 */
.docx { overflow-x: auto; }
.docx :deep(caption) { font-family: '黑体', 'SimHei', sans-serif; font-size: 15px; font-weight: 700; padding: 6px 0; caption-side: top; }
.docx :deep(ol),
.docx :deep(ul) { padding-left: 2.6em; }
.docx :deep(ol) li,
.docx :deep(ul) li { margin: 4px 0; text-indent: 0; }
.docx :deep(table) { width: auto; min-width: 60%; max-width: 100%; }

/* ---- 权威字段分节表：**直接套用正文的公文白纸类 .docx**（同一张纸的字体/字号/行高/内边距），
       只覆盖键值表需要的三点，并用 .docx--fields 限定作用域 —— 不影响正文里的表格。
       ⚠ 必须写在上面 .docx 规则**之后**：两者特异性相同（都是 .类[data-v] 后代），靠源码顺序取胜。 ---- */
.docx--fields :deep(.fd-sec) { margin-bottom: 14px; }
.docx--fields :deep(.fd-sec:last-child) { margin-bottom: 0; }
.docx--fields :deep(h3) { margin: 0 0 8px; }
.docx--fields :deep(table) { width: 100%; min-width: 0; max-width: 100%; margin: 0; }
.docx--fields :deep(td) { text-align: left; line-height: 1.9; word-break: break-all; }
.docx--fields :deep(td.fd-k) { width: 34%; min-width: 150px; color: hsl(var(--muted-foreground)); background: hsl(var(--muted) / 50%); }
.docx--fields :deep(td.fd-v) { color: hsl(var(--foreground)); }

/* ---- 版式文件行 ---- */
.pdf-line { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; font-size: 12.5px; }
.pdf-line .doc { font-weight: 600; color: hsl(var(--foreground)); }
.pdf-line .mono { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; color: hsl(var(--muted-foreground)); }
.pdf-line .tip { color: var(--ind-warn, #b45309); }

/* ---- 加载/异常态（= detail.vue 的 .state-card） ---- */
.state-card { padding: 60px 20px; text-align: center; font-size: 13px; color: hsl(var(--muted-foreground)); }
.state-card .err { margin-bottom: 12px; color: hsl(var(--destructive)); }
</style>
