<template>
  <s-layout title="询价专栏" navbar="normal" :bgStyle="{ color: '#f4f7f6' }">
    <view class="inquiry-market">
      <view class="hero"><text>平台采购需求</text><view>汇总开放租户明确发布到平台的采购询价；供应商报价仍按企业、店铺和品类权限校验</view></view>
      <view class="search"><input v-model="keyword" maxlength="50" confirm-type="search" placeholder="搜索询价标题或材料（最多50字）" @confirm="search" /><button size="mini" :disabled="loading" @tap="search">查询</button></view>
      <view class="tabs"><button :class="{ active: state === 'open' }" @tap="switchState('open')">报价中</button><button :class="{ active: state === 'ended' }" @tap="switchState('ended')">已结束</button></view>
      <view v-if="error" class="notice">{{ error }}<button size="mini" @tap="load(pageNo)">重新加载</button></view>
      <view v-else-if="loading && !rows.length" class="notice">正在加载询价…</view>
      <view v-else-if="!rows.length" class="notice">当前条件下暂无询价单</view>
      <view v-for="row in rows" :key="row.id" class="card" @tap="open(row.id)">
        <view class="card-head"><text class="status">{{ statusText(row) }}</text><text>{{ row.enterpriseName }}</text></view>
        <view class="title">{{ row.title }}</view>
        <view v-if="row.itemSummary" class="summary">{{ row.itemSummary }}</view>
        <view class="facts"><text>物品 {{ row.itemCount }} 项</text><text>报价 {{ row.quoteCount }} 家</text><text>{{ row.deliveryArea }}</text></view>
        <view class="deadline">{{ row.status === 1 ? '报价截止' : '截止时间' }}：{{ formatTime(row.deadline) }}</view>
      </view>
      <view v-if="rows.length" class="pages"><button :disabled="loading || pageNo <= 1" @tap="load(pageNo - 1)">上一页</button><text>第 {{ pageNo }} 页 · 共 {{ total }} 条</text><button :disabled="loading || pageNo * pageSize >= total" @tap="load(pageNo + 1)">下一页</button></view>
    </view>
  </s-layout>
</template>
<script setup>
import { ref } from 'vue';
import { onLoad, onPullDownRefresh } from '@dcloudio/uni-app';
import sheep from '@/sheep';
import InquiryApi from '@/sheep/api/product/inquiry';
import { publicInquiryPage, inquiryStatusText } from '@/sheep/helper/inquiry-market.mjs';
const rows = ref([]), total = ref(0), pageNo = ref(1), pageSize = 10, keyword = ref(''), state = ref('open'), loading = ref(false), error = ref('');
let generation = 0;
const message = cause => cause instanceof Error ? cause.message : '询价列表加载失败，请重试';
async function load(page = 1) {
  const version = ++generation; loading.value = true; error.value = '';
  try {
    const response = await InquiryApi.getPublicPage({ pageNo: page, pageSize, keyword: keyword.value.trim(), state: state.value });
    if (version !== generation) return;
    if (response?.code !== 0) throw new Error(response?.msg || '询价列表加载失败，请重试');
    const result = publicInquiryPage(response.data);
    rows.value = result.list; total.value = result.total; pageNo.value = page;
  } catch (cause) { if (version === generation) { rows.value = []; total.value = 0; error.value = message(cause); } }
  finally { if (version === generation) loading.value = false; }
}
const search = () => load(1);
const switchState = value => { if (state.value !== value) { state.value = value; void load(1); } };
const open = id => sheep.$router.go('/pages/inquiry/detail', { id });
const formatTime = value => { const date = new Date(Number(value)); return Number.isNaN(date.getTime()) ? '待确认' : date.toLocaleString(); };
const statusText = row => inquiryStatusText(row.status, row.deadline);
onLoad(() => load(1));
onPullDownRefresh(async () => { try { await load(1); } finally { uni.stopPullDownRefresh(); } });
</script>
<style scoped>
.inquiry-market{padding:24rpx}.hero{padding:34rpx;background:linear-gradient(120deg,#123f46,#14816d);border-radius:22rpx;color:#fff;margin-bottom:22rpx}.hero>text{font-size:38rpx;font-weight:700}.hero>view{margin-top:14rpx;color:#cae5dc;font-size:24rpx}.search{display:flex;gap:14rpx;padding:18rpx;background:#fff;border:1rpx solid #dce8e3;border-radius:14rpx}.search input{flex:1;padding:8rpx;font-size:26rpx}.search button{margin:0;background:#167762;color:#fff}.tabs{display:flex;gap:16rpx;margin:22rpx 0}.tabs button{flex:1;margin:0;font-size:26rpx;background:#fff;color:#526b66}.tabs button.active{background:#167762;color:#fff}.card{padding:28rpx;margin-bottom:20rpx;background:#fff;border:1rpx solid #dce8e3;border-radius:18rpx;box-shadow:0 8rpx 24rpx rgba(22,62,54,.05)}.card-head{display:flex;gap:14rpx;align-items:center;color:#58706b;font-size:23rpx}.status{padding:5rpx 13rpx;border-radius:999rpx;background:#e6f5ef;color:#12705d}.title{margin:18rpx 0 12rpx;font-size:32rpx;font-weight:700;color:#173d37}.summary{color:#526b66;font-size:25rpx;line-height:1.7}.facts{display:flex;gap:18rpx;flex-wrap:wrap;margin-top:18rpx;color:#6f827f;font-size:23rpx}.deadline{margin-top:18rpx;padding-top:16rpx;border-top:1rpx solid #edf2f0;color:#a8542c;font-size:23rpx}.notice{padding:50rpx 24rpx;text-align:center;color:#64748b;line-height:1.8}.notice button{display:block;margin:20rpx auto}.pages{display:flex;align-items:center;justify-content:center;gap:18rpx;color:#64748b;font-size:23rpx}.pages button{font-size:24rpx}.pages button[disabled]{opacity:.45}
</style>
