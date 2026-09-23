<template>
  <s-layout title="平台购物车" :bgStyle="{ color: '#f4f7fb' }">
    <view class="platform-cart-page">
      <view class="page-head">
        <view>
          <view class="page-title">跨租户采购清单</view>
          <view class="page-tip">商品按租户和店铺分组，结算时由服务端重新核验归属、价格与可售状态</view>
        </view>
        <button class="plain-button" :disabled="state.loading || state.busy" @tap="loadCart">刷新</button>
      </view>

      <view v-if="state.error" class="state-card state-error">
        <text>{{ state.error }}</text>
        <button size="mini" :disabled="state.loading || state.busy" @tap="loadCart">重新加载</button>
      </view>
      <view v-else-if="state.loading" class="state-card">正在加载平台购物车…</view>
      <s-empty
        v-else-if="!state.cart.rows.length"
        icon="/static/cart-empty.png"
        text="平台购物车还是空的"
        showAction
        actionText="浏览平台商品"
        actionUrl="/pages/goods/list?portal=1"
      />

      <view v-else class="cart-groups">
        <view v-for="tenant in state.cart.tenantGroups" :key="tenant.tenantId" class="tenant-card">
          <view class="tenant-head">
            <text class="tenant-name">{{ tenant.tenantName }}</text>
            <text class="tenant-meta">租户商品</text>
          </view>
          <view v-for="shop in tenant.shops" :key="shop.shopId" class="shop-block">
            <view class="shop-head">
              <view>
                <text class="shop-name">{{ shop.shopName }}</text>
                <text class="enterprise-name">{{ shop.enterpriseName }}</text>
              </view>
            </view>
            <view v-for="item in shop.items" :key="item.id" class="cart-row" :class="{ invalid: !item.valid }">
              <radio
                class="row-check"
                :checked="item.valid && item.selected"
                :disabled="state.busy || !item.valid"
                color="#0f766e"
                @tap.stop="toggleItem(item)"
              />
              <image class="goods-image" :src="sheep.$url.cdn(item.picUrl)" mode="aspectFill" />
              <view class="goods-main">
                <text class="goods-name">{{ item.spuName }}</text>
                <text class="goods-sku">{{ formatPlatformProperties(item.properties) }}</text>
                <view v-if="!item.valid" class="invalid-reason">{{ item.invalidReason }}</view>
                <view class="goods-bottom">
                  <text class="goods-price">¥{{ money(item.price) }}</text>
                  <su-number-box
                    :modelValue="item.quantity"
                    :min="1"
                    :max="99999"
                    :disabled="state.busy || !item.valid"
                    @change="updateQuantity($event, item)"
                  />
                </view>
                <button class="delete-button" :disabled="state.busy" @tap="removeItem(item)">删除</button>
              </view>
            </view>
          </view>
        </view>
      </view>

      <su-fixed v-if="state.cart.rows.length" bottom placeholder>
        <view class="cart-footer">
          <label class="select-all" @tap="toggleAll">
            <radio :checked="allSelected" :disabled="state.busy || !validRows.length" color="#0f766e" @tap.stop="toggleAll" />
            <text>全选有效商品</text>
          </label>
          <view class="total-box">
            <text>合计</text>
            <text class="total-price">¥{{ money(selectedAmount) }}</text>
          </view>
          <button class="checkout-button" :disabled="state.busy || !selectedIds.length" @tap="checkout">
            去预览（{{ selectedIds.length }}）
          </button>
        </view>
      </su-fixed>
    </view>
  </s-layout>
</template>

<script setup>
  import { computed, onUnmounted, reactive } from 'vue';
  import { onShow } from '@dcloudio/uni-app';
  import sheep from '@/sheep';
  import PlatformCartApi from '@/sheep/api/trade/platformCart';
  import {
    formatPlatformProperties,
    normalizePlatformCart,
    platformCheckoutPath,
    platformSelectedIds,
  } from '@/sheep/helper/platform-commerce.mjs';
  import { captureTenantOperation, isTenantOperationCurrent } from '@/sheep/helper/tenant-runtime';

  const state = reactive({
    cart: normalizePlatformCart({ tenantGroups: [] }),
    loading: false,
    busy: false,
    error: '',
  });
  let sequence = 0;
  let pageOperation = captureTenantOperation();
  const current = (operation = pageOperation, expected = sequence) =>
    expected === sequence && isTenantOperationCurrent(operation);

  const validRows = computed(() => state.cart.rows.filter((item) => item.valid));
  const selectedIds = computed(() => platformSelectedIds(state.cart));
  const allSelected = computed(() => validRows.value.length > 0
    && validRows.value.every((item) => item.selected));
  const selectedAmount = computed(() => state.cart.rows
    .filter((item) => item.valid && item.selected)
    .reduce((total, item) => total + item.price * item.quantity, 0));
  const money = (value) => (Math.max(0, Number(value) || 0) / 100).toFixed(2);

  async function loadCart() {
    if (state.busy) return false;
    pageOperation = captureTenantOperation();
    const operation = pageOperation;
    const expected = ++sequence;
    state.loading = true;
    state.error = '';
    try {
      const response = await PlatformCartApi.getList();
      if (!current(operation, expected)) return false;
      if (response?.code !== 0 || !response.data) throw new Error(response?.msg || '平台购物车加载失败');
      state.cart = normalizePlatformCart(response.data);
      return true;
    } catch (error) {
      if (current(operation, expected)) state.error = error?.message || '平台购物车加载失败，请稍后重试';
      return false;
    } finally {
      if (current(operation, expected)) state.loading = false;
    }
  }

  async function mutate(action) {
    if (state.busy || state.loading || !current()) return false;
    const operation = pageOperation;
    const expected = sequence;
    state.busy = true;
    try {
      const response = await action();
      if (!current(operation, expected)) return false;
      if (response?.code !== 0) throw new Error(response?.msg || '购物车操作失败');
      state.busy = false;
      return await loadCart();
    } catch (error) {
      if (current(operation, expected)) state.error = error?.message || '操作结果未确认，请刷新购物车核对';
      return false;
    } finally {
      if (current(operation, expected)) state.busy = false;
    }
  }

  function toggleItem(item) {
    if (!item?.valid || state.busy) return;
    mutate(() => PlatformCartApi.updateSelected([item.id], !item.selected));
  }

  function toggleAll() {
    if (state.busy || !validRows.value.length) return;
    mutate(() => PlatformCartApi.updateSelected(validRows.value.map((item) => item.id), !allSelected.value));
  }

  function updateQuantity(value, item) {
    const quantity = Number(value);
    if (state.busy || !item?.valid || !Number.isSafeInteger(quantity)
        || quantity < 1 || quantity > 99999 || quantity === item.quantity) return;
    mutate(() => PlatformCartApi.updateCount(item.id, quantity));
  }

  async function removeItem(item) {
    if (state.busy || !item?.id) return;
    const confirmed = await new Promise((resolve) => uni.showModal({
      title: '删除商品',
      content: `确定从平台购物车删除“${item.spuName}”吗？`,
      success: (result) => resolve(result.confirm === true),
      fail: () => resolve(false),
    }));
    if (confirmed) mutate(() => PlatformCartApi.delete([item.id]));
  }

  function checkout() {
    if (state.busy || !selectedIds.value.length) return;
    sheep.$router.go(platformCheckoutPath(selectedIds.value));
  }

  function invalidate() {
    sequence++;
    state.busy = false;
    state.loading = false;
    state.error = '登录状态已变化，请重新加载平台购物车';
  }
  uni.$on('mall:session-changed', invalidate);
  onUnmounted(() => uni.$off('mall:session-changed', invalidate));
  onShow(loadCart);
</script>

<style lang="scss" scoped>
  .platform-cart-page { padding: 24rpx 24rpx 150rpx; }
  .page-head { display: flex; justify-content: space-between; gap: 24rpx; align-items: flex-start; margin-bottom: 22rpx; }
  .page-title { color: #102f36; font-size: 38rpx; font-weight: 700; }
  .page-tip { max-width: 570rpx; margin-top: 10rpx; color: #64748b; font-size: 24rpx; line-height: 1.6; }
  .plain-button { flex: none; margin: 0; padding: 0 24rpx; border: 1rpx solid #bdd3d0; background: #fff; color: #0f766e; font-size: 24rpx; line-height: 64rpx; }
  .state-card { padding: 34rpx; border-radius: 14rpx; background: #fff; color: #64748b; text-align: center; }
  .state-error { color: #b45309; }.state-error button { margin-top: 18rpx; }
  .tenant-card { overflow: hidden; margin-bottom: 24rpx; border-radius: 16rpx; background: #fff; box-shadow: 0 8rpx 28rpx rgba(15, 54, 60, .07); }
  .tenant-head { display: flex; justify-content: space-between; padding: 22rpx 24rpx; background: #123f46; color: #fff; }
  .tenant-name { font-size: 30rpx; font-weight: 700; }.tenant-meta { color: #b9d8d4; font-size: 22rpx; }
  .shop-block + .shop-block { border-top: 12rpx solid #f1f5f4; }
  .shop-head { padding: 18rpx 24rpx; border-bottom: 1rpx solid #e5ecea; background: #f8fbfa; }
  .shop-name, .enterprise-name { display: block; }.shop-name { color: #183c42; font-size: 28rpx; font-weight: 650; }
  .enterprise-name { margin-top: 4rpx; color: #78908d; font-size: 22rpx; }
  .cart-row { display: grid; grid-template-columns: 54rpx 142rpx 1fr; gap: 16rpx; padding: 22rpx 20rpx; border-bottom: 1rpx solid #edf1f1; }
  .cart-row.invalid { background: #fafafa; }.row-check { align-self: center; transform: scale(.82); }
  .goods-image { width: 142rpx; height: 142rpx; border-radius: 10rpx; background: #eef2f2; }
  .goods-main { min-width: 0; }.goods-name { display: block; color: #172b31; font-size: 28rpx; font-weight: 600; line-height: 1.45; }
  .goods-sku { display: block; margin-top: 8rpx; color: #7a8b8e; font-size: 22rpx; }
  .invalid-reason { margin-top: 10rpx; padding: 10rpx 12rpx; border-radius: 8rpx; background: #fff0ed; color: #b4432f; font-size: 22rpx; }
  .goods-bottom { display: flex; justify-content: space-between; align-items: center; margin-top: 18rpx; }
  .goods-price { color: #df421f; font-size: 30rpx; font-weight: 700; }
  .delete-button { margin: 12rpx 0 0 auto; padding: 0; border: 0; background: transparent; color: #7c8b8e; font-size: 22rpx; line-height: 42rpx; }
  .cart-footer { display: flex; align-items: center; min-height: 112rpx; padding: 14rpx 20rpx; border-top: 1rpx solid #e7eceb; background: #fff; }
  .select-all { display: flex; align-items: center; color: #334e53; font-size: 24rpx; }.select-all radio { transform: scale(.8); }
  .total-box { flex: 1; margin-left: 16rpx; text-align: right; color: #64748b; font-size: 22rpx; }
  .total-price { display: block; color: #df421f; font-size: 30rpx; font-weight: 700; }
  .checkout-button { margin: 0 0 0 18rpx; padding: 0 24rpx; border: 0; border-radius: 38rpx; background: #0f766e; color: #fff; font-size: 24rpx; line-height: 76rpx; }
  .checkout-button[disabled] { background: #aab8b7; }
</style>
