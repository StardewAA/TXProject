<template>
  <s-layout title="跨租户结算" :bgStyle="{ color: '#f4f7fb' }">
    <view class="checkout-page">
      <view class="notice-card">
        当前可保存经服务端复核的 DRAFT_ONLY 拆单规划。该记录不可支付，不会创建正式订单、扣减库存或删除购物车商品。
      </view>

      <view class="section-card">
        <view class="section-head">
          <text>选择收货地址</text>
          <button class="text-button" :disabled="state.loading" @tap="loadAddresses">刷新地址</button>
        </view>
        <view class="address-note">地址来自当前平台账号已核验的来源商城，仅提交地址编号给结算服务。</view>
        <view v-if="state.addresses.length" class="address-list">
          <view
            v-for="address in state.addresses"
            :key="address.id"
            class="address-item"
            :class="{ active: state.addressId === address.id }"
            @tap="selectAddress(address.id)"
          >
            <view class="address-contact">
              <text>{{ address.name }}</text>
              <text>{{ address.mobile }}</text>
              <text v-if="address.defaultStatus" class="default-tag">默认</text>
            </view>
            <view class="address-detail">{{ [address.areaName, address.detailAddress].filter(Boolean).join(' ') }}</view>
          </view>
        </view>
        <view v-else-if="!state.loading" class="empty-address">
          暂无可复用收货地址，请先在账号所属商城补充地址后刷新本页。
        </view>
      </view>

      <view v-if="state.error" class="state-card state-error">
        <text>{{ state.error }}</text>
        <button size="mini" :disabled="state.loading" @tap="loadPreview">重新预览</button>
      </view>
      <view v-else-if="state.loading" class="state-card">正在由服务端核验商品与结算金额…</view>

      <view v-if="state.submitError" class="state-card state-error submit-error">
        <text>{{ state.submitError }}</text>
        <text class="retry-tip">若刚才响应中断，可直接再次点击“保存拆单规划”，系统会使用同一幂等凭据安全核实。</text>
      </view>

      <template v-if="state.preview && !state.loading">
        <view class="section-card">
          <view class="section-head"><text>商品清单</text><text>{{ state.preview.totalQuantity }} 件</text></view>
          <view v-for="tenant in state.preview.tenantGroups" :key="tenant.tenantId" class="tenant-preview">
            <view class="tenant-preview-head">
              <text>{{ tenant.tenantName }}</text><text>¥{{ money(tenant.goodsAmount) }}</text>
            </view>
            <view v-for="shop in tenant.shops" :key="shop.shopId" class="shop-preview">
              <view class="shop-preview-head">
                <view><text>{{ shop.shopName }}</text><text>{{ shop.enterpriseName }}</text></view>
                <text>¥{{ money(shop.goodsAmount) }}</text>
              </view>
              <view v-for="item in shop.items" :key="item.cartItemId" class="preview-row">
                <image :src="sheep.$url.cdn(item.picUrl)" mode="aspectFill" />
                <view class="preview-main">
                  <text>{{ item.spuName || '商品' }}</text>
                  <text>{{ formatPlatformProperties(item.properties) }}</text>
                  <text>¥{{ money(item.unitPrice) }} × {{ item.quantity }}</text>
                </view>
                <text class="preview-price">¥{{ money(item.goodsAmount) }}</text>
              </view>
            </view>
          </view>
        </view>

        <view v-if="state.preview.failedItems.length" class="section-card failed-card">
          <view class="section-title">未通过校验的商品</view>
          <view v-for="item in state.preview.failedItems" :key="item.cartItemId" class="reason-row">
            购物车项 {{ item.cartItemId }}：{{ item.reason }}
          </view>
        </view>

        <view v-if="state.preview.blockingReasons.length" class="section-card warning-card">
          <view class="section-title">正式成交限制</view>
          <view v-for="reason in state.preview.blockingReasons" :key="reason" class="reason-row">{{ reason }}</view>
        </view>

        <view class="section-card amount-card">
          <view><text>商品金额</text><text>¥{{ money(state.preview.goodsAmount) }}</text></view>
          <view><text>运费</text><text>+ ¥{{ money(state.preview.freightAmount) }}</text></view>
          <view><text>优惠</text><text>- ¥{{ money(state.preview.discountAmount) }}</text></view>
          <view class="pay-amount"><text>预览应付</text><text>¥{{ money(state.preview.payAmount) }}</text></view>
        </view>
      </template>

      <su-fixed bottom placeholder>
        <view class="checkout-footer">
          <button class="back-button" :disabled="state.submitting" @tap="sheep.$router.go(platformCartPath())">返回购物车</button>
          <button
            class="submit-button"
            :disabled="state.loading || state.submitting || !state.addressId || !state.preview?.validationPassed"
            @tap="savePlan"
          >
            {{ state.submitting ? '正在保存…' : '保存拆单规划' }}
          </button>
        </view>
      </su-fixed>
    </view>
  </s-layout>
</template>

<script setup>
  import { onUnmounted, reactive } from 'vue';
  import { onLoad } from '@dcloudio/uni-app';
  import sheep from '@/sheep';
  import AddressApi from '@/sheep/api/member/address';
  import PlatformCheckoutApi from '@/sheep/api/trade/platformCheckout';
  import {
    formatPlatformProperties,
    normalizeCartItemIds,
    normalizePlatformCheckoutDetail,
    normalizePlatformPreview,
    openPlatformCheckoutIntent,
    platformCartPath,
    platformCheckoutHistoryPath,
  } from '@/sheep/helper/platform-commerce.mjs';
  import {
    captureTenantOperation,
    captureTenantRequest,
    isTenantOperationCurrent,
  } from '@/sheep/helper/tenant-runtime';

  const state = reactive({
    cartItemIds: [],
    addresses: [],
    addressId: null,
    preview: null,
    loading: false,
    error: '',
    submitting: false,
    submitError: '',
  });
  let alive = true;
  let sequence = 0;
  let submitSequence = 0;
  let pageOperation = captureTenantOperation();
  const current = (operation = pageOperation, expected = sequence) =>
    alive && expected === sequence && isTenantOperationCurrent(operation);
  const submitCurrent = (operation, expected) =>
    alive && expected === submitSequence && isTenantOperationCurrent(operation);
  const money = (value) => (Math.max(0, Number(value) || 0) / 100).toFixed(2);
  const intentStorage = Object.freeze({
    getItem: (key) => uni.getStorageSync(key) || null,
    setItem: (key, value) => uni.setStorageSync(key, value),
    removeItem: (key) => uni.removeStorageSync(key),
  });

  function normalizeAddress(value) {
    const id = Number(value?.id);
    if (!Number.isSafeInteger(id) || id <= 0) return null;
    return {
      ...value,
      id,
      name: String(value?.name || '').trim() || '收货人',
      mobile: String(value?.mobile || '').trim(),
      areaName: String(value?.areaName || '').trim(),
      detailAddress: String(value?.detailAddress || '').trim(),
      defaultStatus: value?.defaultStatus === true,
    };
  }

  async function loadAddresses() {
    if (state.submitting) return;
    pageOperation = captureTenantOperation();
    const operation = pageOperation;
    const expected = ++sequence;
    state.loading = true;
    state.error = '';
    state.submitError = '';
    state.preview = null;
    try {
      const response = await AddressApi.getPlatformAddressList();
      if (!current(operation, expected)) return;
      if (response?.code !== 0 || !Array.isArray(response.data)) throw new Error(response?.msg || '收货地址加载失败');
      state.addresses = response.data.map(normalizeAddress).filter(Boolean);
      if (!state.addresses.some((item) => item.id === state.addressId)) {
        state.addressId = (state.addresses.find((item) => item.defaultStatus) || state.addresses[0])?.id || null;
      }
      state.loading = false;
      if (state.addressId) await loadPreview();
    } catch (error) {
      if (current(operation, expected)) state.error = error?.message || '收货地址加载失败，请稍后重试';
    } finally {
      if (current(operation, expected)) state.loading = false;
    }
  }

  async function loadPreview() {
    if (!state.addressId || !state.cartItemIds.length || state.loading || state.submitting
        || !isTenantOperationCurrent(pageOperation)) return;
    const operation = pageOperation;
    const expected = ++sequence;
    state.loading = true;
    state.error = '';
    state.submitError = '';
    state.preview = null;
    try {
      const response = await PlatformCheckoutApi.preview(state.cartItemIds, state.addressId);
      if (!current(operation, expected)) return;
      if (response?.code !== 0 || !response.data) throw new Error(response?.msg || '平台结算预览失败');
      state.preview = normalizePlatformPreview(response.data, state.cartItemIds);
    } catch (error) {
      if (current(operation, expected)) state.error = error?.message || '平台结算预览失败，请返回购物车核对';
    } finally {
      if (current(operation, expected)) state.loading = false;
    }
  }

  function selectAddress(id) {
    if (state.loading || state.submitting || state.addressId === id) return;
    state.addressId = id;
    loadPreview();
  }

  async function savePlan() {
    if (state.loading || state.submitting || !state.addressId
        || !state.cartItemIds.length || !state.preview?.validationPassed) return;
    const operation = captureTenantOperation();
    const expected = ++submitSequence;
    const identity = captureTenantRequest();
    let intent;
    state.submitting = true;
    state.submitError = '';
    try {
      intent = openPlatformCheckoutIntent(intentStorage, {
        carrierTenantId: identity.platformCarrierTenantId || identity.accountTenantId,
        memberId: identity.platformMemberId || identity.userId,
      }, state.cartItemIds, state.addressId);
      const response = await PlatformCheckoutApi.submit(
        [...state.cartItemIds], state.addressId, intent.idempotencyKey,
      );
      if (!submitCurrent(operation, expected)) return;
      if (response?.code !== 0 || !response.data) {
        if (Number(response?.code) === 1011010006) intent.clear();
        throw new Error(response?.msg || '拆单规划保存结果未确认');
      }
      const detail = normalizePlatformCheckoutDetail(response.data, response.data.checkoutNo);
      if (!detail.planningOnly || detail.payable || detail.aggregateStatus !== 'DRAFT_ONLY'
          || detail.payStatus !== 'NOT_PAYABLE') throw new Error('服务端返回的拆单规划状态异常，请到记录页核实');
      // Only a verified success releases this intent. Unknown responses retain it for an idempotent retry.
      intent.clear();
      await new Promise((resolve) => uni.showModal({
        title: '拆单规划已保存',
        content: `结算单号 ${detail.checkoutNo}。该记录不可支付，不是正式订单，平台购物车仍会保留。`,
        showCancel: false,
        confirmText: '查看记录',
        complete: resolve,
      }));
      if (submitCurrent(operation, expected)) {
        sheep.$router.redirect(platformCheckoutHistoryPath(detail.checkoutNo));
      }
    } catch (error) {
      if (submitCurrent(operation, expected)) {
        state.submitError = error?.message || '拆单规划保存结果未确认，请直接重试核实';
      }
    } finally {
      if (submitCurrent(operation, expected)) state.submitting = false;
    }
  }

  function invalidate() {
    sequence++;
    submitSequence++;
    state.loading = false;
    state.submitting = false;
    state.preview = null;
    state.error = '登录状态已变化，请返回平台购物车重新进入';
  }
  uni.$on('mall:session-changed', invalidate);
  onUnmounted(() => {
    alive = false;
    sequence++;
    submitSequence++;
    uni.$off('mall:session-changed', invalidate);
  });

  onLoad((options = {}) => {
    try {
      state.cartItemIds = normalizeCartItemIds(options.cartItemIds);
      loadAddresses();
    } catch (error) {
      state.error = error?.message || '结算商品清单无效';
    }
  });
</script>

<style lang="scss" scoped>
  .checkout-page { padding: 24rpx 24rpx 150rpx; }
  .notice-card { margin-bottom: 22rpx; padding: 22rpx 24rpx; border: 1rpx solid #f1c98d; border-radius: 14rpx; background: #fff8e8; color: #805315; font-size: 24rpx; line-height: 1.65; }
  .section-card { margin-bottom: 22rpx; padding: 24rpx; border-radius: 16rpx; background: #fff; box-shadow: 0 8rpx 28rpx rgba(15, 54, 60, .06); }
  .section-head { display: flex; justify-content: space-between; align-items: center; color: #173b42; font-size: 30rpx; font-weight: 700; }
  .text-button { margin: 0; padding: 0; border: 0; background: transparent; color: #0f766e; font-size: 24rpx; line-height: 48rpx; }
  .address-note { margin-top: 10rpx; color: #7b8e91; font-size: 22rpx; line-height: 1.5; }
  .address-list { margin-top: 18rpx; }.address-item { margin-top: 14rpx; padding: 20rpx; border: 2rpx solid #e4ecea; border-radius: 12rpx; }
  .address-item.active { border-color: #0f766e; background: #f1faf8; }.address-contact { display: flex; align-items: center; gap: 16rpx; color: #1d353a; font-size: 26rpx; font-weight: 600; }
  .default-tag { padding: 3rpx 10rpx; border-radius: 6rpx; background: #0f766e; color: #fff; font-size: 19rpx; }
  .address-detail { margin-top: 10rpx; color: #64748b; font-size: 24rpx; line-height: 1.5; }.empty-address { margin-top: 20rpx; color: #9a670e; font-size: 24rpx; line-height: 1.6; }
  .state-card { margin-bottom: 22rpx; padding: 34rpx; border-radius: 14rpx; background: #fff; color: #64748b; text-align: center; }.state-error { color: #b45309; }.state-error button { margin-top: 18rpx; }
  .submit-error { text-align: left; }.retry-tip { display: block; margin-top: 10rpx; color: #8a6a35; font-size: 21rpx; line-height: 1.55; }
  .tenant-preview { overflow: hidden; margin-top: 20rpx; border: 1rpx solid #dce7e5; border-radius: 12rpx; }
  .tenant-preview-head { display: flex; justify-content: space-between; padding: 18rpx 20rpx; background: #123f46; color: #fff; font-size: 25rpx; font-weight: 650; }
  .shop-preview + .shop-preview { border-top: 10rpx solid #f2f5f5; }.shop-preview-head { display: flex; justify-content: space-between; align-items: center; padding: 16rpx 20rpx; background: #f6f9f8; }
  .shop-preview-head view text { display: block; }.shop-preview-head view text:first-child { color: #25434a; font-size: 25rpx; font-weight: 600; }.shop-preview-head view text:last-child { margin-top: 4rpx; color: #829194; font-size: 20rpx; }
  .preview-row { display: grid; grid-template-columns: 110rpx 1fr auto; gap: 16rpx; align-items: center; padding: 18rpx 20rpx; border-top: 1rpx solid #edf2f1; }.preview-row image { width: 110rpx; height: 110rpx; border-radius: 8rpx; background: #edf2f1; }
  .preview-main text { display: block; }.preview-main text:first-child { color: #20383d; font-size: 25rpx; font-weight: 600; }.preview-main text:not(:first-child) { margin-top: 6rpx; color: #738489; font-size: 21rpx; }.preview-price { color: #df421f; font-size: 25rpx; font-weight: 700; }
  .failed-card { border-left: 6rpx solid #c2412d; }.warning-card { border-left: 6rpx solid #d49b2d; }.section-title { color: #263e44; font-size: 28rpx; font-weight: 700; }.reason-row { margin-top: 12rpx; color: #805315; font-size: 23rpx; line-height: 1.5; }
  .amount-card > view { display: flex; justify-content: space-between; margin: 14rpx 0; color: #617277; font-size: 25rpx; }.amount-card .pay-amount { margin-top: 20rpx; padding-top: 20rpx; border-top: 1rpx solid #e2e9e8; color: #152f35; font-size: 29rpx; font-weight: 700; }.pay-amount text:last-child { color: #df421f; font-size: 36rpx; }
  .checkout-footer { display: flex; gap: 18rpx; padding: 18rpx 24rpx; border-top: 1rpx solid #e3eae9; background: #fff; }.checkout-footer button { flex: 1; margin: 0; border-radius: 40rpx; font-size: 25rpx; line-height: 78rpx; }
  .back-button { border: 1rpx solid #b9cecb; background: #fff; color: #28535a; }.submit-button { border: 0; background: #0f766e; color: #fff; }.submit-button[disabled] { background: #a7b6b5; }
</style>
