<script lang="ts" setup>
import { onMounted } from 'vue';

/**
 * AI 简历是 HRM 个人端的独立全屏应用入口。
 * 业务导航、用户信息和页面状态由子页面维护，这个布局只负责隔离公共门户的页眉/页脚样式。
 */
onMounted(() => {
  document.documentElement.classList.add('ai-resume-document');
});
</script>

<template>
  <div class="ai-resume-layout">
    <RouterView />
  </div>
</template>

<style>
html.ai-resume-document,
html.ai-resume-document body,
html.ai-resume-document #app {
  min-height: 100%;
  /* 内容区底色 = 侧栏色 #D0E4F2 的 20% 淡化，与 views/ai-resume/index.vue 的 --canvas 一致 */
  background: color-mix(in srgb, #d0e4f2 20%, #fff);
}

html.ai-resume-document body {
  overflow-x: hidden;
}

.ai-resume-layout {
  min-height: 100vh;
  /* 内容区底色 = 侧栏色 #D0E4F2 的 20% 淡化，与 views/ai-resume/index.vue 的 --canvas 一致 */
  background: color-mix(in srgb, #d0e4f2 20%, #fff);
}

/*
 * 打印（简历「下载」走 window.print）时把外层盒子放开。
 *
 * 这几条必须放在布局里而不是页面里：html / body / #app 都是页面的祖先，
 * 页面自己的 scoped 样式够不到它们。屏幕上那层浅蓝底和 min-height 是给眼睛看的，
 * 打出来既浪费墨，又会把简历挤得偏离页面上沿。
 */
@media print {
  html.ai-resume-document,
  html.ai-resume-document body,
  html.ai-resume-document #app,
  html.ai-resume-document .ai-resume-layout {
    height: auto;
    min-height: 0;
    overflow: visible;
    background: #fff;
  }
}
</style>
