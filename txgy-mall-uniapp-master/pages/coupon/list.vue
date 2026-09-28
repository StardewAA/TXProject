<!-- 优惠券中心  -->
<template>
  <s-layout :bgStyle="{ color: '#f2f2f2' }" title="优惠券">
    <su-sticky bgColor="#fff">
      <su-tabs
        :list="tabMaps"
        :scrollable="false"
        @change="onTabsChange"
        :current="state.currentTab"
      />
    </su-sticky>
    <s-empty
      v-if="state.pagination.total === 0 && !state.loading"
      icon="/static/coupon-empty.png"
      text="暂无优惠券"
    />
    <!-- 情况一：领劵中心 -->
    <template v-if="state.currentTab === 0">
      <view v-for="item in state.pagination.list" :key="item.id">
        <s-coupon-list
          :data="item"
          @tap="sheep.$router.go('/pages/coupon/detail', { id: item.id })"
        >
          <template #default>
            <button
              class="ss-reset-button card-btn ss-flex ss-row-center ss-col-center"
              :class="!item.canTake ? 'border-btn' : ''"
              @click.stop="getBuy(item.id)"
              :disabled="!item.canTake"
            >
              {{ item.canTake ? '立即领取' : '已领取' }}
            </button>
          </template>
        </s-coupon-list>
      </view>
    </template>
    <!-- 情况二：我的优惠劵 -->
    <template v-else>
      <view v-for="item in state.pagination.list" :key="item.id">
        <s-coupon-list
          :data="item"
          type="user"
          @tap="sheep.$router.go('/pages/coupon/detail', { couponId: item.id })"
        >
          <template #default>
            <button
              class="ss-reset-button card-btn ss-flex ss-row-center ss-col-center"
              :class="item.status !== 1 ? 'disabled-btn' : ''"
              :disabled="item.status !== 1"
              @click.stop="sheep.$router.go('/pages/coupon/detail', { couponId: item.id })"
            >
              {{ item.status === 1 ? '立即使用' : item.status === 2 ? '已使用' : '已过期' }}
            </button>
          </template>
        </s-coupon-list>
      </view>
    </template>

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
  import sheep from '@/sheep';
  import { onLoad, onReachBottom } from '@dcloudio/uni-app';
  import { reactive } from 'vue';
  import { concat } from 'lodash-es';
  import { resetPagination } from '@/sheep/helper/utils';
  import CouponApi from '@/sheep/api/promotion/coupon';

  // 数据
  const state = reactive({
    currentTab: 0, // 当前 tab
    type: '1',
    pagination: {
      list: [],
      total: 0,
      pageNo: 1,
      pageSize: 5,
    },
    loadStatus: '',
    loading: false,
    takingId: null,
  });

  const tabMaps = [
    {
      name: '领券中心',
      value: 'all',
    },
    {
      name: '已领取',
      value: '1',
    },
    {
      name: '已使用',
      value: '2',
    },
    {
      name: '已失效',
      value: '3',
    },
  ];
  let requestVersion = 0;

  function onTabsChange(e = {}) {
    const eventIndex = Number(e.index);
    const valueIndex = tabMaps.findIndex((item) => String(item.value) === String(e.value ?? ''));
    const nextIndex = Number.isInteger(eventIndex) && eventIndex >= 0 ? eventIndex : valueIndex;
    state.currentTab = Math.min(Math.max(nextIndex >= 0 ? nextIndex : 0, 0), tabMaps.length - 1);
    state.type = tabMaps[state.currentTab]?.value || 'all';
    // 使上一个 tab 的请求失效；无法取消的网络响应也不能覆盖当前 tab。
    requestVersion += 1;
    state.loading = false;
    resetPagination(state.pagination);
    state.loadStatus = '';
    if (state.currentTab === 0) {
      getData();
    } else {
      getCoupon();
    }
  }

  // 将分页接口返回值统一成安全的列表结构。部分旧服务在异常时会返回 false，
  // 或者只返回 data/list 中的一部分字段，页面不能直接解构使用。
  function appendPage(response, pageNo) {
    if (response?.code !== 0) {
      state.loadStatus = state.pagination.list.length > 0 ? 'more' : 'noMore';
      return false;
    }
    const page = response.data && typeof response.data === 'object' ? response.data : {};
    const list = Array.isArray(page.list) ? page.list.filter(Boolean) : [];
    const parsedTotal = Number(page.total);
    const total = Number.isFinite(parsedTotal) && parsedTotal >= 0 ? parsedTotal : 0;
    // 只有当前页仍然有效时才合并，避免切换 tab 后旧请求覆盖新数据。
    if (pageNo !== state.pagination.pageNo) {
      return false;
    }
    state.pagination.list = pageNo === 1 ? list : concat(state.pagination.list, list);
    state.pagination.total = total;
    state.pagination.pageNo = pageNo + 1;
    state.loadStatus = state.pagination.list.length < total ? 'more' : 'noMore';
    return true;
  }

  async function requestPage(loader) {
    if (state.loading) {
      return;
    }
    const version = ++requestVersion;
    const pageNo = Math.max(1, Number(state.pagination.pageNo) || 1);
    state.loading = true;
    state.loadStatus = 'loading';
    try {
      const response = await loader(pageNo);
      if (version === requestVersion) {
        appendPage(response, pageNo);
      }
    } catch (error) {
      // request 层会负责网络错误提示；这里保持页面可继续重试。
      if (version === requestVersion) {
        state.loadStatus = state.pagination.list.length > 0 ? 'more' : 'noMore';
      }
    } finally {
      if (version === requestVersion) {
        state.loading = false;
      }
    }
  }

  // 获得优惠劵模版列表
  async function getData() {
    return requestPage((pageNo) =>
      CouponApi.getCouponTemplatePage({
        pageNo,
        pageSize: state.pagination.pageSize,
      }),
    );
  }

  // 获得我的优惠劵
  async function getCoupon() {
    return requestPage((pageNo) =>
      CouponApi.getCouponPage({
        pageNo,
        pageSize: state.pagination.pageSize,
        status: state.type,
      }),
    );
  }

  // 领取优惠劵
  async function getBuy(id) {
    const couponId = Number(id);
    if (!Number.isFinite(couponId) || couponId <= 0 || state.takingId !== null) {
      return;
    }
    state.takingId = couponId;
    try {
      const response = await CouponApi.takeCoupon(couponId);
      if (response?.code !== 0) {
        return;
      }
      uni.showToast({
        title: '领取成功',
      });
      requestVersion += 1;
      state.loading = false;
      resetPagination(state.pagination);
      state.loadStatus = '';
      await getData();
    } catch (error) {
      // request 层负责展示错误提示。
    } finally {
      state.takingId = null;
    }
  }

  // 加载更多
  function loadMore() {
    if (
      state.loading ||
      state.loadStatus === 'noMore' ||
      (state.pagination.total > 0 && state.pagination.list.length >= state.pagination.total)
    ) {
      return;
    }
    if (state.currentTab === 0) {
      getData();
    } else {
      getCoupon();
    }
  }

  onLoad((options = {}) => {
    const requestedType = String(options.type ?? 'all');
    const typeIndexMap = {
      all: 0,
      geted: 1,
      received: 1,
      1: 1,
      used: 2,
      2: 2,
      expired: 3,
      invalid: 3,
      3: 3,
    };
    state.currentTab = typeIndexMap[requestedType] ?? 0;
    state.type = tabMaps[state.currentTab]?.value || 'all';
    if (state.currentTab === 0) {
      getData();
    } else {
      getCoupon();
    }
  });

  onReachBottom(() => {
    loadMore();
  });
</script>
<style lang="scss" scoped>
  .card-btn {
    // width: 144rpx;
    padding: 0 16rpx;
    height: 50rpx;
    border-radius: 40rpx;
    background: linear-gradient(90deg, var(--ui-BG-Main), var(--ui-BG-Main-gradient));
    color: #ffffff;
    font-size: 24rpx;
    font-weight: 400;
  }

  .border-btn {
    background: linear-gradient(90deg, var(--ui-BG-Main-opacity-4), var(--ui-BG-Main-light));
    color: #fff !important;
  }

  .disabled-btn {
    background: #cccccc;
    background-color: #cccccc !important;
    color: #fff !important;
  }
</style>
