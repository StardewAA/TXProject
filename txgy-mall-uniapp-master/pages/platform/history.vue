<template>
  <s-layout title="平台结算记录" :bgStyle="{ color: '#f4f7fb' }">
    <view class="history-page">
      <view class="boundary-notice">
        <view class="notice-title">当前记录仅用于查看跨租户拆单规划</view>
        <view class="notice-copy">
          DRAFT_ONLY / NOT_PAYABLE 表示只保存了拆单规划：不可支付、不代表正式订单，也不会触发发货。
        </view>
      </view>

      <view class="page-head">
        <view>
          <view class="page-title">我的结算规划</view>
          <view class="page-meta">共 {{ state.total }} 条，仅显示当前登录平台账号本人的记录</view>
        </view>
        <button class="plain-button" :disabled="state.loading" @tap="loadPage(state.pageNo)">刷新</button>
      </view>

      <view v-if="state.error" class="state-card state-error">
        <text>{{ state.error }}</text>
        <button size="mini" :disabled="state.loading" @tap="loadPage(state.pageNo)">重新加载</button>
      </view>
      <view v-else-if="state.loading" class="state-card">正在加载平台结算记录…</view>
      <s-empty
        v-else-if="!state.rows.length"
        icon="/static/order-empty.png"
        text="暂无平台结算记录"
        showAction
        actionText="查看平台购物车"
        actionUrl="/pages/platform/cart"
      />

      <view v-else class="history-list">
        <view v-for="row in state.rows" :key="row.checkoutNo" class="history-card">
          <view class="card-head">
            <view class="checkout-identity">
              <text class="identity-label">平台结算单</text>
              <text class="checkout-no">{{ row.checkoutNo }}</text>
            </view>
            <text class="status-badge" :class="{ 'status-badge--draft': row.planningOnly }">
              {{ aggregateLabel(row.aggregateStatus) }}
            </text>
          </view>

          <view class="card-time">{{ formatTime(row.createTime) }}</view>
          <view class="amount-row">
            <view><text>商品</text><strong>¥{{ money(row.goodsAmount) }}</strong></view>
            <view><text>运费</text><strong>¥{{ money(row.freightAmount) }}</strong></view>
            <view class="pay-amount"><text>规划应付</text><strong>¥{{ money(row.payAmount) }}</strong></view>
          </view>
          <view class="status-line">
            <text>{{ payLabel(row.payStatus) }}</text>
            <text class="status-tech">{{ row.aggregateStatus }} · {{ row.payStatus }}</text>
          </view>
          <view v-if="row.planningOnly || row.payStatus === 'NOT_PAYABLE'" class="planning-note">
            这只是不可支付的拆单规划，不是正式订单。
          </view>
          <view v-if="row.failureReason" class="failure-note">该规划处理未完成，请联系平台客服核对。</view>

          <button class="detail-button" :disabled="state.detailLoading" @tap="toggleDetail(row)">
            {{ state.detailNo === row.checkoutNo ? '收起拆单' : '查看拆单' }}
          </button>

          <view v-if="state.detailNo === row.checkoutNo" class="detail-panel">
            <view v-if="state.detailLoading" class="detail-state">正在加载拆单详情…</view>
            <view v-else-if="state.detailError" class="detail-state detail-state--error">
              <text>{{ state.detailError }}</text>
              <button size="mini" @tap="loadDetail(row.checkoutNo)">重试</button>
            </view>
            <template v-else-if="state.detail && state.detail.checkoutNo === row.checkoutNo">
              <view v-if="state.detail.planningOnly" class="detail-warning">
                当前详情是规划快照，不可支付、不可发货，也不表示任何子订单已经创建。
              </view>
              <view v-if="!state.detail.childPlans.length" class="detail-state">暂无可展示的拆单明细</view>
              <view v-for="plan in state.detail.childPlans" :key="plan.planNo" class="plan-card">
                <view class="plan-head">
                  <view>
                    <text class="plan-shop">{{ plan.shopName }}</text>
                    <text class="plan-owner">{{ plan.tenantName }} · {{ plan.enterpriseName }}</text>
                  </view>
                  <text class="plan-amount">¥{{ money(plan.payAmount) }}</text>
                </view>
                <view class="plan-meta">
                  <text>{{ plan.planNo }}</text>
                  <text>{{ plan.itemCount }} 种 / {{ plan.totalQuantity }} 件</text>
                  <text>{{ plan.planStatus }}</text>
                </view>
                <view v-if="plan.blockReason" class="failure-note">该拆单当前被阻塞，请联系平台客服核对。</view>
                <view v-for="item in plan.items" :key="item.cartItemId" class="plan-item">
                  <image v-if="item.picUrl" :src="sheep.$url.cdn(item.picUrl)" mode="aspectFill" />
                  <view v-else class="item-placeholder">商品</view>
                  <view class="item-main">
                    <text class="item-name">{{ item.spuName }}</text>
                    <text class="item-property">{{ propertyText(item.properties) }}</text>
                    <text class="item-price">¥{{ money(item.unitPrice) }} × {{ item.quantity }}</text>
                  </view>
                  <text class="item-total">¥{{ money(item.goodsAmount) }}</text>
                </view>
              </view>
            </template>
          </view>
        </view>
      </view>

      <view v-if="!state.loading && !state.error && state.total > state.pageSize" class="pagination">
        <button :disabled="state.pageNo <= 1" @tap="changePage(state.pageNo - 1)">上一页</button>
        <text>第 {{ state.pageNo }} / {{ totalPages }} 页</text>
        <button :disabled="state.pageNo >= totalPages" @tap="changePage(state.pageNo + 1)">下一页</button>
      </view>
    </view>
  </s-layout>
</template>

<script setup>
  import { computed, onUnmounted, reactive } from 'vue';
  import { onLoad, onPullDownRefresh, onShow } from '@dcloudio/uni-app';
  import sheep from '@/sheep';
  import PlatformCheckoutApi from '@/sheep/api/trade/platformCheckout';
  import {
    formatPlatformProperties,
    normalizePlatformCheckoutDetail,
    normalizePlatformCheckoutPage,
    platformCheckoutAggregateLabel,
    platformCheckoutPayLabel,
  } from '@/sheep/helper/platform-commerce.mjs';
  import { captureTenantOperation, isTenantOperationCurrent } from '@/sheep/helper/tenant-runtime';

  const state = reactive({
    rows: [],
    total: 0,
    pageNo: 1,
    pageSize: 10,
    loading: false,
    error: '',
    detailNo: '',
    detail: null,
    detailLoading: false,
    detailError: '',
    targetNo: '',
  });
  let alive = true;
  let listSequence = 0;
  let detailSequence = 0;

  const totalPages = computed(() => Math.max(1, Math.ceil(state.total / state.pageSize)));
  const money = (value) => (Math.max(0, Number(value) || 0) / 100).toFixed(2);
  const aggregateLabel = platformCheckoutAggregateLabel;
  const payLabel = platformCheckoutPayLabel;
  const propertyText = formatPlatformProperties;
  const formatTime = (value) => {
    const source = String(value || '').trim();
    return source ? source.replace('T', ' ').replace(/\.\d+$/, '') : '时间待同步';
  };

  function current(operation, expected, type = 'list') {
    return alive && isTenantOperationCurrent(operation)
      && expected === (type === 'detail' ? detailSequence : listSequence);
  }

  function clearDetail() {
    detailSequence++;
    state.detailNo = '';
    state.detail = null;
    state.detailLoading = false;
    state.detailError = '';
  }

  async function loadPage(page = 1) {
    const pageNo = Math.max(1, Number(page) || 1);
    const operation = captureTenantOperation();
    const expected = ++listSequence;
    clearDetail();
    state.loading = true;
    state.error = '';
    try {
      const response = await PlatformCheckoutApi.page(pageNo, state.pageSize);
      if (!current(operation, expected)) return false;
      if (response?.code !== 0 || !response.data) throw new Error(response?.msg || '平台结算记录加载失败');
      const result = normalizePlatformCheckoutPage(response.data);
      state.rows = result.list;
      state.total = result.total;
      state.pageNo = pageNo;
      const target = state.targetNo;
      state.targetNo = '';
      if (target && result.list.some((item) => item.checkoutNo === target)) await loadDetail(target);
      return true;
    } catch (error) {
      if (current(operation, expected)) {
        state.rows = [];
        state.total = 0;
        state.error = error?.message || '平台结算记录加载失败，请稍后重试';
      }
      return false;
    } finally {
      if (current(operation, expected)) state.loading = false;
    }
  }

  async function loadDetail(checkoutNo) {
    const operation = captureTenantOperation();
    const expected = ++detailSequence;
    state.detailNo = checkoutNo;
    state.detail = null;
    state.detailLoading = true;
    state.detailError = '';
    try {
      const response = await PlatformCheckoutApi.get(checkoutNo);
      if (!current(operation, expected, 'detail')) return false;
      if (response?.code !== 0 || !response.data) throw new Error(response?.msg || '拆单详情加载失败');
      state.detail = normalizePlatformCheckoutDetail(response.data, checkoutNo);
      return true;
    } catch (error) {
      if (current(operation, expected, 'detail')) {
        state.detailError = error?.message || '拆单详情加载失败，请稍后重试';
      }
      return false;
    } finally {
      if (current(operation, expected, 'detail')) state.detailLoading = false;
    }
  }

  function toggleDetail(row) {
    if (state.detailNo === row.checkoutNo) {
      clearDetail();
      return;
    }
    loadDetail(row.checkoutNo);
  }

  function changePage(pageNo) {
    if (state.loading || pageNo < 1 || pageNo > totalPages.value) return;
    loadPage(pageNo).then((loaded) => {
      if (loaded) uni.pageScrollTo({ scrollTop: 0, duration: 180 });
    });
  }

  function invalidate() {
    listSequence++;
    clearDetail();
    state.loading = false;
    state.rows = [];
    state.total = 0;
    state.error = '登录状态已变化，请重新加载平台结算记录';
  }

  uni.$on('mall:session-changed', invalidate);
  onLoad((options = {}) => {
    const checkoutNo = String(options.checkoutNo || '').trim();
    state.targetNo = /^[A-Za-z0-9._:-]{1,40}$/.test(checkoutNo) ? checkoutNo : '';
  });
  onUnmounted(() => {
    alive = false;
    listSequence++;
    clearDetail();
    uni.$off('mall:session-changed', invalidate);
  });
  onShow(() => loadPage(1));
  onPullDownRefresh(async () => {
    await loadPage(1);
    uni.stopPullDownRefresh();
  });
</script>

<style lang="scss" scoped>
  .history-page { min-height: 100vh; padding: 24rpx; box-sizing: border-box; }
  .boundary-notice { padding: 24rpx; border: 1rpx solid #f3d39b; border-radius: 16rpx; background: #fff8e8; }
  .notice-title { color: #8a4c0c; font-size: 28rpx; font-weight: 700; }
  .notice-copy { margin-top: 10rpx; color: #9a681e; font-size: 23rpx; line-height: 1.65; }
  .page-head { display: flex; align-items: center; justify-content: space-between; gap: 20rpx; padding: 28rpx 4rpx 20rpx; }
  .page-title { color: #12353b; font-size: 36rpx; font-weight: 750; }
  .page-meta { margin-top: 8rpx; color: #73868a; font-size: 22rpx; }
  .plain-button { flex: none; margin: 0; padding: 0 22rpx; border: 1rpx solid #bcd0ce; background: #fff; color: #0f766e; font-size: 23rpx; line-height: 62rpx; }
  .state-card { padding: 36rpx 24rpx; border-radius: 16rpx; background: #fff; color: #64748b; text-align: center; }
  .state-card button, .detail-state button { margin-top: 18rpx; }.state-error, .detail-state--error { color: #b45309; }
  .history-card { margin-bottom: 22rpx; padding: 24rpx; border: 1rpx solid #e2eae8; border-radius: 18rpx; background: #fff; box-shadow: 0 8rpx 24rpx rgba(15, 54, 60, .055); }
  .card-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 18rpx; }
  .checkout-identity { min-width: 0; }.identity-label { display: block; color: #829194; font-size: 21rpx; }
  .checkout-no { display: block; margin-top: 5rpx; color: #18383e; font-size: 27rpx; font-weight: 700; overflow-wrap: anywhere; }
  .status-badge { flex: none; padding: 7rpx 14rpx; border-radius: 999rpx; background: #e8f3f1; color: #0f766e; font-size: 21rpx; font-weight: 650; }
  .status-badge--draft { background: #fff0d7; color: #a15b0c; }
  .card-time { margin-top: 10rpx; color: #8a999c; font-size: 21rpx; }
  .amount-row { display: grid; grid-template-columns: 1fr 1fr 1.25fr; gap: 10rpx; margin-top: 22rpx; padding: 18rpx; border-radius: 12rpx; background: #f6f9f8; }
  .amount-row view { min-width: 0; }.amount-row text, .amount-row strong { display: block; }
  .amount-row text { color: #78898c; font-size: 20rpx; }.amount-row strong { margin-top: 6rpx; color: #28474c; font-size: 25rpx; }
  .amount-row .pay-amount strong { color: #d64b25; font-size: 28rpx; }
  .status-line { display: flex; justify-content: space-between; gap: 16rpx; margin-top: 18rpx; color: #405b60; font-size: 22rpx; }
  .status-tech { color: #8b989a; font-family: monospace; font-size: 19rpx; text-align: right; }
  .planning-note, .detail-warning { margin-top: 16rpx; padding: 16rpx 18rpx; border-radius: 10rpx; background: #fff7e9; color: #9a5c13; font-size: 22rpx; line-height: 1.55; }
  .failure-note { margin-top: 12rpx; padding: 14rpx 16rpx; border-radius: 10rpx; background: #fff0ed; color: #b0442f; font-size: 21rpx; line-height: 1.5; }
  .detail-button { width: 100%; margin-top: 20rpx; border: 1rpx solid #bbd0cc; border-radius: 10rpx; background: #fff; color: #0f766e; font-size: 24rpx; line-height: 66rpx; }
  .detail-panel { margin-top: 18rpx; padding-top: 18rpx; border-top: 1rpx dashed #d8e2e0; }
  .detail-state { padding: 24rpx 12rpx; color: #718386; font-size: 22rpx; text-align: center; }
  .detail-warning { margin-top: 0; margin-bottom: 16rpx; }
  .plan-card { margin-top: 16rpx; padding: 18rpx; border: 1rpx solid #dfe8e6; border-radius: 14rpx; background: #fbfdfc; }
  .plan-head { display: flex; justify-content: space-between; gap: 16rpx; }.plan-shop, .plan-owner { display: block; }
  .plan-shop { color: #15383e; font-size: 27rpx; font-weight: 700; }.plan-owner { margin-top: 5rpx; color: #75878a; font-size: 20rpx; }
  .plan-amount { flex: none; color: #d64b25; font-size: 27rpx; font-weight: 700; }
  .plan-meta { display: flex; flex-wrap: wrap; gap: 10rpx 18rpx; margin-top: 14rpx; color: #718487; font-size: 19rpx; }
  .plan-item { display: grid; grid-template-columns: 92rpx 1fr auto; gap: 14rpx; align-items: center; margin-top: 16rpx; padding-top: 16rpx; border-top: 1rpx dashed #dde6e4; }
  .plan-item image, .item-placeholder { width: 92rpx; height: 92rpx; border-radius: 9rpx; background: #edf2f1; }
  .item-placeholder { display: flex; align-items: center; justify-content: center; color: #96a5a3; font-size: 20rpx; }
  .item-main { min-width: 0; }.item-main text { display: block; }.item-name { color: #243f44; font-size: 23rpx; font-weight: 650; }
  .item-property { margin-top: 5rpx; color: #849294; font-size: 19rpx; }.item-price { margin-top: 7rpx; color: #64777a; font-size: 20rpx; }
  .item-total { color: #d64b25; font-size: 22rpx; font-weight: 650; }
  .pagination { display: flex; align-items: center; justify-content: center; gap: 22rpx; padding: 12rpx 0 30rpx; }
  .pagination button { margin: 0; padding: 0 24rpx; border: 1rpx solid #c6d6d3; background: #fff; color: #28545a; font-size: 22rpx; line-height: 62rpx; }
  .pagination text { color: #6f8184; font-size: 21rpx; }
</style>
