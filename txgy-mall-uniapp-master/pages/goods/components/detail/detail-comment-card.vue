<!-- 商品评论的卡片 -->
<template>
  <view class="detail-comment-card bg-white">
    <view class="card-header ss-flex ss-col-center ss-row-between ss-p-b-30">
      <view class="ss-flex ss-col-center">
        <view class="line"></view>
        <view class="title ss-m-l-20 ss-m-r-10">评价</view>
        <view class="des">({{ state.total }})</view>
      </view>
      <view
        class="ss-flex ss-col-center"
        @tap="sheep.$router.go('/pages/goods/comment/list', { id: goodsId })"
        v-if="state.commentList.length > 0"
      >
        <button class="ss-reset-button more-btn">查看全部</button>
        <text class="cicon-forward" />
      </view>
    </view>
    <!-- 评论列表 -->
    <view class="card-content">
      <view class="comment-box ss-p-y-30" v-for="item in state.commentList" :key="item.id">
        <comment-item :item="item" />
      </view>
      <s-empty
        v-if="state.commentList.length === 0"
        paddingTop="0"
        icon="/static/comment-empty.png"
        text="期待您的第一个评价"
      />
    </view>
  </view>
</template>

<script setup>
  import { reactive, watch } from 'vue';
  import sheep from '@/sheep';
  import CommentApi from '@/sheep/api/product/comment';
  import commentItem from './comment-item.vue';

  const props = defineProps({
    goodsId: {
      type: [Number, String],
      default: 0,
    },
  });

  const state = reactive({
    commentList: [], // 评论列表，只展示最近的 3 条
    total: 0, // 总评论数
  });

  async function getComment(id) {
    // The detail page renders its children while the SPU request is still in
    // flight. Avoid calling the API with the initial placeholder id (0), and
    // tolerate an unavailable/empty response so a comment card cannot blank
    // the whole product page.
    const spuId = Number(id);
    if (!Number.isFinite(spuId) || spuId <= 0) {
      state.commentList = [];
      state.total = 0;
      return;
    }

    try {
      const res = await CommentApi.getCommentPage(spuId, 1, 3, 0);
      if (res?.code !== 0 || !res?.data) {
        state.commentList = [];
        state.total = 0;
        return;
      }
      state.commentList = Array.isArray(res.data.list) ? res.data.list : [];
      state.total = Number(res.data.total) || 0;
    } catch (error) {
      // Comments are supplementary; an API failure must not interrupt the
      // product detail/checkout flow.
      state.commentList = [];
      state.total = 0;
    }
  }

  // Re-fetch once the asynchronous detail request supplies a real SPU id.
  watch(
    () => props.goodsId,
    (id) => getComment(id),
    { immediate: true },
  );
</script>

<style lang="scss" scoped>
  .detail-comment-card {
    margin: 0 20rpx 20rpx 20rpx;
    padding: 20rpx 20rpx 0 20rpx;
    .card-header {
      .line {
        width: 6rpx;
        height: 30rpx;
        background: linear-gradient(180deg, var(--ui-BG-Main) 0%, var(--ui-BG-Main-gradient) 100%);
        border-radius: 3rpx;
      }

      .title {
        font-size: 30rpx;
        font-weight: bold;
        line-height: normal;
      }

      .des {
        font-size: 24rpx;
        color: $dark-9;
      }

      .more-btn {
        font-size: 24rpx;
        color: var(--ui-BG-Main);
        line-height: normal;
      }

      .cicon-forward {
        font-size: 24rpx;
        line-height: normal;
        color: var(--ui-BG-Main);
        margin-top: 4rpx;
      }
    }
  }
  .comment-box {
    border-bottom: 2rpx solid #eeeeee;
    &:last-child {
      border: none;
    }
  }
</style>
