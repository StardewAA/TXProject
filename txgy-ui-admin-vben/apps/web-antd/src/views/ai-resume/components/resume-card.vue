<script lang="ts" setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { getHrmRecruitFilePreviewUrl } from '#/api/hrm/portal/recruit/file-url';

import { formatDateTime } from './format';

/**
 * 我的简历卡片。
 *
 * 版式对齐参考站：封面固定 205×250、圆角 12，悬停压一层 rgba(0,0,0,.2) 并在右上角
 * 浮出「⋯」；标题行下面是文件大小、时间和来源。
 *
 * 封面图这件事有个现实约束：hrm_personal_resume.avatar_url 目前全库为 NULL，
 * 附件简历也只有 fileUrl（PDF 直链，<img> 渲染不了）。所以这里做成本地绘制的
 * 「文档缩略图」——用姓名和几行示意文字拼出一页简历的样子。avatarUrl 一旦有值
 * 就优先用它（后端补齐封面后前端不用改），fileUrl 走文件预览地址同理。
 */

interface Props {
  /** 已诊断过，封面上挂「简历诊断」标 */
  diagnosed?: boolean;
  /** 已优化过，封面上挂「已优化」标 */
  optimized?: boolean;
  /** 展示用的姓名，缺省时退化成占位符 */
  name?: string;
  resume: {
    avatarUrl?: string;
    createTime?: string;
    fileName?: string;
    fileUrl?: string;
    id: number;
    remark?: string;
    resumeType: number;
    title: string;
  };
}

const props = withDefaults(defineProps<Props>(), {
  diagnosed: false,
  name: '',
  optimized: false,
});

const emit = defineEmits<{
  /** 点封面或标题：只做渲染预览，不改任何数据 */
  preview: [id: number];
  /** 进工作区编辑（「⋯」菜单） */
  edit: [id: number];
  /** 用浏览器打开原始附件（「⋯」菜单） */
  file: [id: number];
  remove: [id: number];
}>();

const isAttachment = computed(() => props.resume.resumeType === 1);

const root = ref<HTMLElement>();

/**
 * 右上角「⋯」菜单。状态留在卡片内部：交给父组件管就得维护「哪张卡开着」，
 * 而列表里每张卡互不相干，各自记一份最省事。
 */
const menuOpen = ref(false);

/** 点菜单之外的地方收起。用捕获阶段，避免被卡片自身的点击处理抢先截断 */
function closeOnOutsideClick(event: MouseEvent) {
  if (!menuOpen.value) return;
  if (!root.value?.contains(event.target as Node)) menuOpen.value = false;
}

onMounted(() => document.addEventListener('click', closeOnOutsideClick, true));
onBeforeUnmount(() =>
  document.removeEventListener('click', closeOnOutsideClick, true),
);

/**
 * 封面图地址。
 * avatarUrl 是后端预留的封面字段；没有就退回文件预览地址——附件简历在部分部署下
 * 能直接给出可渲染的图片，能拿到就用，拿不到再走下面的绘制缩略图。
 */
const coverUrl = computed(() => {
  const avatar = props.resume.avatarUrl?.trim();
  if (avatar) return avatar;
  return isAttachment.value
    ? getHrmRecruitFilePreviewUrl(props.resume.fileUrl)
    : '';
});

/**
 * 封面图加载失败。
 *
 * 附件简历的 fileUrl 指向 PDF，浏览器把它当图片加载会直接失败（裂图 + alt 文字）。
 * 与其赌后端给的是图片，不如让加载失败退回绘制缩略图——两种部署都能看。
 */
const coverFailed = ref(false);

watch(
  () => props.resume.id,
  () => {
    coverFailed.value = false;
  },
);

const displayTitle = computed(
  () => props.resume.fileName?.trim() || props.resume.title,
);

const sourceLabel = computed(() => props.resume.remark?.trim() || '');

/** 缩略图上的假名字：取简历标题里的首个词，够用且不会误导成真实姓名 */
const mockName = computed(() => props.name.trim());

/** 时间只到分钟——参考站也是这个精度，秒对简历列表没有信息量 */
const displayTime = computed(() => formatDateTime(props.resume.createTime));

function toggleMenu() {
  menuOpen.value = !menuOpen.value;
}

/** 选中菜单项后先把菜单收起来，再去派发——否则点完菜单还挂在封面上 */
function pick(action: 'edit' | 'file' | 'remove') {
  menuOpen.value = false;
  emit(action, props.resume.id);
}
</script>

<template>
  <div ref="root" class="resume-card">
    <div class="cover-wrap" :class="{ 'pop-show': menuOpen }">
      <div class="cover">
        <img
          v-if="coverUrl && !coverFailed"
          class="cover-img"
          :src="coverUrl"
          :alt="displayTitle"
          @error="coverFailed = true"
        />
        <!-- 没有封面图（或图挂了）时画一页简历示意，避免整格只剩一块灰底 -->
        <div v-else class="cover-mock" aria-hidden="true">
          <div class="mock-head">
            <span class="mock-name">{{ mockName || '简历' }}</span>
            <span class="mock-contact"></span>
          </div>
          <div v-for="row in 3" :key="row" class="mock-section">
            <span class="mock-bar"></span>
            <span class="mock-line" :style="{ width: `${92 - row * 11}%` }"></span>
            <span class="mock-line" :style="{ width: `${74 + row * 5}%` }"></span>
          </div>
        </div>
      </div>

      <div v-if="diagnosed || optimized" class="tags">
        <div v-if="diagnosed" class="tag analyze-tag">
          <IconifyIcon icon="lucide:scan-search" />
          <span>简历诊断</span>
        </div>
        <div v-if="optimized" class="tag default-tag">
          <IconifyIcon icon="lucide:sparkles" />
          <span>已优化</span>
        </div>
      </div>

      <div class="cover-mask"></div>

      <button
        class="cover-hit"
        type="button"
        :aria-label="`预览 ${displayTitle}`"
        @click="emit('preview', resume.id)"
      ></button>

      <div class="options">
        <button
          class="more-btn"
          type="button"
          :aria-label="`${displayTitle} 的操作`"
          :aria-expanded="menuOpen"
          @click.stop="toggleMenu"
        >
          <IconifyIcon icon="lucide:ellipsis" />
        </button>
        <div v-if="menuOpen" class="menu" role="menu">
          <button type="button" role="menuitem" @click.stop="pick('edit')">
            <IconifyIcon icon="lucide:square-pen" /> 编辑简历
          </button>
          <button
            v-if="isAttachment"
            type="button"
            role="menuitem"
            @click.stop="pick('file')"
          >
            <IconifyIcon icon="lucide:eye" /> 预览原文件
          </button>
          <button
            class="is-danger"
            type="button"
            role="menuitem"
            @click.stop="pick('remove')"
          >
            <IconifyIcon icon="lucide:trash-2" /> 删除
          </button>
        </div>
      </div>
    </div>

    <div class="info">
      <div class="label" :title="displayTitle">{{ displayTitle }}</div>
      <div v-if="displayTime" class="time">{{ displayTime }}</div>
      <div v-if="sourceLabel" class="source">{{ sourceLabel }}</div>
    </div>
  </div>
</template>

<style scoped>
/*
 * 数值逐条取自参考站计算结果：封面 205×250 / radius 12 / 底色 #eee，
 * 标签 radius 8、12px/500、padding 4px 6px，两套渐变见 .tag 规则。
 */
/* 卡片在格子里居中，与参考站一致；文字仍左对齐 */
.resume-card {
  justify-self: center;
  width: 205px;
}

.cover-wrap {
  position: relative;
  border-radius: 12px;
  overflow: hidden;
  cursor: pointer;
}

.cover-wrap:hover .cover-mask,
.cover-wrap.pop-show .cover-mask {
  opacity: 1;
}

.cover {
  position: relative;
  z-index: 0;
  width: 205px;
  height: 250px;
  border-radius: 12px;
  background: #eee;
  overflow: hidden;
}

.cover-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* ── 无封面图时的绘制版缩略图 ── */
.cover-mock {
  display: flex;
  flex-direction: column;
  gap: 14px;
  width: 100%;
  height: 100%;
  padding: 18px 16px;
  background: #fff;
  box-sizing: border-box;
}

.mock-head {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding-bottom: 10px;
  border-bottom: 1px solid #ececec;
}

.mock-name {
  color: #292929;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.2;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mock-contact {
  width: 72px;
  height: 5px;
  border-radius: 3px;
  background: #e3e3e3;
}

.mock-section {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.mock-bar {
  width: 34px;
  height: 6px;
  border-radius: 3px;
  background: #cfe0bd;
}

.mock-line {
  height: 5px;
  border-radius: 3px;
  background: #ededed;
}

/* ── 封面覆盖层 ── */
.cover-hit {
  position: absolute;
  inset: 0;
  z-index: 1;
  padding: 0;
  border: 0;
  background: transparent;
  cursor: pointer;
}

.tags {
  position: absolute;
  z-index: 4;
  top: 8px;
  left: 9px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.tag {
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 4px 6px;
  border-radius: 8px;
  color: #fff;
  font-size: 12px;
  font-weight: 500;
  line-height: 1.4;
  pointer-events: none;
}

.analyze-tag {
  background: linear-gradient(90.5137deg, #e65c62 2%, #e6885c);
}

.default-tag {
  background: linear-gradient(90.4055deg, #568a3c 2%, #8dbd74);
}

.cover-mask {
  position: absolute;
  z-index: 2;
  inset: 0;
  background: rgb(0 0 0 / 20%);
  opacity: 0;
  transition: 0.2s ease-in-out;
  pointer-events: none;
}

.cover-wrap:hover .cover-mask,
.cover-wrap.pop-show .cover-mask {
  opacity: 1;
}

/* ── 右上角「⋯」 ── */
.options {
  position: absolute;
  z-index: 5;
  top: 8px;
  right: 8px;
}

.more-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  padding: 0;
  border: 0;
  border-radius: 6px;
  background: #fff;
  color: #5e5e5e;
  font-size: 15px;
  cursor: pointer;
  opacity: 0;
  transition: 0.2s ease-in-out;
}

.cover-wrap:hover .more-btn,
.cover-wrap.pop-show .more-btn,
.more-btn:focus-visible {
  opacity: 1;
}

.menu {
  position: absolute;
  top: 28px;
  right: 0;
  display: flex;
  flex-direction: column;
  min-width: 116px;
  padding: 4px;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 6px 20px rgb(0 0 0 / 12%);
}

.menu button {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 32px;
  padding: 0 12px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #292929;
  font-size: 14px;
  line-height: 22px;
  text-align: left;
  white-space: nowrap;
  cursor: pointer;
  transition: 0.2s ease-in-out;
}

.menu button:hover {
  background: #f1f2f3;
}

.menu .is-danger {
  color: #d8504e;
}

/* ── 标题行 ── */
.info {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 12px;
}

.label {
  max-width: 200px;
  overflow: hidden;
  color: #292929;
  font-size: 16px;
  font-weight: 500;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.time {
  color: #949494;
  font-size: 14px;
  font-weight: 400;
  line-height: 1.4;
}

.source {
  color: #949494;
  font-size: 12px;
  font-weight: 400;
  line-height: 1.4;
}

@media (max-width: 640px) {
  .resume-card {
    width: 100%;
    max-width: 205px;
  }
}
</style>
