<script setup lang="ts">
/* PDF / 图片在线预览（内嵌查看器）
 * 口径（《11-文件上传与file_id契约-2026-09-10.md》§3.5）：上传成功后响应已带 url，**直接用 url 预览**；
 *   fileId 分支仅服务历史数据（旧记录只存了 file_id）。
 * 取 URL 的两条路径：
 *   1) 调用方给 url（上传接口 /bidding/file/upload 返回，立即可用）——首选；
 *   2) 只给 fileId（如公告版式文件 pdfFileId）：GET /infra/file/get?id= 取 url；
 *      该接口需权限 infra:file:query，业务账号通常没有 ⇒ 历史数据可先用 registerBiddingFileByUrl 补登记，
 *      或由后端在出参直接回传可访问 URL（站内公告 PDF 字节流代理，规范 §3.5 备注）。
 * 渲染：浏览器原生 PDF 查看器（iframe）；图片类型用 <img>。 */
import { computed, ref, watch } from 'vue';

import { getFile } from '#/api/infra/file';

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
const isPdf = computed(() => /\.pdf/i.test(props.fileName || resolved.value || '') || (!isImage.value && !!resolved.value));

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
    const f = await getFile(props.fileId);
    if (f?.url) resolved.value = f.url;
    else error.value = '后端 /infra/file/get 未返回文件 URL，无法预览。';
  } catch (e) {
    error.value =
      '获取文件 URL 失败：' + ((e as Error).message || String(e)) +
      '（该接口需 infra:file:query 权限；建议后端在公告/项目出参中直接回传可访问 URL）';
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
/* 右侧留白：PDF 查看器自带纵向滚动条，贴住外层滚动条会出现两条并排、拖不动的滚动条 */
.pv-frame { width: calc(100% - 14px); height: 70vh; border: 1px solid #d9dfe5; }
.pv-img { max-width: 100%; max-height: 70vh; display: block; margin: 0 auto; border: 1px solid #d9dfe5; }
.pv-bar { margin-top: 8px; display: flex; align-items: center; gap: 12px; font-size: 12.5px; }
.pv-url { color: #8a949e; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex: 1; min-width: 0; }
.pv-hint { padding: 24px 0; text-align: center; color: #6b7681; font-size: 13px; }
.pv-note { margin-top: 6px; font-size: 11.5px; line-height: 1.65; color: #92400e; background: #fef3c7; border: 1px solid #fde68a; border-radius: 3px; padding: 5px 9px; }
.pv-err { padding: 20px 16px; background: #fdf3f2; border: 1px solid #f0c9c4; }
.pv-err-t { font-size: 14px; font-weight: 700; color: #a12a20; margin: 0 0 6px; }
.pv-err-m { font-size: 12.5px; line-height: 1.7; color: #7a3b33; margin: 0 0 4px; }
</style>
