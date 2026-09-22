<!-- 装修用户组件：用户卡片 -->
<template>
  <view class="ss-user-info-wrap ss-p-t-50" :style="[bgStyle, { marginLeft: `${data.space}px` }]">
    <view class="ss-flex ss-col-center ss-row-between ss-m-b-20">
      <view class="left-box ss-flex ss-col-center ss-m-l-36">
        <view class="avatar-box ss-m-r-24">
          <image class="avatar-img" :src="
              isLogin && userInfo.avatar
                ? sheep.$url.cdn(userInfo.avatar)
                : sheep.$url.static('/static/img/shop/default_avatar.png')"
                 mode="aspectFill" @tap="sheep.$router.go('/pages/user/info')">
          </image>
        </view>
        <view>
          <view class="nickname-box ss-flex ss-col-center">
            <view class="nick-name ss-m-r-20" @tap="handleLogin">{{ userInfo?.nickname || nickname }}</view>
            <text v-if="isLogin" class="identity-title">{{ identityTitle }}</text>
          </view>
        </view>
      </view>
      <view class="right-box ss-m-r-52">
        <button class="ss-reset-button" @tap="showSharePoster">
          <text class="sicon-qrcode"></text>
        </button>
      </view>
    </view>

    <!-- 提示绑定手机号 先隐藏 txgy 需要再修改 -->
    <view
      class="bind-mobile-box ss-flex ss-row-between ss-col-center"
      v-if="isLogin && !userInfo.mobile"
    >
      <view class="ss-flex">
        <text class="cicon-mobile-o" />
        <view class="mobile-title ss-m-l-20"> 点击绑定手机号确保账户安全</view>
      </view>
      <button class="ss-reset-button bind-btn" @tap="onBind">去绑定</button>
    </view>
  </view>
</template>

<script setup>
  /**
   * 用户卡片
   *
   * @property {Number} leftSpace                  - 容器左间距
   * @property {Number} rightSpace                  - 容器右间距
   *
   * @property {String} avatar          - 头像
   * @property {String} nickname          - 昵称
   * @property {String} vip              - 等级
   * @property {String} collectNum        - 收藏数
   * @property {String} likeNum          - 点赞数
   *
   *
   */
  import { computed } from 'vue';
  import sheep from '@/sheep';
  import {
    showSharePoster,
    showAuthModal,
  } from '@/sheep/hooks/useModal';

  // 用户信息
  const userInfo = computed(() => sheep.$store('user').userInfo);

  // 是否登录
  const isLogin = computed(() => sheep.$store('user').isLogin);
  // 接收参数
  const props = defineProps({
    // 装修数据
    data: {
      type: Object,
      default: () => ({}),
    },
    // 装修样式
    styles: {
      type: Object,
      default: () => ({}),
    },
    // 头像
    avatar: {
      type: String,
      default: '',
    },
    nickname: {
      type: String,
      default: '请先登录',
    },
    identityTitle: {
      type: String,
      default: '普通用户',
    },
    vip: {
      type: [String, Number],
      default: '1',
    },
    collectNum: {
      type: [String, Number],
      default: '1',
    },
    likeNum: {
      type: [String, Number],
      default: '1',
    },
  });

  // 设置背景样式
  const bgStyle = computed(() => {
    // 直接从 props.styles 解构
    const { bgType, bgImg, bgColor } = props.styles;

    // 根据 bgType 返回相应的样式
    if (bgType === 'img') {
      return {
        background: `url(${bgImg}) no-repeat top center / 100% 100%`,
      };
    }
    if (bgColor) {
      return {
        backgroundColor: bgColor,
      };
    }
    return {};
  });

  // 绑定手机号
  function onBind() {
    showAuthModal('changeMobile');
  }

  // 未登录时点击“请先登录”打开登录弹窗
  function handleLogin() {
    if (!isLogin.value) showAuthModal('accountLogin');
  }
</script>

<style lang="scss" scoped>
  .ss-user-info-wrap {
    box-sizing: border-box;
    position: relative;
    overflow: hidden;
    border-radius: 32rpx;
    background:
      radial-gradient(circle at 14% 18%, rgba(59, 130, 246, 0.16), transparent 30%),
      linear-gradient(135deg, rgba(15, 23, 42, 0.96), rgba(30, 41, 59, 0.92));
    border: 1rpx solid rgba(255, 255, 255, 0.08);
    box-shadow: var(--ui-Surface-shadow-lg);

    &::before {
      content: '';
      position: absolute;
      top: -80rpx;
      right: -40rpx;
      width: 220rpx;
      height: 220rpx;
      border-radius: 50%;
      background: radial-gradient(circle, rgba(96, 165, 250, 0.28), transparent 68%);
      pointer-events: none;
    }

    .avatar-box {
      width: 100rpx;
      height: 100rpx;
      border-radius: 50%;
      overflow: hidden;
      border: 4rpx solid rgba(255, 255, 255, 0.2);
      box-shadow: 0 12rpx 30rpx rgba(15, 23, 42, 0.2);

      .avatar-img {
        width: 100%;
        height: 100%;
      }
    }

    .nick-name {
      font-size: 34rpx;
      font-weight: 600;
      color: #ffffff;
      line-height: normal;
    }

    .identity-title {
      flex: 0 0 auto;
      padding: 7rpx 15rpx;
      border: 1rpx solid rgba(255, 255, 255, 0.22);
      border-radius: 999rpx;
      background: rgba(255, 255, 255, 0.1);
      color: rgba(255, 255, 255, 0.9);
      font-size: 21rpx;
      font-weight: 500;
      line-height: 1;
    }

    .vip-img {
      width: 30rpx;
      height: 30rpx;
    }

    .sicon-qrcode {
      font-size: 40rpx;
      color: #ffffff;
    }

    .right-box {
      .ss-reset-button {
        width: 76rpx;
        height: 76rpx;
        border-radius: 24rpx;
        background: rgba(255, 255, 255, 0.08);
        border: 1rpx solid rgba(255, 255, 255, 0.12);
        backdrop-filter: blur(10px);
        -webkit-backdrop-filter: blur(10px);
        box-shadow: inset 0 1rpx 0 rgba(255, 255, 255, 0.08);
      }
    }
  }

  .bind-mobile-box {
    width: 100%;
    height: 84rpx;
    padding: 0 34rpx 0 44rpx;
    box-sizing: border-box;
    background: rgba(255, 255, 255, 0.08);
    border-top: 1rpx solid rgba(255, 255, 255, 0.08);
    box-shadow: inset 0 1rpx 0 rgba(255, 255, 255, 0.04);
    color: rgba(255, 255, 255, 0.9);

    .cicon-mobile-o {
      font-size: 30rpx;
      color: #fbbf24;
    }

    .mobile-title {
      font-size: 24rpx;
      font-weight: 500;
      color: rgba(255, 255, 255, 0.92);
    }

    .bind-btn {
      width: 100rpx;
      height: 50rpx;
      background: linear-gradient(90deg, #f59e0b, #f97316);
      border-radius: 25rpx;
      font-size: 24rpx;
      font-weight: 500;
      color: #ffffff;
      box-shadow: 0 10rpx 22rpx rgba(249, 115, 22, 0.22);
    }
  }
</style>
