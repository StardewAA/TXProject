<!-- 个人中心：支持装修 -->
<template>
  <s-layout
    title="我的"
    tabbar="/pages/index/user"
    :navbar="hasTemplate ? 'custom' : 'normal'"
    :bgStyle="hasTemplate ? template.page : { color: '#f4f7fb' }"
    :navbarStyle="template.navigationBar || {}"
    onShareAppMessage
  >
    <template v-if="hasTemplate">
      <s-block
        v-for="(item, index) in templateComponents"
        :key="index"
        :styles="item.property.style || {}"
      >
        <s-block-item :type="item.id" :data="item.property" :styles="item.property.style || {}" />
      </s-block>
    </template>

    <!-- 后台没有启用装修模板时，保留一套可直接使用的用户中心，避免页面空白。 -->
    <view v-else class="mall-user-page">
      <s-user-card :data="{ space: 0 }" />
      <s-order-card class="user-section" :data="{ space: 0 }" />
      <s-wallet-card class="user-section" :data="{ space: 0 }" />

      <view class="quick-card user-section">
        <view class="quick-title">常用功能</view>
        <view class="quick-grid">
          <view
            v-for="item in quickActions"
            :key="item.title"
            class="quick-item"
            @tap="openAction(item)"
          >
            <view class="quick-icon">{{ item.icon }}</view>
            <text class="quick-label">{{ item.title }}</text>
          </view>
        </view>
      </view>
    </view>
  </s-layout>
</template>

<script setup>
  import { computed } from 'vue';
  import { onShow, onPageScroll, onPullDownRefresh } from '@dcloudio/uni-app';
  import sheep from '@/sheep';

  // 隐藏原生tabBar
  uni.hideTabBar({
    fail: () => {},
  });

  const template = computed(() => {
    const value = sheep.$store('app').template?.user;
    return value && typeof value === 'object' ? value : {};
  });
  const templateComponents = computed(() => {
    const components = Array.isArray(template.value.components) ? template.value.components : [];
    return components
      .filter(
        (item) =>
          item && typeof item === 'object' && typeof item.id === 'string' && item.id.length > 0,
      )
      .map((item) => {
        const property =
          item.property && typeof item.property === 'object' && !Array.isArray(item.property)
            ? item.property
            : {};
        return {
          ...item,
          property: {
            ...property,
            style:
              property.style && typeof property.style === 'object' && !Array.isArray(property.style)
                ? property.style
                : {},
          },
        };
      });
  });
  const hasTemplate = computed(() => templateComponents.value.length > 0);

  const quickActions = [
    { title: '收货地址', icon: '址', path: '/pages/user/address/list' },
    { title: '我的收藏', icon: '藏', path: '/pages/user/goods-collect' },
    { title: '浏览记录', icon: '足', path: '/pages/user/goods-log' },
    { title: '在线客服', icon: '客', path: '/pages/chat/index' },
    { title: '签到中心', icon: '签', path: '/pages/app/sign' },
    { title: '账户设置', icon: '设', path: '/pages/public/setting' },
  ];

  function openAction(item) {
    sheep.$router.go(item.path);
  }

  onShow(() => {
    sheep.$store('user').updateUserData();
  });

  onPullDownRefresh(() => {
    sheep.$store('user').updateUserData();
    setTimeout(function () {
      uni.stopPullDownRefresh();
    }, 800);
  });

  onPageScroll(() => {});
</script>

<style lang="scss" scoped>
  .mall-user-page {
    min-height: calc(100vh - 88rpx);
    padding: 24rpx 20rpx calc(120rpx + env(safe-area-inset-bottom));
    box-sizing: border-box;
    background: #f4f7fb;
  }

  .user-section {
    margin-top: 18rpx;
  }

  .quick-card {
    padding: 26rpx 18rpx 18rpx;
    border-radius: 28rpx;
    background: #fff;
    border: 1rpx solid rgba(148, 163, 184, 0.14);
    box-shadow: 0 8rpx 24rpx rgba(15, 23, 42, 0.05);
  }

  .quick-title {
    padding: 0 10rpx 20rpx;
    color: #0f172a;
    font-size: 30rpx;
    font-weight: 700;
  }

  .quick-grid {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 12rpx;
  }

  .quick-item {
    min-height: 118rpx;
    border-radius: 20rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-direction: column;
    background: #f8fafc;
  }

  .quick-icon {
    width: 52rpx;
    height: 52rpx;
    border-radius: 18rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #0f766e;
    font-size: 24rpx;
    font-weight: 700;
    background: #ccfbf1;
  }

  .quick-label {
    margin-top: 10rpx;
    color: #475569;
    font-size: 22rpx;
  }
</style>
