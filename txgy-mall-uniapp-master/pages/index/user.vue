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
        <s-block-item
          :type="item.id"
          :data="item.property"
          :styles="item.property.style || {}"
          :identity-title="memberTitle"
        />
      </s-block>
    </template>

    <!-- 后台没有启用装修模板时，保留一套可直接使用的用户中心，避免页面空白。 -->
    <view v-else class="mall-user-page">
      <s-user-card :data="{ space: 0 }" :identity-title="memberTitle" />
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
            <s-menu-icon class="quick-icon" :label="item.title" />
            <text class="quick-label">{{ item.title }}</text>
          </view>
        </view>
      </view>
    </view>

    <!-- 经营辅助能力放在会员核心内容之后，避免抢占头像、订单和资产的首屏位置。 -->
    <view v-if="sheep.$store('user').isLogin" class="member-tools" :class="{ 'member-tools--template': hasTemplate }">
      <view class="service-hub">
        <view class="service-heading">
          <view>
            <view class="service-kicker">MEMBER SERVICES</view>
            <view class="service-title">企业与服务</view>
          </view>
          <view class="service-heading-mark">专属</view>
        </view>
        <view class="service-grid">
          <view class="service-entry" @tap="sheep.$router.go('/pages/user/enterprise-profile')">
            <view class="service-icon service-icon--enterprise"><s-menu-icon label="企业认证" /></view>
            <view class="service-copy">
              <view>企业资料认证</view>
              <text>提交资料与查看审核进度</text>
            </view>
            <text class="service-arrow">›</text>
          </view>
          <view class="service-entry" @tap="sheep.$router.go('/pages/chat/service', { scope: 'PLATFORM', shopId: 0 })">
            <view class="service-icon service-icon--support"><s-menu-icon label="在线客服" /></view>
            <view class="service-copy">
              <view>平台服务中心</view>
              <text>咨询商品、订单及售后问题</text>
            </view>
            <text class="service-arrow">›</text>
          </view>
        </view>
        <view v-if="showCustomerAffiliation" class="affiliation-line">
          <view class="affiliation-dot"></view>
          <text>{{ customer.message.value }}</text>
          <button v-if="customer.retryable.value" size="mini" :disabled="customer.state.loading" @tap.stop="customer.refresh">重试</button>
        </view>
      </view>
      <s-order-workbench :workbench="workbench" />
    </view>
  </s-layout>
</template>

<script setup>
  import { computed, ref } from 'vue';
  import { onShow, onReady, onPageScroll, onPullDownRefresh } from '@dcloudio/uni-app';
  import { showAuthModal } from '@/sheep/hooks/useModal';
  import sheep from '@/sheep';
  import { useOrderWorkbench } from '@/sheep/hooks/useOrderWorkbench';
  import { useCustomerAffiliation } from '@/sheep/hooks/useCustomerAffiliation';
  import EnterpriseApi from '@/sheep/api/member/enterprise';
  const workbench = useOrderWorkbench();
  const customer = useCustomerAffiliation();
  const enterpriseApplication = ref(null);
  const memberTitle = computed(() => {
    if (enterpriseApplication.value?.status !== 1) return '普通用户';
    return enterpriseApplication.value.identity === 'supplier' ? '供应商' : '采购商';
  });

  async function refreshMemberTitle() {
    if (!sheep.$store('user').isLogin) {
      enterpriseApplication.value = null;
      return;
    }
    try {
      const response = await EnterpriseApi.mine();
      enterpriseApplication.value = response?.code === 0 ? response.data || null : null;
    } catch {
      enterpriseApplication.value = null;
    }
  }
  onReady(() => {
    // #ifdef H5
    if (new URLSearchParams(window.location.search).get('mallRegistration') === '1' && !sheep.$store('user').isLogin) showAuthModal('register');
    // #endif
  });

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
  const showCustomerAffiliation = computed(() => {
    if (!customer.visible.value) return false;
    return customer.state.error || ['ACTIVE', 'OWNER_UNAVAILABLE', 'TEMPORARILY_UNAVAILABLE'].includes(customer.state.result?.state);
  });

  const quickActions = [
    { title: '收货地址', icon: '址', path: '/pages/user/address/list' },
    { title: '平台结算记录', icon: '规', path: '/pages/platform/history' },
    { title: '我的收藏', icon: '藏', path: '/pages/user/goods-collect' },
    { title: '浏览记录', icon: '足', path: '/pages/user/goods-log' },
    { title: '在线客服', icon: '客', path: '/pages/chat/service?scope=PLATFORM&shopId=0' },
    { title: '签到中心', icon: '签', path: '/pages/app/sign' },
    { title: '账户设置', icon: '设', path: '/pages/public/setting' },
  ];

  function openAction(item) {
    sheep.$router.go(item.path);
  }

  onShow(async () => {
    await sheep.$store('user').updateUserData();
    await refreshMemberTitle();
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

  .member-tools {
    padding: 0 20rpx calc(132rpx + env(safe-area-inset-bottom));
    background: #f4f7fb;
  }

  .member-tools--template {
    padding-top: 18rpx;
  }

  .service-hub {
    overflow: hidden;
    border: 1rpx solid rgba(15, 118, 110, 0.12);
    border-radius: 28rpx;
    background: #fff;
    box-shadow: 0 12rpx 34rpx rgba(15, 54, 62, 0.07);
  }

  .service-heading {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 26rpx 28rpx 20rpx;
    background: linear-gradient(135deg, #ecf8f4 0%, #f8fbfa 72%, #eef5ff 100%);
  }

  .service-kicker {
    color: #0f766e;
    font-size: 18rpx;
    font-weight: 700;
    letter-spacing: 3rpx;
  }

  .service-title {
    margin-top: 4rpx;
    color: #173d37;
    font-size: 30rpx;
    font-weight: 800;
  }

  .service-heading-mark {
    padding: 8rpx 15rpx;
    border: 1rpx solid rgba(15, 118, 110, 0.18);
    border-radius: 999rpx;
    background: rgba(255, 255, 255, 0.72);
    color: #0f766e;
    font-size: 20rpx;
    font-weight: 700;
  }

  .service-grid {
    padding: 8rpx 22rpx 16rpx;
  }

  .service-entry {
    display: flex;
    align-items: center;
    min-height: 104rpx;
    padding: 15rpx 8rpx;
  }

  .service-entry + .service-entry {
    border-top: 1rpx solid #edf1f0;
  }

  .service-icon {
    width: 70rpx;
    height: 70rpx;
    flex: 0 0 auto;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 22rpx;
    color: #0f766e;
    background: #e6f5f1;
  }

  .service-icon--support {
    color: #315e91;
    background: #eaf2fb;
  }

  .service-copy {
    min-width: 0;
    flex: 1;
    margin-left: 20rpx;
  }

  .service-copy view {
    color: #20363b;
    font-size: 27rpx;
    font-weight: 700;
  }

  .service-copy text {
    display: block;
    margin-top: 6rpx;
    color: #7a898f;
    font-size: 21rpx;
  }

  .service-arrow {
    margin-left: 12rpx;
    color: #9aaba7;
    font-size: 42rpx;
    font-weight: 300;
  }

  .affiliation-line {
    display: flex;
    align-items: center;
    gap: 12rpx;
    margin: 0 24rpx 22rpx;
    padding: 16rpx 18rpx;
    border-radius: 16rpx;
    background: #f4f8f7;
    color: #526a6d;
    font-size: 21rpx;
  }

  .affiliation-line text { min-width: 0; flex: 1; overflow-wrap: anywhere; }
  .affiliation-line button { flex: 0 0 auto; margin: 0; color: #0f766e; font-size: 20rpx; }
  .affiliation-dot { width: 12rpx; height: 12rpx; flex: 0 0 auto; border-radius: 50%; background: #18a58d; box-shadow: 0 0 0 7rpx rgba(24, 165, 141, .12); }
</style>
