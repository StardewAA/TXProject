<!--
  外部公告管理预览（BR-7 manage/get：全状态可读，审核/运营“读完正文再操作”用）
  两个页签：
    1) 审核数据 / 净化原文 —— 字段详情 + 净化正文(contentHtmlSafe) + 附件/病毒状态 + PDF 摘要
    2) 前台观感 —— 审核通过发布后，用户在前台看到的样子：
       复用门户真实渲染语义（portal/bidding/format.ts 的 blocksToHtml/prettyNoticeHtml + 公文 .docx 样式），
       正文优先结构化块(bodyBlocksStatus=SUCCESS → bodyBlocksJson)，回退净化正文；
       头部 tags/meta/关注按钮 均按门户详情布局示意。
-->
<script lang="ts" setup>
import type { NoticeExternalApi } from '#/api/bidding/noticeExternal';
import type { NoticeBodyBlock } from '#/views/portal/bidding/types';

import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { Tag } from 'ant-design-vue';

import { getManageDetail } from '#/api/bidding/noticeExternal';
import {
  blocksToHtml,
  cleanT,
  fmtDT,
  prettyNoticeHtml,
} from '#/views/portal/bidding/format';

import {
  AUDIT_MAP,
  BODY_MAP,
  CONTENT_TYPE_MAP,
  INDUSTRY_MAP,
  STATUS_MAP,
} from '../data';

const detail = ref<NoticeExternalApi.ManageDetail | null>(null);
const loading = ref(false);
const errorMsg = ref('');
/** 页签：data=审核数据/原文；look=前台观感 */
const tab = ref<'data' | 'look'>('look');

const tag = (map: Record<string, { text: string; color: string }>, v?: string) => {
  const c = v ? map[v] : undefined;
  return c ? { text: c.text, color: c.color } : null;
};

const hasBody = computed(() => !!detail.value?.contentHtmlSafe);
const fmtT = (v?: string) => (v ? v.replace('T', ' ').slice(0, 19) : '—');
const empty = (v?: string) => (v ? v : '—');

function parseBlocks(raw?: string): NoticeBodyBlock[] | null {
  if (!raw) return null;
  try {
    const arr = JSON.parse(raw);
    return Array.isArray(arr) ? (arr as NoticeBodyBlock[]) : null;
  } catch {
    return null;
  }
}

/** 去掉与标题完全重复的首个块级标题（门户同款 trimLeadHeading 简化版） */
function trimLead(blocks: NoticeBodyBlock[] | null, title?: string): NoticeBodyBlock[] {
  if (!blocks?.length) return [];
  const first = blocks[0];
  if (!first || first.type !== 'heading' || !first.text || first.level > 2) return blocks;
  const norm = (x: string) => x.replace(/\s+/g, '').replace(/[/／]/g, '');
  const tn = norm(cleanT(title));
  return tn && norm(first.text) === tn ? blocks.slice(1) : blocks;
}

/** 前台观感正文 HTML：结构化块优先 → 净化正文智能排版（与门户 detail 同语义） */
const viewHtml = computed(() => {
  const d = detail.value;
  if (!d) return '';
  if (d.bodyBlocksStatus === 'SUCCESS') {
    const h = blocksToHtml(trimLead(parseBlocks(d.bodyBlocksJson), d.title));
    if (h) return h;
  }
  return prettyNoticeHtml(d.contentHtmlSafe);
});
const hasViewBody = computed(() => !!viewHtml.value);

/** 前台观感是否按 PDF 呈现（无正文块/HTML 时） */
const viewIsPdf = computed(
  () => !hasViewBody.value && detail.value?.contentType === 'PDF',
);

const viewTags = computed(() => {
  const d = detail.value;
  if (!d) return [] as string[];
  const tags: string[] = [];
  if (d.noticeTypeRaw) tags.push(d.noticeTypeRaw);
  const ct = d.contentType ? CONTENT_TYPE_MAP[d.contentType] || d.contentType : '';
  if (ct) tags.push(ct);
  if (d.industryFlag === 'BELONGS') tags.push('行业相关');
  if (d.industryFlag === 'UNCERTAIN') tags.push('行业未知');
  if (d.legalFlag === 'Y') tags.push('依法必招');
  const src = cleanT(d.sourceName);
  if (src) tags.push('来源：' + src);
  return tags;
});

const viewMeta = computed(() => {
  const d = detail.value;
  if (!d) return [] as Array<{ k: string; v: string }>;
  const pairs: Array<{ k: string; v: string }> = [];
  const issuer = cleanT(d.issuerName);
  if (issuer) pairs.push({ k: '发布单位', v: issuer });
  if (fmtDT(d.publishTime)) pairs.push({ k: '发布时间', v: fmtDT(d.publishTime) });
  const eff = [d.effectiveFrom, d.effectiveTo]
    .map(fmtT)
    .filter((x) => x && x !== '—')
    .join(' ~ ');
  if (eff) pairs.push({ k: '生效期', v: eff });
  const proj = cleanT(d.projectName);
  const pKey = cleanT(d.projectKey);
  if (proj) pairs.push({ k: '关联项目', v: proj });
  if (pKey) pairs.push(proj ? { k: '项目编号', v: pKey } : { k: '关联项目', v: pKey });
  const src = [cleanT(d.sourceCode), cleanT(d.sourceName)].filter(Boolean).join(' · ');
  if (src) pairs.push({ k: '来源', v: src });
  return pairs;
});

/** 前台观感顶部“关注”按钮为示意（管理预览无登录态；真实以 ACTIVE 发布后的登录用户为准） */
const viewCanFollow = computed(() => detail.value?.status === 'ACTIVE');

const [Modal, modalApi] = useVbenModal({
  title: '外部公告 · 管理预览',
  showConfirmButton: false,
  onOpenChange: async (isOpen: boolean) => {
    if (!isOpen) {
      detail.value = null;
      errorMsg.value = '';
      tab.value = 'look';
      return;
    }
    const row = modalApi.getData<NoticeExternalApi.ManageItem>();
    if (!row?.id) {
      return;
    }
    loading.value = true;
    errorMsg.value = '';
    tab.value = 'look';
    try {
      detail.value = await getManageDetail(row.id);
    } catch (e) {
      errorMsg.value = e instanceof Error ? e.message : String(e);
    } finally {
      loading.value = false;
    }
  },
});
</script>

<template>
  <Modal class="w-[1180px]">
    <div class="max-h-[76vh] overflow-auto px-2 py-1">
      <div v-if="loading" class="py-16 text-center text-gray-400">加载中…</div>
      <div v-else-if="errorMsg" class="py-10 text-center text-red-500">{{ errorMsg }}</div>
      <div v-else-if="detail">
        <!-- 顶部状态行 -->
        <div class="mb-2 flex flex-wrap items-center gap-2">
          <span class="text-base font-bold text-gray-800">{{ detail.title || '（无标题）' }}</span>
        </div>
        <div class="mb-2 flex flex-wrap items-center gap-1.5 text-xs">
          <template v-if="tag(STATUS_MAP, detail.status)">
            <Tag :color="tag(STATUS_MAP, detail.status)!.color">{{ tag(STATUS_MAP, detail.status)!.text }}</Tag>
          </template>
          <template v-if="tag(AUDIT_MAP, detail.auditStatus)">
            <Tag :color="tag(AUDIT_MAP, detail.auditStatus)!.color">{{ tag(AUDIT_MAP, detail.auditStatus)!.text }}</Tag>
          </template>
          <template v-if="tag(INDUSTRY_MAP, detail.industryFlag)">
            <Tag :color="tag(INDUSTRY_MAP, detail.industryFlag)!.color">行业：{{ tag(INDUSTRY_MAP, detail.industryFlag)!.text }}</Tag>
          </template>
          <Tag v-if="detail.contentType">{{ CONTENT_TYPE_MAP[detail.contentType] || detail.contentType }}</Tag>
          <Tag v-if="detail.noticeTypeRaw">{{ detail.noticeTypeRaw }}</Tag>
          <span v-if="detail.industryConfidence != null" class="text-gray-400">置信度 {{ (detail.industryConfidence * 100).toFixed(0) }}%</span>
        </div>
        <div class="mb-2 flex gap-1 border-b border-gray-100">
          <button class="fb-tab" :class="{ on: tab === 'look' }" @click="tab = 'look'">前台观感（发布后）</button>
          <button class="fb-tab" :class="{ on: tab === 'data' }" @click="tab = 'data'">审核数据 / 净化原文</button>
        </div>

        <!-- ============ 页签1：前台观感（复用门户公文渲染） ============ -->
        <div v-show="tab === 'look'">
          <div class="fb-preview-head">
            <div class="fb-preview-banner">前台观感预览：按门户公告详情渲染语义展示“审核通过并发布后用户看到的样式”（ACTIVE+SOCIAL 后对公开用户可见）。</div>
            <div class="fb-preview-top">
              <div class="fb-preview-tags">
                <span v-for="t in viewTags" :key="t" class="fb-preview-tag">{{ t }}</span>
              </div>
              <div class="fb-preview-actions">
                <button class="fb-follow-btn" :class="{ disabled: !viewCanFollow }" disabled>
                  {{ viewCanFollow ? '＋ 关注' : '审核通过后开放关注' }}
                </button>
                <a v-if="detail.origUrl" class="fb-orig-btn" href="#" @click.prevent>查看原文 ↗</a>
              </div>
            </div>
            <div class="fb-preview-title">{{ detail.title }}</div>
            <div class="fb-preview-meta">
              <span v-for="p in viewMeta" :key="p.k"><b>{{ p.k }}</b>{{ p.v }}</span>
            </div>
          </div>
          <div class="fb-body-area">
            <div v-if="hasViewBody" class="fb-docx" v-dompurify-html="viewHtml"></div>
            <div v-else-if="viewIsPdf" class="fb-pdf-empty">
              PDF 文件 —— 文件服务签名地址（pdfDownloadUrl）回填后即可内嵌预览
              <div v-if="detail.extractionStatus" class="sub">抽取状态：{{ detail.extractionStatus }}</div>
            </div>
            <div v-else class="fb-slim">
              <p>该公告仅有简略信息（标题、发布单位、发布时间见上方），无正文、无文件。</p>
              <a v-if="detail.origUrl" class="fb-primary-btn" href="#" @click.prevent>前往原地址查看</a>
            </div>
          </div>
          <div class="fb-preview-note">正文渲染优先级（与门户一致）：bodyBlocksStatus=SUCCESS → 结构化块（黑体标题/仿宋缩进段落/带框表格/列表）；否则回退净化正文智能排版；HTML 公告发布后同时提供右上“查看原文”（origUrl 经 /go 白名单中转）。</div>
        </div>

        <!-- ============ 页签2：审核数据 / 净化原文 ============ -->
        <div v-show="tab === 'data'">
          <div class="mb-3 grid grid-cols-2 gap-x-6 gap-y-1 rounded border border-gray-100 bg-gray-50 p-3 text-xs text-gray-600">
            <div><b class="text-gray-400">发布单位：</b>{{ empty(detail.issuerName) }}</div>
            <div><b class="text-gray-400">来源：</b>{{ empty(detail.sourceName) }}<span v-if="detail.sourceCode" class="ml-1 text-gray-400">({{ detail.sourceCode }})</span></div>
            <div><b class="text-gray-400">发布时间：</b>{{ fmtT(detail.publishTime) }}</div>
            <div><b class="text-gray-400">生效期：</b>{{ fmtT(detail.effectiveFrom) }} ~ {{ fmtT(detail.effectiveTo) }}</div>
            <div><b class="text-gray-400">关联项目：</b>{{ empty(detail.projectName) }}<span v-if="detail.projectKey" class="ml-1 text-gray-400">({{ detail.projectKey }})</span></div>
            <div><b class="text-gray-400">标段：</b>{{ empty(detail.bidSectionCodes) }}</div>
            <div><b class="text-gray-400">抓取：</b>{{ detail.fetchCount ?? 0 }} 次<template v-if="detail.lastFetchTime">，最近 {{ fmtT(detail.lastFetchTime) }}</template></div>
            <div><b class="text-gray-400">原文：</b><a v-if="detail.origUrl" :href="detail.origUrl" target="_blank" rel="noopener noreferrer" class="text-blue-600 underline">源站链接 ↗</a><span v-else>—</span></div>
          </div>
          <div v-if="detail.contentType === 'PDF'" class="mb-3 rounded border border-amber-100 bg-amber-50 p-3 text-xs text-amber-700">
            PDF 原文件：{{ detail.pdfSize ? Math.round(detail.pdfSize / 1024) + ' KB' : '大小未知' }}
            <template v-if="detail.pdfDownloadUrl">｜<a :href="detail.pdfDownloadUrl" target="_blank" rel="noopener noreferrer" class="underline">下载/预览</a></template>
            <div v-if="detail.extractionSummary" class="mt-1 text-gray-500">抽取摘要：{{ detail.extractionSummary }}</div>
          </div>
          <div class="mb-1 flex items-center gap-2 text-xs text-gray-400">
            <span>净化正文（manage/get 下发 jsoup 白名单净化版 contentHtmlSafe）</span>
            <template v-if="tag(BODY_MAP, detail.bodyBlocksStatus)">
              <Tag :color="tag(BODY_MAP, detail.bodyBlocksStatus)!.color" style="font-size: 11px">正文：{{ tag(BODY_MAP, detail.bodyBlocksStatus)!.text }}</Tag>
            </template>
            <span v-if="detail.bodyParserCode" class="text-gray-400">策略：{{ detail.bodyParserCode === 'AI_FALLBACK' ? 'AI 兜底排版' : '原文结构化' }}</span>
          </div>
          <div v-if="hasBody" class="rounded border border-gray-200 p-4" v-dompurify-html="detail.contentHtmlSafe"></div>
          <div v-else class="rounded border border-dashed border-gray-200 p-6 text-center text-xs text-gray-400">该公告无净化正文（非 HTML 或正文为空），详见上方原文/附件。</div>
          <div v-if="detail.attachments?.length" class="mt-3 text-xs text-gray-500">
            <div class="mb-1 font-bold text-gray-400">附件（{{ detail.attachments.length }}）</div>
            <div v-for="a in detail.attachments" :key="a.id" class="flex items-center gap-2 py-0.5">
              <span>{{ a.name }}</span>
              <Tag v-if="a.virusStatus === 'INFECTED'" color="red" style="font-size: 11px">含病毒已拦截</Tag>
              <Tag v-else-if="a.virusStatus === 'PENDING'" style="font-size: 11px">扫描中</Tag>
              <a v-if="a.downloadUrl && a.virusStatus !== 'INFECTED'" :href="a.downloadUrl" target="_blank" rel="noopener noreferrer" class="text-blue-600 underline">下载</a>
            </div>
          </div>
        </div>
      </div>
    </div>
  </Modal>
</template>

<style>
/* 管理预览内“前台观感”公文容器（门户 .docx 同款；fb- 前缀避免污染全局） */
.fb-tab { padding: 6px 14px; font-size: 13px; color: #7a8591; background: transparent; border: none; border-bottom: 2px solid transparent; cursor: pointer; }
.fb-tab.on { color: #17539a; font-weight: 600; border-bottom-color: #17539a; }
.fb-preview-head { background: #fff; border: 1px solid #e2e6eb; border-bottom: none; padding: 12px 18px; }
.fb-preview-banner { background: #eef4fb; border: 1px solid #d4e2f2; color: #17539a; font-size: 11.5px; padding: 5px 10px; border-radius: 4px; margin-bottom: 10px; }
.fb-preview-top { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.fb-preview-tags { display: flex; flex-wrap: wrap; gap: 5px; }
.fb-preview-tag { display: inline-flex; padding: 0 8px; border-radius: 4px; font-size: 11px; line-height: 1.7; background: #e6eef8; color: #174a86; }
.fb-preview-actions { display: flex; align-items: center; gap: 8px; flex: none; }
.fb-follow-btn { font-size: 12px; font-weight: 700; color: #176b44; border: 1px solid #a9d6bd; background: #e5f2ea; border-radius: 4px; padding: 3px 12px; cursor: default; }
.fb-follow-btn.disabled { color: #8a97a4; border: 1px dashed #d3dae1; background: #fafbfc; }
.fb-orig-btn { font-size: 12px; font-weight: 700; color: #17539a; border: 1px solid #c4d7ec; background: #eef4fb; border-radius: 4px; padding: 3px 12px; text-decoration: none; cursor: default; }
.fb-preview-title { margin: 10px 0; font-size: 17px; line-height: 1.5; color: #1e2832; }
.fb-preview-meta { display: flex; flex-wrap: wrap; gap: 3px 22px; font-size: 12px; color: #44505c; border-top: 1px solid #eceff3; padding-top: 8px; }
.fb-preview-meta b { font-weight: 400; color: #7a8591; margin-right: 4px; }
.fb-body-area { background: #f3f5f7; border: 1px solid #e2e6eb; padding: 14px 18px; }
.fb-docx { background: #fff; border: 1px solid #d9d3c4; width: min(900px, 100%); margin: 0 auto; padding: 40px 60px 44px; font-family: 'FangSong_GB2312', '仿宋', 'FangSong', 'STFangsong', '华文仿宋', 'SimSun', serif; font-size: 17px; line-height: 2; color: #1c1c1c; }
.fb-docx h1, .fb-docx h2, .fb-docx h3, .fb-docx h4, .fb-docx h5, .fb-docx h6 { font-family: 'SimHei', '黑体', 'Microsoft YaHei', sans-serif; font-weight: 700; color: #161616; margin: 20px 0 2px; line-height: 1.8; }
.fb-docx h1 { font-size: 24px; }
.fb-docx h2 { font-size: 20px; }
.fb-docx p { margin: 0; text-indent: 2em; text-align: justify; }
.fb-docx p.doc-para { margin: 0 0 2px; }
.fb-docx h1.doc-title { text-align: center; font-size: 21px; margin: 0 0 14px; }
.fb-docx table { border-collapse: collapse; width: auto; min-width: 60%; max-width: 100%; margin: 12px auto; font-size: 14px; }
.fb-docx caption { font-family: 'SimHei', '黑体', sans-serif; font-size: 14px; font-weight: 700; padding: 5px 0; caption-side: top; }
.fb-docx th, .fb-docx td { border: 1px solid #333; padding: 4px 12px; text-align: center; font-family: inherit; }
.fb-docx th { font-family: 'SimHei', '黑体', sans-serif; font-weight: 700; background: #f7f5ef; }
.fb-docx ol, .fb-docx ul { padding-left: 2.8em; margin: 4px 0; }
.fb-docx li { margin: 3px 0; text-indent: 0; }
.fb-docx p[align='right'], .fb-docx .sign { text-indent: 0; }
.fb-pdf-empty { background: #fff; border: 1px solid #d9dfe5; padding: 50px 20px; text-align: center; color: #7a8591; font-size: 13px; }
.fb-pdf-empty .sub { margin-top: 6px; color: #9aa4af; font-size: 12px; }
.fb-slim { background: #fff; border: 1px solid #f0dcb8; padding: 24px 20px; text-align: center; }
.fb-slim p { color: #8a5a00; font-size: 13px; margin: 0 0 12px; }
.fb-primary-btn { border-radius: 4px; padding: 6px 22px; font-size: 13px; font-weight: 600; color: #fff; background: #17539a; text-decoration: none; display: inline-block; cursor: default; }
.fb-preview-note { color: #9aa4af; font-size: 11.5px; margin-top: 8px; }
</style>
