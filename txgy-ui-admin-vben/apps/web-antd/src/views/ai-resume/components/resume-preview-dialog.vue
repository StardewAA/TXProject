<script lang="ts" setup>
import { computed } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { formatDateTime } from './format';
import { parseResumeContent } from './resume-content';
import ResumeDocument from './resume-document.vue';

/**
 * 简历预览弹窗。
 *
 * 点「我的简历」里的卡片不再直接进工作区，而是先把这份简历渲染出来给他看——
 * 进工作区是一个「要动手改」的动作，从列表点进去容易误触，预览则没有副作用。
 * 想改的人在弹窗里点「进入编辑」。
 */

interface PreviewResume {
  content?: string;
  createTime?: unknown;
  fileName?: string;
  id: number;
  remark?: string;
  resumeType: number;
  title: string;
}

const props = defineProps<{
  open: boolean;
  resume?: PreviewResume;
}>();

const emit = defineEmits<{
  edit: [id: number];
  /** 用浏览器打开原始附件（PDF 走浏览器自带阅读器） */
  file: [id: number];
  close: [];
}>();

const title = computed(
  () => props.resume?.fileName?.trim() || props.resume?.title || '简历预览',
);

const parsed = computed(() =>
  parseResumeContent(props.resume?.content, props.resume?.title || ''),
);

const hasContent = computed(() => Boolean(props.resume?.content?.trim()));

const isAttachment = computed(() => props.resume?.resumeType === 1);
</script>

<template>
  <div v-if="open && resume" class="preview-mask" @click.self="emit('close')">
    <div class="preview" role="dialog" aria-modal="true" aria-labelledby="preview-title">
      <header class="preview-head">
        <div class="head-copy">
          <h2 id="preview-title">{{ title }}</h2>
          <p class="head-meta">
            <span v-if="resume.createTime">{{ formatDateTime(resume.createTime) }}</span>
            <span v-if="resume.remark && resume.remark !== '-'">{{ resume.remark }}</span>
          </p>
        </div>
        <button class="head-close" type="button" aria-label="关闭预览" @click="emit('close')">
          <IconifyIcon icon="lucide:x" />
        </button>
      </header>

      <div class="preview-body">
        <ResumeDocument v-if="hasContent" :content="parsed.draft" />
        <div v-else class="preview-empty">
          <IconifyIcon icon="lucide:file-question" />
          <strong>这份简历还没有可预览的内容</strong>
          <p>
            {{ isAttachment
              ? '附件还没解析出正文，可以先进编辑器手动补充。'
              : '内容为空，可以先进编辑器填写。' }}
          </p>
        </div>
      </div>

      <footer class="preview-foot">
        <button
          v-if="isAttachment && resume.id"
          type="button"
          class="btn-ghost"
          @click="emit('file', resume.id)"
        >
          <IconifyIcon icon="lucide:file-text" /> 打开原文件
        </button>
        <button type="button" class="btn-ghost" @click="emit('close')">关闭</button>
        <button type="button" class="btn-solid" @click="emit('edit', resume.id)">
          <IconifyIcon icon="lucide:square-pen" /> 进入编辑
        </button>
      </footer>
    </div>
  </div>
</template>

<style scoped>
.preview-mask {
  position: fixed;
  z-index: 1200;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgb(0 0 0 / 45%);
}

.preview {
  display: flex;
  flex-direction: column;
  width: min(920px, 100%);
  max-height: calc(100vh - 48px);
  overflow: hidden;
  border-radius: 12px;
  background: #f6f7f5;
}

.preview-head {
  display: flex;
  flex-shrink: 0;
  align-items: flex-start;
  gap: 12px;
  padding: 18px 20px 14px;
  border-bottom: 1px solid #ececec;
  background: #fff;
}

.head-copy {
  min-width: 0;
}

.head-copy h2 {
  margin: 0;
  overflow: hidden;
  color: #292929;
  font-size: 17px;
  font-weight: 600;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.head-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 12px;
  margin: 4px 0 0;
  color: #949494;
  font-size: 13px;
}

.head-close {
  display: flex;
  margin-left: auto;
  padding: 2px;
  border: 0;
  background: transparent;
  color: #999;
  font-size: 18px;
  cursor: pointer;
}

.head-close:hover {
  color: #292929;
}

.preview-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  background: #f6f7f5;
}

.preview-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 80px 24px;
  color: #9a9a9a;
  text-align: center;
}

.preview-empty svg {
  font-size: 34px;
  color: #c3ccc0;
}

.preview-empty strong {
  color: #292929;
  font-size: 15px;
}

.preview-empty p {
  margin: 0;
  font-size: 13px;
}

.preview-foot {
  display: flex;
  flex-shrink: 0;
  justify-content: flex-end;
  gap: 10px;
  padding: 14px 20px;
  border-top: 1px solid #ececec;
  background: #fff;
}

.btn-ghost,
.btn-solid {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 36px;
  padding: 0 18px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: 0.2s ease-in-out;
}

.btn-ghost {
  border: 1px solid #ececec;
  background: #fff;
  color: #5e5e5e;
}

.btn-ghost:hover {
  border-color: #d6d6d6;
  color: #292929;
}

.btn-solid {
  border: 0;
  background: #141414;
  color: #fff;
}

.btn-solid:hover {
  background: #2e2e2e;
}

/* 第一个按钮（打开原文件）靠左，其余靠右 */
.preview-foot .btn-ghost:first-child:not(:last-child) {
  margin-right: auto;
}

@media (max-width: 640px) {
  .preview-mask {
    padding: 12px;
  }

  .preview-foot {
    flex-wrap: wrap;
  }
}
</style>
