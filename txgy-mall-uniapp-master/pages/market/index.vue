<script setup>
import { reactive } from 'vue';
import { onShow, onHide, onUnload } from '@dcloudio/uni-app';
import { publicMarketClient } from '@/sheep/api/public-market';
import { createPublicMarketBrowser, publicMarketState, publicPrice } from '@/sheep/helper/public-market.mjs';
const state = reactive(publicMarketState());
let browser;
onShow(() => { browser = createPublicMarketBrowser(state, publicMarketClient); void browser.open(state.tenantId); });
const stop = () => { browser?.dispose(); browser = null; };
onHide(stop); onUnload(stop);
const home = () => uni.switchTab({ url: '/pages/index/index' });
const selectMarket = event => { const item = state.directory[Number(event.detail.value)]; if (item) void browser?.open(item.tenantId); };
const selectShop = event => { state.shopId = String(state.shops[Number(event.detail.value) - 1]?.id || ''); };
const selectCategory = event => { state.categoryId = String(state.categories[Number(event.detail.value) - 1]?.id || ''); };
const selectedName = (items, id, fallback) => items.find(item => String(item.id) === id)?.name || fallback;
const skuName = sku => sku.properties?.map(p => (p.propertyName || '') + '：' + (p.valueName || '')).join(' / ') || '默认规格';
</script>
<template>
  <view class="public-market">
    <button size="mini" @tap="home">← 返回原商城</button>
    <view class="title">公开商城浏览</view>
    <view class="notice">原账号登录保留；本页仅匿名查看公开商品，不提供加购、下单或企业经营操作。</view>
    <picker v-show="state.directory.length" :range="state.directory.map(item => item.name)" :value="Math.max(0,state.directory.findIndex(item => item.tenantId === state.tenantId))" @change="selectMarket"><view class="pick">选择商城：{{ state.name || '请选择' }} ▾</view></picker>
    <view v-if="state.tenantId" class="filters">
      <picker :range="['全部店铺', ...state.shops.map(item => item.name)]" @change="selectShop"><view class="pick">{{ selectedName(state.shops,state.shopId,'全部店铺') }} ▾</view></picker>
      <picker :range="['全部分类', ...state.categories.map(item => item.name)]" @change="selectCategory"><view class="pick">{{ selectedName(state.categories,state.categoryId,'全部分类') }} ▾</view></picker>
      <input v-model="state.keyword" maxlength="100" placeholder="搜索商品名称或规格" />
      <button :disabled="state.loading" @tap="browser?.search(1)">查询</button>
    </view>
    <view v-if="state.error" class="notice">{{ state.error }}<button @tap="browser?.open(state.tenantId)">重新加载</button></view>
    <view v-else-if="state.loading" class="notice">正在加载商城…</view>
    <template v-else-if="state.tenantId">
      <view>公开商品 {{ state.total }} 件 · 第 {{ state.pageNo }} 页</view>
      <view v-if="!state.products.length" class="notice">当前条件下暂无可展示商品，请调整筛选。</view>
      <view v-for="product in state.products" :key="product.id" class="product" @tap="browser?.detail(product.id)"><view class="name">{{ product.name }}</view><view>{{ product.shopName || '店铺信息以详情为准' }}</view><view class="price">{{ publicPrice(product.price) }}</view><view>查看规格详情 →</view></view>
      <view class="pages"><button :disabled="state.pageNo <= 1" @tap="browser?.search(state.pageNo - 1)">上一页</button><button :disabled="state.pageNo * 12 >= state.total" @tap="browser?.search(state.pageNo + 1)">下一页</button></view>
    </template>
    <view v-if="state.detail || state.detailLoading || state.detailError" class="detail">
      <button size="mini" @tap="browser?.closeDetail()">关闭详情</button>
      <view v-if="state.detailLoading">正在加载详情…</view><view v-else-if="state.detailError">{{ state.detailError }}</view>
      <template v-else-if="state.detail"><view class="title">{{ state.detail.name }}</view><view>{{ state.detail.shopName }}</view><view>{{ state.detail.introduction }}</view><view class="price">{{ publicPrice(state.detail.price) }}</view><view v-for="sku in state.detail.skus || []" :key="sku.id">{{ skuName(sku) }} · {{ publicPrice(sku.price) }} · 库存 {{ sku.stock ?? '待确认' }}</view><view class="notice">价格与库存以后续下单核验为准。跨商城交易暂未开放。</view></template>
    </view>
  </view>
</template>
<style scoped>
.public-market{padding:30rpx;color:#173d37;background:#f4f7f6;min-height:100vh}.title{font-size:38rpx;font-weight:700;margin:26rpx 0}.notice{color:#64748b;line-height:1.8;padding:24rpx 0}.pick,input{padding:24rpx;background:white;border:1rpx solid #dce8e5;border-radius:12rpx;margin:16rpx 0}.product,.detail{padding:28rpx;margin:22rpx 0;background:#fff;border:1rpx solid #dce8e5;border-radius:16rpx;line-height:1.8;overflow-wrap:anywhere}.name{font-size:32rpx;font-weight:600}.price{color:#c7562b;font-size:34rpx}.pages{display:flex;gap:20rpx}button{font-size:26rpx;color:#0f766e}.filters{margin-bottom:28rpx}
</style>
