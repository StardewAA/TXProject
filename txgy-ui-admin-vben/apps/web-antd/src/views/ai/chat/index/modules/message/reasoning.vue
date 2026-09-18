<script setup lang="ts">
import { computed, ref, watch } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { MarkdownView } from '#/components/markdown-view';

const props = defineProps<{
  content?: string;
  reasoningContent?: string;
}>();

// 默认折叠：仅在「思考中」（有思考内容但正文还没出来）时自动展开
const isExpanded = ref(false);

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
  return '已深度思考';
});

/** 切换展开/收起 */
function toggleExpanded() {
  isExpanded.value = !isExpanded.value;
}

// 流式输出时自动切换展开状态：
// - 思考中（有思考内容、正文为空）→ 自动展开，方便实时查看思考过程
// - 正文开始输出 → 自动折叠，突出最终答案
watch(
  () => [props.reasoningContent, props.content],
  () => {
    if (props.reasoningContent && !props.content) {
      isExpanded.value = true;
    } else if (props.content) {
      isExpanded.value = false;
    }
  },
);
</script>

<template>
  <div v-if="shouldShowComponent" class="mt-2.5">
    <!-- 标题栏 -->
    <div
      class="flex cursor-pointer items-center justify-between rounded-t-lg border border-b-0 border-gray-200/60 bg-gradient-to-r from-blue-50 to-purple-50 p-2 transition-all duration-200 hover:from-blue-100 hover:to-purple-100"
      @click="toggleExpanded"
    >
      <div class="flex items-center gap-1.5 text-sm font-medium text-gray-700">
        <IconifyIcon icon="lucide:brain" class="text-blue-600" :size="16" />
        <span>{{ titleText }}</span>
      </div>
      <IconifyIcon
        icon="lucide:chevron-down"
        class="text-gray-500 transition-transform duration-200"
        :class="{ 'rotate-180': isExpanded }"
        :size="14"
      />
    </div>
    <!-- 内容区 -->
    <div
      v-show="isExpanded"
      class="scrollbar-thin max-h-[300px] overflow-y-auto rounded-b-lg border border-t-0 border-gray-200/60 bg-white/70 p-3 shadow-sm backdrop-blur-sm"
    >
      <MarkdownView
        v-if="props.reasoningContent"
        class="text-sm leading-relaxed text-gray-700"
        :content="props.reasoningContent"
      />
    </div>
  </div>
</template>

<style scoped>
/* 自定义滚动条 */
.scrollbar-thin::-webkit-scrollbar {
  width: 4px;
}

.scrollbar-thin::-webkit-scrollbar-track {
  background: transparent;
}

.scrollbar-thin::-webkit-scrollbar-thumb {
  @apply rounded-sm bg-gray-400/40;
}

.scrollbar-thin::-webkit-scrollbar-thumb:hover {
  @apply bg-gray-400/60;
}
</style>
