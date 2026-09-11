<script setup lang="ts">
import { ref } from 'vue';

import { Button, message } from 'ant-design-vue';

const props = defineProps<{ modelValue?: File }>();
const emit = defineEmits<{ 'update:modelValue': [file: File | undefined] }>();

const inputElement = ref<HTMLInputElement>();
const dragging = ref(false);

function openPicker() {
  inputElement.value?.click();
}

function acceptFile(file?: File) {
  if (!file) return;
  if (!/\.(txt|docx|pdf)$/i.test(file.name)) {
    message.warning('仅支持 TXT、DOCX 或 PDF 文件。');
    return;
  }
  if (file.size > 25 * 1024 * 1024) {
    message.warning('文件不能超过 25MB。');
    return;
  }
  emit('update:modelValue', file);
}

function handleChange(event: Event) {
  acceptFile((event.target as HTMLInputElement).files?.[0]);
}

function handleDrop(event: DragEvent) {
  dragging.value = false;
  acceptFile(event.dataTransfer?.files?.[0]);
}

function clearFile() {
  emit('update:modelValue', undefined);
  if (inputElement.value) inputElement.value.value = '';
}
</script>

<template>
  <div
    :class="['cms-file-picker', { dragging }]"
    role="button"
    tabindex="0"
    @click="openPicker"
    @dragenter.prevent="dragging = true"
    @dragleave.prevent="dragging = false"
    @dragover.prevent
    @drop.prevent="handleDrop"
    @keydown.enter.prevent="openPicker"
    @keydown.space.prevent="openPicker"
  >
    <input
      ref="inputElement"
      accept=".txt,.docx,.pdf"
      aria-label="选择待审文档"
      class="native-input"
      type="file"
      @change="handleChange"
    />
    <div class="upload-mark">↑</div>
    <div class="upload-copy">
      <strong>{{ props.modelValue ? props.modelValue.name : '点击选择文件，或拖拽到这里' }}</strong>
      <span v-if="props.modelValue">
        已选择 · {{ (props.modelValue.size / 1024 / 1024).toFixed(2) }} MB
      </span>
      <span v-else>支持 UTF-8 TXT、DOCX、PDF，单份最大 25MB</span>
      <small>扫描版 PDF 请先完成 OCR 并校对正文。</small>
    </div>
    <div class="upload-actions">
      <Button size="large" type="primary" @click.stop="openPicker">
        {{ props.modelValue ? '重新选择' : '选择文件' }}
      </Button>
      <Button v-if="props.modelValue" danger @click.stop="clearFile">移除</Button>
    </div>
  </div>
</template>

<style scoped>
.cms-file-picker {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  min-height: 132px;
  padding: 22px 24px;
  border: 2px dashed #3b82f6;
  border-radius: 12px;
  background: rgb(37 99 235 / 8%);
  cursor: pointer;
  gap: 18px;
  transition: border-color 0.2s, background 0.2s, transform 0.2s;
}
.cms-file-picker:hover,
.cms-file-picker.dragging {
  border-color: #60a5fa;
  background: rgb(37 99 235 / 15%);
  transform: translateY(-1px);
}
.native-input { display: none; }
.upload-mark {
  display: grid;
  width: 48px;
  height: 48px;
  place-items: center;
  border-radius: 50%;
  background: #1677ff;
  color: #fff;
  font-size: 30px;
  font-weight: 700;
}
.upload-copy { display: grid; min-width: 0; gap: 5px; }
.upload-copy strong { overflow: hidden; color: inherit; font-size: 16px; text-overflow: ellipsis; white-space: nowrap; }
.upload-copy span { color: #60a5fa; font-size: 13px; }
.upload-copy small { color: #64748b; font-size: 12px; }
.upload-actions { display: flex; align-items: center; gap: 8px; }
@media (max-width: 720px) {
  .cms-file-picker { grid-template-columns: auto 1fr; padding: 18px; }
  .upload-actions { grid-column: 1 / -1; }
  .upload-actions :deep(.ant-btn-primary) { flex: 1; }
}
</style>
