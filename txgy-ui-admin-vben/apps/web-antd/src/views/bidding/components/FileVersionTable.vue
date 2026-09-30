<script setup lang="ts">
/* 文件版本列表（各详情页「文件版本」分区共用）
 * 口径（《11-文件上传与file_id契约-2026-09-10.md》§3.5）：
 *   · 预览一律 fileId → PdfPreview（业务受控**内联**出口 /bidding/file/download），
 *     绝不把签名下载地址塞进 iframe —— 那是"点预览变下载"的根因；
 *   · 下载走同一受控出口 + <a download>，不新开需要登录态的地址页；
 *   · 上传回执缓存（fileRefCache）命中时用其 url 直读，未命中由 PdfPreview 按 fileId 兜底。 */
import { buildBiddingFileDownloadUrl, withBiddingFileToken } from '#/api/bidding/file';

import { fmtDateTime } from '../certificate/format';
import { type FileVersionRow } from './fileVersion';

const props = withDefaults(
  defineProps<{
    emptyText?: string;
    loading?: boolean;
    rows?: FileVersionRow[];
  }>(),
  { emptyText: '暂无文件', loading: false, rows: () => [] },
);
const emit = defineEmits<{ (e: 'preview', row: FileVersionRow): void }>();

const columns = [
  { title: '文件名', key: 'name', width: 300 },
  { title: '类别', key: 'category', width: 130 },
  { title: '上传时间', key: 'uploadTime', width: 170 },
  { title: '操作', key: 'action', width: 150 },
];

function readable(row: FileVersionRow): boolean {
  return row.fileId !== undefined && row.fileId !== null && row.fileId !== '';
}
/** 下载地址：优先上传回执里的可访问 URL，否则由 fileId 拼受控出口（补 token，浏览器自发出请求需要） */
function downloadHref(row: FileVersionRow): string {
  if (row.url) return row.url;
  return readable(row)
    ? withBiddingFileToken(buildBiddingFileDownloadUrl(row.fileId as number | string))
    : '';
}
function rowKey(row: FileVersionRow): string {
  return String(row.fileId ?? row.name);
}
</script>

<template>
  <a-table
    class="ct-table"
    :columns="columns"
    :data-source="props.rows"
    :loading="props.loading"
    :pagination="false"
    :row-key="rowKey"
    size="small"
  >
    <template #bodyCell="{ column, record }">
      <template v-if="column.key === 'name'">
        <div style="font-weight: 600">{{ record.name }}</div>
        <div v-if="record.note" class="ct-panel-meta">{{ record.note }}</div>
      </template>
      <template v-else-if="column.key === 'category'">
        <a-tag v-if="record.categoryLabel">{{ record.categoryLabel }}</a-tag>
        <span v-else class="ct-panel-meta">—</span>
      </template>
      <template v-else-if="column.key === 'uploadTime'">
        {{ record.uploadTime ? fmtDateTime(record.uploadTime) : '—' }}
      </template>
      <template v-else-if="column.key === 'action'">
        <a-space size="small">
          <a v-if="readable(record)" @click="emit('preview', record)">预览</a>
          <a v-if="downloadHref(record)" :href="downloadHref(record)" download>下载</a>
          <span v-if="!readable(record) && !downloadHref(record)" class="ct-panel-meta">不可用</span>
        </a-space>
      </template>
    </template>
    <template #emptyText>
      <div class="ct-empty">{{ props.emptyText }}</div>
    </template>
  </a-table>
</template>
