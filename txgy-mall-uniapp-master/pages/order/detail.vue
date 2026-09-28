<!-- 订单详情 -->
<template>
  <s-layout title="订单详情" class="index-wrap" navbar="inner">
    <view
      class="state-box ss-flex-col ss-col-center ss-row-right"
      :style="[
        {
          marginTop: '-' + Number(statusBarHeight + 88) + 'rpx',
          paddingTop: Number(statusBarHeight + 88) + 'rpx',
        },
      ]"
    >
      <view class="ss-flex ss-m-t-32 ss-m-b-20">
        <!-- 待支付 -->
        <image
          v-if="state.orderInfo.status === 0"
          class="state-img"
          :src="sheep.$url.static('/static/img/shop/order/no_pay.png')"
        />
        <!-- 待发货 -->
        <image
          v-if="state.orderInfo.status === 10"
          class="state-img"
          :src="sheep.$url.static('/static/img/shop/order/order_loading.png')"
        />
        <!-- 已完成 -->
        <image
          v-else-if="state.orderInfo.status === 30"
          class="state-img"
          :src="sheep.$url.static('/static/img/shop/order/order_success.png')"
        />
        <!-- 已关闭 -->
        <image
          v-else-if="state.orderInfo.status === 40"
          class="state-img"
          :src="sheep.$url.static('/static/img/shop/order/order_close.png')"
        />
        <!-- 已发货 -->
        <image
          v-else-if="state.orderInfo.status === 20"
          class="state-img"
          :src="sheep.$url.static('/static/img/shop/order/order_express.png')"
        />
        <view class="ss-font-30">{{ formatOrderStatus(state.orderInfo) }}</view>
      </view>
      <view class="ss-font-26 ss-m-x-20 ss-m-b-70">
        {{ state.orderInfo.status === 0 ? paymentState.text : formatOrderStatusDescription(state.orderInfo) }}
      </view>
    </view>

    <view v-if="state.orderInfo.status === 0 || !confirmed" class="ss-m-20 ss-flex ss-row-between">
      <text v-if="!confirmed && !syncing">订单状态暂未确认，请刷新后重试</text>
      <button class="ss-reset-button" :disabled="syncing" @tap="refresh">刷新订单状态</button>
    </view>
    <!-- 收货地址 -->
    <view v-if="state.orderInfo.erpInventory?.length" style="margin:24rpx;padding:24rpx;background:#fff8e8;border-radius:12rpx">
      <view style="font-weight:bold">历史履约记录</view>
      <view style="margin-top:8rpx;color:#8a7350">以下是切换为 WMS 直连前的记录，仅供查询。</view>
      <view v-for="entry in state.orderInfo.erpInventory" :key="entry.id" style="margin-top:16rpx">
        <text>{{ ({RESERVE:'库存预占',RELEASE:'释放预占',OUTBOUND:'出库',RETURN:'退货入库'})[entry.operation] }}：
          {{ ({PENDING:'历史待处理',CONFIRMED:'历史已确认',REJECTED:'历史处理失败'})[entry.status] }}</text>
        <view>{{ entry.message }}</view>
      </view>
    </view>
    <view class="order-address-box" v-if="state.orderInfo.receiverAreaId > 0">
      <view class="ss-flex ss-col-center">
        <text class="address-username">
          {{ state.orderInfo.receiverName }}
        </text>
        <text class="address-phone">{{ state.orderInfo.receiverMobile }}</text>
      </view>
      <view class="address-detail">
        {{ state.orderInfo.receiverAreaName }} {{ state.orderInfo.receiverDetailAddress }}
      </view>
      <view v-if="receiverLocation" class="address-detail">{{ receiverLocation }}</view>
      <button v-if="canUpdateAddress" class="ss-reset-button update-address-btn" @tap="selectNewAddress">
        修改收货地址
      </button>
    </view>

    <view
      class="detail-goods"
      :style="[{ marginTop: state.orderInfo.receiverAreaId > 0 ? '0' : '-40rpx' }]"
    >
      <!-- 订单信 -->
      <view
        class="order-list"
        v-for="item in state.orderInfo.items || []"
        :key="item.id || item.goods_id || item.skuId"
      >
        <view class="order-card">
          <s-goods-item
            @tap="onGoodsDetail(item.spuId)"
            :img="item.picUrl"
            :title="item.spuName"
            :skuText="(item.properties || []).map((property) => property.valueName).join(' ')"
            :price="item.price"
            :num="item.count"
          >
            <template #tool>
              <view class="ss-flex">
                <button
                  class="ss-reset-button apply-btn"
                  v-if="[10, 20, 30].includes(state.orderInfo.status) && item.afterSaleStatus === 0"
                  @tap.stop="
                    sheep.$router.go('/pages/order/aftersale/apply', {
                      orderId: state.orderInfo.id,
                      itemId: item.id,
                    })
                  "
                >
                  申请售后
                </button>
                <button
                  class="ss-reset-button apply-btn"
                  v-if="item.afterSaleStatus === 10"
                  @tap.stop="
                    sheep.$router.go('/pages/order/aftersale/detail', {
                      id: item.afterSaleId,
                    })
                  "
                >
                  退款中
                </button>
                <button
                  class="ss-reset-button apply-btn"
                  v-if="item.afterSaleStatus === 20"
                  @tap.stop="
                    sheep.$router.go('/pages/order/aftersale/detail', {
                      id: item.afterSaleId,
                    })
                  "
                >
                  退款成功
                </button>
              </view>
            </template>
            <template #priceSuffix>
              <button class="ss-reset-button tag-btn" v-if="item.status_text">
                {{ item.status_text }}
              </button>
            </template>
          </s-goods-item>
        </view>
      </view>
    </view>

    <!--  自提核销  -->
    <PickUpVerify
      :order-info="state.orderInfo"
      :systemStore="systemStore"
      ref="pickUpVerifyRef"
    ></PickUpVerify>

    <!-- 订单信息 -->
    <view class="notice-box">
      <view class="notice-box__content">
        <view class="notice-item--center">
          <view class="ss-flex ss-flex-1">
            <text class="title">订单编号：</text>
            <text class="detail">{{ state.orderInfo.no }}</text>
          </view>
          <button class="ss-reset-button copy-btn" @tap="onCopy">复制</button>
        </view>
        <view class="notice-item">
          <text class="title">下单时间：</text>
          <text class="detail">
            {{ sheep.$helper.timeFormat(state.orderInfo.createTime, 'yyyy-mm-dd hh:MM:ss') }}
          </text>
        </view>
        <view class="notice-item" v-if="state.orderInfo.payTime">
          <text class="title">支付时间：</text>
          <text class="detail">
            {{ sheep.$helper.timeFormat(state.orderInfo.payTime, 'yyyy-mm-dd hh:MM:ss') }}
          </text>
        </view>
        <view class="notice-item">
          <text class="title">店铺：</text>
          <text class="detail">{{ state.orderInfo.shopName || '平台自营' }}</text>
          <button v-if="state.orderInfo.id" size="mini" @tap="contactService">{{ state.orderInfo.shopId > 0 ? '联系店铺客服' : '联系平台客服' }}</button>
        </view>
        <view class="notice-item">
          <text class="title">支付方式：</text>
          <text class="detail">{{ state.orderInfo.payChannelName || '-' }}</text>
        </view>
      </view>
    </view>

    <!-- 价格信息 -->
    <view class="order-price-box">
      <view class="notice-item ss-flex ss-row-between">
        <text class="title">商品总额</text>
        <view class="ss-flex">
          <text class="detail">￥{{ fen2yuan(state.orderInfo.totalPrice) }}</text>
        </view>
      </view>
      <view class="notice-item ss-flex ss-row-between">
        <text class="title">运费</text>
        <text class="detail">￥{{ fen2yuan(state.orderInfo.deliveryPrice) }}</text>
      </view>
      <view class="notice-item ss-flex ss-row-between" v-if="state.orderInfo.couponPrice > 0">
        <text class="title">优惠劵金额</text>
        <text class="detail">-¥{{ fen2yuan(state.orderInfo.couponPrice) }}</text>
      </view>
      <view class="notice-item ss-flex ss-row-between" v-if="state.orderInfo.pointPrice > 0">
        <text class="title">积分抵扣</text>
        <text class="detail">-¥{{ fen2yuan(state.orderInfo.pointPrice) }}</text>
      </view>
      <view class="notice-item ss-flex ss-row-between" v-if="state.orderInfo.discountPrice > 0">
        <text class="title">活动优惠</text>
        <text class="detail">¥{{ fen2yuan(state.orderInfo.discountPrice) }}</text>
      </view>
      <view class="notice-item ss-flex ss-row-between" v-if="state.orderInfo.vipPrice > 0">
        <text class="title">会员优惠</text>
        <text class="detail">-¥{{ fen2yuan(state.orderInfo.vipPrice) }}</text>
      </view>
      <view class="notice-item all-rpice-item ss-flex ss-m-t-20">
        <text class="title">{{ state.orderInfo.payStatus ? '已付款' : '需付款' }}</text>
        <text class="detail all-price">￥{{ fen2yuan(state.orderInfo.payPrice) }}</text>
      </view>
      <view
        class="notice-item all-rpice-item ss-flex ss-m-t-20"
        v-if="state.orderInfo.refundPrice > 0"
      >
        <text class="title">已退款</text>
        <text class="detail all-price">￥{{ fen2yuan(state.orderInfo.refundPrice) }}</text>
      </view>
    </view>

    <!-- 底部按钮 -->
    <!-- TODO: 查看物流、等待成团、评价完后返回页面没刷新页面 -->
    <su-fixed bottom placeholder bg="bg-white" v-if="state.orderInfo.buttons?.length">
      <view class="footer-box ss-flex ss-col-center ss-row-right">
        <button
          class="ss-reset-button cancel-btn"
          v-if="state.orderInfo.buttons?.includes('cancel')"
          @tap="onCancel(state.orderInfo.id)"
        >
          取消订单
        </button>
        <button
          class="ss-reset-button pay-btn ui-BG-Main-Gradient"
          v-if="state.orderInfo.buttons?.includes('pay') && state.orderInfo.payOrderId"
          :disabled="!canPay"
          @tap="onPay(state.orderInfo.payOrderId)"
        >
          继续支付
        </button>
        <button
          class="ss-reset-button cancel-btn"
          v-if="state.orderInfo.buttons?.includes('combination')"
          @tap="
            sheep.$router.go('/pages/activity/groupon/detail', {
              id: state.orderInfo.combinationRecordId,
            })
          "
        >
          拼团详情
        </button>
        <button
          class="ss-reset-button cancel-btn"
          v-if="state.orderInfo.deliveryType === 1 && state.orderInfo.id"
          @tap="onExpress(state.orderInfo.id)"
        >
          查看物流
        </button>
        <button
          class="ss-reset-button cancel-btn"
          v-if="state.orderInfo.buttons?.includes('confirm')"
          @tap="onConfirm(state.orderInfo.id)"
        >
          确认收货
        </button>
        <button
          class="ss-reset-button cancel-btn"
          v-if="state.orderInfo.buttons?.includes('comment')"
          @tap="onComment(state.orderInfo.id)"
        >
          评价
        </button>
      </view>
    </su-fixed>
  </s-layout>
</template>

<script setup>
  import sheep from '@/sheep';
  import { chatEntry } from '@/sheep/helper/kefu-chat.mjs';
  import { onLoad, onUnload } from '@dcloudio/uni-app';
  import { computed, reactive, ref, watch } from 'vue';
  import { locationSummary } from '@/sheep/helper/fulfillment-view.mjs';
  import { isEmpty } from 'lodash-es';
  import {
    fen2yuan,
    formatOrderStatus,
    formatOrderStatusDescription,
    handleOrderButtons,
  } from '@/sheep/hooks/useGoods';
  import OrderApi from '@/sheep/api/trade/order';
  import DeliveryApi from '@/sheep/api/trade/delivery';
  import PayOrderApi from '@/sheep/api/pay/order';
  import PickUpVerify from '@/pages/order/pickUpVerify.vue';
  import { usePaymentDeadline } from '@/sheep/hooks/usePaymentDeadline';
  import { monotonicNow, orderPaymentState, stampPaymentClock } from '@/sheep/helper/payment-deadline.mjs';

  const statusBarHeight = sheep.$platform.device.statusBarHeight * 2;
  const headerBg = sheep.$url.css('/static/img/shop/order/order_bg.png');

  const state = reactive({
    orderInfo: {},
  });
  const { now, active, syncing, confirmed, refresh } = usePaymentDeadline(
    (time) => [orderPaymentState(state.orderInfo, time)],
    () => state.orderInfo.id ? getOrderDetail(state.orderInfo.id) : false,
  );
  const paymentState = computed(() => orderPaymentState(state.orderInfo, now.value));
  const receiverLocation = computed(() => locationSummary(state.orderInfo, 'receiver'));
  const contactService = () => sheep.$router.go('/pages/chat/service', chatEntry(state.orderInfo.shopId, 'ORDER', state.orderInfo.id));
  const canPay = computed(() => active.value && !syncing.value && confirmed.value && paymentState.value.canPay);
  const canUpdateAddress = computed(() => canPay.value && state.orderInfo.status === 0
    && state.orderInfo.deliveryType === 1 && Boolean(state.orderInfo.payOrderId));

  function selectNewAddress() {
    if (!canUpdateAddress.value) return sheep.$helper.toast('当前订单不能修改收货地址');
    sheep.$router.go('/pages/user/address/list', { type: 'select' });
  }

  async function onAddressSelected(event = {}) {
    const addressId = event.addressInfo?.id;
    if (!addressId || !state.orderInfo.id || !canUpdateAddress.value) return;
    const modal = await uni.showModal({
      title: '修改收货地址',
      content: '修改后将按新地址重新计算运费和待支付金额，是否继续？',
      confirmText: '确认修改',
    });
    if (!modal.confirm) return;
    try {
      const response = await OrderApi.updateOrderAddress({ id: state.orderInfo.id, addressId });
      if (response?.code !== 0) return;
      sheep.$helper.toast('收货地址已更新，请核对支付金额');
      await getOrderDetail(state.orderInfo.id);
    } catch (error) {
      console.warn('修改收货地址失败', error);
    }
  }

  // 订单详情接口在网络异常或历史数据不完整时可能返回空字段。先归一化数组和金额，
  // 确保详情页模板不会因 properties/items/buttons 缺失而中断渲染。
  function normalizeOrderDetail(order) {
    const source = order && typeof order === 'object' ? order : {};
    const items = Array.isArray(source.items)
      ? source.items.filter(Boolean).map((item) => ({
          ...item,
          properties: Array.isArray(item.properties) ? item.properties : [],
          count: Number(item.count) || 0,
          price: Number(item.price) || 0,
          afterSaleStatus: Number(item.afterSaleStatus) || 0,
        }))
      : [];
    const status = Number(source.status);
    const itemShopNames = [
      ...new Set(items.map((item) => String(item.shopName || '').trim()).filter(Boolean)),
    ];
    const shopName = String(source.shopName || '').trim();
    return {
      ...source,
      status: Number.isFinite(status) ? status : source.status,
      items,
      // 详情接口旧版本没有订单级店铺字段，单店铺订单可从订单项安全推断。
      shopName:
        shopName ||
        (itemShopNames.length === 1
          ? itemShopNames[0]
          : itemShopNames.length > 1
          ? '多店铺订单'
          : '平台自营'),
      buttons: Array.isArray(source.buttons) ? source.buttons : [],
      totalPrice: Number(source.totalPrice) || 0,
      deliveryPrice: Number(source.deliveryPrice) || 0,
      couponPrice: Number(source.couponPrice) || 0,
      pointPrice: Number(source.pointPrice) || 0,
      discountPrice: Number(source.discountPrice) || 0,
      vipPrice: Number(source.vipPrice) || 0,
      payPrice: Number(source.payPrice) || 0,
      refundPrice: Number(source.refundPrice) || 0,
    };
  }

  // ========== 门店自提（核销） ==========
  const systemStore = ref({}); // 门店信息

  // 复制
  const onCopy = () => {
    if (state.orderInfo.no) {
      sheep.$helper.copyText(state.orderInfo.no);
    }
  };

  // 去支付
  async function onPay(payOrderId) {
    if (!payOrderId || !canPay.value) {
      sheep.$helper.toast(paymentState.value.text || '请刷新订单后重试');
      return;
    }
    if (!(await refresh()) || !canPay.value) return;
    sheep.$router.go('/pages/pay/index', {
      id: state.orderInfo.payOrderId,
      tradeOrderId: state.orderInfo.id,
    });
  }

  // 查看商品
  function onGoodsDetail(id) {
    if (!id) {
      return;
    }
    sheep.$router.go('/pages/goods/index', {
      id,
    });
  }

  // 取消订单
  async function onCancel(orderId) {
    uni.showModal({
      title: '提示',
      content: '确定要取消订单吗?',
      success: async function (res) {
        if (!res.confirm) {
          return;
        }
        try {
          const response = await OrderApi.cancelOrder(orderId);
          if (response?.code === 0) {
            await getOrderDetail(orderId);
          }
        } catch (error) {
          console.warn('取消订单失败', error);
        }
      },
    });
  }

  // 查看物流
  async function onExpress(id) {
    if (!id) {
      return;
    }
    sheep.$router.go('/pages/order/express/log', {
      id,
    });
  }

  // 确认收货
  async function onConfirm(orderId, ignore = false) {
    // 需开启确认收货组件
    // todo: 芋艿：【微信物流】待接入微信 https://gitee.com/sheepjs/shopro-uniapp/commit/a6bbba49b84dd418b84c5fefc8b7463df8f4901f
    // 1.怎么检测是否开启了发货组件功能？如果没有开启的话就不能在这里return出去
    // 2.如果开启了走mpConfirm方法,需要在App.vue的show方法中拿到确认收货结果
    let isOpenBusinessView = true;
    if (
      sheep.$platform.name === 'WechatMiniProgram' &&
      !isEmpty(state.orderInfo.wechat_extra_data) &&
      isOpenBusinessView &&
      !ignore
    ) {
      mpConfirm(orderId);
      return;
    }

    uni.showModal({
      title: '提示',
      content: '确认收货吗？',
      success: async function (res) {
        if (!res.confirm) {
          return;
        }
        // 正常的确认收货流程
        try {
          const response = await OrderApi.receiveOrder(orderId);
          if (response?.code === 0) {
            await getOrderDetail(orderId);
          }
        } catch (error) {
          console.warn('确认收货失败', error);
        }
      },
    });
  }

  // #ifdef MP-WEIXIN
  // 小程序确认收货组件
  function mpConfirm(orderId) {
    if (!wx.openBusinessView) {
      sheep.$helper.toast(`请升级微信版本`);
      return;
    }
    wx.openBusinessView({
      businessType: 'weappOrderConfirm',
      extraData: {
        merchant_trade_no: state.orderInfo.wechat_extra_data.merchant_trade_no,
        transaction_id: state.orderInfo.wechat_extra_data.transaction_id,
      },
      success(response) {
        console.log('success:', response);
        if (response.errMsg === 'openBusinessView:ok') {
          if (response.extraData.status === 'success') {
            onConfirm(orderId, true);
          }
        }
      },
      fail(error) {
        console.log('error:', error);
      },
      complete(result) {
        console.log('result:', result);
      },
    });
  }

  // #endif

  // 评价
  function onComment(id) {
    if (!id) {
      return;
    }
    sheep.$router.go('/pages/goods/comment/add', {
      id,
    });
  }

  const pickUpVerifyRef = ref();
  let detailRequest;

  function getOrderDetail(id) {
    if (detailRequest) return detailRequest;
    detailRequest = loadOrderDetail(id).finally(() => { detailRequest = undefined; });
    return detailRequest;
  }

  async function loadOrderDetail(id) {
    if (!id) {
      sheep.$helper.toast('订单不存在');
      return false;
    }
    // 对详情数据进行适配
    const requestedAt = monotonicNow();
    try {
      let res;
      if (state.comeinType === 'wechat') {
        // TODO 芋艿：【微信物流】微信场景下
        res = await OrderApi.getOrderDetail(id, {
          merchant_trade_no: state.merchantTradeNo,
        });
      } else {
        res = await OrderApi.getOrderDetail(id);
      }
      if (res?.code !== 0 || !res.data) {
        return false;
      }

      state.orderInfo = stampPaymentClock(normalizeOrderDetail(res.data), monotonicNow(), requestedAt);
      handleOrderButtons(state.orderInfo);
      // 配送方式：门店自提
      if (state.orderInfo.pickUpStoreId) {
        try {
          const storeResponse = await DeliveryApi.getDeliveryPickUpStore(
            state.orderInfo.pickUpStoreId,
          );
          if (storeResponse?.code === 0) {
            systemStore.value = storeResponse.data || {};
          }
        } catch (error) {
          console.warn('加载自提门店失败', error);
        }
      }
      if (state.orderInfo.deliveryType === 2 && state.orderInfo.payStatus) {
        pickUpVerifyRef.value && pickUpVerifyRef.value.markCode(state.orderInfo.pickUpVerifyCode);
      }
      return true;
    } catch (error) {
      console.warn('加载订单详情失败', error);
      return false;
    }
  }

  onLoad(async (options = {}) => {
    uni.$on('SELECT_ADDRESS', onAddressSelected);
    let id = 0;
    if (options.id) {
      id = options.id;
    }
    // 场景：例如说“微信小程序购物订单”
    // https://developers.weixin.qq.com/miniprogram/dev/platform-capabilities/business-capabilities/order_center/order_center.html
    // （小程序商品订单详情 path）配置参考：pages/order/detail?payOrderNo=${商品订单号} 。其中：${商品订单号} out_trade_no 为 payOrderNo
    if (!id && options.payOrderNo) {
      // 查询支付订单：根据 payOrderNo 取 merchantOrderId ，merchantOrderId 即 tradeOrderId
      try {
        const payOrder = await PayOrderApi.getOrder(undefined, undefined, options.payOrderNo);
        if (payOrder?.code === 0) {
          id = payOrder.data?.merchantOrderId;
        }
      } catch (error) {
        console.warn('查询支付订单失败', error);
      }
    }
    state.orderInfo.id = id;
    // onLoad 与 onShow 共享单一请求，恢复页面时重新校准服务端时钟。
    if (id) {
      await refresh();
    } else {
      sheep.$helper.toast('订单不存在');
    }
  });
  onUnload(() => uni.$off('SELECT_ADDRESS', onAddressSelected));
</script>

<style lang="scss" scoped>
  .score-img {
    width: 36rpx;
    height: 36rpx;
    margin: 0 4rpx;
  }

  .apply-btn {
    width: 140rpx;
    height: 50rpx;
    border-radius: 25rpx;
    font-size: 24rpx;
    border: 2rpx solid #dcdcdc;
    line-height: normal;
    margin-left: 16rpx;
  }

  .state-box {
    color: rgba(#fff, 0.9);
    width: 100%;
    background: v-bind(headerBg) no-repeat,
      linear-gradient(90deg, var(--ui-BG-Main), var(--ui-BG-Main-gradient));
    background-size: 750rpx 100%;
    box-sizing: border-box;

    .state-img {
      width: 60rpx;
      height: 60rpx;
      margin-right: 20rpx;
    }
  }

  .order-address-box {
    background-color: #fff;
    border-radius: 10rpx;
    margin: -50rpx 20rpx 16rpx 20rpx;
    padding: 44rpx 34rpx 42rpx 20rpx;
    font-size: 30rpx;
    box-sizing: border-box;
    font-weight: 500;
    color: rgba(51, 51, 51, 1);

    .address-username {
      margin-right: 20rpx;
    }

    .address-detail {
      font-size: 26rpx;
      font-weight: 500;
      color: rgba(153, 153, 153, 1);
      margin-top: 20rpx;
    }

    .update-address-btn {
      margin-top: 22rpx;
      color: var(--ui-BG-Main);
      font-size: 26rpx;
      text-align: left;
    }
  }

  .detail-goods {
    border-radius: 10rpx;
    margin: 0 20rpx 20rpx 20rpx;

    .order-list {
      margin-bottom: 20rpx;
      background-color: #fff;

      .order-card {
        padding: 20rpx 0;

        .order-sku {
          font-size: 24rpx;

          font-weight: 400;
          color: rgba(153, 153, 153, 1);
          width: 450rpx;
          margin-bottom: 20rpx;

          .order-num {
            margin-right: 10rpx;
          }
        }

        .tag-btn {
          margin-left: 16rpx;
          font-size: 24rpx;
          height: 36rpx;
          color: var(--ui-BG-Main);
          border: 2rpx solid var(--ui-BG-Main);
          border-radius: 14rpx;
          padding: 0 4rpx;
        }
      }
    }
  }

  // 订单信息。
  .notice-box {
    background: #fff;
    border-radius: 10rpx;
    margin: 0 20rpx 20rpx 20rpx;

    .notice-box__head {
      font-size: 30rpx;

      font-weight: 500;
      color: rgba(51, 51, 51, 1);
      line-height: 80rpx;
      border-bottom: 1rpx solid #dfdfdf;
      padding: 0 25rpx;
    }

    .notice-box__content {
      padding: 20rpx;

      .self-pickup-box {
        width: 100%;

        .self-pickup--img {
          width: 200rpx;
          height: 200rpx;
          margin: 40rpx 0;
        }
      }
    }

    .notice-item,
    .notice-item--center {
      display: flex;
      align-items: center;
      line-height: normal;
      margin-bottom: 24rpx;

      .title {
        font-size: 28rpx;
        color: #999;
      }

      .detail {
        font-size: 28rpx;
        color: #333;
        flex: 1;
      }
    }
  }

  .copy-btn {
    width: 100rpx;
    line-height: 50rpx;
    border-radius: 25rpx;
    padding: 0;
    background: rgba(238, 238, 238, 1);
    font-size: 22rpx;
    font-weight: 400;
    color: rgba(51, 51, 51, 1);
  }

  // 订单价格信息
  .order-price-box {
    background-color: #fff;
    border-radius: 10rpx;
    padding: 20rpx;
    margin: 0 20rpx 20rpx 20rpx;

    .notice-item {
      line-height: 70rpx;

      .title {
        font-size: 28rpx;
        color: #999;
      }

      .detail {
        font-size: 28rpx;
        color: #333;
        font-family: OPPOSANS;
      }
    }

    .all-rpice-item {
      justify-content: flex-end;
      align-items: center;

      .title {
        font-size: 26rpx;
        font-weight: 500;
        color: #333333;
        line-height: normal;
      }

      .all-price {
        font-size: 26rpx;
        font-family: OPPOSANS;
        line-height: normal;
        color: $red;
      }
    }
  }

  // 底部
  .footer-box {
    height: 100rpx;
    width: 100%;
    box-sizing: border-box;
    border-radius: 10rpx;
    padding-right: 20rpx;

    .cancel-btn {
      width: 160rpx;
      height: 60rpx;
      background: #eeeeee;
      border-radius: 30rpx;
      margin-right: 20rpx;
      font-size: 26rpx;
      font-weight: 400;
      color: #333333;
    }

    .pay-btn {
      width: 160rpx;
      height: 60rpx;
      font-size: 26rpx;
      border-radius: 30rpx;
      font-weight: 500;
      color: #fff;
    }
  }
</style>
