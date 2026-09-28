<!-- 评价  -->
<template>
  <s-layout title="评价">
    <view v-if="state.error" class="comment-notice">{{ state.error }}</view>
    <button class="refresh-comments" :disabled="state.loading || state.submitting || state.expired" @tap="flow?.load()">刷新核验待评价项目</button>
    <s-service-entry v-if="state.orderInfo.id && !state.expired && !state.loading && !state.needsRefresh" resource-type="ORDER" :resource-id="state.orderInfo.id" :disabled="state.submitting" />
    <view v-if="state.loading" class="comment-notice">正在读取待评价商品…</view>
    <view v-else-if="!state.commentList.length && !state.needsRefresh" class="comment-notice">{{ state.expired ? '登录账号已变化，请重新进入订单评价' : '当前没有可评价的商品，请从已收货订单进入' }}</view>
    <view>
      <view v-for="(item, index) in state.orderInfo.items" :key="item.id">
        <view>
          <view class="commont-from-wrap">
            <!-- 评价商品 -->
            <s-goods-item
              :img="item.picUrl"
              :title="item.spuName"
              :skuText="(Array.isArray(item.properties) ? item.properties : []).map((property) => property.valueName).join(' ')"
              :price="item.payPrice"
              :num="item.count"
            />
          </view>

          <view class="form-item">
            <!-- 评分 -->
            <view class="star-box ss-flex ss-col-center">
              <view class="star-title ss-m-r-40">商品质量</view>
              <uni-rate v-model="state.commentList[index].descriptionScores" :disabled="state.submitting || state.needsRefresh" />
            </view>
            <view class="star-box ss-flex ss-col-center">
              <view class="star-title ss-m-r-40">服务态度</view>
              <uni-rate v-model="state.commentList[index].benefitScores" :disabled="state.submitting || state.needsRefresh" />
            </view>
            <!-- 评价 -->
            <view class="area-box">
              <uni-easyinput
                :inputBorder="false"
                type="textarea"
                maxlength="120"
                autoHeight
                v-model="state.commentList[index].content"
                :disabled="state.submitting || state.needsRefresh"
                placeholder="宝贝满足你的期待吗？说说你的使用心得，分享给想买的他们吧~"
              />
              <view class="img-box">
                <s-uploader v-if="!state.submitting && !state.needsRefresh"
                  v-model:url="state.commentList[index].picUrls"
                  directory="mall/comment"
                  fileMediatype="image"
                  limit="9"
                  mode="grid"
                  :imageStyles="{ width: '168rpx', height: '168rpx' }"
                />
              </view>
            </view>
            <view class="checkbox-container">
              <checkbox-group @change="(event) => toggleAnonymous(index, event)">
                <label>
                  <checkbox value="anonymousChecked" :disabled="state.submitting || state.needsRefresh" />
                  匿名评论
                </label>
              </checkbox-group>
            </view>
          </view>
        </view>
      </view>
    </view>
    <su-fixed v-if="state.commentList.length" bottom placeholder>
      <view class="foot_box ss-flex ss-row-center ss-col-center">
        <button class="ss-reset-button post-btn ui-BG-Main-Gradient ui-Shadow-Main" :disabled="state.submitting || state.loading || state.needsRefresh || state.expired" @tap="onSubmit">
          发布剩余评价（{{ state.commentList.length }}）
        </button>
      </view>
    </su-fixed>
  </s-layout>
</template>

<script setup>
  import sheep from '@/sheep';
  import { onLoad, onUnload } from '@dcloudio/uni-app';
  import { reactive } from 'vue';
  import OrderApi from '@/sheep/api/trade/order';
  import { captureTenantOperation, isTenantOperationCurrent } from '@/sheep/helper/tenant-runtime';
  import { createOrderCommentFlow } from '@/sheep/helper/order-comment-flow.mjs';

  const state = reactive({
    orderInfo: { items: [] },
    commentList: [],
    id: null,
    loading: false,
    submitting: false,
    error: '', needsRefresh: false, expired: false,
  });
  let flow, operation;
  function sessionChanged() { if (operation && !isTenantOperationCurrent(operation)) { state.expired = true; flow?.dispose(); } }
  uni.$on('mall:session-changed', sessionChanged);
  onUnload(() => { flow?.dispose(); uni.$off('mall:session-changed', sessionChanged); });

  /**
   * 切换是否匿名
   *
   * @param commentIndex  当前评论下标
   * @param event 复选框事件
   */
  function toggleAnonymous(commentIndex, event) {
    state.commentList[commentIndex].anonymous = event.detail.value[0] === 'anonymousChecked';
  }

  /**
   * 发布评论
   *
   * @returns {Promise<void>}
   */
  async function onSubmit() {
    if (await flow?.submit() && !state.expired && isTenantOperationCurrent(operation)) sheep.$router.back();
  }

  onLoad(async (options) => {
    if (!options?.id) {
      sheep.$helper.toast(`缺少订单信息，请检查`);
      return;
    }
    state.id = String(options.id);
    operation = captureTenantOperation();
    flow = createOrderCommentFlow(state, {
      isCurrent: () => !state.expired && isTenantOperationCurrent(operation),
      readOrder: async id => { const response = await OrderApi.getOrderDetail(id); if (response?.code !== 0 || !response.data) throw new Error(response?.msg || '订单读取失败'); return response.data; },
      sendComment: async data => { const response = await OrderApi.createOrderItemComment(data); if (response?.code !== 0) throw new Error(response?.msg || '评价提交结果未确认'); },
    });
    await flow.load();
  });
</script>

<style lang="scss" scoped>
  .comment-notice { padding: 24rpx; color: #8e5428; background: #fff7ee; line-height: 1.7; }
  .refresh-comments { font-size: 26rpx; margin: 20rpx; }
  // 评价商品
  .goods-card {
    margin: 10rpx 0;
    padding: 20rpx;
    background: #fff;
  }

  // 评论，选择图片
  .form-item {
    background: #fff;

    .star-box {
      height: 100rpx;
      padding: 0 25rpx;
    }

    .star-title {
      font-weight: 600;
    }
  }

  .area-box {
    width: 690rpx;
    min-height: 306rpx;
    background: rgba(249, 250, 251, 1);
    border-radius: 20rpx;
    padding: 28rpx;
    margin: auto;

    .img-box {
      margin-top: 20rpx;
    }
  }

  .checkbox-container {
    padding: 10rpx;
  }

  .post-btn {
    width: 690rpx;
    line-height: 80rpx;
    border-radius: 40rpx;
    color: rgba(#fff, 0.9);
    margin-bottom: 20rpx;
  }
</style>
