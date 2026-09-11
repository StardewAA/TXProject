<script setup lang="ts">
import type { VoField } from './voSpec.gen';

/* 附件面板（任何环节可用）：严格按 VO 的附件模型组织
 *   AttachmentItemVO 附件采集项（35 类）= 分组维度（含 5 类陕西必传）
 *   AttachmentSetVO 附件集结构 = 每个附件的字段（GUID / 关联数据集标识 / 名称 / 类型 / 文件名 / URL）
 *   20号令 A.1.2/A.1.4：数据集以 ATTACHMENT_CODE 关联附件集，多个标识号半角分号隔开 → 每环节可加多个附件
 * 上传：POST /bidding/file/upload（响应必带 file_id + url）；URL 与 file_id 均由上传结果自动写入，
 *   URL 只读展示不提供手填（规范《11-文件上传与file_id契约》§3.4：移除手填 URL 与"仅 URL"兜底提示）。
 * 说明：项目/招标项目/标段的附件明细持久化接口后端尚未提供，本面板产出符合 VO 的附件数据（本地暂存）。 */
import { computed, ref, watch } from 'vue';

import { Button, Input, message, Tag, Upload } from 'ant-design-vue';

import {
  attachmentCodeOf,
  type AttachmentRecord,
  makeAttachment,
  missingRequiredItems,
  stageItems,
} from './attachmentStages';
import { uploadAttachment } from './attachmentUpload';

const props = withDefaults(
  defineProps<{
    directory?: string;
    disabled?: boolean;
    modelValue: AttachmentRecord[];
    stage: string;
    title?: string;
  }>(),
  { title: '附件', disabled: false, directory: 'bidding/attachment' },
);
const emit = defineEmits<{ (e: 'update:modelValue', v: AttachmentRecord[]): void }>();

const list = ref<AttachmentRecord[]>([...props.modelValue]);
watch(
  () => props.modelValue,
  (v) => {
    if (v !== list.value) list.value = [...v];
  },
);
function push(next: AttachmentRecord[]): void {
  list.value = next;
  emit('update:modelValue', next);
}

const items = computed<VoField[]>(() => stageItems(props.stage));
const uploadingKey = ref('');
/** 附件关联标识号（ATTACHMENT_CODE）：多个 GUID 半角分号隔开 —— 交给所在数据集的该字段 */
const attachmentCode = computed(() => attachmentCodeOf(list.value));
const missing = computed(() => missingRequiredItems(props.stage, list.value));

function ofItem(code: string): AttachmentRecord[] {
  return list.value.filter((a) => a.setCode === code);
}
async function onPick(item: VoField, file: File): Promise<boolean> {
  uploadingKey.value = item.code;
  try {
    const up = await uploadAttachment(file, props.directory);
    push([...list.value, makeAttachment(item.code, up)]);
    message.success('已添加附件：' + file.name + '（file_id ' + up.fileId + '）');
  } catch (e) {
    message.error('上传失败：' + ((e as Error).message || String(e)));
  } finally {
    uploadingKey.value = '';
  }
  return false;
}
/* 规范 §3.4：删除"登记 URL / 手填 URL"入口——URL 由上传结果自动写入，用户不接触地址与 ID。
 * 仅保留附件名称可改（ATTACHMENT_NAME 属业务名称，非系统生成）。 */
function patch(a: AttachmentRecord, key: 'name', v: string): void {
  push(list.value.map((x) => (x.guid === a.guid ? { ...x, [key]: v } : x)));
}
function remove(a: AttachmentRecord): void {
  push(list.value.filter((x) => x.guid !== a.guid));
}
function copyCode(): void {
  if (!attachmentCode.value) return;
  void navigator.clipboard?.writeText(attachmentCode.value);
  message.success('已复制附件关联标识号（ATTACHMENT_CODE）');
}
</script>

<template>
  <section class="ap">
    <header class="ap-h">
      <div class="ap-h-l">
        <span class="ap-t">{{ title }}</span>
        <span class="ap-meta">附件集结构 AttachmentSetVO · 采集项 AttachmentItemVO · 共 {{ list.length }} 个附件</span>
      </div>
      <div class="ap-h-r">
        <Tag v-if="missing.length" color="red">缺 {{ missing.length }} 类必传</Tag>
        <Tag v-else-if="list.length" color="green">必传齐备</Tag>
        <Button v-if="attachmentCode" size="small" @click="copyCode">复制 ATTACHMENT_CODE</Button>
      </div>
    </header>

    <div v-if="missing.length" class="ap-warn">
      陕西必传口径缺附件：{{ missing.join('、') }}（规范 AttachmentItemVO 必填=是）
    </div>

    <div v-for="it in items" :key="it.code" class="ap-grp">
      <div class="ap-grp-h">
        <span class="ap-grp-t">
          <span v-if="it.required" class="ap-ast" title="规范必填=是（陕西必传口径）">*</span>{{ it.label }}
          <code class="ap-code">{{ it.code }}</code>
        </span>
        <span class="ap-grp-note">{{ it.note }}</span>
        <span class="ap-grp-cnt">{{ ofItem(it.code).length }} 个</span>
        <div class="ap-grp-act">
          <Upload :show-upload-list="false" multiple :disabled="disabled" :before-upload="(f: File) => onPick(it, f)">
            <Button size="small" :loading="uploadingKey === it.code" :disabled="disabled">选择文件上传</Button>
          </Upload>
        </div>
      </div>

      <div v-if="ofItem(it.code).length" class="ap-list">
        <div v-for="a in ofItem(it.code)" :key="a.guid" class="ap-row">
          <Input :value="a.name" class="ap-name" placeholder="附件名称（ATTACHMENT_NAME）" :disabled="disabled" @change="(e) => patch(a, 'name', String((e.target as HTMLInputElement).value ?? ''))" />
          <div class="ap-file">
            <span class="ap-file-n">{{ a.fileName }}</span>
            <span class="ap-file-t">{{ a.type || '—' }}<template v-if="a.size"> · {{ (a.size / 1024).toFixed(0) }} KB</template><template v-if="a.fileId !== undefined"> · file_id {{ a.fileId }}</template></span>
          </div>
          <div class="ap-url" :title="a.url">
            <span class="ap-url-t">URL 由上传自动写入</span>
            <span class="ap-url-v">{{ a.url }}</span>
          </div>
          <div class="ap-row-act">
            <a v-if="a.url" :href="a.url" target="_blank" rel="noopener noreferrer" class="ap-open">打开</a>
            <Button size="small" type="link" danger :disabled="disabled" @click="remove(a)">移除</Button>
          </div>
          <div class="ap-guid" :title="`ATTACHMENT_GUID：${ a.guid}`">{{ a.guid }}</div>
        </div>
      </div>
      <div v-else class="ap-empty">暂无附件（可上传多个；同一采集项可加多个附件）</div>
    </div>

    <footer class="ap-f">
      <span class="ap-f-l">ATTACHMENT_CODE（半角分号分隔）：</span>
      <code class="ap-f-code">{{ attachmentCode || '（无附件时不写入该字段）' }}</code>
      <span v-if="list.length" class="ap-f-note">附件三件套（file_id / sha256 / size）由上传接口返回并落库；URL 仅供预览</span>
    </footer>
  </section>
</template>

<style scoped>
.ap {
  border: 1px solid #d9dfe5;
  background: #fff;
}
.ap-h {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
  padding: 9px 12px;
  background: #f7f9fb;
  border-bottom: 1px solid #d9dfe5;
}
.ap-h-l { display: flex; align-items: baseline; gap: 10px; flex-wrap: wrap; }
.ap-t { font-size: 14px; font-weight: 700; color: #2b3440; }
.ap-meta { font-size: 12px; color: #78838f; }
.ap-h-r { display: flex; align-items: center; gap: 8px; }
.ap-warn {
  margin: 10px 12px 0;
  padding: 7px 10px;
  font-size: 12.5px;
  line-height: 1.6;
  color: #a12a20;
  background: #fdf3f2;
  border: 1px solid #f0c9c4;
}
.ap-grp { border-top: 1px solid #edf0f3; padding: 8px 12px 10px; }
.ap-grp:first-of-type { border-top: none; }
.ap-grp-h { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.ap-grp-t { font-size: 13.5px; font-weight: 600; color: #2e3947; display: flex; align-items: center; gap: 5px; }
.ap-ast { color: #d93025; font-weight: 700; }
.ap-code {
  font-size: 10.5px;
  font-weight: 400;
  color: #8a949e;
  background: #f6f8fa;
  border: 1px solid #e3e8ed;
  border-radius: 2px;
  padding: 0 4px;
}
.ap-grp-note { font-size: 11.5px; color: #8a949e; }
.ap-grp-cnt { font-size: 11.5px; color: #6b7681; margin-left: auto; }
.ap-grp-act { display: flex; align-items: center; gap: 4px; }
.ap-list { margin-top: 7px; display: flex; flex-direction: column; gap: 6px; }
.ap-row {
  display: grid;
  grid-template-columns: minmax(140px, 1.1fr) minmax(140px, 1fr) minmax(200px, 1.6fr) auto;
  gap: 8px;
  align-items: center;
  padding: 6px 8px;
  background: #fbfcfd;
  border: 1px solid #e6eaee;
}
.ap-file { min-width: 0; display: flex; flex-direction: column; }
.ap-file-n { font-size: 12.5px; color: #2e3947; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ap-file-t { font-size: 11px; color: #8a949e; }
.ap-url { min-width: 0; display: flex; flex-direction: column; }
.ap-url-t { font-size: 10.5px; color: #9aa4af; }
.ap-url-v { font-size: 11.5px; color: #6b7681; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ap-row-act { display: flex; align-items: center; gap: 6px; white-space: nowrap; }
.ap-open { font-size: 12.5px; color: #2b6cb0; }
.ap-guid {
  grid-column: 1 / -1;
  font-size: 10.5px;
  color: #9aa4af;
  letter-spacing: 0.02em;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ap-empty { margin-top: 6px; font-size: 12px; color: #9aa4af; }
.ap-f {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding: 8px 12px;
  background: #f7f9fb;
  border-top: 1px solid #d9dfe5;
}
.ap-f-l { font-size: 12px; color: #6b7681; }
.ap-f-code { font-size: 11.5px; color: #2b3440; word-break: break-all; }
.ap-f-note { font-size: 11px; color: #a05a00; }
:deep(.ant-input) { border: 1px solid #c9cfd6; border-radius: 3px; font-size: 12.5px; }
:deep(.ant-input:focus) { border-color: #2b6cb0; box-shadow: none; }
</style>
