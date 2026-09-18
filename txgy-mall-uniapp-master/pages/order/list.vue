<!-- 订单列表 -->
<template>
  <s-layout title="我的订单">
    <s-order-workbench :workbench="workbench" />
    <view class="state-scope ss-p-x-20">当前状态订单数量 · 待评价为已完成中的辅助筛选，不重复计入总数</view>
    <su-sticky bgColor="#fff">
      <su-tabs
        :list="tabMaps"
        :scrollable="true"
        @change="onTabsChange"
        :current="state.currentTab"
      />
    </su-sticky>
    <view v-if="!confirmed && !syncing" class="ss-m-20 ss-flex ss-row-between">
      <text>订单状态暂未确认，请刷新后重试</text>
      <button class="ss-reset-button" @tap="refresh">刷新订单状态</button>
    </view>
    <s-empty v-if="state.pagination.total === 0" icon="/static/order-empty.png" text="暂无订单" />
    <view v-if="state.pagination.total > 0">
      <view
        class="bg-white order-list-card-box ss-r-10 ss-m-t-14 ss-m-20"
        v-for="order in state.pagination.list"
        :key="order.id"
        @tap="onOrderDetail(order.id)"
      >
        <view class="order-card-header ss-flex ss-col-center ss-row-between ss-p-x-20">
          <view class="order-no"><text v-if="unreadForOrder(workbench.state.summary, order.id).length" class="new-order">新</text>订单号：{{ order.no }}</view>
          <view class="order-state ss-font-26" :class="formatOrderColor(order)">
            {{ formatOrderStatus(order) }}
          </view>
        </view>
        <view class="order-shop-name ss-p-x-20">店铺：{{ order.shopName }}</view>
        <view v-if="order.status === 0" class="payment-deadline ss-p-x-20">
          {{ paymentState(order).text }}
        </view>
        <view class="border-bottom" v-for="item in order.items" :key="item.id">
          <s-goods-item
            :img="item.picUrl"
            :title="item.spuName"
            :skuText="(item.properties || []).map((property) => property.valueName).join(' ')"
            :price="item.price"
            :num="item.count"
          />
        </view>
        <view class="pay-box ss-m-t-30 ss-flex ss-row-right ss-p-r-20">
          <view class="ss-flex ss-col-center">
            <view class="discounts-title pay-color"
              >共 {{ order.productCount }} 件商品,总金额:</view
            >
            <view class="discounts-money pay-color"> ￥{{ fen2yuan(order.payPrice) }} </view>
          </view>
        </view>
        <view
          class="order-card-footer ss-flex ss-col-center ss-p-x-20"
          :class="order.buttons.length > 3 ? 'ss-row-between' : 'ss-row-right'"
        >
          <view class="ss-flex ss-col-center">
            <button
              v-if="order.buttons.includes('combination')"
              class="tool-btn ss-reset-button"
              @tap.stop="onOrderGroupon(order)"
            >
              拼团详情
            </button>
            <button
              v-if="order.buttons.length === 0"
              class="tool-btn ss-reset-button"
              @tap.stop="onOrderDetail(order.id)"
            >
              查看详情
            </button>
            <button
              v-if="order.buttons.includes('confirm')"
              class="tool-btn ss-reset-button"
              @tap.stop="onConfirm(order)"
            >
              确认收货
            </button>
            <button
              v-if="order.buttons.includes('express')"
              class="tool-btn ss-reset-button"
              @tap.stop="onExpress(order.id)"
            >
              查看物流
            </button>
            <button
              v-if="order.buttons.includes('cancel')"
              class="tool-btn ss-reset-button"
              @tap.stop="onCancel(order.id)"
            >
              取消订单
            </button>
            <button
              v-if="order.buttons.includes('comment')"
              class="tool-btn ss-reset-button"
              @tap.stop="onComment(order.id)"
            >
              评价
            </button>
            <button
              v-if="order.buttons.includes('delete')"
              class="delete-btn ss-reset-button"
              @tap.stop="onDelete(order.id)"
            >
              删除订单
            </button>
            <button
              v-if="order.buttons.includes('pay') && order.payOrderId"
              class="tool-btn ss-reset-button ui-BG-Main-Gradient"
              :disabled="!canPay(order)"
              @tap.stop="onPay(order)"
            >
              继续支付
            </button>
          </view>
        </view>
      </view>
    </view>

    <!-- 加载更多 -->
    <uni-load-more
      v-if="state.pagination.total > 0"
      :status="state.loadStatus"
      :content-text="{
        contentdown: '上拉加载更多',
      }"
      @tap="loadMore"
    />
  </s-layout>
</template>

<script setup>
  import { computed, reactive } from 'vue';
  import { onLoad, onReachBottom, onPullDownRefresh } from '@dcloudio/uni-app';
  import {
    fen2yuan,
    formatOrderColor,
    formatOrderStatus,
    handleOrderButtons,
  } from '@/sheep/hooks/useGoods';
  import sheep from '@/sheep';
  import { concat, isEmpty } from 'lodash-es';
  import OrderApi from '@/sheep/api/trade/order';
  import { resetPagination } from '@/sheep/helper/utils';
  import { usePaymentDeadline } from '@/sheep/hooks/usePaymentDeadline';
  import { monotonicNow, orderPaymentState, stampPaymentClock } from '@/sheep/helper/payment-deadline.mjs';
  import { useOrderWorkbench } from '@/sheep/hooks/useOrderWorkbench';
  import { initialOrderTab, orderTabQuery, tabsWithCounts, unreadForOrder } from '@/sheep/helper/order-workbench.mjs';

  // 数据
  const state = reactive({
    currentTab: 0, // 选中的 tabMaps 下标
    pagination: {
      list: [],
      total: 0,
      pageNo: 1,
      pageSize: 5,
    },
    loadStatus: '',
  });
  const { now, active, syncing, confirmed, refresh } = usePaymentDeadline(
    (time) => state.pagination.list.map((order) => orderPaymentState(order, time)),
    () => getOrderList(true),
  );
  const paymentState = (order) => orderPaymentState(order, now.value);
  const canPay = (order) => active.value && !syncing.value && confirmed.value && paymentState(order).canPay;
  const workbench = useOrderWorkbench(() => ({ orderIds: state.pagination.list.map(order => order.id).slice(0, 100).join(',') || undefined }));
  const tabMaps = computed(() => tabsWithCounts(workbench.state.summary));

  // 接口异常或历史数据可能缺少 items/properties/buttons；统一补齐后再交给模板和按钮处理，
  // 避免一条脏数据导致整个订单列表渲染失败。
  function normalizeOrder(order) {
    const source = order && typeof order === 'object' ? order : {};
    const items = Array.isArray(source.items)
      ? source.items.filter(Boolean).map((item) => ({
          ...item,
          properties: Array.isArray(item.properties) ? item.properties : [],
          count: Number(item.count) || 0,
          price: Number(item.price) || 0,
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
      // 列表接口通常直接返回订单店铺；兼容旧数据时从订单项推断，最后回退平台自营。
      shopName:
        shopName ||
        (itemShopNames.length === 1
          ? itemShopNames[0]
          : itemShopNames.length > 1
          ? '多店铺订单'
          : '平台自营'),
      buttons: Array.isArray(source.buttons) ? source.buttons : [],
      productCount: Number(source.productCount) || items.reduce((sum, item) => sum + item.count, 0),
      payPrice: Number(source.payPrice) || 0,
    };
  }

  // 切换选项卡
  function onTabsChange(e = {}) {
    if (syncing.value || state.loadStatus === 'loading') return;
    const index = Number(e.index);
    if (!Number.isInteger(index) || index < 0 || index >= tabMaps.value.length) {
      return;
    }
    if (state.currentTab === index) {
      return;
    }
    // 重头加载代码
    resetPagination(state.pagination);
    state.currentTab = index;
    void refresh();
  }

  // 订单详情
  async function onOrderDetail(id) {
    if (!id) {
      return;
    }
    if (!await workbench.readOrder(id)) return;
    sheep.$router.go('/pages/order/detail', {
      id,
    });
  }

  // 跳转拼团记录的详情
  function onOrderGroupon(order) {
    sheep.$router.go('/pages/activity/groupon/detail', {
      id: order.combinationRecordId,
    });
  }

  // 继续支付
  async function onPay(order) {
    if (!canPay(order)) {
      sheep.$helper.toast(paymentState(order).text || '请刷新订单后重试');
      return;
    }
    if (!(await refresh())) return;
    const latest = state.pagination.list.find((item) => item.id === order.id);
    if (!latest || !canPay(latest)) return;
    sheep.$router.go('/pages/pay/index', {
      id: latest.payOrderId,
      tradeOrderId: latest.id,
    });
  }

  // 评价
  function onComment(id) {
    sheep.$router.go('/pages/goods/comment/add', {
      id,
    });
  }

  // 确认收货
  async function onConfirm(order, ignore = false) {
    // 需开启确认收货组件
    // todo: 芋艿：【微信物流】需要后续接入微信收货组件 https://gitee.com/sheepjs/shopro-uniapp/commit/a6bbba49b84dd418b84c5fefc8b7463df8f4901f
    // 1.怎么检测是否开启了发货组件功能？如果没有开启的话就不能在这里return出去
    // 2.如果开启了走mpConfirm方法,需要在App.vue的show方法中拿到确认收货结果
    let isOpenBusinessView = true;
    if (
      sheep.$platform.name === 'WechatMiniProgram' &&
      !isEmpty(order.wechat_extra_data) &&
      isOpenBusinessView &&
      !ignore
    ) {
      mpConfirm(order);
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
          const response = await OrderApi.receiveOrder(order.id);
          if (response?.code === 0) {
            resetPagination(state.pagination);
            await getOrderList();
          }
        } catch (error) {
          console.warn('确认收货失败', error);
        }
      },
    });
  }

  // #ifdef MP-WEIXIN
  // 小程序确认收货组件 TODO 芋艿：【微信物流】后续再接入
  function mpConfirm(order) {
    if (!wx.openBusinessView) {
      sheep.$helper.toast(`请升级微信版本`);
      return;
    }
    wx.openBusinessView({
      businessType: 'weappOrderConfirm',
      extraData: {
        merchant_id: '1481069012',
        merchant_trade_no: order.wechat_extra_data.merchant_trade_no,
        transaction_id: order.wechat_extra_data.transaction_id,
      },
      success(response) {
        console.log('success:', response);
        if (response.errMsg === 'openBusinessView:ok') {
          if (response.extraData.status === 'success') {
            onConfirm(order, true);
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

  // 查看物流
  async function onExpress(id) {
    sheep.$router.go('/pages/order/express/log', {
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
          if (response?.code !== 0) {
            return;
          }
          // 修改数据的状态
          let index = state.pagination.list.findIndex((order) => order.id === orderId);
          if (index >= 0) {
            const orderInfo = state.pagination.list[index];
            orderInfo.status = 40;
            handleOrderButtons(orderInfo);
          }
        } catch (error) {
          console.warn('取消订单失败', error);
        }
      },
    });
  }

  // 删除订单
  function onDelete(orderId) {
    uni.showModal({
      title: '提示',
      content: '确定要删除订单吗?',
      success: async function (res) {
        if (res.confirm) {
          try {
            const response = await OrderApi.deleteOrder(orderId);
            if (response?.code !== 0) {
              return;
            }
            // 删除数据
            let index = state.pagination.list.findIndex((order) => order.id === orderId);
            if (index >= 0) {
              state.pagination.list.splice(index, 1);
              state.pagination.total = Math.max(0, state.pagination.total - 1);
            }
          } catch (error) {
            console.warn('删除订单失败', error);
          }
        }
      },
    });
  }

  // 获取订单列表
  async function getOrderList(reload = false) {
    if (state.loadStatus === 'loading') return false;
    state.loadStatus = 'loading';
    const requestedAt = monotonicNow();
    try {
      const pageNumbers = reload
        ? Array.from({ length: state.pagination.pageNo }, (_, index) => index + 1)
        : [state.pagination.pageNo];
      const responses = await Promise.all(pageNumbers.map((pageNo) => OrderApi.getOrderPage({
          pageNo,
          pageSize: state.pagination.pageSize,
          ...orderTabQuery(state.currentTab),
        })));
      if (responses.some((response) => !response || response.code !== 0)) {
        state.loadStatus = state.pagination.list.length ? 'more' : 'noMore';
        return false;
      }
      const pageData = responses[0].data || {};
      const pageList = responses.flatMap((response) => Array.isArray(response.data?.list) ? response.data.list : [])
        .filter(Boolean).map((order) => {
            const normalized = stampPaymentClock(normalizeOrder(order), monotonicNow(), requestedAt);
            handleOrderButtons(normalized);
            return normalized;
          });
      state.pagination.list = reload ? pageList : concat(state.pagination.list, pageList);
      const total = Number(pageData.total);
      state.pagination.total =
        Number.isFinite(total) && total >= 0 ? total : state.pagination.list.length;
      state.loadStatus = state.pagination.list.length < state.pagination.total ? 'more' : 'noMore';
      return true;
    } catch (error) {
      console.warn('加载订单列表失败', error);
      state.loadStatus = state.pagination.list.length ? 'more' : 'noMore';
      return false;
    }
  }

  onLoad(async (options = {}) => {
    state.currentTab = initialOrderTab(options);
    await refresh();
  });

  // 加载更多
  function loadMore() {
    if (syncing.value || state.loadStatus === 'noMore' || state.loadStatus === 'loading') {
      return;
    }
    state.pagination.pageNo++;
    getOrderList();
  }

  // 上拉加载更多
  onReachBottom(() => {
    loadMore();
  });

  // 下拉刷新
  onPullDownRefresh(async () => {
    if (syncing.value || state.loadStatus === 'loading') { uni.stopPullDownRefresh(); return; }
    resetPagination(state.pagination);
    try {
      await refresh();
    } finally {
      uni.stopPullDownRefresh();
    }
  });
</script>

<style lang="scss" scoped>
  .state-scope { color: #687d83; font-size: 23rpx; margin-bottom: 14rpx; }
  .new-order { display: inline-block; font-size: 22rpx; color: #754507; background: #fff0da; border: 1rpx solid #d6a45d; border-radius: 5rpx; padding: 0 8rpx; margin-right: 8rpx; }
  .payment-deadline { padding-bottom: 16rpx; color: #a56a24; font-size: 24rpx; line-height: 1.6; }
  .tool-btn[disabled] { opacity: .5; }
  .score-img {
    width: 36rpx;
    height: 36rpx;
    margin: 0 4rpx;
  }

  .tool-btn {
    width: 160rpx;
    height: 60rpx;
    background: #f6f6f6;
    font-size: 26rpx;
    border-radius: 30rpx;
    margin-right: 10rpx;

    &:last-of-type {
      margin-right: 0;
    }
  }

  .delete-btn {
    width: 160rpx;
    height: 56rpx;
    color: #ff3000;
    background: #fee;
    border-radius: 28rpx;
    font-size: 26rpx;
    margin-right: 10rpx;
    line-height: normal;

    &:last-of-type {
      margin-right: 0;
    }
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

  .swiper-box {
    flex: 1;

    .swiper-item {
      height: 100%;
      width: 100%;
    }
  }

  .order-list-card-box {
    .order-shop-name {
      padding-bottom: 14rpx;
      color: #666;
      font-size: 24rpx;
    }

    .order-card-header {
      height: 80rpx;

      .order-no {
        font-size: 26rpx;
        font-weight: 500;
      }

      .order-state {
      }
    }

    .pay-box {
      .discounts-title {
        font-size: 24rpx;
        line-height: normal;
        color: #999999;
      }

      .discounts-money {
        font-size: 24rpx;
        line-height: normal;
        color: #999;
        font-family: OPPOSANS;
      }

      .pay-color {
        color: #333;
      }
    }

    .order-card-footer {
      height: 100rpx;

      .more-item-box {
        padding: 20rpx;

        .more-item {
          height: 60rpx;

          .title {
            font-size: 26rpx;
          }
        }
      }

      .more-btn {
        color: $dark-9;
        font-size: 24rpx;
      }

      .content {
        width: 154rpx;
        color: #333333;
        font-size: 26rpx;
        font-weight: 500;
      }
    }
  }

  :deep(.uni-tooltip-popup) {
    background: var(--ui-BG);
  }

  .warning-color {
    color: #faad14;
  }

  .danger-color {
    color: #ff3000;
  }

  .success-color {
    color: #52c41a;
  }

  .info-color {
    color: #999999;
  }
</style>
