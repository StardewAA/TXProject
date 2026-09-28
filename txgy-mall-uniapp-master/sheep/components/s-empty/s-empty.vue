<template>
  <view
    class="ss-flex-col ss-col-center ss-row-center empty-box"
    :style="[{ paddingTop: paddingTop + 'rpx' }]"
  >
    <view class=""><image class="empty-icon" :src="icon" mode="widthFix"></image></view>
    <view class="empty-text ss-m-t-28 ss-m-b-40">
      <text v-if="text !== ''">{{ text }}</text>
    </view>
    <button class="ss-reset-button empty-btn" v-if="showAction" @tap="clickAction">
      {{ actionText }}
    </button>
  </view>
</template>

<script setup>
  import sheep from '@/sheep';
  /**
   * 容器组件 - 装修组件的样式容器
   */

  const props = defineProps({
    // 图标
    icon: {
      type: String,
      default: '',
    },
    // 描述
    text: {
      type: String,
      default: '',
    },
    // 是否显示button
    showAction: {
      type: Boolean,
      default: false,
    },
    // button 文字
    actionText: {
      type: String,
      default: '',
    },
    // 链接
    actionUrl: {
      type: String,
      default: '',
    },
    // 间距
    paddingTop: {
      type: String,
      default: '260',
    },
    //主题色
    buttonColor: {
      type: String,
      default: 'var(--ui-BG-Main)',
    },
  });

  const emits = defineEmits(['clickAction']);

  function clickAction() {
    if (props.actionUrl !== '') {
      sheep.$router.go(props.actionUrl);
    }
    emits('clickAction');
  }
</script>

<style lang="scss" scoped>
  .empty-box {
    width: 100%;
    box-sizing: border-box;
    padding: 64rpx 40rpx 56rpx;
    margin: 0 24rpx;
    border-radius: 32rpx;
    background:
      radial-gradient(circle at top, rgba(59, 130, 246, 0.08), transparent 38%),
      linear-gradient(180deg, rgba(255, 255, 255, 0.92), rgba(247, 250, 255, 0.96));
    border: 1rpx solid rgba(148, 163, 184, 0.14);
    box-shadow: 0 20rpx 50rpx rgba(15, 23, 42, 0.08);
    position: relative;
    overflow: hidden;

    &::before {
      content: '';
      position: absolute;
      inset: auto -40rpx -60rpx auto;
      width: 180rpx;
      height: 180rpx;
      border-radius: 50%;
      background: radial-gradient(circle, rgba(59, 130, 246, 0.16), transparent 70%);
      pointer-events: none;
    }
  }
  .empty-icon {
    width: 220rpx;
    filter: drop-shadow(0 16rpx 28rpx rgba(59, 130, 246, 0.12));
  }

  .empty-text {
    font-size: 26rpx;
    font-weight: 500;
    color: #64748b;
    line-height: 40rpx;
    text-align: center;
  }

  .empty-btn {
    width: 320rpx;
    height: 70rpx;
    border: none;
    border-radius: 35rpx;
    font-weight: 600;
    color: #ffffff;
    font-size: 28rpx;
    background: v-bind('buttonColor');
    box-shadow: 0 14rpx 30rpx rgba(15, 23, 42, 0.14);
  }
</style>
