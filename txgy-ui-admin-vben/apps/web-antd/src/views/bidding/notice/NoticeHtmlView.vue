<!-- ============================================================================
  公告详情渲染器 NoticeHtmlView（前端自研 HTML 渲染，非后端生成的 HTML）
  业务（08-公告公示前端交付-业务口径说明 + docs/公告发布-字段对齐与自研渲染器-附件公开-2026-09-10.md）：
    公告/公示 = 招标人对外发布的法律文书；门户/详情按“结构化字段(权威数据集) + 正文 + 版式文件/附件”三部分展示。
    本组件是管理端详情与门户详情的共用渲染件：数据源 = NoticeDetailRespVO 全量（payload 权威字段/content 正文 vN/
    pdfFileId 对外最终版式 + attachments 附件清单 + 元数据），后端不产 HTML，前端负责渲染。
  渲染分层（文件可见性，勿无条件给下载）：
    L1 公告版式 PDF —— 对外公告的“最终公布件”（盖章 PDF，优先于富文本正文，正文用于站内阅读）：
       社会公开(SOCIAL)公告免费查阅/下载；政务公开(GOVERNMENT)/企业内部(internalFlag)受限（本租户可见）。
    L2 公告附件 —— 随公告公开性；外部公告附件须病毒扫描 CLEAN 后放行；下载地址由业务系统按可见性签发（前端零拼接）。
    L3 招标文件/资格预审文件 —— 不公开下载，只展示获取指引（DOC_GET_START/END_TIME、FILE_RECEIVE_ADDRESS、
       TENDER_DOC_GET_METHOD、NOMINAL_FEE）；获取按招标文件规定办理（时间/地点/方式与标书费以公告为准）。
  安全：字段/元信息等业务文本一律文本节点输出（{{ }}）；公告正文（content 可能是富文本 HTML）经
       v-dompurify-html（DOMPurify 白名单）二次净化后渲染——与 InternalNoticePreview/门户外部公告同机制，
       纯文本自动转义分段；不使用裸 v-html / innerHTML，防存储型 XSS（安全评审）。
  依据（法规/技术规范，含文件类型）：
    · 招标投标法 第16条（法律）——公开招标应当发布招标公告；公告载明获取招标文件的办法等法定事项；
    · 实施条例 第15条（行政法规）——依法发布招标公告、编制招标文件；
    · 招标公告和公示信息发布管理办法（发改委令第10号，部门规章/政府文件）第5/6/12/13条——公告/公示载明事项、
      免费提供依法必须招标项目的公告公示查阅、信息归类与检索（本组件对公告信息做免费只读展示的落点）；
    · 电子招标投标办法（八部委令第20号，部门规章/政府文件）第9条——市场主体免费获取依法公开的招投标信息；
    · GB 32100-2015（技术规范）——统一社会信用代码唯一标识（正文/字段中的企业主体），本组件原样展示不改写。
  后端契约（嵌套仓库 docs/）：
    · 前端规范-全域接口说明/08-公告公示前端交付-业务口径说明.md §4（pdfFileId=对外最终件、internalFlag=false 对外）、§5（合规提醒）；
    · 前端规范-全域接口说明/01-接口逐一说明与设计依据.md §1（notice T 轨）与 06-契约对照 §2；
    · docs/公告发布-字段对齐与自研渲染器-附件公开-2026-09-10.md §三（文件可见性分层 L1/L2/L3）与 NoticeDetailRespVO 字段；
    · 字段字典真源：payloadLabel.ts = 公共资源交易平台系统数据规范 V2.0/陕西 V2.0 字段清单（技术规范，数据集字段）；
      后端字典接口（契约 §四 F-1，R-4 字段字典）落地后以接口为准，本字典仅离线兜底。
============================================================================ -->
<script lang="ts" setup>
import { computed, ref } from 'vue';

import { fmtBytes } from '#/utils/format';

import PdfPreview from '../vo/PdfPreview.vue';
import { fmtFieldValue, groupPayload, PAYLOAD_LABEL, UNKNOWN_FIELD_LABEL } from './payloadLabel';
import { buildNoticeSections } from './noticeSections';

export interface HtmlAttachment {
  id?: number | string;
  fileId?: number | string;
  name?: string;
  size?: number;
  kind?: string;
  downloadUrl?: string;
}
export interface RenderDetail {
  title?: string;
  noticeCode?: string;
  /** 公告根字段·真实项目编号（三级跟踪第①级，不是招标项目编号；见 noticeCodes.ts） */
  projectCode?: string;
  /** 公告根字段·项目名称快照 */
  projectName?: string;
  noticeType?: string;
  noticeTypeName?: string;
  status?: string;
  publisherName?: string;
  publishTime?: number | string;
  publicLevel?: string;
  internalFlag?: boolean;
  contentVersion?: null | number;
  content?: null | string;
  payload?: null | Record<string, unknown>;
  pdfFileId?: null | number | string;
  pdfSize?: null | number;
  pdfSha256?: null | string;
  pdfDownloadUrl?: null | string;
  attachments?: HtmlAttachment[] | null;
}

const props = withDefaults(defineProps<{ detail: RenderDetail; showHead?: boolean }>(), { showHead: true });
const sections = computed(() => groupPayload(props.detail?.payload));
/** 四类公告文书分节（noticeSections.ts 单一数据源；与打印 HTML/版式 PDF 同源，保证展示=版式） */
const docSections = computed(() =>
  buildNoticeSections(props.detail?.noticeType, props.detail?.payload, {
    projectCode: props.detail?.projectCode,
    projectName: props.detail?.projectName,
  }),
);
/** HTML 转义（正文纯文本 → 段落之前的转义；与门户 format.ts escapeHtml 同语义） */
function escHtml(s: unknown): string {
  return String(s ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}
/** 公告正文 → 安全 HTML（v-dompurify-html 白名单二次净化）：
 *  · 已含 HTML 标签（富文本）→ 原样交给 DOMPurify 净化渲染（保留排版结构）；
 *  · 纯文本 → 转义 + 按空行分段（p 容器 + <br/> 保换行），与 InternalNoticePreview 同观感。 */
const bodyHtml = computed(() => {
  const raw = String(props.detail?.content ?? '').trim();
  if (!raw) return '';
  if (/<[a-z][\s\S]*>/i.test(raw)) return raw;
  return raw
    .split(/\r?\n\s*\r?\n/)
    .map((para) => `<p class="nh-para">${escHtml(para).replace(/\n/g, '<br/>')}</p>`)
    .join('');
});
const p2 = (n: number): string => String(n).padStart(2, '0');
/** 时间格式化：兼容 epoch 毫秒(数字字符串)与 ISO 字符串 → yyyy-MM-dd HH:mm。
 * 业务/理由：权威数据集时间类值为 yyyyMMddHHmmss / epoch ms，统一转可读时间展示（payloadLabel.ts 约定）；
 * 展示“发布日期/公示起止”等法定时效字段时保持原文语义，不擅自改时区/裁剪（08 §5 合规提醒）。
 * 依据：10号令 第5/6条（部门规章/政府文件，公示期限等载明事项）；契约：08 §5、NoticeDetailRespVO 时间字段为 epoch ms。 */
function fmtDateTime(v?: null | number | string): string {
  if (v === undefined || v === null || v === '') return '—';
  const s = String(v);
  const d = /^\d+$/.test(s) ? new Date(Number(s)) : new Date(s);
  if (Number.isNaN(+d)) return fmtFieldValue(v);
  return d.getFullYear() + '-' + p2(d.getMonth() + 1) + '-' + p2(d.getDate()) + ' ' + p2(d.getHours()) + ':' + p2(d.getMinutes());
}
/** 本页空值留白（公告正文里的附件清单，没有大小就不显示单位占位） */
const sizeText = (n?: null | number): string => fmtBytes(n, '');
/** 公告生命周期文案/色板（管理端可见草稿/撤下/归档；大厅只读只展示 PUBLISHED）。
 * 契约：08 §3 状态机 DRAFT→PUBLISHED→UNPUBLISHED→(再发布)；ARCHIVED=归档终态；管理态字段仅管理端详情下发。 */
const statusText: Record<string, string> = { DRAFT: '草稿', PUBLISHED: '已发布', UNPUBLISHED: '已撤下', ARCHIVED: '已归档' };
/** 状态色板走组件变量（--nh-st-*）：管理端随深浅色切换，门户内锁定浅色见样式块。 */
const statusColor: Record<string, string> = {
  DRAFT: 'var(--nh-st-draft)',
  PUBLISHED: 'var(--nh-st-published)',
  UNPUBLISHED: 'var(--nh-st-unpublished)',
  ARCHIVED: 'var(--nh-st-archived)',
};

/* L3 招标文件获取指引：仅当 payload 含获取字段时渲染（DOC_GET_START/END_TIME、FILE_RECEIVE_ADDRESS、
 * TENDER_DOC_GET_METHOD、NOMINAL_FEE——键名中的星号为“通配”示意，非代码通配符）。
 * 业务/理由：招标文件/资格预审文件“获取方式”是招标公告的法定载明事项（时间/地点/方式/费用），
 * 但平台不提供无条件下载——获取须按招标文件规定办理，避免把文件获取前置条件/收费口径在门户暴露错。
 * 依据：招标投标法 第16条（法律，公告应载明获取招标文件的办法）；10号令 第5条（部门规章/政府文件）。
 * 契约：docs/公告发布-字段对齐… §三 L3；后端契约 F-2/R-3（多文件+公开URL 待补，前端先按提示位展示）。 */
const GET_HINT_KEYS = ['DOC_GET_START_TIME', 'DOC_GET_END_TIME', 'FILE_RECEIVE_ADDRESS', 'TENDER_DOC_GET_METHOD', 'NOMINAL_FEE'] as const;
const getHints = computed<Array<{ key: string; label: string; value: string }>>(() => {
  const p = props.detail?.payload;
  if (!p) return [];
  const out: Array<{ key: string; label: string; value: string }> = [];
  for (const k of GET_HINT_KEYS) {
    const raw = p[k];
    const value = fmtFieldValue(raw);
    if (!value) continue;
    out.push({ key: k, label: PAYLOAD_LABEL[k] || k, value });
  }
  return out;
});
/** 是否“社会公开可免费查阅/下载”：对外(SOCIAL)公告的 L1 版式 PDF 免费提供；政务公开(GOVERNMENT)/企业内部公告受限。
 * 业务/理由：10号令 第12条 免费查阅；对外法律文书为最终公布件，公开性由 publicLevel+internalFlag 决定（08 §4）。
 * 依据：10号令 第12/13条（部门规章/政府文件）；电子办法 第9条（部门规章/政府文件）。
 * 契约：docs/公告发布-字段对齐… §三 L1 可见性；NoticeDetailRespVO.publicLevel/internalFlag。 */
const publicReadable = computed(() => !props.detail?.internalFlag && props.detail?.publicLevel !== 'GOVERNMENT');

/* ===== 预览 与 下载 是**两个动作**（用户 2026-09-14 指出此前混为一谈） =====
 *   · 预览：内嵌查看器（iframe），只读，不触发另存 —— 走 PdfPreview 组件（给 url 优先；只有 file_id 时它去 /infra/file/get 取地址）；
 *   · 下载：独立入口，用 HTML download 属性触发另存，与预览互不替代；
 *   · 此前本区只有一条「免费查阅下载 ↗」指向 pdfDownloadUrl：既没有预览实现（预览区因此是空的），
 *     又把"查阅"和"下载"混成一个词 —— 故拆开并分别命名。
 *   · 内部公告（T 轨）目前**没有**后端字节流代理（外部公告才有 /bidding/notice-external/pdf），
 *     所以 pdfDownloadUrl 为空时预览只能靠 file_id 走 /infra/file/get（可能 403）；已在契约 14 请求补齐。 */
const previewOpen = ref(false);
const previewUrl = ref('');
const previewFileId = ref<number | string | undefined>(undefined);
const previewName = ref('');
function openPdfPreview(url?: null | string, fileId?: null | number | string, name?: string): void {
  previewUrl.value = url ?? '';
  previewFileId.value = fileId ?? undefined;
  previewName.value = name ?? '';
  previewOpen.value = true;
}
</script>

<template>
  <div class="nhtml">
    <!-- 头部（可选） -->
    <div v-if="showHead" class="nh-head">
      <div class="nh-ttl">{{ detail?.title || '（无标题）' }}</div>
      <div class="nh-meta">
        <span v-if="detail?.noticeTypeName" class="pill">{{ detail.noticeTypeName }}</span>
        <span v-if="detail?.status" class="pill" :style="{ color: statusColor[detail.status], borderColor: statusColor[detail.status] }">{{ statusText[detail.status] || detail.status }}</span>
        <span v-if="detail?.publisherName" class="item">{{ detail.publisherName }}</span>
        <span v-if="detail?.noticeCode" class="item mono">{{ detail.noticeCode }}</span>
        <span v-if="detail?.publishTime" class="item">发布时间 {{ fmtDateTime(detail.publishTime) }}</span>
        <span v-if="detail?.publicLevel" class="item">{{ detail.publicLevel === 'GOVERNMENT' ? '政务公开' : '社会公开' }}<template v-if="detail?.internalFlag">（企业内部）</template></span>
        <span v-if="detail?.contentVersion" class="item">正文 v{{ detail.contentVersion }}</span>
      </div>
    </div>

    <!-- 权威字段区：四类公告按文书分节渲染（noticeSections.ts 单一数据源，与打印 HTML/版式 PDF 同源，
         政务报送字段不进分节）；未知/未注册类型回退通用分组（groupPayload 兜底）。
         依据：10号令 第5/6条（部门规章/政府文件，载明事项）；契约：docs/公告发布… §二、NoticeDetailRespVO.payload。 -->
    <template v-if="docSections.length">
      <section v-for="sec in docSections" :key="sec.title" class="nh-sec">
        <div class="nh-sec-t">{{ sec.title }}</div>
        <table class="nh-tbl">
          <tbody>
            <tr v-for="it in sec.items" :key="it.key">
              <td class="nh-k">{{ it.label }}</td>
              <td class="nh-v">{{ it.value }}</td>
            </tr>
          </tbody>
        </table>
      </section>
    </template>
    <template v-else>
      <section v-for="sec in sections" :key="sec.title" class="nh-sec">
        <div class="nh-sec-t">{{ sec.title }}</div>
        <table class="nh-tbl">
          <tbody>
            <tr v-for="it in sec.items" :key="it.key">
              <td class="nh-k">{{ it.label }}<span v-if="!it.known" class="nh-uk" :title="UNKNOWN_FIELD_LABEL">待补</span></td>
              <td class="nh-v">{{ it.value }}</td>
            </tr>
          </tbody>
        </table>
      </section>
      <div v-if="!sections.length" class="nh-none">（无结构化字段）</div>
    </template>

    <!-- ③ 正文：富文本正文用于站内阅读；对外“最终公布件”为 L1 盖章 PDF（若两者均传以 PDF 为准，08 §4）。
         依据：招标投标法 第16条（法律）/10号令 第5条（部门规章/政府文件）公告载明事项；契约：08 §4、NoticeDetailRespVO.content/contentVersion。 -->
    <section class="nh-sec">
      <div class="nh-sec-t">公告正文<template v-if="detail?.contentVersion">（v{{ detail.contentVersion }}）</template></div>
      <div v-if="bodyHtml" class="nh-body" v-dompurify-html="bodyHtml"></div>
      <div v-else class="nh-none">（暂无正文——发布前须录入正文）</div>
    </section>

    <!-- ④ L3 招标文件/资格预审文件获取指引（不提供下载，仅指引）：
         依据：招标投标法 第16条（法律）公告应载明获取招标文件的办法；10号令 第5条（部门规章/政府文件）；
         契约：docs/公告发布… §三 L3（DOC_GET_*/FILE_RECEIVE_ADDRESS/TENDER_DOC_GET_METHOD/NOMINAL_FEE，标书费/保证金提示不公开下载）。 -->
    <section v-if="getHints.length" class="nh-sec">
      <div class="nh-sec-t">招标文件 / 资格预审文件获取指引</div>
      <table class="nh-tbl">
        <tbody>
          <tr v-for="h in getHints" :key="h.key">
            <td class="nh-k">{{ h.label }}</td>
            <td class="nh-v">{{ h.value }}</td>
          </tr>
        </tbody>
      </table>
      <div class="nh-note">获取招标文件按招标文件规定办理（时间/地点/方式与标书费以公告为准），不提供无条件下载。</div>
    </section>

    <!-- ⑤ L1 版式 PDF + L2 附件：下载地址由业务系统按公开性签发（前端零拼接）；
         依据：10号令 第12/13条（部门规章/政府文件，免费查阅与归类检索）、电子办法 第9条（部门规章/政府文件）；
         契约：docs/公告发布… §三 L1/L2；外部公告附件病毒扫描 CLEAN 后放行 —— **后端已实现**
         （NoticeExternalServiceImpl：INFECTED 一律不下发链接、逐条下发 virusStatus，前端据此显示"已拦截/扫描中"）；
         仍在的缺口是签名下载地址：后端注释为"FileApi 签名 downloadUrl 待 infra 文件下载能力接入后回填（TODO）"，
         故此处 pdfDownloadUrl 为空时只给提示、不拼链接。 -->
    <section v-if="detail?.pdfFileId || detail?.attachments?.length" class="nh-sec">
      <div class="nh-sec-t">版式文件与公告附件</div>
      <ul class="nh-files">
        <li v-if="detail?.pdfFileId" class="nh-file">
          <span class="doc">公告版式文件（PDF）</span>
          <span class="meta mono">file_id #{{ detail.pdfFileId }}<template v-if="detail?.pdfSize"> · {{ sizeText(detail.pdfSize) }}</template></span>
          <a class="dl" role="button" tabindex="0" @click="openPdfPreview(detail?.pdfDownloadUrl, detail?.pdfFileId, '公告版式文件（PDF）')" @keydown.enter.prevent="openPdfPreview(detail?.pdfDownloadUrl, detail?.pdfFileId, '公告版式文件（PDF）')">预览</a>
          <a v-if="detail?.pdfDownloadUrl" class="dl" :href="detail.pdfDownloadUrl" download rel="noopener">下载</a>
          <span v-else class="tip">下载地址由业务系统签发后开放</span>
          <span v-if="!publicReadable" class="tip">受限公告：仅本租户可见</span>
        </li>
        <li v-for="a in detail?.attachments || []" :key="String(a.id ?? a.fileId)" class="nh-file">
          <span class="doc">{{ a.kind === 'PDF' ? 'PDF 附件' : '附件' }} {{ a.name || '（未命名）' }}</span>
          <span class="meta mono">{{ sizeText(a.size) }}</span>
          <a v-if="a.kind === 'PDF'" class="dl" role="button" tabindex="0" @click="openPdfPreview(a.downloadUrl, a.fileId, a.name)" @keydown.enter.prevent="openPdfPreview(a.downloadUrl, a.fileId, a.name)">预览</a>
          <a v-if="a.downloadUrl" class="dl" :href="a.downloadUrl" download rel="noopener">下载</a>
          <span v-else class="tip">（附件下载地址由业务系统按公开性签发；外部公告附件需病毒扫描 CLEAN 后放行）</span>
        </li>
      </ul>
    </section>
    <!-- 预览弹窗（iframe 内嵌查看器）。**预览与下载是两个动作**：预览只读不改动文件，下载另存；
         此前本区只有一条"免费查阅下载"链接且没有预览实现（预览区因此是空的）。 -->
    <PdfPreview
      v-model:open="previewOpen"
      :file-id="previewFileId"
      :file-name="previewName"
      :title="previewName || '文件预览'"
      :url="previewUrl"
    />
  </div>
</template>

<style scoped>
/* 默认跟随平台主题（管理端详情）；门户（.portal-shell，固定浅色站点）内由下方覆盖块锁浅色 */
.nhtml {
  --nh-fg: hsl(var(--foreground));
  --nh-strong: hsl(var(--foreground));
  --nh-muted: hsl(var(--muted-foreground));
  --nh-border: hsl(var(--border));
  --nh-accent: hsl(var(--primary));
  --nh-accent-soft: hsl(var(--primary) / 8%);
  --nh-accent-border: hsl(var(--primary) / 25%);
  --nh-head-bg: hsl(var(--muted) / 50%);
  --nh-warn-fg: var(--ind-warn, #b45309);
  --nh-warn-bg: var(--ind-warn-bg, #fef3c7);
  --nh-warn-border: var(--ind-warn-border, #fde68a);
  --nh-st-draft: #909399;
  --nh-st-published: #16a34a;
  --nh-st-unpublished: #ea580c;
  --nh-st-archived: #2563eb;
  font-size: 13px;
  color: var(--nh-fg);
}
/* 深色模式：状态徽标取亮色变体，保证在深底上的可读性。
 * 注意：整条选择器必须写进 :global()——写成「:global(.dark) .nhtml」时后者会被编译丢掉。 */
:global(.dark .nhtml) {
  --nh-warn-fg: #fbbf24;
  --nh-warn-bg: hsl(var(--warning) / 15%);
  --nh-warn-border: hsl(var(--warning) / 35%);
  --nh-st-draft: #9ca3af;
  --nh-st-published: #4ade80;
  --nh-st-unpublished: #fb923c;
  --nh-st-archived: #60a5fa;
}
/* 说明：2026-09-11 起门户招投标页同样跟随平台主题，原先「门户内锁浅色」的
 * :global(.portal-shell .nhtml) 覆盖块已移除；非招投标门户页的浅色锁定见 layouts/portal.vue（.portal-light）。 */
.nh-head { padding-bottom: 10px; border-bottom: 2px solid var(--nh-accent); margin-bottom: 14px; }
.nh-ttl { font-size: 17px; font-weight: 700; line-height: 1.5; color: var(--nh-strong); }
.nh-meta { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; margin-top: 8px; color: var(--nh-muted); font-size: 12px; }
.pill { padding: 0 7px; line-height: 18px; color: var(--nh-accent); background: var(--nh-accent-soft); border: 1px solid var(--nh-accent-border); border-radius: 4px; }
.mono { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; }
.nh-sec { margin-top: 14px; }
.nh-sec-t { margin-bottom: 6px; padding-left: 7px; font-size: 13px; font-weight: 600; color: var(--nh-strong); border-left: 3px solid var(--nh-accent); }
.nh-tbl { width: 100%; border-collapse: collapse; }
.nh-tbl td { padding: 6px 9px; vertical-align: top; line-height: 1.6; word-break: break-all; border: 1px solid var(--nh-border); }
.nh-tbl td.nh-k { width: 46%; min-width: 180px; font-size: 12px; color: var(--nh-muted); background: var(--nh-head-bg); }
.nh-tbl td.nh-v { color: var(--nh-strong); }
.nh-uk { margin-left: 6px; padding: 0 4px; font-size: 10px; color: var(--nh-warn-fg); background: var(--nh-warn-bg); border-radius: 3px; }
.nh-body { font-size: 13.5px; line-height: 1.95; }
.nh-body :deep(p) { margin: 0 0 8px; }
.nh-body :deep(p.nh-para) { text-indent: 2em; }
.nh-none { padding: 6px 0; font-size: 12px; color: var(--nh-muted); }
.nh-note { margin-top: 6px; padding: 5px 9px; font-size: 11px; color: var(--nh-warn-fg); background: var(--nh-warn-bg); border: 1px solid var(--nh-warn-border); border-radius: 4px; }
.nh-files { list-style: none; margin: 0; padding: 0; }
.nh-file { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; padding: 7px 10px; margin-bottom: 6px; font-size: 12px; border: 1px solid var(--nh-border); border-radius: var(--radius); }
.nh-file .doc { font-weight: 600; color: var(--nh-strong); }
.nh-file .meta { color: var(--nh-muted); }
.nh-file .dl { color: var(--nh-accent); text-decoration: none; }
.nh-file .tip { font-size: 11px; color: var(--nh-warn-fg); }
</style>
