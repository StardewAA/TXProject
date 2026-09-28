<script setup lang="ts">
/* 文件在线预览（内嵌查看器；2026-10-01 方案 A：PDF/图片/docx/xlsx/text/csv）
 * 口径（《11-文件上传与file_id契约-2026-09-10.md》§3.5）：上传成功后响应已带 url，**直接用 url 预览**；
 *   fileId 分支仅服务历史数据（旧记录只存了 file_id）。
 * 渲染矩阵（纯前端，后端零改动，见 filePreview.ts）：
 *   pdf → iframe 原生；image → <img>；text/csv → 字节解码（UTF-8/GB18030 回退）→ <pre>；
 *   docx → mammoth HTML；xlsx/xls → SheetJS 表格（只读、行/列截断）；其余 → 新窗口/下载兜底。
 * 取 URL 的两条路径：
 *   1) 调用方给 url（上传接口 /bidding/file/upload 返回，或出参下发的 previewUrl）——首选；
 *   2) 只给 fileId（如公告版式文件 pdfFileId、生命周期佐证材料）→ 拼**业务受控出口**
 *      GET /bidding/file/download?id= 直接内联预览。
 * 2026-09-20 变更：原先 fileId 分支走 GET /infra/file/get，有两个坑（需 infra:file:query 权限、业务账号没有；
 *   且该端点 Content-Disposition: attachment，是**强制下载不是预览**）。契约要求改用业务出口：
 *   见《后端变更通知-电子证照域字典治理》§六-5、《电子证照域-权限与操作全表》§三。 */
import { computed, ref, watch } from 'vue';

import { buildBiddingFileDownloadUrl, withBiddingFileToken } from '#/api/bidding/file';
import { fetchFileBlob } from '#/utils/fetchFile';

import {
  decodeText,
  detectPreviewKind,
  docxToHtml,
  escHtml,
  type PreviewKind,
  sheetToHtmlTable,
} from './filePreview';

const props = withDefaults(
  defineProps<{
    fileId?: number | string;
    fileName?: string;
    open: boolean;
    title?: string;
    url?: string;
  }>(),
  { title: '文件预览', url: '', fileId: undefined, fileName: '' },
);
const emit = defineEmits<{ (e: 'update:open', v: boolean): void }>();

const resolved = ref('');
const loading = ref(false);
const error = ref('');

const isImage = computed(() => /\.(png|jpe?g|gif|webp|bmp|svg)$/i.test(props.fileName || resolved.value || ''));
const isPdf = computed(() => /\.pdf/i.test(props.fileName || resolved.value || ''));
/** 按文件名/URL 判定渲染分类（2026-10-01 方案 A：docx/xlsx/text/csv 走字节渲染） */
const kind = computed<PreviewKind>(() => detectPreviewKind(props.fileName || resolved.value));
/** 需要字节渲染的类型（docx/xlsx/text/csv）；空串 = 走 iframe/img/新窗口兜底 */
const byteKind = computed<'' | 'csv' | 'docx' | 'text' | 'xlsx'>(() =>
  kind.value === 'text' || kind.value === 'csv' || kind.value === 'docx' || kind.value === 'xlsx'
    ? kind.value
    : '',
);

async function resolve(): Promise<void> {
  error.value = '';
  resolved.value = props.url || '';
  if (resolved.value) return;
  if (props.fileId === undefined || props.fileId === null || props.fileId === '') {
    error.value = '缺少文件 URL 与 file_id，无法预览。';
    return;
  }
  loading.value = true;
  try {
    // 业务受控出口：内联预览，无需 infra:file:query（契约：不再依赖 infra 权限）
    // ⚠️ 必须带 token：iframe 由浏览器自己发请求、不带 Authorization 头，裸 URL 会被判 401
    //   （框架支持从查询参数取令牌，实测 ?token= 可取到字节；见 withBiddingFileToken 注释）
    resolved.value = withBiddingFileToken(buildBiddingFileDownloadUrl(props.fileId));
  } catch (e) {
    error.value = '获取文件地址失败：' + ((e as Error).message || String(e));
  } finally {
    loading.value = false;
  }
}
watch(
  () => [props.open, props.url, props.fileId],
  () => {
    if (props.open) void resolve();
  },
  { immediate: true },
);

/* ============ 字节类渲染（docx/xlsx/text/csv，2026-10-01 方案 A） ============ */
/** 字节类预览上限（docx/xlsx 全量进内存，约 2 份峰值；超出提示下载） */
const MAX_BYTE_PREVIEW = 50 * 1024 * 1024;
function formatSize(n: number): string {
  if (n < 1024) return n + ' B';
  if (n < 1024 * 1024) return (n / 1024).toFixed(1) + ' KB';
  return (n / (1024 * 1024)).toFixed(1) + ' MB';
}

const byteLoading = ref(false);
const blobError = ref('');
const blobHtml = ref('');

/** 取流（fetchFileBlob 自带鉴权头 + HTTP200 错误包体嗅探）→ 按类型分发：
 *  text/csv → GB18030 兼容解码 → <pre>；docx → mammoth HTML；xlsx/xls → SheetJS 表格 */
async function renderBytes(): Promise<void> {
  blobError.value = '';
  blobHtml.value = '';
  if (!resolved.value || !byteKind.value) return;
  byteLoading.value = true;
  try {
    const blob = await fetchFileBlob(resolved.value);
    if (blob.size > MAX_BYTE_PREVIEW) {
      blobError.value = `文件 ${formatSize(blob.size)} 超过预览上限（${formatSize(MAX_BYTE_PREVIEW)}），请下载后查看。`;
      return;
    }
    const ab = await blob.arrayBuffer();
    if (byteKind.value === 'text' || byteKind.value === 'csv') {
      blobHtml.value = escHtml(decodeText(new Uint8Array(ab)));
    } else if (byteKind.value === 'docx') {
      blobHtml.value = (await docxToHtml(ab)).html;
    } else {
      blobHtml.value = (await sheetToHtmlTable(ab)).html;
    }
  } catch (e) {
    blobError.value =
      '解析失败：' + ((e as Error).message || String(e)) + ' —— 可点下方「新窗口打开」或「下载」查看原文件。';
  } finally {
    byteLoading.value = false;
  }
}
watch([resolved, byteKind], () => {
  void renderBytes();
});

function close(): void {
  emit('update:open', false);
}
</script>

<template>
  <a-modal :open="open" :title="title || '文件预览'" :width="980" :footer="null" destroy-on-close @cancel="close">
    <div class="pv">
      <div v-if="loading" class="pv-hint">正在获取文件…</div>
      <template v-else-if="resolved">
        <img v-if="isImage" :src="resolved" class="pv-img" alt="附件预览" />
        <iframe v-else-if="isPdf" :src="resolved" class="pv-frame" title="PDF 预览"></iframe>
        <!-- 字节类渲染（docx/mammoth、xlsx/SheetJS、text&csv/GB18030 兼容解码）
             eslint-disable vue/no-v-html：输出已消毒 —— 文本类先 escHtml 转义，docx/xlsx 由
             mammoth/SheetJS 转义单元格与正文（两者均不产出 <script>），且本组件仅展示受控下载出口的字节 -->
        <template v-else-if="byteKind">
          <div v-if="byteLoading" class="pv-hint">正在解析文件…</div>
          <div v-else-if="blobError" class="pv-err">
            <p class="pv-err-t">无法内嵌解析该文件</p>
            <p class="pv-err-m">{{ blobError }}</p>
          </div>
          <pre v-else-if="byteKind === 'text' || byteKind === 'csv'" class="pv-text" v-html="blobHtml"></pre>
          <div v-else class="pv-blob" v-html="blobHtml"></div>
        </template>
        <div v-else class="pv-hint">
          该类型不支持内嵌预览，请
          <a :href="resolved" target="_blank" rel="noopener noreferrer">在新窗口打开</a>。
        </div>
        <div v-if="isPdf" class="pv-note">
          若下方预览为空白：说明该文件地址（如平台文件服务内网 host）在浏览器侧不可达，或需鉴权头（iframe 无法携带）。
          请点「新窗口打开」验证；此类情形需后端提供字节流代理端点（T 轨公告参照外部域 GET /bidding/notice-external/pdf 的实现）。
        </div>
        <div class="pv-bar">
          <span class="pv-url" :title="resolved">{{ resolved }}</span>
          <a :href="resolved" target="_blank" rel="noopener noreferrer">新窗口打开</a>
          <a :href="resolved" download>下载</a>
        </div>
      </template>
      <div v-else class="pv-err">
        <p class="pv-err-t">无法预览该文件</p>
        <p class="pv-err-m">{{ error }}</p>
        <p class="pv-err-m">附件若为上传接口返回的 URL 可直接预览；公告版式文件（pdfFileId）需后端回传 URL 或放开 infra:file:query。</p>
      </div>
    </div>
  </a-modal>
</template>

<style scoped>
.pv { min-height: 420px; }
.pv-frame { width: 100%; height: 70vh; border: 1px solid #d9dfe5; }
.pv-img { max-width: 100%; max-height: 70vh; display: block; margin: 0 auto; border: 1px solid #d9dfe5; }
/* 字节类渲染容器：文本 <pre> 与 docx/xlsx HTML 同高、可滚动 */
.pv-text {
  width: calc(100% - 14px);
  height: 70vh;
  overflow: auto;
  margin: 0;
  padding: 12px 14px;
  font: 12.5px/1.7 Consolas, 'Courier New', monospace;
  white-space: pre-wrap;
  word-break: break-all;
  background: hsl(var(--muted) / 30%);
  border: 1px solid #d9dfe5;
}
.pv-blob {
  width: calc(100% - 14px);
  height: 70vh;
  overflow: auto;
  border: 1px solid #d9dfe5;
}
.pv-blob :deep(table) {
  border-collapse: collapse;
  width: 100%;
  font-size: 12.5px;
}
.pv-blob :deep(th),
.pv-blob :deep(td) {
  border: 1px solid #e2e6ea;
  padding: 4px 8px;
  text-align: left;
  white-space: nowrap;
}
.pv-blob :deep(th) {
  background: hsl(var(--muted) / 50%);
}
.pv-bar { margin-top: 8px; display: flex; align-items: center; gap: 12px; font-size: 12.5px; }
.pv-url { color: #8a949e; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex: 1; min-width: 0; }
.pv-hint { padding: 24px 0; text-align: center; color: #6b7681; font-size: 13px; }
.pv-note { margin-top: 6px; font-size: 11.5px; line-height: 1.65; color: #92400e; background: #fef3c7; border: 1px solid #fde68a; border-radius: 3px; padding: 5px 9px; }
.pv-err { padding: 20px 16px; background: #fdf3f2; border: 1px solid #f0c9c4; }
.pv-err-t { font-size: 14px; font-weight: 700; color: #a12a20; margin: 0 0 6px; }
.pv-err-m { font-size: 12.5px; line-height: 1.7; color: #7a3b33; margin: 0 0 4px; }
</style>
