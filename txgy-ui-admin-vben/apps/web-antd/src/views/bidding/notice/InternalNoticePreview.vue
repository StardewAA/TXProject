<!--
  内部公告 · 发布后大厅预览（复用外部公告展示器「前台观感」渲染语义）
  业务：内部公告写完后，预览"发布后在大厅看到的样子"——复用 noticeExternal/modules/detail.vue 的
        「前台观感」页签结构（.fb-docx 公文容器 + 头部 tags/meta + 正文 + 附件），数据源适配内部公告
        NoticeDetailRespVO（title/content 正文/payload 权威字段集）。
  分层（依据法规，公开/私密）：
    · 字段提取的信息 → 头部 meta（发布单位/时间/公告编号/类型/关联项目·招标项目·标段），不堆表；
    · 公告正文（content 全文）→ .fb-docx 公文段落（仿宋、首行缩进 2em、标题居中黑体）；
    · 公开范围 → internalFlag=true 内部公告（仅本租户，标注"内部"）、publicLevel=GOVERNMENT 政务公开
      （仅报政务平台，不对外）、SOCIAL 社会公开；私密类型不展示"关注/查看原文"等对外元素。
  法规依据：
    · 招标公告和公示信息发布管理办法（发改委令第10号）第5/6/12/13条——公告/公示应载明事项（字段）、
      免费查阅、归类检索；第16条（纠正/澄清）。
    · 电子招标投标办法（八部委令第20号）第9条/第41条——依法公开信息免费获取；应公布的信息（字段）。
    · 招标投标法 第16条——公开招标应当发布招标公告。
  安全：所有输入统一转义，正文经 v-dompurify-html 二次净化（同前台外部公告机制）。
-->
<script lang="ts" setup>
import { computed } from 'vue';

import { fmtDateTime } from '#/utils/datetime';

import { buildCodeItems } from './noticeCodes';

/** 兼容管理端 NoticeApi.Detail 与门户 InternalNoticeDetail 的结构子集（仅预览用到字段） */
export interface PreviewNotice {
  title?: string;
  noticeType?: string;
  noticeTypeName?: string;
  publisherName?: string;
  publishTime?: number | string;
  noticeCode?: string;
  projectName?: string;
  projectCode?: string;
  publicLevel?: string;
  internalFlag?: boolean;
  content?: string;
  payload?: Record<string, unknown>;
}

const props = defineProps<{ detail: PreviewNotice | null; open: boolean }>();
const emit = defineEmits<{ (e: 'update:open', v: boolean): void }>();

function fmtT(v?: number | string): string {
  return fmtDateTime(v ?? '', '—');
}

function esc(value: unknown): string {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

/** 公告正文 → 公文段落：富文本（含真实 HTML 标签，如 Word/网页粘贴）原样交 v-dompurify-html 净化渲染（保留排版）；
 *  纯文本先转义（空行分段、保留换行），避免把字面 < > 当标签吃掉；空值给占位。与 NoticeHtmlView 同机制。 */
const bodyHtml = computed(() => {
  const raw = String(props.detail?.content ?? '').trim();
  if (!raw) return '<p class="doc-empty">（暂无正文）</p>';
  if (/<[a-z][\s\S]*>/i.test(raw)) return raw;
  return raw
    .split(/\r?\n\s*\r?\n/)
    .map((p) => `<p class="doc-para">${esc(p).replaceAll('\n', '<br/>')}</p>`)
    .join('');
});

/** payload 关键字段 → 概览（从字段提取的信息，不整表；只取拟展示的公开业务字段，未知字段不泄英文 key） */
const SUMMARY_FIELDS: Array<[string, string]> = [
  ['TENDERER_NAME', '招标人'],
  ['UNIFIED_DEAL_CODE', '统一交易标识码'],
  ['FUND_SOURCE', '资金来源'],
  ['DOC_GET_START_TIME', '文件获取开始'],
  ['DOC_GET_END_TIME', '文件获取截止'],
  ['BID_END_TIME', '投标递交截止'],
  ['FILE_RECEIVE_ADDRESS', '文件获取地点'],
  ['NOMINAL_FEE', '标书费'],
];

const summaryPairs = computed<Array<{ k: string; v: string }>>(() => {
  const p = props.detail?.payload;
  if (!p) return [];
  const out: Array<{ k: string; v: string }> = [];
  for (const [key, label] of SUMMARY_FIELDS) {
    const raw = p[key];
    if (raw !== undefined && raw !== null && String(raw).trim() !== '') {
      out.push({ k: label, v: String(raw).replace(/[;；]$/, '') });
    }
  }
  return out;
});

/** 三级编号行（项目编号/项目名称/招标项目编号/标段（包）编号/标段（包）数量）：
 *  与正文 HTML（noticeHtmlConverter）、版式 PDF（noticeSections 首节）同源（noticeCodes.ts），
 *  预览里看到的编号 = 发布后读者在门户「我的关注」里跟踪的三种键。 */
const codePairs = computed(() =>
  buildCodeItems({
    payload: props.detail?.payload,
    projectCode: props.detail?.projectCode,
    projectName: props.detail?.projectName,
  }),
);

/** 头部 meta：发布单位/时间/公告编号/关联项目（字段提取） */
const meta = computed<Array<{ k: string; v: string }>>(() => {
  const d = props.detail;
  const pairs: Array<{ k: string; v: string }> = [];
  if (d?.publisherName) pairs.push({ k: '发布单位', v: d.publisherName });
  if (d?.publishTime) pairs.push({ k: '发布时间', v: fmtT(d.publishTime) });
  if (d?.noticeCode) pairs.push({ k: '公告编号', v: d.noticeCode });
  // 项目编号/项目名称由下方「三级编号」行统一展示（不重复占位）
  if (d?.projectName) pairs.push({ k: '关联项目', v: d.projectName });
  return pairs;
});

/** 类型 + 可见范围标签（公开/私密分层） */
const tags = computed<string[]>(() => {
  const d = props.detail;
  const out: string[] = [];
  if (d?.noticeTypeName) out.push(d.noticeTypeName);
  else if (d?.noticeType) out.push(d.noticeType);
  if (d?.internalFlag) out.push('内部公告');
  else if (d?.publicLevel === 'GOVERNMENT') out.push('政务公开');
  else out.push('社会公开');
  return out;
});

/** 是否对外公开（SOCIAL）：对外才展示关注/查看原文等元素 */
const publicReadable = computed(
  () => props.detail?.internalFlag !== true && props.detail?.publicLevel !== 'GOVERNMENT',
);
</script>

<template>
  <a-modal
    :open="open"
    :title="'发布后大厅预览' + (detail?.title ? ' · ' + detail.title : '')"
    :width="1080"
    :footer="null"
    destroy-on-close
    @update:open="(v: boolean) => emit('update:open', v)"
  >
    <div v-if="detail" class="ip-wrap">
      <!-- 前台观感头：复用外部公告展示器结构（.fb-preview-head） -->
      <div class="ip-preview-head">
        <div class="ip-preview-banner">
          前台观感预览：按门户公告详情渲染语义展示"发布后在大厅看到的样子"
          <template v-if="detail.internalFlag">（内部公告，仅本租户可见，不对社会公开）</template>
          <template v-else-if="detail.publicLevel === 'GOVERNMENT'">（政务公开，仅报政务平台，不对公众公开）</template>
        </div>
        <div class="ip-preview-top">
          <div class="ip-preview-tags">
            <span v-for="t in tags" :key="t" class="ip-preview-tag">{{ t }}</span>
          </div>
          <div class="ip-preview-actions">
            <button v-if="publicReadable" class="ip-follow-btn">＋ 关注</button>
            <span v-else class="ip-follow-btn disabled">私密公告 · 不公开</span>
          </div>
        </div>
        <div class="ip-preview-title">{{ detail.title || '（无标题）' }}</div>
        <div class="ip-preview-meta">
          <span v-for="m in meta" :key="m.k"><b>{{ m.k }}</b>{{ m.v }}</span>
        </div>
        <!-- 三级编号：项目 / 招标项目 / 标段（包）——关注与投标流程跟踪的三种编号键 -->
        <div v-if="codePairs.length" class="ip-codes">
          <span v-for="c in codePairs" :key="c.key" class="ip-code-item">
            <b>{{ c.label }}</b><em>{{ c.value }}</em>
          </span>
        </div>
        <!-- 概要（从字段提取的关键信息，非表格） -->
        <div v-if="summaryPairs.length" class="ip-summary">
          <span v-for="s in summaryPairs" :key="s.k" class="ip-summary-item">
            <b>{{ s.k }}</b><em>{{ s.v }}</em>
          </span>
        </div>
      </div>

      <!-- 公告正文：.fb-docx 公文容器（仿宋/标题居中/正文首行缩进），与外部公告前台观感一致 -->
      <div class="ip-body-area">
        <div class="ip-docx" v-dompurify-html="bodyHtml"></div>
      </div>
      <div class="ip-note">
        正文为公告全文（招标公告和公示信息发布管理办法第5条应载明内容）；概要字段为从权威数据集提取的公开信息（电子招标投标办法第41条）。
      </div>
    </div>
    <div v-else class="ip-empty">无公告数据可预览</div>
  </a-modal>
</template>

<style scoped>
/* 复用外部公告展示器「前台观感」公文样式（ip- 前缀，浅色公文观感 = 发布后门户效果） */
.ip-preview-head { background: hsl(var(--card)); border: 1px solid hsl(var(--border)); padding: 12px 18px; }
.ip-preview-banner { background: hsl(var(--primary) / 8%); border: 1px solid hsl(var(--primary) / 25%); color: hsl(var(--primary)); font-size: 11.5px; padding: 5px 10px; border-radius: 4px; margin-bottom: 10px; }
.ip-preview-top { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.ip-preview-tags { display: flex; flex-wrap: wrap; gap: 5px; }
.ip-preview-tag { display: inline-flex; padding: 0 8px; border-radius: 4px; font-size: 11px; line-height: 1.7; background: hsl(var(--muted)); color: hsl(var(--muted-foreground)); }
.ip-preview-actions { display: flex; align-items: center; gap: 8px; flex: none; }
.ip-follow-btn { font-size: 12px; font-weight: 600; color: var(--ind-ok); border: 1px solid hsl(var(--success) / 45%); background: hsl(var(--success) / 10%); border-radius: 4px; padding: 3px 12px; }
.ip-follow-btn.disabled { color: hsl(var(--muted-foreground)); border: 1px dashed hsl(var(--border)); background: hsl(var(--muted) / 40%); }
.ip-preview-title { margin: 10px 0; font-size: 18px; font-weight: 600; line-height: 1.5; color: hsl(var(--foreground)); }
.ip-preview-meta { display: flex; flex-wrap: wrap; gap: 3px 22px; font-size: 12px; color: hsl(var(--muted-foreground)); border-top: 1px solid hsl(var(--border)); padding-top: 8px; }
.ip-preview-meta b { font-weight: 400; color: hsl(var(--muted-foreground)); margin-right: 4px; }
.ip-summary { display: flex; flex-wrap: wrap; gap: 4px 20px; margin-top: 10px; font-size: 12.5px; color: hsl(var(--muted-foreground)); }
.ip-summary-item b { font-weight: 400; color: hsl(var(--muted-foreground)); margin-right: 5px; }
.ip-summary-item em { font-style: normal; font-weight: 600; color: hsl(var(--foreground)); }
.ip-codes { display: flex; flex-direction: column; gap: 3px; margin-top: 9px; padding: 7px 10px; border: 1px dashed hsl(var(--primary) / 35%); border-radius: 4px; background: hsl(var(--primary) / 5%); font-size: 12.5px; }
.ip-code-item b { font-weight: 400; color: hsl(var(--muted-foreground)); margin-right: 5px; }
.ip-code-item em { font-style: normal; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-weight: 600; color: hsl(var(--foreground)); }
.ip-body-area { background: hsl(var(--muted) / 40%); border: 1px solid hsl(var(--border)); padding: 14px 18px; }
.ip-docx { background: #fff; border: 1px solid #d9d3c4; width: min(900px, 100%); margin: 0 auto; padding: 40px 60px 44px; font-family: 'FangSong_GB2312', '仿宋', 'FangSong', 'STFangsong', '华文仿宋', 'SimSun', serif; font-size: 17px; line-height: 2; color: #1c1c1c; }
.ip-docx :deep(p) { margin: 0; text-indent: 2em; text-align: justify; }
.ip-docx :deep(p.doc-para) { margin: 0 0 2px; }
.ip-docx :deep(p.doc-empty) { text-indent: 0; color: #999; }
.ip-note { color: hsl(var(--muted-foreground)); font-size: 11.5px; margin-top: 8px; }
.ip-empty { padding: 40px 0; text-align: center; color: hsl(var(--muted-foreground)); }
</style>