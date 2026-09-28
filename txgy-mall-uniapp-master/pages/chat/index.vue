<template>
  <s-layout title="平台客服">
    <view class="redirect-note">正在打开平台客服…</view>
  </s-layout>
</template>

<script setup>
  import { onLoad } from '@dcloudio/uni-app';

  // Keep old bookmarks usable without opening the obsolete WebSocket chat.
  onLoad(() => {
    uni.redirectTo({ url: '/pages/chat/service?scope=PLATFORM&shopId=0' });
  });
</script>

<style scoped>
  .redirect-note {
    padding: 48rpx 24rpx;
    color: #687d83;
    text-align: center;
  }
</style>
