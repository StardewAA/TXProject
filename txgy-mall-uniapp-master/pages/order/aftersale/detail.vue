<!-- 售后详情 -->
<template>
  <s-layout title="售后详情" :navbar="state.loaded && !isEmpty(state.info) ? 'inner' : 'normal'">
    <view class="content_box" v-if="state.loaded && !isEmpty(state.info)">
      <!-- 步骤条 -->
      <view
        class="steps-box ss-flex"
        :style="[
          {
            marginTop: '-' + Number(statusBarHeight + 88) + 'rpx',
            paddingTop: Number(statusBarHeight + 88) + 'rpx',
          },
        ]"
      >
        <view class="ss-flex">
          <view class="steps-item" v-for="(item, index) in state.list" :key="index">
            <view class="ss-flex">
              <text
                class="sicon-circleclose"
                v-if="state.list.length - 1 === index && [61, 62, 63].includes(state.info.status)"
              />
              <text
                class="sicon-circlecheck"
                v-else
                :class="state.active >= index ? 'activity-color' : 'info-color'"
              />

              <view
                v-if="state.list.length - 1 !== index"
                class="line"
                :class="state.active >= index ? 'activity-bg' : 'info-bg'"
              />
            </view>
            <view
              class="steps-item-title"
              :class="state.active >= index ? 'activity-color' : 'info-color'"
            >
              {{ item.title }}
            </view>
          </view>
        </view>
      </view>

      <!-- 服务状态 -->
      <view
        class="status-box ss-flex ss-col-center ss-row-between ss-m-x-20"
        @tap="sheep.$router.go('/pages/order/aftersale/log', { id: state.id })"
      >
        <view class="">
          <view class="status-text">
            {{ formatAfterSaleStatusDescription(state.info) }}
          </view>
          <view class="status-time">
            {{ sheep.$helper.timeFormat(state.info.updateTime, 'yyyy-mm-dd hh:MM:ss') }}
          </view>
        </view>
        <text class="ss-iconfont _icon-forward" style="color: #666" />
      </view>

      <!-- 退款金额 -->
      <view class="aftersale-money ss-flex ss-col-center ss-row-between">
        <view class="aftersale-money--title">退款总额</view>
        <view class="aftersale-money--num">￥{{ fen2yuan(state.info.refundPrice) }}</view>
      </view>
      <!-- 服务商品 -->
      <view class="order-shop">
        <view class="shop-name">店铺：{{ state.info.shopName || '平台自营' }}</view>
        <s-goods-item
          :img="state.info.picUrl"
          :title="state.info.spuName"
          :titleWidth="480"
          :skuText="(state.info.properties || []).map((property) => property.valueName).join(' ')"
          :num="state.info.count"
        />
      </view>

      <!-- 服务内容 -->
      <view class="aftersale-content">
        <view class="aftersale-item ss-flex ss-col-center">
          <view class="item-title">服务单号：</view>
          <view class="item-content ss-m-r-16">{{ state.info.no }}</view>
          <button class="ss-reset-button copy-btn" @tap="onCopy">复制</button>
        </view>
        <view class="aftersale-item ss-flex ss-col-center">
          <view class="item-title">申请时间：</view>
          <view class="item-content">
            {{ sheep.$helper.timeFormat(state.info.createTime, 'yyyy-mm-dd hh:MM:ss') }}
          </view>
        </view>
        <view class="aftersale-item ss-flex ss-col-center">
          <view class="item-title">售后类型：</view>
          <view class="item-content">{{ state.info.way === 10 ? '仅退款' : '退款退货' }}</view>
        </view>
        <view class="aftersale-item ss-flex ss-col-center">
          <view class="item-title">申请原因：</view>
          <view class="item-content">{{ state.info.applyReason }}</view>
        </view>
        <view class="aftersale-item ss-flex ss-col-center">
          <view class="item-title">相关描述：</view>
          <view class="item-content">{{ state.info.applyDescription }}</view>
        </view>
      </view>
    </view>

    <!-- 操作区 -->
    <s-empty
      v-if="state.loaded && isEmpty(state.info)"
      icon="/static/order-empty.png"
      text="暂无该订单售后详情"
    />
    <su-fixed bottom placeholder bg="bg-white" v-if="state.loaded && !isEmpty(state.info)">
      <view class="foot_box">
        <button
          class="ss-reset-button btn"
          v-if="state.info.buttons?.includes('cancel')"
          :disabled="state.cancelling"
          @tap="onApply(state.info.id)"
        >
          取消申请
        </button>
        <button
          class="ss-reset-button btn"
          v-if="state.info.buttons?.includes('delivery')"
          @tap="sheep.$router.go('/pages/order/aftersale/return-delivery', { id: state.info.id })"
        >
          填写退货
        </button>
        <button
          class="ss-reset-button contcat-btn btn"
          @tap="sheep.$router.go('/pages/chat/index')"
        >
          联系客服
        </button>
      </view>
    </su-fixed>
  </s-layout>
</template>

<script setup>
  import sheep from '@/sheep';
  import { onLoad } from '@dcloudio/uni-app';
  import { reactive } from 'vue';
  import { isEmpty } from 'lodash-es';
  import {
    fen2yuan,
    formatAfterSaleStatusDescription,
    handleAfterSaleButtons,
  } from '@/sheep/hooks/useGoods';
  import AfterSaleApi from '@/sheep/api/trade/afterSale';

  const statusBarHeight = sheep.$platform.device.statusBarHeight * 2;
  const headerBg = sheep.$url.css('/static/img/shop/order/order_bg.png');
  const state = reactive({
    id: 0, // 售后编号
    info: {}, // 收货信息
    loading: false,
    loaded: false,
    cancelling: false,
    active: 0, // 在 list 的激活位置
    list: [
      {
        title: '提交申请',
      },
      {
        title: '处理中',
      },
      {
        title: '完成',
      },
    ], // 时间轴
  });

  function onApply(id) {
    if (state.cancelling || !id) {
      return;
    }
    uni.showModal({
      title: '提示',
      content: '确定要取消此申请吗？',
      success: async function (res) {
        if (!res.confirm) {
          return;
        }
        state.cancelling = true;
        try {
          const response = await AfterSaleApi.cancelAfterSale(id);
          if (response?.code === 0) {
            await getDetail(id);
          }
        } catch (error) {
          console.warn('取消售后申请失败', error);
          sheep.$helper.toast('取消售后申请失败');
        } finally {
          state.cancelling = false;
        }
      },
    });
  }

  // 复制
  const onCopy = () => {
    if (state.info?.no) {
      sheep.$helper.copyText(state.info.no);
    }
  };

  async function getDetail(id) {
    if (!id) {
      state.info = null;
      state.loaded = true;
      return;
    }
    state.loading = true;
    state.loaded = false;
    state.info = {};
    try {
      const response = await AfterSaleApi.getAfterSale(id);
      if (response?.code !== 0 || !response.data || typeof response.data !== 'object') {
        state.info = null;
        state.active = 0;
        return;
      }
      const source = response.data;
      state.info = {
        ...source,
        // Long 编号保留原始值，状态及金额字段统一成可比较的数字
        id: source.id,
        status: Number(source.status),
        way: Number(source.way),
        shopName: String(source.shopName || '').trim() || '平台自营',
        properties: Array.isArray(source.properties) ? source.properties : [],
        buttons: Array.isArray(source.buttons) ? source.buttons : [],
        refundPrice: Number(source.refundPrice) || 0,
        count: Number(source.count) || 0,
      };
      handleAfterSaleButtons(state.info);

      // 处理时间轴
      if ([10].includes(state.info.status)) {
        state.active = 0;
      } else if ([20, 30].includes(state.info.status)) {
        state.active = 1;
      } else if ([40, 50].includes(state.info.status)) {
        state.active = 2;
      } else if ([61, 62, 63].includes(state.info.status)) {
        state.active = 2;
      } else {
        state.active = 0;
      }
    } catch (error) {
      console.warn('加载售后详情失败', error);
      state.info = null;
      state.active = 0;
    } finally {
      state.loading = false;
      state.loaded = true;
    }
  }

  onLoad((options = {}) => {
    const id = String(options.id || '').trim();
    if (!/^\d+$/.test(id) || /^0+$/.test(id)) {
      sheep.$helper.toast(`缺少订单信息，请检查`);
      state.info = null;
      state.loaded = true;
      return;
    }
    // Long 类型编号可能超过 JS 安全整数范围，按字符串传递
    state.id = id;
    getDetail(id);
  });
</script>

<style lang="scss" scoped>
  // 步骤条
  .steps-box {
    width: 100%;
    height: 190rpx;
    background: v-bind(headerBg) no-repeat,
      linear-gradient(90deg, var(--ui-BG-Main), var(--ui-BG-Main-gradient));
    background-size: 750rpx 100%;
    padding-left: 72rpx;

    .steps-item {
      .sicon-circleclose {
        font-size: 24rpx;
        color: #fff;
      }

      .sicon-circlecheck {
        font-size: 24rpx;
      }

      .steps-item-title {
        font-size: 24rpx;
        font-weight: 400;
        margin-top: 16rpx;
        margin-left: -36rpx;
        width: 100rpx;
        text-align: center;
      }
    }
  }

  .activity-color {
    color: #fff;
  }

  .info-color {
    color: rgba(#fff, 0.4);
  }

  .activity-bg {
    background: #fff;
  }

  .info-bg {
    background: rgba(#fff, 0.4);
  }

  .line {
    width: 270rpx;
    height: 4rpx;
  }

  // 服务状态
  .status-box {
    position: relative;
    z-index: 3;
    background-color: #fff;
    border-radius: 20rpx 20rpx 0px 0px;
    padding: 20rpx;
    margin-top: -20rpx;

    .status-text {
      font-size: 28rpx;

      font-weight: 500;
      color: rgba(51, 51, 51, 1);
      margin-bottom: 20rpx;
    }

    .status-time {
      font-size: 24rpx;

      font-weight: 400;
      color: rgba(153, 153, 153, 1);
    }
  }

  // 退款金额
  .aftersale-money {
    background-color: #fff;
    height: 98rpx;
    padding: 0 20rpx;
    margin: 20rpx;

    .aftersale-money--title {
      font-size: 28rpx;

      font-weight: 500;
      color: rgba(51, 51, 51, 1);
    }

    .aftersale-money--num {
      font-size: 28rpx;
      font-family: OPPOSANS;
      font-weight: 500;
      color: #ff3000;
    }
  }

  // order-shop
  .order-shop {
    padding: 20rpx;
    background-color: #fff;
    margin: 0 20rpx 20rpx 20rpx;

    .shop-name {
      margin-bottom: 12rpx;
      color: #666;
      font-size: 24rpx;
    }
  }

  // 服务内容
  .aftersale-content {
    background-color: #fff;
    padding: 20rpx;
    margin: 0 20rpx;

    .aftersale-item {
      height: 60rpx;

      .copy-btn {
        background: #eeeeee;
        color: #333;
        border-radius: 20rpx;
        width: 75rpx;
        height: 40rpx;
        font-size: 22rpx;
      }

      .item-title {
        color: #999;
        font-size: 28rpx;
      }

      .item-content {
        color: #333;
        font-size: 28rpx;
      }
    }
  }

  // 底部功能
  .foot_box {
    height: 100rpx;
    background-color: #fff;
    display: flex;
    align-items: center;
    justify-content: flex-end;

    .btn {
      width: 160rpx;
      line-height: 60rpx;
      background: rgba(238, 238, 238, 1);
      border-radius: 30rpx;
      padding: 0;
      margin-right: 20rpx;
      font-size: 26rpx;

      font-weight: 400;
      color: rgba(51, 51, 51, 1);
    }
  }
</style>
