<script setup lang="ts">
import { computed, ref } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { MarkdownView } from '#/components/markdown-view';

const props = defineProps<{
  content?: string;
  reasoningContent?: string;
  /** 本次思考耗时（秒），由发起流式对话的页面估算后传入 */
  durationSeconds?: number;
}>();

/** 折叠状态：默认收起（只露一行标题，点击展开） */
const isExpanded = ref(false);

/** 是否展开中 */
const isOpen = computed(() => isExpanded.value);

/** 判断是否应该显示组件 */
const shouldShowComponent = computed(() => {
  return props.reasoningContent && props.reasoningContent.trim() !== '';
});

/** 标题文本 */
const titleText = computed(() => {
  const hasReasoningContent =
    props.reasoningContent && props.reasoningContent.trim() !== '';
  const hasContent = props.content && props.content.trim() !== '';
  if (hasReasoningContent && !hasContent) {
    return '深度思考中';
  }
  if (
    hasReasoningContent &&
    props.durationSeconds &&
    props.durationSeconds > 0
  ) {
    return `已思考 ${props.durationSeconds} 秒`;
  }
  return '已深度思考';
});

/** 切换展开/收起 */
function toggleExpanded() {
  isExpanded.value = !isExpanded.value;
}
</script>

<template>
  <div
    v-if="shouldShowComponent"
    class="mes-reasoning-block"
    style="margin-bottom: 8px"
  >
    <!-- 标题栏：默认折叠，只露一行小标题 + 箭头 -->
    <div
      class="mes-reasoning-head flex cursor-pointer items-center justify-between"
      role="button"
      tabindex="0"
      @click="toggleExpanded"
      @keydown.enter="toggleExpanded"
    >
      <span class="mes-reasoning-title">{{ titleText }}</span>
      <IconifyIcon
        icon="lucide:chevron-down"
        class="mes-reasoning-arrow"
        :class="{ 'mes-reasoning-arrow-open': isOpen }"
        :size="12"
      />
    </div>
    <!-- 内容区：0.2s ease 过渡，展开最高 200px，超出滚动 -->
    <div
      class="mes-reasoning-body"
      :class="{ 'mes-reasoning-body-open': isOpen }"
    >
      <MarkdownView
        v-if="props.reasoningContent"
        class="mes-reasoning-content text-sm leading-relaxed"
        :content="props.reasoningContent"
      />
    </div>
  </div>
</template>

<style scoped>
/* 标题栏：12px 次要信息色，无图标无装饰 */
.mes-reasoning-head {
  user-select: none;
  line-height: 1.4;
}

.mes-reasoning-title {
  font-size: 12px;
  color: #8a8f98;
}

.mes-reasoning-arrow {
  color: #8a8f98;
  transition: transform 0.2s ease;
}

.mes-reasoning-arrow-open {
  transform: rotate(180deg);
}

/* 内容区：默认收起（高度 0 / 透明），展开 200px 高可滚动 */
.mes-reasoning-body {
  max-height: 0;
  opacity: 0;
  overflow: hidden;
  overflow-y: auto;
  transition:
    max-height 0.2s ease,
    opacity 0.2s ease,
    margin-top 0.2s ease;
}

.mes-reasoning-body-open {
  max-height: 200px;
  opacity: 1;
  margin-top: 6px;
}

/* 自定义细滚动条 */
.mes-reasoning-body::-webkit-scrollbar {
  width: 4px;
}

.mes-reasoning-body::-webkit-scrollbar-track {
  background: transparent;
}

.mes-reasoning-body::-webkit-scrollbar-thumb {
  @apply rounded-sm bg-gray-400/40;
}

.mes-reasoning-body::-webkit-scrollbar-thumb:hover {
  @apply bg-gray-400/60;
}
</style>
