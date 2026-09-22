<template>
  <s-layout title="询价详情" navbar="normal" :bgStyle="{ color: '#f4f7f6' }">
    <view class="inquiry-detail">
      <view v-if="error" class="notice">{{ error }}<button size="mini" @tap="load">重新加载</button></view>
      <view v-else-if="loading" class="notice">正在加载询价详情…</view>
      <template v-else-if="detail">
        <view class="panel heading"><view class="status">{{ statusText }}</view><view class="title">{{ detail.order.title }}</view><view class="company">{{ detail.order.enterpriseName }}</view><view class="meta"><text>收货区域：{{ detail.order.deliveryArea }}</text><text>截止：{{ formatTime(detail.order.deadline) }}</text><text>已有 {{ detail.order.quoteCount }} 家报价</text></view></view>
        <view class="panel"><view class="section-title">采购物品</view><view v-for="item in detail.items" :key="item.id" class="item"><image v-if="item.picUrl" :src="sheep.$url.cdn(item.picUrl)" mode="aspectFill"/><view><text class="item-name">{{ item.name }}</text><text>{{ item.category }} · {{ item.specification || '规格待沟通' }}</text><text>{{ item.quantity }} {{ item.unit }}<template v-if="item.code"> · {{ item.code }}</template></text><text v-if="item.remark">采购备注：{{ item.remark }}</text></view></view></view>
        <view v-if="detail.order.remark" class="panel"><view class="section-title">采购说明</view><text class="public-remark">{{ detail.order.remark }}</text></view>
        <view class="panel privacy"><view class="section-title">报价说明</view><text>平台不主动展示结构化联系人、手机号和详细收货地址；发布方不得在公开文字或图片中填写联系方式、详细地址或二维码。</text><text>仅询价双方按权限查看沟通内容。供应商报价须绑定本店已审核在售商品，库存、运费和可配送范围在生成订单时再次核验。</text></view>
        <view class="action"><button v-if="canQuote" @tap="quoteNotice">供应商报价</button><button v-else disabled>{{ statusText }}</button></view>
      </template>
    </view>
  </s-layout>
</template>
<script setup>
import { computed, ref } from 'vue';
import { onLoad, onPullDownRefresh } from '@dcloudio/uni-app';
import sheep from '@/sheep';
import InquiryApi from '@/sheep/api/product/inquiry';
import { publicInquiryDetail, inquiryStatusText, canPublicQuote } from '@/sheep/helper/inquiry-market.mjs';
const id = ref(''), detail = ref(null), loading = ref(false), error = ref(''); let generation = 0;
const statusText = computed(() => detail.value ? inquiryStatusText(detail.value.order.status, detail.value.order.deadline) : '');
const canQuote = computed(() => detail.value && canPublicQuote(detail.value.order));
const formatTime = value => { const date = new Date(Number(value)); return Number.isNaN(date.getTime()) ? '待确认' : date.toLocaleString(); };
async function load() {
  const version = ++generation; loading.value = true; error.value = ''; detail.value = null;
  try { const response = await InquiryApi.getPublicDetail(id.value); if (version !== generation) return; if (response?.code !== 0) throw new Error(response?.msg || '询价详情加载失败'); detail.value = publicInquiryDetail(response.data); }
  catch (cause) { if (version === generation) error.value = cause instanceof Error ? cause.message : '询价详情加载失败，请重试'; }
  finally { if (version === generation) loading.value = false; }
}
function quoteNotice() { uni.showModal({ title: '企业店铺报价', content: '供应商报价与沟通请使用后台管理端“销售管理—询价机会”。移动端不开放正式询价业务。', showCancel: false }); }
onLoad(options => { id.value = String(options?.id || ''); void load(); });
onPullDownRefresh(async () => { try { await load(); } finally { uni.stopPullDownRefresh(); } });
</script>
<style scoped>
.inquiry-detail{padding:24rpx 24rpx 150rpx}.panel{padding:30rpx;margin-bottom:22rpx;background:#fff;border:1rpx solid #dce8e3;border-radius:18rpx}.heading{background:linear-gradient(145deg,#fff,#eef8f4)}.status{display:inline-block;padding:7rpx 16rpx;border-radius:999rpx;background:#dff3ea;color:#126e5b;font-size:23rpx}.title{margin:20rpx 0 12rpx;font-size:38rpx;font-weight:700;color:#173d37}.company{color:#45635d}.meta{display:grid;gap:12rpx;margin-top:24rpx;color:#657b77;font-size:24rpx}.section-title{font-size:30rpx;font-weight:700;color:#173d37;margin-bottom:18rpx}.item{display:flex;gap:20rpx;padding:22rpx 0;border-top:1rpx solid #edf2f0}.item image{width:150rpx;height:150rpx;border-radius:12rpx;background:#f0f4f3}.item>view{flex:1;min-width:0}.item text{display:block;margin-bottom:10rpx;color:#657b77;font-size:24rpx;overflow-wrap:anywhere}.item .item-name{color:#173d37;font-size:28rpx;font-weight:600}.public-remark{display:block;color:#657b77;font-size:25rpx;line-height:1.75;white-space:pre-wrap;overflow-wrap:anywhere}.privacy text{display:block;margin-top:14rpx;color:#64748b;font-size:24rpx;line-height:1.7}.notice{padding:70rpx 20rpx;text-align:center;color:#64748b}.notice button{display:block;margin:20rpx auto}.action{position:fixed;left:0;right:0;bottom:0;padding:18rpx 24rpx;background:#fff;border-top:1rpx solid #dce8e3;z-index:10}.action button{margin:0;background:#167762;color:#fff;font-size:28rpx}.action button[disabled]{background:#9aaba7}
</style>
