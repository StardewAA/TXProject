<script lang="ts" setup>
import { computed, ref, watch } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { formatDateTime } from './format';

/**
 * 「请选择需要诊断的简历」对话框。
 *
 * 数值取自参考站：条目 padding 9px 16px / radius 8，选中态 #F2F5EE 底 + #CBD7BD 边，
 * 悬停 #DAE6CF 描边；上传区 #FAFAF9 底 + 1px dashed rgba(10,27,51,.12)。
 *
 * 用自绘遮罩而不是 antd Modal：参考站的标题是「主标题 + 一行灰色说明」的两段式，
 * antd 的 title 塞不进第二段，硬凑要打一堆 :deep 补丁，还不如自己写一层。
 */

interface PickableResume {
  createTime?: string;
  fileName?: string;
  id: number;
  remark?: string;
  resumeType: number;
  title: string;
}

const props = withDefaults(
  defineProps<{
    /** 本次要对简历做的动作：诊断 / 优化。标题与说明跟着它变 */
    action?: string;
    /** 确定按钮的忙碌态 */
    confirming?: boolean;
    loading?: boolean;
    open: boolean;
    resumes: PickableResume[];
  }>(),
  { action: '诊断', confirming: false, loading: false },
);

const emit = defineEmits<{
  /** 关掉对话框（取消、点遮罩、点叉都走这里） */
  cancel: [];
  confirm: [id: number];
  /** 换一版附件：该简历重新上传 */
  replace: [id: number];
  upload: [file: File];
}>();

const selectedId = ref<number>();

const fileInput = ref<HTMLInputElement>();
const dragActive = ref(false);

/*
 * 每次打开都重选第一份。
 * 不这么做的话，上一次选的简历会留在 state 里，用户下次进来看到的是
 * 「已经选好了」的样子，但他并没有选。
 */
watch(
  () => props.open,
  (open) => {
    if (open) selectedId.value = props.resumes[0]?.id;
  },
);

const selectedResume = computed(() =>
  props.resumes.find((item) => item.id === selectedId.value),
);

function pick(id: number) {
  selectedId.value = id;
}

function displayName(resume: PickableResume) {
  return resume.fileName?.trim() || resume.title;
}

function displayTime(resume: PickableResume) {
  return formatDateTime(resume.createTime);
}

function confirm() {
  if (!selectedId.value) return;
  emit('confirm', selectedId.value);
}

function chooseFile() {
  fileInput.value?.click();
}

function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = '';
  if (file) emit('upload', file);
}

/** 拖进上传区。drop 要 preventDefault，否则浏览器会直接打开这个文件 */
function onDrop(event: DragEvent) {
  dragActive.value = false;
  const file = event.dataTransfer?.files?.[0];
  if (file) emit('upload', file);
}
</script>

<template>
  <div v-if="open" class="picker-mask" @click.self="emit('cancel')">
    <div class="picker" role="dialog" aria-modal="true" aria-labelledby="picker-title">
      <header class="picker-head">
        <h2 id="picker-title">请选择需要{{ action }}的简历</h2>
        <p class="picker-intro">选择后将用于简历编辑</p>
      </header>

      <div class="picker-body">
        <div v-if="loading" class="picker-state">
          <IconifyIcon icon="lucide:loader-circle" class="spin" />
          <span>正在读取你的简历</span>
        </div>

        <template v-else>
          <div class="picker-list">
            <button
              v-for="resume in resumes"
              :key="resume.id"
              type="button"
              class="resume-item"
              :class="{ 'is-selected': resume.id === selectedId }"
              @click="pick(resume.id)"
            >
              <span class="radio" :class="{ 'is-on': resume.id === selectedId }"></span>
              <span class="doc-icon">
                <IconifyIcon icon="lucide:file-text" />
              </span>
              <span class="resume-info">
                <span class="resume-title">
                  <span class="resume-name">{{ displayName(resume) }}</span>
                </span>
                <span class="resume-meta">
                  <span class="resume-time">更新于{{ displayTime(resume) }}</span>
                  <span v-if="resume.remark && resume.remark !== '-'">{{ resume.remark }}</span>
                </span>
              </span>
              <span class="resume-actions">
                <span
                  class="action-btn"
                  role="button"
                  tabindex="0"
                  @click.stop="emit('replace', resume.id)"
                >
                  替换简历
                </span>
              </span>
            </button>

            <p v-if="resumes.length" class="list-end">已加载全部</p>
            <p v-else class="list-end">还没有简历，先上传一份</p>
          </div>

          <button
            type="button"
            class="upload-area"
            :class="{ 'is-drag': dragActive }"
            @click="chooseFile"
            @dragover.prevent="dragActive = true"
            @dragleave="dragActive = false"
            @drop.prevent="onDrop"
          >
            <IconifyIcon class="upload-icon" icon="lucide:arrow-up-from-line" />
            <span class="upload-text">点击或拖拽文件到此处上传</span>
            <span class="upload-hint">仅支持 PDF，大小不超过 10MB；</span>
          </button>
          <input
            ref="fileInput"
            type="file"
            accept=".pdf"
            style="display: none"
            @change="onFileChange"
          />
        </template>
      </div>

      <footer class="picker-foot">
        <button type="button" class="btn-ghost" @click="emit('cancel')">取消</button>
        <button
          type="button"
          class="btn-solid"
          :disabled="!selectedResume || confirming"
          @click="confirm"
        >
          {{ confirming ? '处理中…' : '确定' }}
        </button>
      </footer>
    </div>
  </div>
</template>

<style scoped>
.picker-mask {
  position: fixed;
  z-index: 1200;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgb(0 0 0 / 45%);
}

.picker {
  display: flex;
  flex-direction: column;
  width: min(640px, 100%);
  max-height: min(760px, calc(100vh - 48px));
  overflow: hidden;
  border-radius: 12px;
  background: #fff;
}

.picker-head {
  flex-shrink: 0;
  padding: 24px 24px 0;
}

.picker-head h2 {
  margin: 0;
  color: #292929;
  font-size: 18px;
  font-weight: 500;
  line-height: 26px;
}

.picker-intro {
  margin: 2px 0 0;
  color: #858585;
  font-size: 14px;
  font-weight: 400;
  line-height: 24px;
}

.picker-body {
  flex: 1;
  padding: 8px 24px 0;
  overflow-y: auto;
}

.picker-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  min-height: 200px;
  padding: 60px 0;
  color: #999;
  font-size: 14px;
}

.picker-list {
  display: flex;
  flex-direction: column;
  gap: 11px;
  margin-top: 8px;
}

.resume-item {
  display: flex;
  align-items: center;
  width: 100%;
  padding: 9px 16px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: #fff;
  text-align: left;
  cursor: pointer;
  transition: 0.2s ease-in-out;
}

.resume-item:hover {
  border-color: #dae6cf;
  box-shadow: 0 0 1px 1px #dae6cf;
}

.resume-item.is-selected {
  border-color: #cbd7bd;
  background: #f2f5ee;
}

.radio {
  flex-shrink: 0;
  width: 16px;
  height: 16px;
  border: 1px solid #d0d0d0;
  border-radius: 50%;
  transition: 0.2s ease-in-out;
}

.radio.is-on {
  border-color: #59822f;
  border-width: 5px;
}

.doc-icon {
  flex-shrink: 0;
  display: flex;
  margin-left: 12px;
  color: #e05252;
  font-size: 26px;
}

.resume-info {
  flex: 1;
  min-width: 0;
  margin-left: 12px;
}

.resume-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 2px;
}

.resume-name {
  overflow: hidden;
  color: #292929;
  font-size: 16px;
  line-height: 24px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.resume-meta {
  display: block;
  margin-top: 4px;
  color: #b8b8b8;
  font-size: 14px;
  line-height: 22px;
}

.resume-time {
  margin-right: 12px;
}

.resume-actions {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 16px;
}

.action-btn {
  color: #6a785e;
  font-size: 14px;
  line-height: 22px;
  cursor: pointer;
  transition: color 0.2s;
}

.action-btn:hover {
  color: #526045;
}

.list-end {
  margin: 4px 0 0;
  color: #b8b8b8;
  font-size: 13px;
  text-align: center;
}

.upload-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  margin: 16px 0 0;
  padding: 23px;
  border: 1px dashed rgb(10 27 51 / 12%);
  border-radius: 8px;
  background: #fafaf9;
  cursor: pointer;
  transition: border-color 0.2s;
}

.upload-area:hover,
.upload-area.is-drag {
  border-color: #999;
}

.upload-icon {
  color: #999;
  font-size: 20px;
}

.upload-text {
  color: #5e5e5e;
  font-size: 14px;
  line-height: 22px;
}

.upload-hint {
  color: #b8b8b8;
  font-size: 12px;
  line-height: 20px;
}

.picker-foot {
  flex-shrink: 0;
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 24px;
}

.btn-ghost,
.btn-solid {
  min-width: 88px;
  height: 40px;
  padding: 0 20px;
  border-radius: 29px;
  font-size: 14px;
  font-weight: 500;
  line-height: 22px;
  cursor: pointer;
  transition: 0.2s ease-in-out;
}

/* 参考站的取消键是「白底 + 2px #F5F5F5 描边」的胶囊 */
.btn-ghost {
  border: 2px solid #f5f5f5;
  background: #fff;
  color: #000;
}

.btn-ghost:hover {
  background: #f6f6f6;
}

.btn-solid {
  border: 0;
  background: #141414;
  color: #fff;
}

.btn-solid:hover:not(:disabled) {
  background: #2e2e2e;
}

.btn-solid:disabled {
  background: rgb(0 0 0 / 10%);
  color: rgb(0 0 0 / 30%);
  cursor: not-allowed;
}

.spin {
  animation: picker-spin 1s linear infinite;
}

@keyframes picker-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
