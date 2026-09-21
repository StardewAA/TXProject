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
    /** dropdown 形态下的触发器文案 */
    triggerLabel?: string;
    /** dropdown = 导航浮层；inline = 页面内工具条 */
    variant?: 'dropdown' | 'inline';
  }>(),
  {
    variant: 'inline',
    triggerLabel: '在线简历',
  },
);

const emit = defineEmits<{
  ai: [];
  edit: [];
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
  >
    <button
      v-if="isDropdown"
      ref="triggerRef"
      type="button"
      class="resume-tools-trigger"
      aria-haspopup="menu"
      :aria-expanded="isOpen"
      :aria-controls="panelId"
      @click="togglePanel"
    >
      <span class="resume-tools-trigger-label">{{ triggerLabel }}</span>
      <IconifyIcon class="resume-tools-trigger-arrow" icon="lucide:chevron-down" />
    </button>

    <section
      v-show="!isDropdown || isOpen"
      :id="panelId"
      class="resume-tools-panel"
      :role="isDropdown ? 'menu' : 'group'"
      :aria-label="`${triggerLabel}工具`"
    >
      <header class="resume-tools-head">
        <strong>简历工具</strong>
        <small>选择要使用的简历能力</small>
      </header>

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
          <span class="resume-tool-icon"><IconifyIcon :icon="tool.icon" /></span>
          <span class="resume-tool-copy">
            <strong>{{ tool.title }}</strong>
            <small>{{ tool.description }}</small>
          </span>
        </button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.resume-tools {
  --resume-tool-ink: #17343d;
  --resume-tool-muted: #5f7a80;
  --resume-tool-line: #dce8e7;
  --resume-tool-soft: #edf7f5;
  position: relative;
}

.resume-tools-trigger {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  height: 58px;
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
.resume-tools-trigger[aria-expanded='true'] {
  color: #c96632;
}

.resume-tools-trigger:focus-visible {
  border-radius: 4px;
  outline: 2px solid #4de0d2;
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
  border: 1px solid var(--resume-tool-line);
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 16px 38px rgb(23 52 61 / 12%);
}

.resume-tools--dropdown .resume-tools-panel {
  position: absolute;
  top: calc(100% + 10px);
  right: 0;
  z-index: 40;
  width: min(92vw, 620px);
  box-shadow: 0 22px 48px rgb(23 52 61 / 18%);
}

/* 页面内工具条不需要铺满整行，否则卡片被拉得过宽 */
.resume-tools--inline .resume-tools-panel {
  max-width: 720px;
}

.resume-tools-head {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.resume-tools-head strong {
  color: var(--resume-tool-ink);
  font-size: 15px;
}

.resume-tools-head small {
  color: var(--resume-tool-muted);
  font-size: 12px;
}

.resume-tools-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.resume-tool-card {
  position: relative;
  display: grid;
  gap: 10px;
  align-content: start;
  padding: 16px 14px;
  border: 1px solid var(--resume-tool-line);
  border-radius: 12px;
  background: #fff;
  color: var(--resume-tool-ink);
  cursor: pointer;
  text-align: left;
  transition: border-color 160ms ease, box-shadow 160ms ease, transform 160ms ease;
}

.resume-tool-card.is-ai {
  --tool-ink: #0f766e;
  --tool-tint: #e6f7f4;
  --tool-tint-2: #d3f0ea;
}

.resume-tool-card.is-edit {
  --tool-ink: #15803d;
  --tool-tint: #e8f6ee;
  --tool-tint-2: #d6efe0;
}

.resume-tool-card.is-version {
  --tool-ink: #0e7490;
  --tool-tint: #e6f4f8;
  --tool-tint-2: #d4ebf3;
}

.resume-tool-card.is-upload {
  --tool-ink: #0d7f74;
  --tool-tint: #eef7f6;
  --tool-tint-2: #dcf0ed;
}

.resume-tool-card:hover {
  border-color: #9ed4cc;
  box-shadow: 0 10px 22px rgb(23 52 61 / 10%);
  transform: translateY(-2px);
}

.resume-tool-card:focus-visible {
  outline: 2px solid #4de0d2;
  outline-offset: 2px;
}

.resume-tool-icon {
  display: grid;
  width: 42px;
  height: 42px;
  place-items: center;
  border-radius: 13px;
  background: linear-gradient(140deg, var(--tool-tint), var(--tool-tint-2));
  color: var(--tool-ink);
  font-size: 22px;
}

.resume-tool-copy {
  display: grid;
  gap: 3px;
}

.resume-tool-copy strong {
  color: var(--resume-tool-ink);
  font-size: 15px;
  font-weight: 650;
}

.resume-tool-copy small {
  color: var(--resume-tool-muted);
  font-size: 12px;
  line-height: 1.5;
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
  .resume-tools-trigger-arrow {
    transition: none;
  }
}
</style>
