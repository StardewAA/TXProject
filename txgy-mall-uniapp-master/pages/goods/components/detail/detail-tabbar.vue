<!-- 商品详情的底部导航 -->
<template>
  <su-fixed bottom placeholder bg="bg-white">
    <view class="ui-tabbar-box">
      <view class="ui-tabbar ss-flex ss-col-center ss-row-between">
        <view
          v-if="collectIcon"
          class="detail-tabbar-item ss-flex ss-flex-col ss-row-center ss-col-center"
          @tap="onFavorite"
        >
          <block v-if="modelValue.favorite">
            <image
              class="item-icon"
              src="/static/icons/mall/star-filled.svg"
              mode="aspectFit"
            />
            <view class="item-title">已收藏</view>
          </block>
          <block v-else>
            <image
              class="item-icon"
              src="/static/icons/mall/star.svg"
              mode="aspectFit"
            />
            <view class="item-title">收藏</view>
          </block>
        </view>
        <view
          v-if="serviceIcon"
          class="detail-tabbar-item ss-flex ss-flex-col ss-row-center ss-col-center"
          @tap="onChat"
        >
          <image
            class="item-icon"
            src="/static/icons/mall/chat.svg"
            mode="aspectFit"
          />
          <view class="item-title">客服</view>
        </view>
        <view
          v-if="shareIcon"
          class="detail-tabbar-item ss-flex ss-flex-col ss-row-center ss-col-center"
          @tap="showShareModal"
        >
          <image
            class="item-icon"
            src="/static/icons/mall/share.svg"
            mode="aspectFit"
          />
          <view class="item-title">分享</view>
        </view>
        <slot></slot>
      </view>
    </view>
  </su-fixed>
</template>

<script setup>
  /**
   *
   * 底部导航
   *
   * @property {String} bg 			 			- 背景颜色Class
   * @property {String} ui 			 			- 自定义样式Class
   * @property {Boolean} noFixed 		 			- 是否定位
   * @property {Boolean} topRadius 		 		- 上圆角
   */
  import { reactive } from 'vue';
  import sheep from '@/sheep';
  import { showAuthModal, showShareModal } from '@/sheep/hooks/useModal';
  import FavoriteApi from '@/sheep/api/product/favorite';

  // 数据
  const state = reactive({});

  // 接收参数
  const props = defineProps({
    modelValue: {
      type: Object,
      default() {},
    },
    bg: {
      type: String,
      default: 'bg-white',
    },
    bgStyles: {
      type: Object,
      default() {},
    },
    ui: {
      type: String,
      default: '',
    },

    noFixed: {
      type: Boolean,
      default: false,
    },
    topRadius: {
      type: Number,
      default: 0,
    },
    collectIcon: {
      type: Boolean,
      default: true,
    },
    serviceIcon: {
      type: Boolean,
      default: true,
    },
    shareIcon: {
      type: Boolean,
      default: true,
    },
  });

  async function onFavorite() {
    if (!props.modelValue?.id) {
      return;
    }
    if (!sheep.$store('user').isLogin) {
      // 收藏接口需要会员上下文；匿名点击只打开登录弹窗，不让拒绝的 Promise
      // 冒泡成未处理异常。
      showAuthModal();
      return;
    }
    try {
      // 情况一：取消收藏
      const response = props.modelValue.favorite
        ? await FavoriteApi.deleteFavorite(props.modelValue.id)
        : await FavoriteApi.createFavorite(props.modelValue.id);
      if (response?.code !== 0) {
        return;
      }
      props.modelValue.favorite = !props.modelValue.favorite;
      sheep.$helper.toast(props.modelValue.favorite ? '收藏成功' : '取消收藏');
    } catch (error) {
      // 请求层负责展示错误提示，组件只需保持当前收藏状态。
    }
  }

  const onChat = () => {
    sheep.$router.go('/pages/chat/index', {
      id: props.modelValue.id,
    });
  };
</script>

<style lang="scss" scoped>
  .ui-tabbar-box {
    box-shadow: 0px -6px 10px 0px rgba(51, 51, 51, 0.2);
  }
  .ui-tabbar {
    display: flex;
    height: 50px;
    background: #fff;

    .detail-tabbar-item {
      width: 100rpx;

      .item-icon {
        width: 40rpx;
        height: 40rpx;
      }

      .item-title {
        font-size: 20rpx;
        font-weight: 500;
        line-height: 20rpx;
        margin-top: 12rpx;
      }
    }
  }
</style>
