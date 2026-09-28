<template>
  <view class="comparison-bar">
    <view class="comparison-icon"><text>⇄</text></view>
    <view class="comparison-copy">
      <text>{{ state.ready ? `已选 ${state.list.skuIds.length} 个商品规格` : '跨店商品对比' }}</text>
      <text>{{ state.ready ? '横向对照参数与采购价格' : '正在读取对比清单' }}</text>
    </view>
    <button size="mini" @tap="open">去对比<text>›</text></button>
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
.comparison-bar { display:flex; align-items:center; gap:18rpx; margin:20rpx; padding:20rpx 22rpx; border:1rpx solid #cfe1dc; border-radius:22rpx; background:linear-gradient(120deg,#f8fbfa,#eaf5f2); box-shadow:0 10rpx 28rpx rgba(20,76,66,.09); color:#155f55; }
.comparison-icon { display:flex; align-items:center; justify-content:center; width:70rpx; height:70rpx; flex:none; border-radius:18rpx; background:linear-gradient(145deg,#163844,#0b8a74); color:#fff; font-size:34rpx; box-shadow:0 8rpx 18rpx rgba(15,101,87,.2); }
.comparison-copy { display:flex; flex:1; min-width:0; flex-direction:column; }
.comparison-copy text:first-child { color:#193f3a; font-size:26rpx; font-weight:800; }
.comparison-copy text:last-child { margin-top:4rpx; overflow:hidden; color:#71827e; font-size:20rpx; text-overflow:ellipsis; white-space:nowrap; }
.comparison-bar button { display:flex; align-items:center; gap:4rpx; margin:0; padding:0 18rpx; border:0; border-radius:28rpx; background:#0c8370; color:white; font-size:22rpx; font-weight:700; }
.comparison-bar button::after { border:0; }
.comparison-bar button text { font-size:29rpx; font-weight:400; }
</style>
