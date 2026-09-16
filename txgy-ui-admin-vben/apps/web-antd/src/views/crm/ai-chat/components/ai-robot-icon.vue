<script setup lang="ts">
/**
 * AI 智能助手头像图标
 *
 * 说明：使用 src/assets/imgs/ai-assistant-bear.png（线稿小熊 + 齿轮 + 扳手）。
 * 保留原有 size 属性与组件文件名，调用方无需改动；相比矢量线稿，这里是一张位图头像，
 * 因此不再随外层 CSS 的 color 变化（深色模式白色底，自行加 class 调整即可）。
 */
import aiAssistantIcon from '#/assets/imgs/ai-assistant-bear.png';

withDefaults(defineProps<{ size?: number | string }>(), {
  size: 64,
});
</script>

<template>
  <img
    :src="aiAssistantIcon"
    :width="size"
    :height="size"
    class="rounded-full object-cover"
    alt="AI 智能助手"
    aria-hidden="true"
  />
</template>
