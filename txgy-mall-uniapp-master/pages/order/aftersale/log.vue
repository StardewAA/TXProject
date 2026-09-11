<!-- 售后日志列表  -->
<template>
  <s-layout title="售后进度">
    <view class="log-box">
      <view v-for="(item, index) in state.list" :key="item.id">
        <log-item :item="item" :index="index" :data="state.list" />
      </view>
    </view>
  </s-layout>
</template>

<script setup>
  import { onLoad } from '@dcloudio/uni-app';
  import { reactive } from 'vue';
  import logItem from './log-item.vue';
  import AfterSaleApi from '@/sheep/api/trade/afterSale';

  const state = reactive({
    list: [],
    loading: false,
  });

  async function getDetail(id) {
    if (state.loading) return;
    state.loading = true;
    try {
      const res = await AfterSaleApi.getAfterSaleLogList(String(id));
      state.list = Array.isArray(res?.data) ? res.data : [];
    } catch {
      state.list = [];
    } finally {
      state.loading = false;
    }
  }

  onLoad((options) => {
    if (!options?.id) return;
    state.aftersaleId = String(options.id);
    getDetail(state.aftersaleId);
  });
</script>

<style lang="scss" scoped>
  .log-box {
    padding: 24rpx 24rpx 24rpx 40rpx;
    background-color: #fff;
  }
</style>
