<!-- 我的商品收藏 -->
<template>
  <s-layout title="商品收藏">
    <view class="cart-box ss-flex ss-flex-col ss-row-between">
      <!-- 头部 -->
      <view class="cart-header ss-flex ss-col-center ss-row-between ss-p-x-30">
        <view class="header-left ss-flex ss-col-center ss-font-26">
          共
          <text class="goods-number ui-TC-Main ss-flex">{{ state.pagination.total }}</text> 件商品
        </view>
        <view class="header-right">
          <button
            v-if="state.editMode && state.pagination.total"
            class="ss-reset-button"
            @tap="state.editMode = false"
          >
            取消
          </button>
          <button
            v-if="!state.editMode && state.pagination.total"
            class="ss-reset-button ui-TC-Main"
            @tap="state.editMode = true"
          >
            编辑
          </button>
        </view>
      </view>
      <!-- 内容 -->
      <view class="cart-content">
        <view
          class="goods-box ss-r-10 ss-m-b-14"
          v-for="item in state.pagination.list"
          :key="item.id"
        >
          <view class="ss-flex ss-col-center">
            <label
              class="check-box ss-flex ss-col-center ss-p-l-10"
              v-if="state.editMode"
              @tap="onSelect(item.spuId)"
            >
              <radio
                :checked="state.selectedCollectList.includes(item.spuId)"
                color="var(--ui-BG-Main)"
                style="transform: scale(0.8)"
                @tap.stop="onSelect(item.spuId)"
              />
            </label>
            <view v-if="collectionUnavailable(item)" class="unavailable-product">
              <view class="unavailable-title">商品暂不可购买</view>
              <view class="unavailable-reason">{{ collectionUnavailableReason(item) }}</view>
              <view class="unavailable-help">仍可通过“编辑”取消收藏，不展示待审价格和内容。</view>
            </view>
            <s-goods-item
              v-else
              :title="item.spuName"
              :img="item.picUrl"
              :price="item.price"
              priceColor="#FF3000"
              :titleWidth="400"
              @tap="openGoods(item)"
            />
          </view>
        </view>
      </view>

      <!-- 底部 -->
      <su-fixed bottom :val="0" placeholder v-show="state.editMode">
        <view class="cart-footer ss-flex ss-col-center ss-row-between ss-p-x-30 border-bottom">
          <view class="footer-left ss-flex ss-col-center">
            <label class="check-box ss-flex ss-col-center ss-p-r-30" @tap="onSelectAll">
              <radio
                :checked="state.selectAll"
                color="var(--ui-BG-Main)"
                style="transform: scale(0.7)"
                @tap.stop="onSelectAll"
              />
              <view> 全选 </view>
            </label>
          </view>
          <view class="footer-right">
            <button
              class="ss-reset-button ui-BG-Main-Gradient pay-btn ss-font-28 ui-Shadow-Main"
              @tap="onCancel"
            >
              取消收藏
            </button>
          </view>
        </view>
      </su-fixed>
    </view>
    <uni-load-more
      v-if="state.pagination.total > 0"
      :status="state.loadStatus"
      :content-text="{
        contentdown: '上拉加载更多',
      }"
      @tap="loadMore"
    />
    <s-empty v-if="state.pagination.total === 0" text="暂无收藏" icon="/static/collect-empty.png" />
  </s-layout>
</template>

<script setup>
  import sheep from '@/sheep';
  import { reactive } from 'vue';
  import { onLoad, onReachBottom } from '@dcloudio/uni-app';
  import { concat } from 'lodash-es';
  import FavoriteApi from '@/sheep/api/product/favorite';
  import { resetPagination } from '@/sheep/helper/utils';
  import { collectionCanOpen, collectionUnavailable, collectionUnavailableReason } from '@/sheep/helper/collection-product.mjs';

  const sys_navBar = sheep.$platform.navbar;
  function openGoods(item) {
    if (state.editMode || !collectionCanOpen(item)) return;
    sheep.$router.go('/pages/goods/index', { id: item.spuId });
  }

  const state = reactive({
    pagination: {
      list: [],
      total: 0,
      pageNo: 1,
      pageSize: 6,
    },
    loadStatus: '',

    editMode: false,
    selectedCollectList: [], // 选中的 SPU 数组
    selectAll: false,
  });

  async function getData() {
    state.loadStatus = 'loading';
    try {
      const response = await FavoriteApi.getFavoritePage({
        pageNo: state.pagination.pageNo,
        pageSize: state.pagination.pageSize,
      });
      if (response?.code !== 0) {
        state.loadStatus = state.pagination.list.length ? 'more' : 'noMore';
        return;
      }
      const pageData = response.data || {};
      const pageList = Array.isArray(pageData.list) ? pageData.list.filter(Boolean) : [];
      state.pagination.list = concat(state.pagination.list, pageList);
      const total = Number(pageData.total);
      state.pagination.total = Number.isFinite(total) && total >= 0 ? total : state.pagination.list.length;
      state.loadStatus = state.pagination.list.length < state.pagination.total ? 'more' : 'noMore';
    } catch (error) {
      state.loadStatus = state.pagination.list.length ? 'more' : 'noMore';
    }
  }

  // 单选选中
  const onSelect = (spuId) => {
    if (!state.selectedCollectList.includes(spuId)) {
      state.selectedCollectList.push(spuId);
    } else {
      state.selectedCollectList.splice(state.selectedCollectList.indexOf(spuId), 1);
    }
    state.selectAll = state.selectedCollectList.length === state.pagination.list.length;
  };

  // 全选
  const onSelectAll = () => {
    state.selectAll = !state.selectAll;
    if (!state.selectAll) {
      state.selectedCollectList = [];
    } else {
      state.selectedCollectList = state.pagination.list.map((item) => item.spuId);
    }
  };

  async function onCancel() {
    if (!state.selectedCollectList.length) {
      return;
    }
    // 取消收藏
    for (const spuId of state.selectedCollectList) {
      try {
        await FavoriteApi.deleteFavorite(spuId);
      } catch (error) {
        // 继续处理其余收藏项，最后重新拉取列表。
      }
    }

    // 清空选择 + 重新加载
    state.editMode = false;
    state.selectedCollectList = [];
    state.selectAll = false;
    resetPagination(state.pagination);
    await getData();
  }

  // 加载更多
  function loadMore() {
    if (state.loadStatus === 'noMore' || state.loadStatus === 'loading') {
      return;
    }
    state.pagination.pageNo++;
    getData();
  }

  onReachBottom(() => {
    loadMore();
  });

  onLoad(() => {
    getData();
  });
</script>

<style lang="scss" scoped>
  .unavailable-product { flex: 1; min-width: 0; padding: 30rpx; background: #f5f7f8; border-radius: 10rpx; }
  .unavailable-title { color: #475569; font-size: 30rpx; font-weight: 600; }
  .unavailable-reason { color: #64748b; font-size: 26rpx; margin-top: 18rpx; line-height: 1.6; word-break: break-all; }
  .unavailable-help { color: #94a3b8; font-size: 22rpx; margin-top: 12rpx; }
  .cart-box {
    .cart-header {
      height: 70rpx;
      background-color: #f6f6f6;
      width: 100%;
      position: fixed;
      left: 0;
      top: v-bind('sys_navBar') rpx;
      z-index: 1000;
      box-sizing: border-box;
    }

    .cart-footer {
      height: 100rpx;
      background-color: #fff;

      .pay-btn {
        height: 80rpx;
        line-height: 80rpx;
        border-radius: 40rpx;
        padding: 0 40rpx;
        min-width: 200rpx;
      }
    }

    .cart-content {
      width: 100%;
      margin-top: 70rpx;
      padding: 0 20rpx;
      box-sizing: border-box;
      .goods-box {
        background-color: #fff;
        &:last-child {
          margin-bottom: 40rpx;
        }
      }
    }
  }
</style>
