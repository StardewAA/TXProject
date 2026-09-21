<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue';
import { publicMarketClient } from '@/sheep/api/public-market';
const visible = ref(false);
let alive = true;
onMounted(async () => {
  // #ifdef H5
  try { const directory = await publicMarketClient.directory(); if (alive) visible.value = directory.enabled; } catch { /* Keep the original market usable. */ }
  // #endif
});
onBeforeUnmount(() => { alive = false; });
function open() { uni.navigateTo({ url: '/pages/market/index' }); }
</script>
<template><button v-if="visible" class="market-entry" @tap="open">选择其他商城 · 匿名浏览 →</button></template>
<style scoped>.market-entry{margin:20rpx 0;background:#e9f4f1;color:#0f766e;font-size:26rpx;border-radius:14rpx}</style>
