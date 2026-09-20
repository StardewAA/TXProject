<!-- 收银台 -->
<template>
  <s-layout title="收银台">
    <view class="bg-white ss-modal-box ss-flex-col">
      <!-- 订单信息 -->
      <view class="modal-header ss-flex-col ss-col-center ss-row-center">
        <view class="money-box ss-m-b-20">
          <text class="money-text">{{ fen2yuan(state.orderInfo.price) }}</text>
        </view>
        <view class="time-text">
          <text>{{ payDescText }}</text>
        </view>
      </view>

      <!-- 支付方式 -->
      <view class="modal-content ss-flex-1">
        <view class="pay-title ss-p-l-30 ss-m-y-30">选择支付方式</view>
        <radio-group @change="onTapPay">
          <label class="pay-type-item" v-for="item in state.payMethods" :key="item.title">
            <view
              class="pay-item ss-flex ss-col-center ss-row-between ss-p-x-30 border-bottom"
              :class="{ 'disabled-pay-item': item.disabled }"
            >
              <view class="ss-flex ss-col-center">
                <image
                  class="pay-icon"
                  v-if="item.disabled"
                  :src="sheep.$url.static('/static/img/shop/pay/cod_disabled.png')"
                  mode="aspectFit"
                />
                <image
                  class="pay-icon"
                  v-else
                  :src="sheep.$url.static(item.icon)"
                  mode="aspectFit"
                />
                <text class="pay-title">{{ item.title }}</text>
              </view>
              <view class="check-box ss-flex ss-col-center ss-p-l-10">
                <view class="userInfo-money ss-m-r-10" v-if="item.value === 'wallet'">
                  余额: {{ fen2yuan(userWallet?.balance) }}元
                </view>
                <radio
                  :value="item.value"
                  color="var(--ui-BG-Main)"
                  style="transform: scale(0.8)"
                  :disabled="item.disabled || !paymentAllowed || state.paying"
                  :checked="state.payment === item.value"
                />
              </view>
            </view>
          </label>
        </radio-group>
      </view>

      <!-- 工具 -->
      <view class="modal-footer ss-flex ss-row-center ss-col-center ss-m-t-80 ss-m-b-40">
        <button v-if="state.payStatus === 0" class="ss-reset-button past-due-btn">
          检测支付环境中
        </button>
        <button v-else-if="state.payStatus === -1" class="ss-reset-button past-due-btn" disabled>
          支付单已关闭
        </button>
        <button
          v-else
          class="ss-reset-button save-btn"
          @tap="onPay"
          :disabled="!paymentAllowed || state.paying"
          :class="{ 'disabled-btn': !paymentAllowed || state.paying }"
        >
          立即支付
        </button>
      </view>
      <view class="ss-m-b-30 ss-flex ss-row-center"><button class="ss-reset-button" :disabled="syncing || state.paying" @tap="refresh">刷新支付状态</button></view>
    </view>
  </s-layout>
</template>
<script setup>
  import { computed, reactive } from 'vue';
  import { onLoad } from '@dcloudio/uni-app';
  import sheep from '@/sheep';
  import { fen2yuan } from '@/sheep/hooks/useGoods';
  import PayOrderApi from '@/sheep/api/pay/order';
  import PayChannelApi from '@/sheep/api/pay/channel';
  import { getPayMethods, goPayResult } from '@/sheep/platform/pay';
  import OrderApi from '@/sheep/api/trade/order';
  import { usePaymentDeadline } from '@/sheep/hooks/usePaymentDeadline';
  import { cashierPaymentState, monotonicNow, stampPaymentClock } from '@/sheep/helper/payment-deadline.mjs';

  const userWallet = computed(() => sheep.$store('user').userWallet);

  // 检测支付环境
  const state = reactive({
    orderType: 'goods', // 订单类型; goods - 商品订单, recharge - 充值订单
    orderInfo: {}, // 支付单信息
    payStatus: 0, // 0=检测支付环境, -2=未查询到支付单信息， -1=支付已过期， 1=待支付，2=订单已支付
    payMethods: [], // 可选的支付方式
    payment: '', // 选中的支付方式
    payOrderId: null,
    tradeOrder: null,
    paying: false,
  });
  let channelAppId;
  let successNotified = false;
  const { now, active, syncing, confirmed, refresh } = usePaymentDeadline(
    (time) => [cashierPaymentState(state.orderInfo, state.tradeOrder, state.orderType, time)],
    () => state.payOrderId ? setOrder(state.payOrderId) : false,
  );
  const paymentState = computed(() => cashierPaymentState(state.orderInfo, state.tradeOrder, state.orderType, now.value));
  const paymentAllowed = computed(() => active.value && !syncing.value && confirmed.value
    && state.payStatus === 1 && paymentState.value.canPay);

  const onPay = async () => {
    if (state.paying) return;
    if (!paymentAllowed.value) {
      sheep.$helper.toast(payDescText.value || '支付单信息不可用，请刷新重试');
      return;
    }
    const method = state.payMethods.find((item) => item.value === state.payment && !item.disabled);
    if (!method) {
      sheep.$helper.toast('请选择支付方式');
      return;
    }
    state.paying = true;
    try {
      if (method.value === 'wallet') {
        const confirmed = await new Promise((resolve) => uni.showModal({
          title: '提示', content: '确定要支付吗?',
          success: (result) => resolve(result.confirm), fail: () => resolve(false),
        }));
        if (!confirmed) return;
      }
      // The wallet confirmation may remain open past the deadline. Recheck after it closes.
      if (!(await refresh()) || !paymentAllowed.value) {
        sheep.$helper.toast(payDescText.value || '暂不可支付，请刷新订单');
        return;
      }
      if (!state.payMethods.some((item) => item.value === method.value && !item.disabled)) return;
      const payment = sheep.$platform.pay(method.value, state.orderType, state.orderInfo.id);
      await payment?.pending;
      if (active.value) await refresh();
    } finally {
      state.paying = false;
    }
  };

  // 支付文案提示
  const payDescText = computed(() => {
    if (state.payStatus === 2) {
      return '该订单已支付';
    }
    if (state.payStatus === 1) {
      return paymentState.value.text;
    }
    if (state.payStatus === -2) {
      return '支付状态未确认，请刷新后重试';
    }
    if (state.payStatus === -1) return '支付单已关闭，请返回订单查看最新记录';
    if (state.payStatus === 3) return '该支付单已退款，请返回查看记录';
    return '';
  });

  // 状态转换：payOrder.status => payStatus
  function checkPayStatus() {
    const status = Number(state.orderInfo?.status);
    if (!Number.isFinite(status)) {
      state.payStatus = -2;
      return;
    }
    if (status === 20) { state.payStatus = 3; return; }
    if (status === 10) {
      // 支付成功
      state.payStatus = 2;
      // 跳转回支付成功页
      if (successNotified) return;
      successNotified = true;
      uni.showModal({
        title: '提示',
        content: '订单已支付',
        showCancel: false,
        success: function () {
          goPayResult(state.orderInfo.id, state.orderType);
        },
      });
      return;
    }
    if (status === 30) {
      // 支付关闭
      state.payStatus = -1;
      return;
    }
    state.payStatus = 1; // 待支付
  }

  // 切换支付方式
  function onTapPay(e) {
    if (state.paying || !paymentAllowed.value) return;
    const value = e?.detail?.value;
    if (state.payMethods.some((item) => item.value === value && !item.disabled)) {
      state.payment = value;
    }
  }

  // 设置支付订单信息
  async function setOrder(id) {
    if (!id) {
      state.payStatus = -2;
      return false;
    }

    try {
      const requestedAt = monotonicNow();
      // 获得支付订单信息。request 在网络异常时可能返回 false，不能直接解构。
      const response = await PayOrderApi.getOrder(id, true);
      const data = response?.data;
      if (
        response?.code !== 0 ||
        !data ||
        typeof data !== 'object' ||
        Array.isArray(data) ||
        data.id === undefined ||
        data.id === null ||
        data.status === undefined ||
        data.status === null
      ) {
        state.payStatus = -2;
        return false;
      }
      state.orderInfo = stampPaymentClock(data, monotonicNow(), requestedAt);
      state.tradeOrder = null;
      if (state.orderType !== 'recharge' && Number(data.status) === 0) {
        if (!data.merchantOrderId) { state.payStatus = -2; return false; }
        const tradeRequestedAt = monotonicNow();
        const tradeResponse = await OrderApi.getOrderDetail(data.merchantOrderId, true);
        if (tradeResponse?.code !== 0 || !tradeResponse.data
          || String(tradeResponse.data.payOrderId) !== String(data.id)
          || String(tradeResponse.data.id) !== String(data.merchantOrderId)) {
          state.payStatus = -2;
          return false;
        }
        state.tradeOrder = stampPaymentClock(tradeResponse.data, monotonicNow(), tradeRequestedAt);
      }
      // 设置支付状态
      checkPayStatus();
      // 获得支付方式
      if (Number(data.status) === 0 && (channelAppId !== data.appId || !state.payMethods.length)) {
        await setPayMethods();
      }
      return true;
    } catch (error) {
      state.payStatus = -2;
      state.payMethods = [];
      return false;
    }
  }

  // 获得支付方式
  async function setPayMethods() {
    try {
      const response = await PayChannelApi.getEnableChannelCodeList(state.orderInfo.appId);
      const data = response?.data;
      if (response?.code !== 0 || !Array.isArray(data)) {
        state.payMethods = [];
        return;
      }
      state.payMethods = (getPayMethods(data) || []).filter(
        (item) => item && typeof item === 'object' && item.value,
      );
      channelAppId = state.orderInfo.appId;
      if (!state.payMethods.some((item) => item.value === state.payment && !item.disabled)) {
        state.payment = state.payMethods.find((item) => !item.disabled)?.value || '';
      }
    } catch (error) {
      state.payMethods = [];
      state.payment = '';
    }
  }

  onLoad((options = {}) => {
    if (
      sheep.$platform.name === 'WechatOfficialAccount' &&
      sheep.$platform.os === 'ios' &&
      !sheep.$platform.landingPage.includes('pages/pay/index')
    ) {
      location.reload();
      return;
    }
    // 获得支付订单信息
    state.payOrderId = options?.id;
    if (options?.orderType) {
      state.orderType = options.orderType;
    }
    void refresh();
    // 刷新钱包的缓存
    sheep.$store('user').getWallet();
  });
</script>

<style lang="scss" scoped>
  .pay-icon {
    width: 36rpx;
    height: 36rpx;
    margin-right: 26rpx;
  }

  .ss-modal-box {
    // max-height: 1000rpx;

    .modal-header {
      position: relative;
      padding: 60rpx 20rpx 40rpx;

      .money-text {
        color: $red;
        font-size: 46rpx;
        font-weight: bold;
        font-family: OPPOSANS;

        &::before {
          content: '￥';
          font-size: 30rpx;
        }
      }

      .time-text {
        font-size: 26rpx;
        color: $gray-b;
      }

      .close-icon {
        position: absolute;
        top: 10rpx;
        right: 20rpx;
        font-size: 46rpx;
        opacity: 0.2;
      }
    }

    .modal-content {
      overflow-y: auto;

      .pay-title {
        font-size: 26rpx;
        font-weight: 500;
        color: #333333;
      }

      .pay-tip {
        font-size: 26rpx;
        color: #bbbbbb;
      }

      .pay-item {
        height: 86rpx;
      }
      .disabled-pay-item {
        .pay-title {
          color: #999999;
        }
      }

      .userInfo-money {
        font-size: 26rpx;
        color: #bbbbbb;
        line-height: normal;
      }
    }

    .save-btn {
      width: 710rpx;
      height: 80rpx;
      border-radius: 40rpx;
      background: linear-gradient(90deg, var(--ui-BG-Main), var(--ui-BG-Main-gradient));
      color: $white;
    }
    .disabled-btn {
      background: #e5e5e5;
      color: #999999;
    }

    .past-due-btn {
      width: 710rpx;
      height: 80rpx;
      border-radius: 40rpx;
      background-color: #999;
      color: #fff;
    }
  }
</style>
