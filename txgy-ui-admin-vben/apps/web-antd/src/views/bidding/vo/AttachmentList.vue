<script setup lang="ts">
/* 附件只读列表（按 AttachmentItemVO 采集项分组）：详情/列表页展示已挂附件，支持 PDF/图片内嵌预览。
 * 说明：项目/招标项目/标段后端的附件持久化接口尚未提供（当前仅能落 ATTACHMENT_CODE），
 *  因此这里展示的是本页可获得的附件集（上传后本地留存 + 规范标识号），后端接口就绪后改为按实体读取。 */
import { computed, ref } from 'vue';

import { Button, Empty } from 'ant-design-vue';

import { attachmentItemLabel, attachmentItemRequired, type AttachmentRecord } from './attachmentStages';
import PdfPreview from './PdfPreview.vue';

const props = withDefaults(
  defineProps<{ compact?: boolean; list: AttachmentRecord[]; title?: string; }>(),
  { title: '附件', compact: false },
);

interface Group {
  code: string;
  label: string;
  required: boolean;
  items: AttachmentRecord[];
}
const groups = computed<Group[]>(() => {
  const map = new Map<string, AttachmentRecord[]>();
  for (const a of props.list) {
    const arr = map.get(a.setCode) ?? [];
    arr.push(a);
    map.set(a.setCode, arr);
  }
  return [...map.entries()].map(([code, items]) => ({
    code,
    label: attachmentItemLabel(code),
    required: attachmentItemRequired(code),
    items,
  }));
});

const previewOpen = ref(false);
const previewUrl = ref('');
const previewName = ref('');
function preview(a: AttachmentRecord): void {
  previewUrl.value = a.url;
  previewName.value = a.fileName || a.name;
  previewOpen.value = true;
}
function isPreviewable(a: AttachmentRecord): boolean {
  return /\.(pdf|png|jpe?g|gif|webp|bmp|svg)$/i.test(a.fileName || a.url || '');
}
</script>

<template>
  <div class="al" :class="{ 'al-compact': compact }">
    <div class="al-h">
      <span class="al-t">{{ title }}</span>
      <span class="al-c">共 {{ list.length }} 个</span>
    </div>
    <Empty v-if="!list.length" :image="Empty.PRESENTED_IMAGE_SIMPLE" description="暂无附件" />
    <div v-for="g in groups" :key="g.code" class="al-grp">
      <div class="al-grp-h">
        <span class="al-grp-t">
          <span v-if="g.required" class="al-ast" title="规范必填（陕西必传口径）">*</span>{{ g.label }}
        </span>
        <code class="al-code">{{ g.code }}</code>
        <span class="al-grp-c">{{ g.items.length }} 个</span>
      </div>
      <div v-for="a in g.items" :key="a.guid" class="al-row">
        <span class="al-name" :title="a.name">{{ a.name }}</span>
        <span class="al-file" :title="a.fileName">{{ a.fileName }}</span>
        <span class="al-meta">
          {{ a.type || '—' }}<template v-if="a.size"> · {{ (a.size / 1024).toFixed(0) }} KB</template>
        </span>
        <span class="al-act">
          <Button v-if="isPreviewable(a)" size="small" type="link" @click="preview(a)">预览</Button>
          <a v-if="a.url" :href="a.url" target="_blank" rel="noopener noreferrer">打开</a>
          <a v-if="a.url" :href="a.url" download>下载</a>
        </span>
        <span class="al-guid" :title="`ATTACHMENT_GUID：${ a.guid}`">{{ a.guid }}</span>
      </div>
    </div>
    <PdfPreview v-model:open="previewOpen" :url="previewUrl" :file-name="previewName" title="附件预览" />
  </div>
</template>

<style scoped>
.al { border: 1px solid #e4e8ec; background: #fff; }
.al-h { display: flex; align-items: baseline; gap: 8px; padding: 8px 11px; background: #f7f9fb; border-bottom: 1px solid #e4e8ec; }
.al-t { font-size: 13.5px; font-weight: 700; color: #2e3947; }
.al-c { font-size: 12px; color: #8a949e; }
.al-grp { padding: 7px 11px; border-top: 1px solid #eef1f4; }
.al-grp:first-of-type { border-top: none; }
.al-grp-h { display: flex; align-items: center; gap: 7px; }
.al-grp-t { font-size: 13px; font-weight: 600; color: #2e3947; }
.al-ast { color: #d93025; font-weight: 700; }
.al-code { font-size: 10.5px; color: #8a949e; background: #f6f8fa; border: 1px solid #e3e8ed; border-radius: 2px; padding: 0 4px; }
.al-grp-c { margin-left: auto; font-size: 11.5px; color: #8a949e; }
.al-row {
  display: grid;
  grid-template-columns: minmax(120px, 1.2fr) minmax(120px, 1.2fr) auto auto;
  gap: 8px;
  align-items: center;
  padding: 5px 0;
  border-top: 1px dashed #eef1f4;
  font-size: 12.5px;
}
.al-name, .al-file { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: #2e3947; }
.al-file { color: #6b7681; }
.al-meta { font-size: 11.5px; color: #8a949e; white-space: nowrap; }
.al-act { display: flex; align-items: center; gap: 8px; white-space: nowrap; }
.al-guid { grid-column: 1 / -1; font-size: 10.5px; color: #9aa4af; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.al-compact .al-guid { display: none; }
</style>
