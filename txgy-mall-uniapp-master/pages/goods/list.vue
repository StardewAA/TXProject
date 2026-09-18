<template>
  <s-layout
    navbar="normal"
    :leftWidth="0"
    :rightWidth="0"
    tools="search"
    :defaultSearch="state.keyword"
    @search="onSearch"
  >
    <view v-if="state.shopId" class="shop-banner">
      <view v-if="state.shopLoading">正在加载店铺…</view>
      <view v-else-if="state.shopError">店铺不存在、已停用或暂时无法访问</view>
      <template v-else-if="state.shop"><view class="shop-title">{{ state.shop.name }}</view><view class="shop-description">{{ state.shop.introduction || '企业材料供应店铺' }}</view><view class="shop-tip">店内搜索 · 共 {{ state.pagination.total }} 款商品</view></template>
      <view class="shop-directory-link" @tap="sheep.$router.go('/pages/shop/list')">查看全部店铺 ›</view>
      <button v-if="state.shop && !state.shopError && !state.shopLoading" size="mini" @tap="sheep.$router.go('/pages/chat/service', { scope: 'SHOP', shopId: state.shopId })">联系本店客服</button>
    </view>
    <view v-if="state.shopError && !state.invalidShopId" class="shop-retry" @tap="loadShop">重新加载店铺</view>
    <s-comparison-bar />
    <!-- 筛选 -->
    <su-sticky bgColor="#fff">
      <view class="ss-flex">
        <view class="ss-flex-1">
          <su-tabs
            :list="state.tabList"
            :scrollable="false"
            @change="onTabsChange"
            :current="state.currentTab"
          />
        </view>
        <view class="list-icon" @tap="state.iconStatus = !state.iconStatus">
          <text v-if="state.iconStatus" class="sicon-goods-list" />
          <text v-else class="sicon-goods-card" />
        </view>
      </view>
    </su-sticky>

    <!-- 弹窗 -->
    <su-popup
      :show="state.showFilter"
      type="top"
      round="10"
      :space="sys_navBar + 38"
      backgroundColor="#F6F6F6"
      :zIndex="10"
      @close="state.showFilter = false"
    >
      <view class="filter-list-box">
        <view
          class="filter-item"
          v-for="(item, index) in state.tabList[state.currentTab].list"
          :key="item.value"
          :class="[{ 'filter-item-active': index === state.curFilter }]"
          @tap="onFilterItem(index)"
        >
          {{ item.label }}
        </view>
      </view>
    </su-popup>

    <!-- 情况一：单列布局 -->
    <view v-if="state.iconStatus && state.pagination.total > 0" class="goods-list ss-m-t-20">
      <view
        class="ss-p-l-20 ss-p-r-20 ss-m-b-20"
        v-for="item in state.pagination.list"
        :key="item.id"
      >
        <s-goods-column
          class=""
          size="lg"
          :data="item"
          :show-shop-name="true"
          :topRadius="10"
          :bottomRadius="10"
          @click="sheep.$router.go('/pages/goods/index', { id: item.id })"
        />
        <button size="mini" @tap="sheep.$router.go('/pages/goods/index', { id: item.id })">选规格加入对比</button>
      </view>
    </view>
    <!-- 情况二：双列布局 -->
    <view
      v-if="!state.iconStatus && state.pagination.total > 0"
      class="ss-flex ss-flex-wrap ss-p-x-20 ss-m-t-20 ss-col-top"
    >
      <view class="goods-list-box">
        <view class="left-list" v-for="item in state.leftGoodsList" :key="item.id">
          <s-goods-column
            class="goods-md-box"
            size="md"
            :data="item"
            :show-shop-name="true"
            :topRadius="10"
            :bottomRadius="10"
            @click="sheep.$router.go('/pages/goods/index', { id: item.id })"
            @getHeight="mountMasonry($event, 'left')"
          >
            <template v-slot:cart>
              <button size="mini" @tap.stop="sheep.$router.go('/pages/goods/index', { id: item.id })">选规格对比</button>
            </template>
          </s-goods-column>
        </view>
      </view>
      <view class="goods-list-box">
        <view class="right-list" v-for="item in state.rightGoodsList" :key="item.id">
          <s-goods-column
            class="goods-md-box"
            size="md"
            :topRadius="10"
            :bottomRadius="10"
            :data="item"
            :show-shop-name="true"
            @click="sheep.$router.go('/pages/goods/index', { id: item.id })"
            @getHeight="mountMasonry($event, 'right')"
          >
            <template v-slot:cart>
              <button size="mini" @tap.stop="sheep.$router.go('/pages/goods/index', { id: item.id })">选规格对比</button>
            </template>
          </s-goods-column>
        </view>
      </view>
    </view>
    <uni-load-more
      v-if="state.pagination.total > 0"
      :status="state.loadStatus"
      :content-text="{
        contentdown: '上拉加载更多',
      }"
      @tap="loadMore"
    />
    <view v-if="state.loadStatus === 'loading' && !state.pagination.total" class="shop-retry">正在加载商品…</view>
    <view v-else-if="state.listError" class="shop-retry" @tap="getList">商品加载失败，点击重试</view>
    <s-empty v-else-if="state.pagination.total === 0 && !state.shopLoading && !state.shopError" icon="/static/soldout-empty.png" :text="state.shopId ? (state.keyword ? '本店没有匹配的商品' : '本店暂无在售商品') : '暂无商品'" />
  </s-layout>
</template>

<script setup>
  import { reactive } from 'vue';
  import ShopApi from '@/sheep/api/product/shop';
  import { onLoad, onReachBottom } from '@dcloudio/uni-app';
  import sheep from '@/sheep';
  import { concat } from 'lodash-es';
  import { resetPagination } from '@/sheep/helper/utils';
  import SpuApi from '@/sheep/api/product/spu';
  import OrderApi from '@/sheep/api/trade/order';
  import { appendSettlementProduct } from '@/sheep/hooks/useGoods';

  const sys_navBar = sheep.$platform.navbar;
  const userStore = sheep.$store('user');

  const state = reactive({
    pagination: {
      list: [],
      total: 0,
      pageNo: 1,
      pageSize: 6,
    },
    currentSort: undefined,
    currentOrder: undefined,
    currentTab: 0, // 当前选中的 tab
    curFilter: 0, // 当前选中的 list 筛选项
    showFilter: false,
    iconStatus: false, // true - 单列布局；false - 双列布局
    keyword: '',
    categoryId: 0,
    shopId: 0,
    shop: null,
    shopLoading: false,
    shopError: false,
    invalidShopId: false,
    listError: false,
    tabList: [
      {
        name: '综合推荐',
        list: [
          {
            label: '综合推荐',
          },
          {
            label: '价格升序',
            sort: 'price',
            order: true,
          },
          {
            label: '价格降序',
            sort: 'price',
            order: false,
          },
        ],
      },
      {
        name: '销量',
        sort: 'salesCount',
        order: false,
      },
      {
        name: '新品优先',
        sort: 'createTime',
        order: false,
      },
    ],
    loadStatus: '',
    leftGoodsList: [], // 双列布局 - 左侧商品
    rightGoodsList: [], // 双列布局 - 右侧商品
  });

  // 加载瀑布流
  let count = 0;
  let requestId = 0;
  let leftHeight = 0;
  let rightHeight = 0;

  // 处理双列布局 leftGoodsList + rightGoodsList
  function mountMasonry(height = 0, where = 'left') {
    if (where === 'left') {
      leftHeight += height;
    } else {
      rightHeight += height;
    }
    if (!state.pagination.list[count]) {
      return;
    }

    if (leftHeight <= rightHeight) {
      state.leftGoodsList.push(state.pagination.list[count]);
    } else {
      state.rightGoodsList.push(state.pagination.list[count]);
    }
    count++;
  }

  // 清空列表
  function emptyList() {
    requestId++;
    resetPagination(state.pagination);
    state.leftGoodsList = [];
    state.rightGoodsList = [];
    count = 0;
    leftHeight = 0;
    rightHeight = 0;
  }

  // 搜索
  function onSearch(e) {
    state.keyword = e;
    emptyList();
    getList(state.currentSort, state.currentOrder);
  }

  // 点击
  function onTabsChange(e) {
    // 如果点击的是【综合推荐】，则直接展开或者收起筛选项
    if (state.tabList[e.index].list) {
      state.currentTab = e.index;
      state.showFilter = !state.showFilter;
      return;
    }
    state.showFilter = false;

    // 如果点击的是【销量】或者【新品优先】，则直接切换 tab
    if (e.index === state.currentTab) {
      return;
    }

    state.currentTab = e.index;
    state.currentSort = e.sort;
    state.currentOrder = e.order;
    emptyList();
    getList(e.sort, e.order);
  }

  // 点击 tab 的 list 筛选项
  const onFilterItem = (val) => {
    // 如果点击的是当前的筛选项，则直接收起筛选项，不要加载数据
    // 这里选择 tabList[0] 的原因，是目前只有它有 list
    if (
      state.currentSort === state.tabList[0].list[val].sort &&
      state.currentOrder === state.tabList[0].list[val].order
    ) {
      state.showFilter = false;
      return;
    }
    state.showFilter = false;

    // 设置筛选条件
    state.curFilter = val;
    state.tabList[0].name = state.tabList[0].list[val].label;
    state.currentSort = state.tabList[0].list[val].sort;
    state.currentOrder = state.tabList[0].list[val].order;
    // 清空 + 加载数据
    emptyList();
    getList();
  };

  async function getList() {
    if (state.shopLoading || state.shopError) return;
    const currentRequest = ++requestId;
    state.listError = false;
    state.loadStatus = 'loading';
    try {
      const response = await SpuApi.getSpuPage({
        pageNo: state.pagination.pageNo,
        pageSize: state.pagination.pageSize,
        sortField: state.currentSort,
        sortAsc: state.currentOrder,
        categoryId: state.categoryId,
        keyword: state.keyword,
        shopId: state.shopId || undefined,
      });
      if (currentRequest !== requestId) return;
      // request 在网络异常时会返回 false，不能直接解构，否则会导致页面白屏。
      if (!response || response.code !== 0) {
        state.listError = true;
        state.loadStatus = state.pagination.list.length ? 'more' : 'noMore';
        return;
      }
      const pageData = response.data || {};
      const pageList = Array.isArray(pageData.list) ? pageData.list.filter((item) => item?.id) : [];

      // 营销结算信息仅对已登录用户请求。匿名接口在部分旧服务中会触发用户信息调用，
      // 商品列表本身不应因该附加请求失败而中断。
      if (userStore.isLogin && pageList.length) {
        try {
          const settlement = await OrderApi.getSettlementProduct(
            pageList.map((item) => item.id).join(','),
          );
          if (settlement?.code === 0) {
            appendSettlementProduct(pageList, settlement.data);
          }
        } catch (error) {
          console.warn('加载商品营销价格失败，使用基础商品信息', error);
        }
      }

      if (currentRequest !== requestId) return;
      state.pagination.list = concat(state.pagination.list, pageList);
      const total = Number(pageData.total);
      state.pagination.total = Number.isFinite(total) && total >= 0 ? total : state.pagination.list.length;
      state.loadStatus =
        state.pagination.list.length < state.pagination.total ? 'more' : 'noMore';
      if (pageList.length) {
        mountMasonry();
      }
    } catch (error) {
      if (currentRequest !== requestId) return;
      state.listError = true;
      console.warn('加载商品列表失败', error);
      state.loadStatus = state.pagination.list.length ? 'more' : 'noMore';
    }
  }

  // 加载更多
  function loadMore() {
    if (state.shopLoading || state.shopError || state.listError) return;
    if (state.loadStatus === 'noMore' || state.loadStatus === 'loading') {
      return;
    }
    state.pagination.pageNo++;
    getList(state.currentSort, state.currentOrder);
  }

  async function loadShop() {
    if (state.invalidShopId || state.shopLoading) return;
    state.shopLoading = true;
    state.shopError = false;
    emptyList();
    try {
      const result = await ShopApi.get(state.shopId);
      if (result?.code !== 0 || !result.data?.id) throw new Error('店铺不可用');
      state.shop = result.data;
    } catch { state.shopError = true; state.shop = null; }
    finally { state.shopLoading = false; }
    if (!state.shopError) await getList();
  }

  onLoad((options = {}) => {
    const categoryId = Number(options.categoryId);
    state.categoryId = Number.isFinite(categoryId) && categoryId > 0 ? categoryId : 0;
    state.keyword = options.keyword || '';
    if (options.shopId !== undefined) {
      const shopId = Number(options.shopId);
      state.shopId = Number.isSafeInteger(shopId) && shopId > 0 ? shopId : -1;
      if (state.shopId === -1) { state.shopError = true; state.invalidShopId = true; return; }
      loadShop();
      return;
    }
    getList(state.currentSort, state.currentOrder);
  });

  // 上拉加载更多
  onReachBottom(() => {
    loadMore();
  });
</script>

<style lang="scss" scoped>
  .shop-banner { margin: 20rpx; padding: 30rpx; border-radius: 18rpx; background: linear-gradient(120deg, #153f44, #147d6b); color: white; font-size: 26rpx; }
  .shop-title { font-size: 34rpx; font-weight: 600; }.shop-description { margin-top: 16rpx; line-height: 1.7; color: #d2e5dc; font-size: 24rpx; }.shop-tip { margin-top: 20rpx; color: #c0ded0; font-size: 23rpx; }.shop-directory-link { margin-top: 20rpx; font-size: 24rpx; }.shop-retry { padding: 30rpx; text-align: center; color: #147d6b; font-size: 26rpx; }
  .goods-list-box {
    width: 50%;
    box-sizing: border-box;

    .left-list {
      margin-right: 10rpx;
      margin-bottom: 20rpx;
    }

    .right-list {
      margin-left: 10rpx;
      margin-bottom: 20rpx;
    }
  }

  .goods-box {
    &:nth-last-of-type(1) {
      margin-bottom: 0 !important;
    }

    &:nth-child(2n) {
      margin-right: 0;
    }
  }

  .list-icon {
    width: 80rpx;

    .sicon-goods-card {
      font-size: 40rpx;
    }

    .sicon-goods-list {
      font-size: 40rpx;
    }
  }

  .goods-card {
    margin-left: 20rpx;
  }

  .list-filter-tabs {
    background-color: #fff;
  }

  .filter-list-box {
    padding: 28rpx 52rpx;

    .filter-item {
      font-size: 28rpx;
      font-weight: 500;
      color: #333333;
      line-height: normal;
      margin-bottom: 24rpx;

      &:nth-last-child(1) {
        margin-bottom: 0;
      }
    }

    .filter-item-active {
      color: var(--ui-BG-Main);
    }
  }

  .tab-item {
    height: 50px;
    position: relative;
    z-index: 11;

    .tab-title {
      font-size: 30rpx;
    }

    .cur-tab-title {
      font-weight: $font-weight-bold;
    }

    .tab-line {
      width: 60rpx;
      height: 6rpx;
      border-radius: 6rpx;
      position: absolute;
      left: 50%;
      transform: translateX(-50%);
      bottom: 10rpx;
      background-color: var(--ui-BG-Main);
      z-index: 12;
    }
  }
</style>
