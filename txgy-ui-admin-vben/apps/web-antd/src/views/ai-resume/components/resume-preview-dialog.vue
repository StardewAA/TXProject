<script lang="ts" setup>
import { computed } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { getHrmRecruitFilePreviewUrl } from '#/api/hrm/portal/recruit/file-url';

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
  fileUrl?: string;
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

/** 原件存在 HRM 自己的文件口子下 —— 只有这一种能匿名取到，见下 */
const HRM_FILE_PATH = '/hrm/portal/recruit/file/';

/**
 * 把原件 PDF 直接嵌进来看的地址；返回空串就走文字渲染。
 *
 * <p><b>为什么纯文本附件不渲染正文、改给原件：</b>抽取出来的正文是给 AI 用的
 * （诊断/优化拿它当输入），不是为了给人看的 —— 它是一整段无字段结构的纯文本，
 * 塞进文档模板后会被当成「个人优势」压在最后，上面留着一大片空白的分区骨架。
 * 实测过，那种预览八成是空的占位提示，反而更难读。原件带版式带照片，才是简历该有的样子。</p>
 *
 * <p><b>为什么结构化正文不走这条路：</b>在线简历与「附件简历制作」生成的那几份
 * content 是结构化 JSON，文档组件本来就是照它写的，渲染得很好，没必要退回原件。</p>
 *
 * <p><b>为什么限定 HRM 文件口子：</b>iframe 发请求带不上 Bearer 头，只有
 * /hrm/portal/recruit/file/** 是 @PermitAll 能匿名取到。老记录（test.txgy.iocoder.cn
 * 那批）映射到 infra 文件服务，实测匿名与带 token 都返回 {"code":500} —— 那是另一套
 * 环境的存储，本环境里没有。嵌进来只会显示一串报错 JSON，不如老实说没有。
 * DOC/DOCX 也一律不走这里：丢进 iframe 不会渲染，只会触发一次下载。</p>
 */
const inlinePdfUrl = computed(() => {
  if (!isAttachment.value || parsed.value.structured) return '';
  const resume = props.resume;
  const fileUrl = resume?.fileUrl || '';
  if (!fileUrl.includes(HRM_FILE_PATH)) return '';
  // 用 fileName 判类型而不是 fileUrl：后者末尾是 uuid，看不出是什么格式
  if (!resume?.fileName?.toLowerCase().endsWith('.pdf')) return '';
  return getHrmRecruitFilePreviewUrl(fileUrl);
});
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

      <div class="preview-body" :class="{ 'is-frame': inlinePdfUrl }">
        <!-- 顺序要紧：原件一旦可用就压过文字渲染，否则 hasContent 为真时永远轮不到它 -->
        <iframe
          v-if="inlinePdfUrl"
          class="preview-frame"
          :src="inlinePdfUrl"
          :title="title"
        ></iframe>
        <!--
          deliver：预览看的是「这份简历长什么样」，编辑器专用的填写指引、空值占位、
          占位骨架都不该出现（「大学之前的教育经历建议不写」这类话既不是简历内容，
          也容易被当成正文读）。与「下载」导出的口径一致。
        -->
        <ResumeDocument
          v-else-if="hasContent"
          :content="parsed.draft"
          deliver
        />
        <div v-else class="preview-empty">
          <IconifyIcon icon="lucide:file-question" />
          <strong>这份简历还没有可预览的内容</strong>
          <p>
            {{ isAttachment
              ? '附件还没解析出正文，可以点左下角打开原文件。'
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

/*
 * 内联渲染原件时弹窗必须有一个**确定高度**。
 *
 * <p>.preview 平时只有 max-height，高度由内容撑开；这种容器里子元素的 flex:1
 * 没有可分配的空间，iframe 会退回浏览器默认的 150px 高 —— 实测过，预览就是一条横条。
 * 给了确定高度，.preview-body 的 flex:1 才有东西可分，iframe 再 flex:1 撑满中间那段。</p>
 *
 * <p>用 min() 而不是写死：矮窗口下不能顶出视口，否则底部按钮会被 overflow:hidden 裁掉。</p>
 */
.preview:has(.preview-body.is-frame) {
  height: min(880px, calc(100vh - 48px));
}

.preview-body.is-frame {
  display: flex;
  overflow: hidden;
  padding: 0;
}

/* PDF 阅读器自带工具栏，底色给它白的；圆角由外层 overflow:hidden 裁 */
.preview-frame {
  flex: 1;
  width: 100%;
  border: 0;
  background: #fff;
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
