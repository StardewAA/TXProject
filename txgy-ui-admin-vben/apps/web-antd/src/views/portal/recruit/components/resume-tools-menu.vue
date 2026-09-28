<script lang="ts" setup>
/**
 * 招聘门户「简历工具」菜单。
 *
 * - variant="dropdown"：右上角导航里的触发器 + 浮层，支持点击/键盘展开、外部点击与 ESC 关闭
 * - variant="inline"：简历页里的静态工具条
 *
 * 四张卡片都对应真实动作，点击后向上抛出事件由页面处理；
 * 这里不产生任何结果，也不编造内容。
 */
import { computed, onMounted, onUnmounted, ref, useId } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { RESUME_TOOL_ITEMS, type ResumeToolItem } from './resume-tools';

defineOptions({ name: 'ResumeToolsMenu' });

const props = withDefaults(
  defineProps<{
    active?: boolean;
    /** dropdown 形态下点击触发器的行为 */
    triggerAction?: 'navigate' | 'toggle';
    /** dropdown 形态下的触发器文案 */
    triggerLabel?: string;
    /** dropdown = 导航浮层；inline = 页面内工具条 */
    variant?: 'dropdown' | 'inline';
  }>(),
  {
    variant: 'inline',
    triggerLabel: '在线简历',
    triggerAction: 'toggle',
    active: false,
  },
);

const emit = defineEmits<{
  ai: [];
  edit: [];
  generate: [];
  trigger: [];
  upload: [];
  version: [];
}>();

const rootRef = ref<HTMLElement>();
const triggerRef = ref<HTMLButtonElement>();
const isOpen = ref(false);
const panelId = `resume-tools-panel-${useId()}`;

const isDropdown = computed(() => props.variant === 'dropdown');

function openPanel() {
  isOpen.value = true;
}

function closePanel() {
  isOpen.value = false;
}

function togglePanel() {
  if (isOpen.value) {
    closePanel();
    return;
  }
  openPanel();
}

function handleTriggerClick() {
  if (props.triggerAction === 'navigate') {
    emit('trigger');
    return;
  }
  togglePanel();
}

function handlePointerEnter() {
  if (isDropdown.value) openPanel();
}

function handlePointerLeave() {
  if (isDropdown.value) closePanel();
}

function handleToolClick(tool: ResumeToolItem) {
  closePanel();
  // 显式分发，避免把 kind 直接当事件名传给 emit 造成类型不收敛
  switch (tool.kind) {
    case 'ai': {
      emit('ai');
      break;
    }
    case 'edit': {
      emit('edit');
      break;
    }
    case 'generate': {
      emit('generate');
      break;
    }
    case 'upload': {
      emit('upload');
      break;
    }
    case 'version': {
      emit('version');
      break;
    }
    default: {
      break;
    }
  }
}

function handleDocumentPointerDown(event: Event) {
  if (!isOpen.value) return;
  const root = rootRef.value;
  if (root && !root.contains(event.target as Node)) closePanel();
}

function handleDocumentKeydown(event: KeyboardEvent) {
  if (!isOpen.value || event.key !== 'Escape') return;
  closePanel();
  triggerRef.value?.focus();
}

onMounted(() => {
  document.addEventListener('mousedown', handleDocumentPointerDown);
  document.addEventListener('keydown', handleDocumentKeydown);
});

onUnmounted(() => {
  document.removeEventListener('mousedown', handleDocumentPointerDown);
  document.removeEventListener('keydown', handleDocumentKeydown);
});
</script>

<template>
  <div
    ref="rootRef"
    class="resume-tools"
    :class="[`resume-tools--${variant}`, { 'is-open': isOpen }]"
    @mouseenter="handlePointerEnter"
    @mouseleave="handlePointerLeave"
  >
    <button
      v-if="isDropdown"
      ref="triggerRef"
      type="button"
      class="resume-tools-trigger"
      :class="{ active }"
      aria-haspopup="menu"
      :aria-expanded="isOpen"
      :aria-controls="panelId"
      @click="handleTriggerClick"
    >
      <span class="resume-tools-trigger-label">{{ triggerLabel }}</span>
    </button>

    <section
      v-show="!isDropdown || isOpen"
      :id="panelId"
      class="resume-tools-panel"
      :role="isDropdown ? 'menu' : 'group'"
      :aria-label="`${triggerLabel}工具`"
    >
      <div class="resume-tools-grid">
        <button
          v-for="tool in RESUME_TOOL_ITEMS"
          :key="tool.key"
          type="button"
          class="resume-tool-card"
          :class="`is-${tool.key}`"
          :role="isDropdown ? 'menuitem' : undefined"
          @click="handleToolClick(tool)"
        >
          <span class="resume-tool-icon" :class="{ 'is-image': tool.iconImage }">
            <img v-if="tool.iconImage" :src="tool.iconImage" alt="" class="resume-tool-image" />
            <IconifyIcon v-else :icon="tool.icon" />
          </span>
          <span class="resume-tool-copy">
            <strong>{{ tool.title }}</strong>
          </span>
        </button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.resume-tools {
  /* 复用职位页那份配色 token（值取自 index.vue 的 .recruit-page）。
     这里必须再声明一份，不能只靠祖先继承：这是个被顶栏复用的组件，
     挂在门户外壳、职位详情、企业页三处，单元测试里更是脱离任何祖先单独挂载，
     而 var() 解析失败会让整条声明作废（不是回退到默认色）。 */
  --job-ink: #1b2a3d;
  --job-muted: #5f7288;
  --job-blue: #2f6fb3;
  --job-blue-soft: #e8f1fb;
  --job-line: #d7e5f4;
  --job-green: #0c9653;
  --job-green-soft: #e6fcf1;
  --job-green-line: #bcebd2;
  position: relative;
}

.resume-tools-trigger {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  height: 60px;
  padding: 0 4px;
  border: 0;
  background: transparent;
  color: #587178;
  cursor: pointer;
  font-size: 15px;
  font-weight: 600;
  transition: color 180ms ease;
}

.resume-tools-trigger:hover,
.resume-tools-trigger[aria-expanded='true'],
.resume-tools-trigger.active {
  color: #c96632;
}

.resume-tools-trigger.active { position: relative; }
.resume-tools-trigger.active::after {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  height: 3px;
  border-radius: 3px 3px 0 0;
  background: #fff;
  content: '';
}

/* 触发器在深色导航里是白字、浅色导航里是黑字，聚焦环跟着 currentColor 走，
   两种底色下都看得见（原先是青色，和门户配色没有关系） */
.resume-tools-trigger:focus-visible {
  border-radius: 4px;
  outline: 2px solid currentColor;
  outline-offset: 3px;
}

.resume-tools-trigger-arrow {
  font-size: 14px;
  transition: transform 180ms ease;
}

.resume-tools.is-open .resume-tools-trigger-arrow {
  transform: rotate(180deg);
}

.resume-tools-panel {
  display: grid;
  gap: 12px;
  padding: 16px;
  border: 1px solid var(--job-line);
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 18px 40px rgb(27 42 61 / 12%), 0 2px 6px rgb(27 42 61 / 5%);
}

.resume-tools--dropdown .resume-tools-panel {
  position: absolute;
  top: 100%;
  right: -110px;
  z-index: 40;
  width: min(92vw, 500px);
  box-shadow: 0 24px 52px rgb(27 42 61 / 18%), 0 2px 8px rgb(27 42 61 / 6%);
}

/* 展开时重放一次下落动画（v-show 切的是 display，is-open 与它同步）；
   收起不播，免得鼠标移开时还拖一条残影 */
.resume-tools--dropdown.is-open .resume-tools-panel {
  animation: resume-tools-drop 180ms ease-out;
}

@keyframes resume-tools-drop {
  from {
    opacity: 0;
    transform: translateY(-6px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 641–1100px：触发器右边缘离视口右侧还很远，绝对定位会让面板左侧溢出被裁切，
   改成贴在导航栏正下方的右对齐浮层。top 取 60px 与导航栏等高 —— 留缝的话
   鼠标从触发器移向面板的途中会离开组件，mouseleave 立刻把面板收掉。 */
@media (max-width: 1100px) {
  .resume-tools--dropdown .resume-tools-panel {
    position: fixed;
    top: 60px;
    right: 12px;
    left: auto;
    width: min(660px, calc(100vw - 24px));
  }
}

/* 页面内工具条不需要铺满整行，否则卡片被拉得过宽 */
.resume-tools--inline .resume-tools-panel {
  max-width: 720px;
}

/*
 * 面板收到 500px 宽后，4 列的卡片只剩 110px 左右，
 * 间距和卡内边距都得跟着收：不然「附件简历制作」这 6 个字（14px 约 84px）
 * 放不进卡内 84px 的可用宽度，会折成两行，那一列就被撑高、四张卡不齐。
 */
.resume-tools-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}

.resume-tool-card {
  /* 默认淡蓝；换绿的是下面那两张 */
  --tool-ink: var(--job-blue);
  --tool-line: var(--job-line);
  --tool-tint: var(--job-blue-soft);
  --tool-wash: #f7fbff;
  position: relative;
  display: grid;
  gap: 8px;
  /* 图标和标题居中：justify-items 管横向、text-align 管文字本身 */
  justify-items: center;
  align-content: center;
  padding: 10px;
  border: 1px solid var(--job-line);
  border-radius: 12px;
  background: #fff;
  color: var(--job-ink);
  cursor: pointer;
  text-align: center;
  transition: border-color 180ms ease, background 180ms ease, box-shadow 180ms ease, transform 180ms ease;
}

/* 绿色只给「AI简历」「附件简历制作」这两张 —— 其余两张保持淡蓝。
   沿用招聘页「淡蓝承载工具、绿色点亮重点」的分工；四张卡各配一个青/绿/蓝色号
   时并排看更像配色事故而不是层次，所以不做四色。 */
.resume-tool-card.is-ai,
.resume-tool-card.is-generate {
  --tool-ink: var(--job-green);
  --tool-line: var(--job-green-line);
  --tool-tint: var(--job-green-soft);
  --tool-wash: #f6fefb;
}

.resume-tool-card:hover {
  border-color: color-mix(in srgb, var(--tool-ink) 42%, #fff);
  background: var(--tool-wash);
  box-shadow: 0 12px 24px rgb(27 42 61 / 10%);
  transform: translateY(-2px);
}

.resume-tool-card:focus-visible {
  outline: 2px solid var(--tool-ink);
  outline-offset: 2px;
}

/* 纯色块 + 同色描边，比原先那段淡到发灰的渐变清晰 */
.resume-tool-icon {
  display: grid;
  width: 48px;
  height: 48px;
  place-items: center;
  border: 1px solid var(--tool-line);
  border-radius: 10px;
  background: var(--tool-tint);
  color: var(--tool-ink);
  /* 字号取块的一半。不跟着块走的话，块里会空出一大圈 */
  font-size: 24px;
  transition: background 180ms ease, box-shadow 180ms ease;
}

/* 悬停把图标块翻成实心反白。只把底色压深、字色留着的话，
   蓝底压深后仍是蓝字，刚够看清但明显发闷 */
.resume-tool-card:hover .resume-tool-icon {
  border-color: var(--tool-ink);
  background: var(--tool-ink);
  color: #fff;
  box-shadow: 0 4px 10px color-mix(in srgb, var(--tool-ink) 28%, transparent);
}

/* 位图图标：42px，占 60px 块的七成，和图标字体那一档留白一致。
   插画里有机器人、文档、对话气泡三个元素，小了会糊成一团。 */
.resume-tool-image {
  width: 34px;
  height: auto;
}

/*
 * 用插画的那张卡不跟着翻实心反白。
 *
 * 图标字体能整体变白，是因为它只有一个颜色；插画里机器人脸和对话气泡是实心块，
 * 压成纯白之后内部结构全没了 —— 试过 filter: brightness(0) invert(1)，糊成一片白。
 * 所以这里改成把淡底压深一档，悬停反馈交给卡片本身的描边、底色和投影去承担。
 */
.resume-tool-card:hover .resume-tool-icon.is-image {
  border-color: color-mix(in srgb, var(--tool-ink) 42%, #fff);
  background: color-mix(in srgb, var(--tool-tint) 80%, var(--tool-ink));
  box-shadow: none;
}

/* max-width 兜住：justify-items:center 会把这一项按内容宽度摆放，
   标题过长时得允许它回落到卡内宽度，否则会横向溢出 */
.resume-tool-copy {
  display: grid;
  max-width: 100%;
}

.resume-tool-copy strong {
  color: var(--job-ink);
  font-size: 14px;
  font-weight: 650;
}

@media (max-width: 640px) {
  .resume-tools--dropdown .resume-tools-trigger {
    height: 44px;
  }

  /* 触发器不在屏幕右边缘，绝对定位会让面板向左溢出被裁切；
     窄屏改为贴底弹出，始终完整可见 */
  .resume-tools--dropdown .resume-tools-panel {
    position: fixed;
    top: auto;
    right: 10px;
    bottom: 10px;
    left: 10px;
    z-index: 60;
    width: auto;
    max-height: 74vh;
    overflow-y: auto;
  }

  .resume-tools-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (prefers-reduced-motion: reduce) {
  .resume-tool-card,
  .resume-tool-icon,
  .resume-tools-trigger-arrow {
    transition: none;
  }

  .resume-tools--dropdown.is-open .resume-tools-panel {
    animation: none;
  }
}
</style>
