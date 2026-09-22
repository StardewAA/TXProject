<!-- 售后列表 -->
<template>
  <s-layout title="售后列表">
    <s-order-workbench :workbench="workbench" />
    <!-- tab -->
    <su-sticky bgColor="#fff">
      <su-tabs
        :list="tabMaps"
        :scrollable="false"
        @change="onTabsChange"
        :current="state.currentTab"
      />
    </su-sticky>
    <s-empty
      v-if="state.loaded && state.pagination.list.length === 0"
      icon="/static/data-empty.png"
      text="暂无数据"
    />
    <!-- 列表 -->
    <view v-if="state.pagination.list.length > 0">
      <view
        class="list-box ss-m-y-20"
        v-for="order in state.pagination.list"
        :key="order.id"
        @tap="openAfterSale(order.id)"
      >
        <view class="order-head ss-flex ss-col-center ss-row-between">
          <text class="no"><text v-if="unreadForOrder(workbench.state.summary, order.id, true).length" class="notice-new">新 · </text>服务单号：{{ order.no }}</text>
          <text class="state">{{ formatAfterSaleStatus(order) }}</text>
        </view>
        <view class="shop-name">店铺：{{ order.shopName }}</view>
        <s-goods-item
          :img="order.picUrl"
          :title="order.spuName"
          :skuText="(order.properties || []).map((property) => property.valueName).join(' ')"
          :price="order.refundPrice"
        />
        <view class="apply-box ss-flex ss-col-center ss-row-between border-bottom ss-p-x-20">
          <view class="ss-flex ss-col-center">
            <view class="title ss-m-r-20">{{ order.way === 10 ? '仅退款' : '退款退货' }}</view>
            <view class="value">{{ formatAfterSaleStatusDescription(order) }}</view>
          </view>
          <text class="_icon-forward"></text>
        </view>
        <view class="tool-btn-box ss-flex ss-col-center ss-row-right ss-p-r-20">
          <view>
            <button
              class="ss-reset-button tool-btn"
              @tap.stop="onApply(order.id)"
              :disabled="String(state.cancellingId) === String(order.id)"
              v-if="order?.buttons?.includes('cancel')"
            >
              取消申请
            </button>
          </view>
        </view>
      </view>
    </view>
    <uni-load-more
      v-if="
        state.pagination.list.length > 0 && state.pagination.total > state.pagination.list.length
      "
      :status="state.loadStatus"
      :content-text="{
        contentdown: '上拉加载更多',
      }"
      @tap="loadMore"
    />
  </s-layout>
</template>

<script setup>
  import sheep from '@/sheep';
  import { onLoad, onReachBottom } from '@dcloudio/uni-app';
  import { reactive } from 'vue';
  import { concat } from 'lodash-es';
  import {
    formatAfterSaleStatus,
    formatAfterSaleStatusDescription,
    handleAfterSaleButtons,
  } from '@/sheep/hooks/useGoods';
  import AfterSaleApi from '@/sheep/api/trade/afterSale';
  import { resetPagination } from '@/sheep/helper/utils';
  import { useOrderWorkbench } from '@/sheep/hooks/useOrderWorkbench';
  import { unreadForOrder } from '@/sheep/helper/order-workbench.mjs';

  const state = reactive({
    currentTab: 0,
    showApply: false,
    pagination: {
      list: [],
      total: 0,
      pageNo: 1,
      pageSize: 10,
    },
    loadStatus: '',
    loaded: false,
    loading: false,
    cancellingId: null,
  });

  const workbench = useOrderWorkbench(() => ({ afterSaleIds: state.pagination.list.map(item => item.id).slice(0, 100).join(',') || undefined }));
  async function openAfterSale(id) {
    if (!await workbench.readOrder(id, true)) return;
    sheep.$router.go('/pages/order/aftersale/detail', { id });
  }

  const tabMaps = [
    {
      name: '全部',
      value: [],
    },
    {
      name: '申请中',
      value: [10],
    },
    {
      name: '处理中',
      value: [20, 30, 40],
    },
    {
      name: '已完成',
      value: [50],
    },
    {
      name: '已拒绝',
      value: [61, 62, 63],
    },
  ];

  let requestSerial = 0;

  function normalizeAfterSale(order) {
    const source = order && typeof order === 'object' ? order : {};
    return {
      ...source,
      // Long 类型编号可能超过 JS 安全整数范围，保留后端原始字符串/数字
      id: source.id,
      status: Number(source.status),
      way: Number(source.way),
      shopName: String(source.shopName || '').trim() || '平台自营',
      properties: Array.isArray(source.properties) ? source.properties : [],
      buttons: Array.isArray(source.buttons) ? source.buttons : [],
      refundPrice: Number(source.refundPrice) || 0,
      count: Number(source.count) || 0,
    };
  }

  // 切换选项卡
  function onTabsChange(e) {
    const index = Number(e?.index);
    if (!Number.isInteger(index) || index < 0 || index >= tabMaps.length) {
      return;
    }
    requestSerial++;
    resetPagination(state.pagination);
    state.currentTab = index;
    state.loaded = false;
    state.loadStatus = '';
    getOrderList(true);
  }

  // 获取售后列表
  async function getOrderList(force = false) {
    if (state.loading && !force) {
      return;
    }
    const serial = ++requestSerial;
    const pageNo = state.pagination.pageNo;
    state.loading = true;
    state.loadStatus = 'loading';
    try {
      const response = await AfterSaleApi.getAfterSalePage({
        pageNo,
        pageSize: state.pagination.pageSize,
        statuses: tabMaps[state.currentTab].value.join(','),
      });
      if (serial !== requestSerial) {
        return;
      }
      if (response?.code !== 0) {
        if (pageNo > 1) {
          state.pagination.pageNo = pageNo - 1;
        }
        state.loadStatus = 'more';
        return;
      }

      const page = response.data && typeof response.data === 'object' ? response.data : {};
      const list = Array.isArray(page.list)
        ? page.list.filter(Boolean).map((order) => {
            const normalized = normalizeAfterSale(order);
            handleAfterSaleButtons(normalized);
            return normalized;
          })
        : [];
      state.pagination.list = pageNo === 1 ? list : concat(state.pagination.list, list);
      const total = Number(page.total);
      state.pagination.total =
        Number.isFinite(total) && total >= 0 ? total : state.pagination.list.length;
      state.loadStatus = state.pagination.list.length < state.pagination.total ? 'more' : 'noMore';
    } catch (error) {
      if (serial === requestSerial) {
        if (pageNo > 1) {
          state.pagination.pageNo = pageNo - 1;
        }
        state.loadStatus = 'more';
        console.warn('加载售后列表失败', error);
      }
    } finally {
      if (serial === requestSerial) {
        state.loading = false;
        state.loaded = true;
      }
    }
  }

  function onApply(orderId) {
    if (!orderId || state.cancellingId !== null) {
      return;
    }
    uni.showModal({
      title: '提示',
      content: '确定要取消此申请吗？',
      success: async function (res) {
        if (!res.confirm) {
          return;
        }
        state.cancellingId = orderId;
        try {
          const response = await AfterSaleApi.cancelAfterSale(orderId);
          if (response?.code === 0) {
            resetPagination(state.pagination);
            await getOrderList();
          }
        } catch (error) {
          console.warn('取消售后申请失败', error);
          sheep.$helper.toast('取消售后申请失败');
        } finally {
          state.cancellingId = null;
        }
      },
    });
  }

  onLoad(async (options = {}) => {
    const index = Number(options.type);
    if (Number.isInteger(index) && index >= 0 && index < tabMaps.length) {
      state.currentTab = index;
    }
    await getOrderList();
  });

  // 加载更多
  function loadMore() {
    if (
      state.loading ||
      state.loadStatus === 'noMore' ||
      (state.pagination.total > 0 && state.pagination.list.length >= state.pagination.total)
    ) {
      return;
    }
    state.pagination.pageNo++;
    getOrderList();
  }

  // 上拉加载更多
  onReachBottom(() => {
    loadMore();
  });
</script>

<style lang="scss" scoped>
  .notice-new {
    color: #b54708;
    font-weight: 700;
  }

  .list-box {
    background-color: #fff;

    .order-head {
      padding: 0 25rpx;
      height: 77rpx;
    }

    .shop-name {
      padding: 0 25rpx 14rpx;
      color: #666;
      font-size: 24rpx;
    }

    .apply-box {
      height: 82rpx;

      .title {
        font-size: 24rpx;
      }

      .value {
        font-size: 22rpx;
        color: $dark-6;
      }
    }

    .tool-btn-box {
      height: 100rpx;

      .tool-btn {
        width: 160rpx;
        height: 60rpx;
        background: #f6f6f6;
        border-radius: 30rpx;
        font-size: 26rpx;
        font-weight: 400;
      }
    }
  }
</style>
