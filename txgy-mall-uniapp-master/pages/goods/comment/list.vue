<!-- 商品评论的分页 -->
<template>
  <s-layout title="全部评论">
    <su-tabs
      :list="state.type"
      :scrollable="false"
      @change="onTabsChange"
      :current="state.currentTab"
    />
    <!-- 评论列表 -->
    <view class="ss-m-t-20">
      <view
        class="list-item"
        v-for="(item, index) in state.pagination.list"
        :key="item.id || item.orderItemId || index"
      >
        <comment-item :item="item" />
      </view>
    </view>
    <s-empty v-if="state.pagination.total === 0" text="暂无数据" icon="/static/data-empty.png" />
    <!-- 下拉 -->
    <uni-load-more
      icon-type="auto"
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
  import CommentApi from '@/sheep/api/product/comment';
  import { onLoad, onReachBottom } from '@dcloudio/uni-app';
  import { reactive } from 'vue';
  import { concat } from 'lodash-es';
  import commentItem from '../components/detail/comment-item.vue';

  const state = reactive({
    id: 0, // 商品 SPU 编号
    type: [
      { type: 0, name: '全部' },
      { type: 1, name: '好评' },
      { type: 2, name: '中评' },
      { type: 3, name: '差评' },
    ],
    currentTab: 0, // 选中的 TAB
    loadStatus: 'more',
    loading: false,
    pagination: {
      list: [],
      total: 0,
      pageNo: 1,
      pageSize: 8,
    },
  });

  // 切换选项卡
  function onTabsChange(e = {}) {
    const index = Number(e.index);
    if (!Number.isInteger(index) || index < 0 || index >= state.type.length) {
      return;
    }
    state.currentTab = index;
    // 加载列表
    state.pagination.pageNo = 1;
    state.pagination.list = [];
    state.pagination.total = 0;
    getList();
  }

  async function getList() {
    if (state.loading) return false;
    state.loading = true;
    // 加载列表
    state.loadStatus = 'loading';
    let res;
    try {
      res = await CommentApi.getCommentPage(
        String(state.id),
        state.pagination.pageNo,
        state.pagination.pageSize,
        state.type[state.currentTab].type,
      );
      if (!res || res.code !== 0) {
        state.loadStatus = state.pagination.list.length ? 'more' : 'noMore';
        return false;
      }
      const data = res.data && typeof res.data === 'object' ? res.data : {};
      const list = Array.isArray(data.list) ? data.list.filter(Boolean) : [];
      state.pagination.list = concat(state.pagination.list, list);
      state.pagination.total = Number(data.total) || 0;
      state.loadStatus = state.pagination.list.length < state.pagination.total ? 'more' : 'noMore';
      return true;
    } catch (error) {
      console.warn('加载商品评论失败', error);
      state.loadStatus = state.pagination.list.length ? 'more' : 'noMore';
      return false;
    } finally {
      if (state.loadStatus === 'loading') {
        state.loadStatus = state.pagination.list.length ? 'more' : 'noMore';
      }
      state.loading = false;
    }
  }

  // 加载更多
  async function loadMore() {
    if (state.loadStatus === 'noMore' || state.loadStatus === 'loading') {
      return;
    }
    if (state.loading) return;
    const nextPage = state.pagination.pageNo + 1;
    state.pagination.pageNo = nextPage;
    const loaded = await getList();
    if (!loaded) state.pagination.pageNo = Math.max(1, nextPage - 1);
  }

  onLoad((options = {}) => {
    const id = String(options.id || '').trim();
    if (!/^\d+$/.test(id) || /^0+$/.test(id)) {
      state.loadStatus = 'noMore';
      return;
    }
    state.id = id;
    getList();
  });

  // 上拉加载更多
  onReachBottom(() => {
    loadMore();
  });
</script>

<style lang="scss" scoped>
  .list-item {
    padding: 32rpx 30rpx 20rpx 20rpx;
    background: #fff;

    .avatar {
      width: 52rpx;
      height: 52rpx;
      border-radius: 50%;
    }

    .nickname {
      font-size: 26rpx;
      font-weight: 500;
      color: #999999;
    }

    .create-time {
      font-size: 24rpx;
      font-weight: 500;
      color: #c4c4c4;
    }

    .content-title {
      font-size: 26rpx;
      font-weight: 400;
      color: #666666;
      line-height: 42rpx;
    }

    .content-img {
      width: 174rpx;
      height: 174rpx;
    }

    .cicon-info-o {
      font-size: 26rpx;
      color: #c4c4c4;
    }

    .foot-title {
      font-size: 24rpx;
      font-weight: 500;
      color: #999999;
    }
  }

  .btn-box {
    width: 100%;
    height: 120rpx;
    background: #fff;
    border-top: 2rpx solid #eee;
  }

  .tab-btn {
    width: 130rpx;
    height: 62rpx;
    background: #eeeeee;
    border-radius: 31rpx;
    font-size: 28rpx;
    font-weight: 400;
    color: #999999;
    border: 1px solid #e5e5e5;
    margin-right: 10rpx;
  }
</style>
