<template>
  <view class="comparison-bar">
    <text>{{ state.ready ? `对比篮 ${state.list.skuIds.length}/4` : '跨店商品对比' }}</text>
    <button size="mini" @tap="open">查看对比</button>
  </view>
</template>
<script setup>
  import { onMounted } from 'vue';
  import { onShow } from '@dcloudio/uni-app';
  import sheep from '@/sheep';
  import { comparison, comparisonState as state } from '@/sheep/helper/comparison-runtime';
  const open = () => sheep.$router.go('/pages/goods/compare');
  onMounted(() => comparison.load());
  onShow(() => comparison.load());
</script>
<style scoped>
.comparison-bar { display:flex; align-items:center; justify-content:space-between; gap:20rpx; margin:20rpx; padding:18rpx 24rpx; background:#e9f5f2; color:#155f55; border-radius:12rpx; font-size:26rpx; }
.comparison-bar button { margin:0; background:#187768; color:white; }
</style>
