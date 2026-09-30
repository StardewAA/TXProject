<script lang="ts" setup>
import { computed, ref } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { useSortable } from '@vueuse/integrations/useSortable';

import { RESUME_SECTION_MAP, resolveSectionOrder, type ResumeSection } from './resume-sections';

/**
 * 页面布局面板（左栏「页面布局」页）。
 *
 * 构成对齐参考站：顶部「间距」滑块 + 「一页模式」开关，下面是「模块管理」——
 * 逐个小节控制显隐与顺序。四项都直接写回草稿，中栏立刻跟着变，不需要额外保存动作。
 *
 * 小节的清单、标题、图标都取自 resume-sections，本文件不再自己维护一份
 * （改造前这里抄了第三份顺序，靠注释手工同步）。
 */

const props = defineProps<{
  content: Record<string, any>;
}>();

/**
 * 模块清单，顺序即中栏的渲染顺序。
 *
 * 做成可写 computed 是为了能直接交给拖拽库：它在 onUpdate 里会 `list.value = 排好序的新数组`，
 * 有了 setter 这一步就等于写回草稿，不需要再另接一个事件回调。
 */
const orderKeys = computed<string[]>({
  get: () => resolveSectionOrder(props.content),
  set: (value) => {
    props.content.sectionOrder = [...value];
  },
});

const orderedSections = computed<ResumeSection[]>(
  () =>
    orderKeys.value
      .map((key) => RESUME_SECTION_MAP.get(key))
      .filter((item): item is ResumeSection => Boolean(item)),
);

const listEl = ref<HTMLElement>();

/*
 * 这里**不能**包在自己的 onMounted 里调：useSortable 内部是 tryOnMounted 注册挂载钩子的，
 * 在已挂载的钩子里再注册一次，那个钩子永远不会触发，拖拽会静默失效（不报错、就是不响应）。
 * 传 ref + watchElement 让它自己去等元素出现。
 */
useSortable(listEl, orderKeys, {
  animation: 180,
  // 走指针事件而不是 HTML5 拖放，理由同中栏那份（见 resume-document.vue）
  forceFallback: true,
  // 手柄在每行左侧，点名字或眼睛按钮不会误触发拖动
  draggable: 'li',
  ghostClass: 'sec-ghost',
  handle: '.sec-drag',
  watchElement: true,
});

const spacing = computed({
  get: () => Number(props.content.pageSpacing ?? 1),
  set: (value: number) => {
    props.content.pageSpacing = value;
  },
});

const onePage = computed({
  get: () => props.content.onePage === true,
  set: (value: boolean) => {
    props.content.onePage = value;
  },
});

const hidden = computed<string[]>(() =>
  Array.isArray(props.content.hiddenSections)
    ? props.content.hiddenSections
    : [],
);

/** 字号：三档就够了，做成滑块反而难对准 */
const FONT_SIZES = [
  { key: 'small', label: '小' },
  { key: 'standard', label: '标准' },
  { key: 'large', label: '大' },
];

const fontSize = computed({
  get: () => String(props.content.pageFontSize || 'standard'),
  set: (value: string) => {
    props.content.pageFontSize = value;
  },
});

const showAvatar = computed({
  get: () => props.content.showAvatar !== false,
  set: (value: boolean) => {
    props.content.showAvatar = value;
  },
});

function isVisible(key: string) {
  return !hidden.value.includes(key);
}

function toggleSection(key: string) {
  const next = isVisible(key)
    ? [...hidden.value, key]
    : hidden.value.filter((item) => item !== key);
  // 存成显式的「隐藏了哪些」而不是「显示了哪些」：
  // 以后新增小节时，老简历默认是显示的，不会因为清单里没有它就凭空消失
  props.content.hiddenSections = next;
}

/** 间距数值的展示刻度，滑块的 min/max 与它一致 */
const SPACING_MIN = 0.8;
const SPACING_MAX = 1.4;
const SPACING_STEP = 0.05;
</script>

<template>
  <div class="layout-panel">
    <section class="group">
      <label class="row">
        <span class="row-label">间距</span>
        <span class="row-value">{{ Math.round(spacing * 100) }}%</span>
      </label>
      <input
        v-model.number="spacing"
        class="slider"
        type="range"
        :min="SPACING_MIN"
        :max="SPACING_MAX"
        :step="SPACING_STEP"
      />
    </section>

    <section class="group">
      <label class="row is-switch">
        <span class="row-label">一页模式</span>
        <input v-model="onePage" type="checkbox" class="switch" />
      </label>
      <p class="note">压缩行距与段距，尽量把内容收在一页里。</p>
    </section>

    <section class="group">
      <span class="row-label">正文字号</span>
      <div class="seg">
        <button
          v-for="item in FONT_SIZES"
          :key="item.key"
          type="button"
          :class="{ 'is-on': fontSize === item.key }"
          @click="fontSize = item.key"
        >
          {{ item.label }}
        </button>
      </div>
    </section>

    <section class="group">
      <label class="row is-switch">
        <span class="row-label">在简历上显示头像</span>
        <input v-model="showAvatar" type="checkbox" class="switch" />
      </label>
    </section>

    <section class="group">
      <h3 class="group-title">模块管理</h3>
      <p class="group-note">拖动左侧圆点调整顺序</p>
      <ul ref="listEl" class="section-list">
        <li v-for="item in orderedSections" :key="item.key" :data-section="item.key">
          <span
            class="sec-drag"
            role="button"
            tabindex="0"
            :title="`拖动${item.label}排序`"
            :aria-label="`拖动${item.label}排序`"
          >
            <IconifyIcon icon="lucide:move" />
          </span>
          <span class="sec-name">
            <IconifyIcon :icon="item.icon" />
            {{ item.label }}
          </span>
          <button
            type="button"
            class="eye"
            :class="{ 'is-off': !isVisible(item.key) }"
            :aria-pressed="isVisible(item.key)"
            :aria-label="`${isVisible(item.key) ? '隐藏' : '显示'}${item.label}`"
            @click="toggleSection(item.key)"
          >
            <IconifyIcon :icon="isVisible(item.key) ? 'lucide:eye' : 'lucide:eye-off'" />
          </button>
        </li>
      </ul>
    </section>
  </div>
</template>

<style scoped>
.layout-panel {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 16px;
  overflow-y: auto;
}

.group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.group-title {
  margin: 0;
  color: #292929;
  font-size: 13px;
  font-weight: 600;
}

.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.row-label {
  color: #5e5e5e;
  font-size: 13px;
}

.row-value {
  color: #8a8a8a;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.note {
  margin: 0;
  color: #a8a8a8;
  font-size: 12px;
  line-height: 1.7;
}

/* ── 滑块 ── */
.slider {
  width: 100%;
  height: 4px;
  border-radius: 2px;
  background: #ececec;
  appearance: none;
  cursor: pointer;
}

.slider::-webkit-slider-thumb {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: #141414;
  appearance: none;
  cursor: pointer;
}

/* ── 开关 ── */
.switch {
  position: relative;
  width: 38px;
  height: 22px;
  appearance: none;
  border-radius: 999px;
  background: #e0e0e0;
  cursor: pointer;
  transition: background 0.2s ease-in-out;
}

.switch::after {
  content: '';
  position: absolute;
  top: 2px;
  left: 2px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #fff;
  transition: transform 0.2s ease-in-out;
}

.switch:checked {
  background: #6e8d57;
}

.switch:checked::after {
  transform: translateX(16px);
}

/* ── 字号分段控件 ── */
.seg {
  display: flex;
  padding: 2px;
  border-radius: 8px;
  background: #f3f3f3;
}

.seg button {
  flex: 1;
  padding: 5px 0;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #7d7d7d;
  font-size: 12px;
  cursor: pointer;
  transition: 0.2s ease-in-out;
}

.seg button.is-on {
  background: #fff;
  color: #292929;
  font-weight: 500;
}

/* ── 模块管理 ── */
.section-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.section-list li {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 0;
  border-bottom: 1px solid #f2f2f2;
}

.section-list li:last-child {
  border-bottom: 0;
}

/* 名字与眼睛按钮一起靠右，左边的拖拽手柄留在行首 */
.section-list li .eye {
  margin-left: auto;
}

.group-note {
  margin: -2px 0 0;
  color: #b0b0b0;
  font-size: 12px;
  line-height: 18px;
}

/*
 * 拖拽手柄。
 * 平时淡一点、悬停才明显：它只是个入口，不该跟小节名字抢注意力。
 */
.sec-drag {
  display: inline-flex;
  flex-shrink: 0;
  color: #d0d0d0;
  font-size: 14px;
  cursor: move;
  transition: color 0.15s ease-in-out;
}

.section-list li:hover .sec-drag,
.sec-drag:focus-visible {
  color: #8a8a8a;
}

/* 拖拽时的落点提示，与中栏那份同色 */
.sec-ghost {
  border-radius: 6px;
  background: #fdf4e8;
  opacity: 0.6;
  outline: 1px dashed #e0913f;
}

.sec-name {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #4a4a4a;
  font-size: 13px;
}

.eye {
  display: flex;
  padding: 4px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #6e8d57;
  font-size: 15px;
  cursor: pointer;
  transition: 0.2s ease-in-out;
}

.eye:hover {
  background: #f2f6ee;
}

.eye.is-off {
  color: #c4c4c4;
}
</style>
