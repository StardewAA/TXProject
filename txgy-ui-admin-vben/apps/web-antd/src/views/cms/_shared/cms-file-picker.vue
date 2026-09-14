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
      <strong>{{
        props.modelValue ? props.modelValue.name : '点击选择文件，或拖拽到这里'
      }}</strong>
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
      <Button v-if="props.modelValue" danger @click.stop="clearFile"
        >移除</Button
      >
    </div>
  </div>
</template>

<style scoped>
.cms-file-picker {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 18px;
  align-items: center;
  min-height: 132px;
  padding: 22px 24px;
  cursor: pointer;
  background:
    radial-gradient(circle at 5% 50%, rgb(79 158 240 / 12%), transparent 28%),
    rgb(241 247 254 / 58%);
  border: 1px dashed rgb(49 116 211 / 48%);
  border-radius: 16px;
  box-shadow: inset 0 1px 0 rgb(255 255 255 / 78%);
  backdrop-filter: blur(14px);
  transition:
    border-color 0.2s,
    background 0.2s,
    box-shadow 0.2s,
    transform 0.2s;
}

.cms-file-picker:hover,
.cms-file-picker.dragging {
  background: rgb(235 245 255 / 78%);
  border-color: #4a91e5;
  box-shadow: 0 12px 28px rgb(45 94 153 / 9%);
  transform: translateY(-2px);
}

.native-input {
  display: none;
}

.upload-mark {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  font-size: 27px;
  font-weight: 500;
  color: #fff;
  background: linear-gradient(145deg, #347fe4, #1460c6);
  border: 1px solid rgb(255 255 255 / 56%);
  border-radius: 14px;
  box-shadow: 0 9px 20px rgb(31 104 202 / 22%);
}

.upload-copy {
  display: grid;
  gap: 5px;
  min-width: 0;
}

.upload-copy strong {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 14px;
  font-weight: 620;
  color: #293c58;
  white-space: nowrap;
}

.upload-copy span {
  font-size: 12px;
  color: #4d82c4;
}

.upload-copy small {
  font-size: 11px;
  color: #8996a8;
}

.upload-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

@media (max-width: 720px) {
  .cms-file-picker {
    grid-template-columns: auto 1fr;
    padding: 18px;
  }

  .upload-actions {
    grid-column: 1 / -1;
  }

  .upload-actions :deep(.ant-btn-primary) {
    flex: 1;
  }
}
</style>
