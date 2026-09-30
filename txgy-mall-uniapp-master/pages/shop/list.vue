<template>
  <s-layout title="企业店铺" navbar="normal">
    <view class="shop-directory">
      <view class="directory-heading"><text>按店铺找材料</text><view>了解供应企业，集中查看店铺商品</view></view>
      <view class="shop-search"><input v-model="keyword" placeholder="搜索店铺名称或主营材料" confirm-type="search" /><text v-if="keyword" @tap="keyword = ''">清除</text></view>
      <s-shop-showcase ref="showcase" :keyword="keyword" />
    </view>
  </s-layout>
</template>
<script setup>
import { ref } from 'vue';
import { onPullDownRefresh } from '@dcloudio/uni-app';
const keyword = ref('');
const showcase = ref();
onPullDownRefresh(async () => { try { await showcase.value?.refresh(); } finally { uni.stopPullDownRefresh(); } });
</script>
<style scoped>
.shop-directory{padding:24rpx}.directory-heading{padding:30rpx;background:linear-gradient(120deg,#153f44,#147d6b);border-radius:20rpx;color:white;margin-bottom:24rpx}.directory-heading>text{font-size:36rpx;font-weight:600}.directory-heading>view{font-size:24rpx;color:#c6e0d6;margin-top:14rpx}.shop-search{padding:22rpx 26rpx;display:flex;gap:16rpx;background:white;border:1rpx solid #dce8e2;border-radius:14rpx;margin-bottom:24rpx}.shop-search input{flex:1;font-size:26rpx}.shop-search text{font-size:24rpx;color:#147d6b}
</style>
