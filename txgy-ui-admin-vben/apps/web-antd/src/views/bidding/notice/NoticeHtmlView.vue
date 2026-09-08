<!--
 * 自研公告渲染器（内部 HTML 形态，2026-09-10 修订版）
 * 依据契约《公告发布-字段对齐与自研渲染器-附件公开》：
 *  - 数据源 = NoticeDetailRespVO（payload 权威字段全集/content 正文 vN/pdfFileId）——后端不生成 HTML，前端渲染；
 *  - 文件区按可见性分层（不无条件给下载）：
 *      L1 公告版式 PDF（公告公示材料，社会公开公告免费查阅/下载；内部公告仅租户内）；
 *      L2 公告附件（随公告公开性；CLEAN 放行）；
 *      L3 招标文件/资格预审文件（不公开下载，仅展示获取指引：DOC_GET_*/FILE_RECEIVE_ADDRESS/TENDER_DOC_GET_METHOD/NOMINAL_FEE）；
 *  - 安全：业务文本全部文本节点输出，无 innerHTML。
 -->
<script lang="ts" setup>
import { computed } from 'vue';

import { PAYLOAD_LABEL, UNKNOWN_FIELD_LABEL, fmtFieldValue, groupPayload } from './payloadLabel';

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
  noticeType?: string;
  noticeTypeName?: string;
  status?: string;
  publisherName?: string;
  publishTime?: number | string;
  publicLevel?: string;
  internalFlag?: boolean;
  contentVersion?: number | null;
  content?: string | null;
  payload?: Record<string, unknown> | null;
  pdfFileId?: number | string | null;
  pdfSize?: number | null;
  pdfSha256?: string | null;
  pdfDownloadUrl?: string | null;
  attachments?: HtmlAttachment[] | null;
}

const props = withDefaults(defineProps<{ detail: RenderDetail; showHead?: boolean }>(), { showHead: true });
const sections = computed(() => groupPayload(props.detail?.payload));
const p2 = (n: number): string => String(n).padStart(2, '0');
function fmtDateTime(v?: number | string | null): string {
  if (v === undefined || v === null || v === '') return '—';
  const s = String(v);
  const d = /^\d+$/.test(s) ? new Date(Number(s)) : new Date(s);
  if (Number.isNaN(+d)) return fmtFieldValue(v);
  return d.getFullYear() + '-' + p2(d.getMonth() + 1) + '-' + p2(d.getDate()) + ' ' + p2(d.getHours()) + ':' + p2(d.getMinutes());
}
const fmtBytes = (n?: number | null): string => {
  if (n === undefined || n === null) return '';
  if (n < 1024) return n + ' B';
  if (n < 1024 * 1024) return (n / 1024).toFixed(1) + ' KB';
  return (n / 1024 / 1024).toFixed(2) + ' MB';
};
const statusText: Record<string, string> = { DRAFT: '草稿', PUBLISHED: '已发布', UNPUBLISHED: '已撤下', ARCHIVED: '已归档' };
const statusColor: Record<string, string> = { DRAFT: '#909399', PUBLISHED: '#16a34a', UNPUBLISHED: '#ea580c', ARCHIVED: '#2563eb' };

/** L3 文件获取指引：仅当 payload 含获取字段时渲染（按招标文件规定，非公开下载） */
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
const publicReadable = computed(() => !props.detail?.internalFlag && props.detail?.publicLevel !== 'GOVERNMENT');
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

    <!-- 权威字段表 -->
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

    <!-- 正文 -->
    <section class="nh-sec">
      <div class="nh-sec-t">公告正文<template v-if="detail?.contentVersion">（v{{ detail.contentVersion }}）</template></div>
      <div v-if="detail?.content" class="nh-body">{{ detail.content }}</div>
      <div v-else class="nh-none">（暂无正文——发布前须录入正文）</div>
    </section>

    <!-- L3 招标文件获取指引（不公开下载） -->
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

    <!-- L1 版式 PDF / L2 公告附件 -->
    <section v-if="detail?.pdfFileId || detail?.attachments?.length" class="nh-sec">
      <div class="nh-sec-t">版式文件与公告附件</div>
      <ul class="nh-files">
        <li v-if="detail?.pdfFileId" class="nh-file">
          <span class="doc">公告版式文件（PDF）</span>
          <span class="meta mono">file_id #{{ detail.pdfFileId }}<template v-if="detail?.pdfSize"> · {{ fmtBytes(detail.pdfSize) }}</template></span>
          <a v-if="detail?.pdfDownloadUrl" class="dl" :href="detail.pdfDownloadUrl" target="_blank" rel="noopener">{{ publicReadable ? '免费查阅下载 ↗' : '查阅 ↗' }}</a>
          <span v-else class="tip">{{ publicReadable ? '（公开公告可免费查阅；下载地址由业务系统签发后开放）' : '（企业内部公告，仅本租户可见）' }}</span>
        </li>
        <li v-for="a in detail?.attachments || []" :key="String(a.id ?? a.fileId)" class="nh-file">
          <span class="doc">{{ a.kind === 'PDF' ? 'PDF 附件' : '附件' }} {{ a.name || '（未命名）' }}</span>
          <span class="meta mono">{{ fmtBytes(a.size) }}</span>
          <a v-if="a.downloadUrl" class="dl" :href="a.downloadUrl" target="_blank" rel="noopener">下载 ↗</a>
          <span v-else class="tip">（附件下载地址由业务系统按公开性签发；外部公告附件需病毒扫描 CLEAN 后放行）</span>
        </li>
      </ul>
    </section>
  </div>
</template>

<style scoped>
.nhtml { font-size: 13px; color: #1f2937; }
.nh-head { padding-bottom: 10px; border-bottom: 2px solid #1d4ed8; margin-bottom: 14px; }
.nh-ttl { font-size: 17px; font-weight: 700; line-height: 1.5; color: #111827; }
.nh-meta { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; margin-top: 8px; color: #6b7280; font-size: 12px; }
.pill { border: 1px solid #c7d7f7; color: #1d4ed8; background: #eff4ff; border-radius: 4px; padding: 0 7px; line-height: 18px; }
.mono { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; }
.nh-sec { margin-top: 14px; }
.nh-sec-t { font-size: 13px; font-weight: 600; color: #111827; margin-bottom: 6px; border-left: 3px solid #1d4ed8; padding-left: 7px; }
.nh-tbl { width: 100%; border-collapse: collapse; }
.nh-tbl td { border: 1px solid #eef0f3; padding: 6px 9px; vertical-align: top; line-height: 1.6; word-break: break-all; }
.nh-tbl td.nh-k { width: 46%; min-width: 180px; color: #4b5563; background: #fafbfc; font-size: 12px; }
.nh-tbl td.nh-v { color: #111827; }
.nh-uk { margin-left: 6px; font-size: 10px; color: #b45309; background: #fef3c7; border-radius: 3px; padding: 0 4px; }
.nh-body { white-space: pre-wrap; line-height: 1.95; font-size: 13.5px; }
.nh-none { color: #9ca3af; font-size: 12px; padding: 6px 0; }
.nh-note { margin-top: 6px; font-size: 11px; color: #92400e; background: #fef3c7; border: 1px solid #fde68a; border-radius: 4px; padding: 5px 9px; }
.nh-files { list-style: none; margin: 0; padding: 0; }
.nh-file { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; border: 1px solid #eef0f3; border-radius: 6px; padding: 7px 10px; margin-bottom: 6px; font-size: 12px; }
.nh-file .doc { font-weight: 600; color: #1f2937; }
.nh-file .meta { color: #9ca3af; }
.nh-file .dl { color: #1d4ed8; text-decoration: none; }
.nh-file .tip { color: #b45309; font-size: 11px; }
</style>
