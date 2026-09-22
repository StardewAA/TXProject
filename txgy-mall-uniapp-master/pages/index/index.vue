<!-- 防腐保温商城首页 -->
<template>
  <s-layout
    title="防腐保温商城"
    navbar="normal"
    tabbar="/pages/index/index"
    :bgStyle="{ color: '#f4f7fb' }"
    onShareAppMessage
  >
    <view class="mall-home">
      <view class="home-hero">
        <view class="hero-brand">
          <image class="brand-mark" src="/static/mall-logo.png" mode="aspectFit" />
          <view>
            <view class="brand-name">防腐保温材料商城</view>
            <view class="brand-subtitle">工程材料一站采购 · 质保可追溯</view>
          </view>
        </view>
        <view class="hero-title">让每一层防护，都有可靠的材料支撑</view>
        <view class="hero-tags">
          <text>防腐涂料</text>
          <text>保温保冷</text>
          <text>施工辅材</text>
        </view>
        <button class="industry-primary hero-purchase" @tap="openPortalProducts()">开始选购材料 <text>→</text></button>
      </view>

      <view class="search-bar" @tap="openSearch">
        <text class="search-icon">⌕</text>
        <text class="search-placeholder">搜索产品名称、编号或规格</text>
        <text class="search-action">搜索</text>
      </view>

      <view class="quick-actions">
        <button @tap="openPortalProducts()"><text class="quick-symbol">▦</text><text>全部商品</text><text class="quick-caption">跨租户找材料</text></button>
        <button @tap="sheep.$router.go('/pages/platform/cart')"><text class="quick-symbol">＋</text><text>采购清单</text><text class="quick-caption">跨租户购物车</text></button>
        <button @tap="sheep.$router.go('/pages/order/list')"><text class="quick-symbol">≡</text><text>我的订单</text><text class="quick-caption">跟进采购进度</text></button>
        <button @tap="sheep.$router.go('/pages/inquiry/index')"><text class="quick-symbol">询</text><text>询价专栏</text><text class="quick-caption">全平台公开需求</text></button>
      </view>

      <view class="service-strip">
        <view class="service-item" @tap="openPortalProducts()">
          <text class="service-icon">厂</text>
          <view>
            <text class="service-title">平台供应商</text>
            <text class="service-desc">查看开放租户商品</text>
          </view>
        </view>
        <view class="service-item" @tap="sheep.$router.go('/pages/order/aftersale/list')">
          <text class="service-icon">售</text>
          <view>
            <text class="service-title">售后服务</text>
            <text class="service-desc">查看本人售后进度</text>
          </view>
        </view>
        <view class="service-item" @tap="sheep.$router.go('/pages/chat/service', { scope: 'PLATFORM', shopId: 0 })">
          <text class="service-icon">客</text>
          <view>
            <text class="service-title">在线客服</text>
            <text class="service-desc">咨询商品、订单与售后</text>
          </view>
        </view>
      </view>

      <view class="section-block">
        <view class="section-heading">
          <view>
            <text class="section-title">热门租户</text>
            <text class="section-subtitle">近期成交优先，展示更多在售供应商</text>
          </view>
          <view class="ranking-windows">
            <text :class="{ active: rankingWindow === '7d' }" @tap="changeRankingWindow('7d')">近 7 天</text>
            <text :class="{ active: rankingWindow === '30d' }" @tap="changeRankingWindow('30d')">近 30 天</text>
          </view>
        </view>
        <view v-if="hotLoading" class="portal-state">正在加载热门租户…</view>
        <view v-else-if="hotError" class="portal-state portal-retry" @tap="loadHotTenants">榜单暂时无法加载，点击重试</view>
        <view v-else-if="hotTenants.length" class="tenant-ranking-list">
          <view
            v-for="tenant in visibleHotTenants"
            :key="tenant.tenantSlug"
            class="tenant-ranking-item"
            @tap="openTenant(tenant.tenantSlug)"
          >
            <view class="tenant-rank">{{ tenant.rank || '·' }}</view>
            <view class="tenant-ranking-info">
              <text class="tenant-ranking-name">{{ tenant.tenantName }}</text>
              <text class="tenant-ranking-category">{{ tenant.mainCategories || '防腐保温材料' }}</text>
            </view>
            <text class="tenant-ranking-arrow">›</text>
          </view>
          <view v-if="hotTenants.length > 5" class="tenant-ranking-toggle" @tap="hotExpanded = !hotExpanded">
            {{ hotExpanded ? '收起榜单' : `查看其余 ${hotTenants.length - 5} 家供应商` }}
            <text>{{ hotExpanded ? '⌃' : '⌄' }}</text>
          </view>
        </view>
        <view v-else class="portal-state">暂无销量榜单，可继续浏览平台商品</view>
      </view>

      <view class="section-block product-section">
        <view class="section-heading">
          <view>
            <text class="section-title">平台精选商品</text>
            <text class="section-subtitle">汇总所有已开放租户的合规在售商品</text>
          </view>
          <text class="section-more" @tap="openPortalProducts()">查看全部 ›</text>
        </view>
        <view v-if="catalogSource === 'loading'" class="portal-state">正在加载平台商品…</view>
        <view v-else-if="catalogSource === 'error'" class="portal-state portal-retry" @tap="loadPortalProducts">商品暂时无法加载，点击重试</view>
        <view class="product-grid">
          <view
            v-for="product in featuredProducts"
            :key="`${product.tenantId}-${product.spuId}`"
            class="product-card"
            @tap="openProduct(product)"
          >
            <view class="product-cover">
              <image
                v-if="product.picUrl"
                class="product-cover-image"
                :src="sheep.$url.cdn(product.picUrl)"
                mode="aspectFill"
              />
              <text class="product-code">{{ product.tenantName }}</text>
              <text class="product-unit">{{ product.skus.length }} 个规格</text>
              <text class="product-mark">{{ product.shopName }}</text>
            </view>
            <view class="product-body">
              <text class="product-name">{{ product.spuName }}</text>
              <text class="product-spec">{{ product.enterpriseName }} · {{ product.shopName }}</text>
              <view class="product-price-row">
                <text class="product-price">¥{{ formatPrice(product.price) }}</text>
                <text class="product-unit-label">起</text>
              </view>
              <text class="product-stock">已售 {{ product.salesCount }} · 商品当前可售</text>
              <button class="material-select" @tap.stop="openTenant(product.tenantSlug)"><text>进入租户商城</text><text aria-hidden="true">→</text></button>
            </view>
          </view>
        </view>
      </view>

      <view class="selection-guide">
        <text class="guide-kicker">MATERIAL GUIDE / 选材指南</text>
        <view class="guide-title">采购前，先核对这三件事</view>
        <view class="guide-row"><text>01</text><view><text>明确使用工况</text><text>室内或室外、使用温度、腐蚀环境</text></view></view>
        <view class="guide-row"><text>02</text><view><text>核对规格与单位</text><text>材质、口径、厚度，及每件对应的计量单位</text></view></view>
        <view class="guide-row"><text>03</text><view><text>确认数量与收货信息</text><text>下单前核对库存、收货地址与订单金额</text></view></view>
        <button class="guide-action" @tap="openPortalProducts()">查看平台全部材料 →</button>
      </view>

      <view class="catalog-note">
        <text>{{ catalogSource === 'api' ? (products.length ? '展示所有开放租户的已审核、已上架商品' : '平台暂无可售商品') : catalogSource === 'loading' ? '正在加载平台商品' : '商品暂无法加载，请刷新重试' }}</text>
        <text>选购前请核对规格与单位，成交金额以结算页为准</text>
      </view>
    </view>
  </s-layout>
</template>

<script setup>
  import { onLoad, onPullDownRefresh } from '@dcloudio/uni-app';
  import { computed, ref } from 'vue';
  import sheep from '@/sheep';
  import $share from '@/sheep/platform/share';
  import PortalApi from '@/sheep/api/product/portal';
  import {
    normalizePortalPage,
    normalizePortalTenant,
    portalProductDetailPath,
    portalProductListPath,
  } from '@/sheep/helper/platform-portal.mjs';

  // 隐藏原生 tabBar，使用商城自定义 tabBar。
  uni.hideTabBar({
    fail: () => {},
  });

  const products = ref([]);
  const catalogSource = ref('loading');
  const hotTenants = ref([]);
  const hotLoading = ref(true);
  const hotError = ref(false);
  const hotExpanded = ref(false);
  const rankingWindow = ref('7d');
  const visibleHotTenants = computed(() => hotExpanded.value ? hotTenants.value : hotTenants.value.slice(0, 5));
  const featuredProducts = computed(() =>
    (Array.isArray(products.value) ? products.value : []).filter(Boolean).slice(0, 6),
  );

  const formatPrice = (price) => {
    const value = Number(price);
    return Number.isSafeInteger(value) && value >= 0 ? (value / 100).toFixed(2) : '0.00';
  };

  const openPortalProducts = (params = {}) => {
    sheep.$router.go(portalProductListPath(params));
  };

  const openTenant = (tenantSlug) => {
    openPortalProducts({ tenantSlug });
  };

  const openProduct = (product) => {
    sheep.$router.go(portalProductDetailPath({
      tenantSlug: product.tenantSlug,
      spuId: product.spuId,
    }));
  };

  const openSearch = () => {
    openPortalProducts();
  };

  async function loadPortalProducts() {
    catalogSource.value = 'loading';
    try {
      const response = await PortalApi.getProducts({ pageNo: 1, pageSize: 6 });
      if (response?.code !== 0) throw new Error('平台商品暂不可用');
      products.value = normalizePortalPage(response.data).list;
      catalogSource.value = 'api';
    } catch (error) {
      products.value = [];
      catalogSource.value = 'error';
    }
  }

  async function loadHotTenants() {
    hotLoading.value = true;
    hotError.value = false;
    try {
      const response = await PortalApi.getHotTenants(rankingWindow.value, 10);
      if (response?.code !== 0 || !Array.isArray(response.data)) throw new Error('热门租户暂不可用');
      hotTenants.value = response.data.map(normalizePortalTenant).filter(Boolean);
    } catch (error) {
      hotTenants.value = [];
      hotError.value = true;
    } finally {
      hotLoading.value = false;
    }
  }

  const changeRankingWindow = (value) => {
    if (value === rankingWindow.value || !['7d', '30d'].includes(value)) return;
    rankingWindow.value = value;
    hotExpanded.value = false;
    loadHotTenants();
  };

  onLoad((options = {}) => {
    loadPortalProducts();
    loadHotTenants();
    // 小程序识别二维码
    // #ifdef MP
    if (options?.scene) {
      try {
        const sceneParams = decodeURIComponent(options.scene).split('=');
        if (sceneParams[0]) options[sceneParams[0]] = sceneParams[1];
      } catch (error) {
        // 忽略非法二维码参数，仍允许首页正常打开。
      }
    }
    // #endif

    if (options?.spm) {
      try {
        $share.decryptSpm(options.spm);
      } catch (error) {
        // 分享参数异常不应阻断首页。
      }
    }

    // 进入指定页面（完整页面路径）
    if (options?.page) {
      try {
        sheep.$router.go(decodeURIComponent(options.page));
      } catch (error) {
        // 忽略非法跳转参数。
      }
    }
  });

  onPullDownRefresh(async () => {
    try {
      // 管理后台新增/修改商品后，允许用户端通过下拉刷新立即同步。
      await Promise.allSettled([loadPortalProducts(), loadHotTenants()]);
    } finally {
      uni.stopPullDownRefresh();
    }
  });
</script>

<style lang="scss" scoped>
  .mall-home {
    padding: 24rpx 24rpx calc(120rpx + env(safe-area-inset-bottom));
    color: #0f172a;
  }

  .home-hero {
    padding: 34rpx 32rpx 38rpx;
    border-radius: 28rpx;
    color: #fff;
    background:
      radial-gradient(circle at 90% 0%, rgba(45, 212, 191, 0.35), transparent 38%),
      linear-gradient(135deg, #0f766e 0%, #0f3d56 58%, #102a43 100%);
    box-shadow: 0 18rpx 40rpx rgba(15, 61, 86, 0.2);
  }

  .hero-brand {
    display: flex;
    align-items: center;
  }

  .brand-mark {
    width: 74rpx;
    height: 90rpx;
    margin-right: 18rpx;
    flex-shrink: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #0f766e;
    font-size: 24rpx;
    font-weight: 700;
    background: transparent;
  }

  .brand-name {
    font-size: 30rpx;
    font-weight: 700;
    letter-spacing: 1rpx;
  }

  .brand-subtitle {
    margin-top: 6rpx;
    color: rgba(255, 255, 255, 0.72);
    font-size: 22rpx;
  }

  .hero-title {
    margin-top: 44rpx;
    max-width: 590rpx;
    font-size: 38rpx;
    font-weight: 700;
    line-height: 1.35;
  }

  .hero-tags {
    display: flex;
    margin-top: 26rpx;
  }

  .hero-tags text {
    margin-right: 14rpx;
    padding: 8rpx 16rpx;
    border: 1rpx solid rgba(255, 255, 255, 0.26);
    border-radius: 999rpx;
    color: rgba(255, 255, 255, 0.88);
    font-size: 22rpx;
    background: rgba(255, 255, 255, 0.1);
  }

  .search-bar {
    height: 82rpx;
    margin: -18rpx 20rpx 24rpx;
    padding: 0 22rpx;
    border-radius: 18rpx;
    display: flex;
    align-items: center;
    position: relative;
    z-index: 2;
    background: #fff;
    box-shadow: 0 14rpx 32rpx rgba(15, 23, 42, 0.1);
  }

  .search-icon {
    margin-right: 12rpx;
    color: #0f766e;
    font-size: 42rpx;
    line-height: 1;
  }

  .search-placeholder {
    flex: 1;
    color: #94a3b8;
    font-size: 24rpx;
  }

  .search-action {
    padding-left: 18rpx;
    border-left: 1rpx solid #e2e8f0;
    color: #0f766e;
    font-size: 24rpx;
    font-weight: 600;
  }

  .service-strip {
    display: flex;
    padding: 20rpx 18rpx;
    border-radius: 20rpx;
    background: #fff;
    box-shadow: 0 8rpx 24rpx rgba(15, 23, 42, 0.05);
  }

  .service-item {
    flex: 1;
    min-width: 0;
    display: flex;
    align-items: center;
  }

  .service-item + .service-item {
    margin-left: 10rpx;
    padding-left: 10rpx;
    border-left: 1rpx solid #e2e8f0;
  }

  .service-icon {
    width: 48rpx;
    height: 48rpx;
    margin-right: 10rpx;
    border-radius: 14rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #0f766e;
    font-size: 22rpx;
    font-weight: 700;
    background: #ccfbf1;
  }

  .service-title,
  .service-desc {
    display: block;
    white-space: nowrap;
  }

  .service-title {
    color: #1e293b;
    font-size: 22rpx;
    font-weight: 600;
  }

  .service-desc {
    margin-top: 4rpx;
    color: #94a3b8;
    font-size: 18rpx;
  }

  .section-block {
    margin-top: 30rpx;
  }

  .section-heading {
    display: flex;
    align-items: flex-end;
    justify-content: space-between;
    margin-bottom: 18rpx;
  }

  .section-title,
  .section-subtitle {
    display: block;
  }

  .section-title {
    color: #0f172a;
    font-size: 32rpx;
    font-weight: 700;
  }

  .section-subtitle {
    margin-top: 6rpx;
    color: #64748b;
    font-size: 21rpx;
  }

  .section-more {
    color: #0f766e;
    font-size: 22rpx;
  }

  .ranking-windows {
    display: flex;
    padding: 5rpx;
    border-radius: 999rpx;
    background: #e2ebe8;
  }

  .ranking-windows text {
    padding: 7rpx 14rpx;
    border-radius: 999rpx;
    color: #64748b;
    font-size: 20rpx;
  }

  .ranking-windows .active {
    color: #fff;
    background: #0f766e;
  }

  .portal-state {
    padding: 34rpx 22rpx;
    border-radius: 18rpx;
    color: #64748b;
    font-size: 23rpx;
    text-align: center;
    background: #fff;
  }

  .portal-retry {
    color: #0f766e;
  }

  .category-scroll {
    width: 100%;
    white-space: nowrap;
  }

  .category-row {
    display: inline-flex;
    padding-bottom: 8rpx;
  }

  .category-tile {
    width: 142rpx;
    margin-right: 16rpx;
    padding: 20rpx 12rpx 16rpx;
    border-radius: 20rpx;
    display: flex;
    align-items: center;
    flex-direction: column;
    background: #fff;
    box-shadow: 0 8rpx 22rpx rgba(15, 23, 42, 0.05);
  }

  .tenant-ranking-list {
    overflow: hidden;
    border: 1rpx solid #e2ebe8;
    border-radius: 22rpx;
    background: #fff;
    box-shadow: 0 8rpx 22rpx rgba(15, 23, 42, 0.05);
  }

  .tenant-ranking-item {
    min-height: 104rpx;
    padding: 18rpx 22rpx;
    box-sizing: border-box;
    display: flex;
    align-items: center;
  }

  .tenant-ranking-item + .tenant-ranking-item {
    border-top: 1rpx solid #edf2f0;
  }

  .tenant-ranking-item:active {
    background: #f1f7f4;
  }

  .tenant-rank {
    width: 58rpx;
    height: 58rpx;
    flex: 0 0 58rpx;
    border-radius: 18rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    font-size: 22rpx;
    font-weight: 700;
    background: linear-gradient(145deg, #0f766e, #155e75);
  }

  .tenant-ranking-info {
    min-width: 0;
    flex: 1;
    margin-left: 20rpx;
  }

  .tenant-ranking-name,
  .tenant-ranking-category {
    display: block;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .tenant-ranking-name {
    color: #1e293b;
    font-size: 24rpx;
    font-weight: 600;
  }

  .tenant-ranking-category {
    margin-top: 7rpx;
    color: #94a3b8;
    font-size: 19rpx;
  }

  .tenant-ranking-arrow {
    margin-left: 16rpx;
    color: #0f766e;
    font-size: 40rpx;
    line-height: 1;
  }

  .tenant-ranking-toggle {
    height: 76rpx;
    border-top: 1rpx solid #e2ebe8;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 10rpx;
    color: #0f766e;
    font-size: 21rpx;
    font-weight: 600;
    background: #f7faf9;
  }

  .category-icon {
    width: 66rpx;
    height: 66rpx;
    border-radius: 22rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    font-size: 21rpx;
    font-weight: 700;
  }

  .category-name {
    max-width: 100%;
    margin-top: 14rpx;
    overflow: hidden;
    color: #1e293b;
    font-size: 23rpx;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .category-count {
    margin-top: 6rpx;
    color: #94a3b8;
    font-size: 19rpx;
  }

  .product-section {
    margin-top: 36rpx;
  }

  .product-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 18rpx;
  }

  .product-card {
    overflow: hidden;
    border-radius: 22rpx;
    background: #fff;
    box-shadow: 0 8rpx 22rpx rgba(15, 23, 42, 0.06);
  }

  .product-cover {
    height: 184rpx;
    box-sizing: border-box;
    position: relative;
    overflow: hidden;
    background: #eef2f1;
  }

  .product-cover-image {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
    opacity: 1;
  }

  .product-code,
  .product-unit,
  .product-mark {
    position: relative;
    z-index: 1;
  }

  .product-code {
    position: absolute;
    top: 14rpx;
    left: 14rpx;
    max-width: calc(100% - 110rpx);
    padding: 5rpx 9rpx;
    overflow: hidden;
    border-radius: 7rpx;
    background: rgba(15, 23, 42, 0.72);
    color: #fff;
    font-size: 20rpx;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .product-unit {
    position: absolute;
    top: 14rpx;
    right: 14rpx;
    padding: 6rpx 12rpx;
    border-radius: 999rpx;
    background: rgba(15, 23, 42, 0.72);
    color: #fff;
    font-size: 19rpx;
  }

  .product-mark {
    position: absolute;
    right: 0;
    bottom: 0;
    left: 0;
    padding: 10rpx 14rpx;
    overflow: hidden;
    background: rgba(15, 23, 42, 0.72);
    color: #fff;
    font-size: 22rpx;
    font-weight: 600;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .product-body {
    padding: 18rpx 18rpx 20rpx;
  }

  .product-name,
  .product-spec {
    display: block;
  }

  .product-name {
    overflow: hidden;
    color: #1e293b;
    font-size: 25rpx;
    font-weight: 600;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .product-spec {
    height: 34rpx;
    margin-top: 8rpx;
    overflow: hidden;
    color: #64748b;
    font-size: 19rpx;
    line-height: 34rpx;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .product-price-row {
    display: flex;
    align-items: baseline;
    margin-top: 14rpx;
  }

  .product-price {
    color: #dc2626;
    font-size: 30rpx;
    font-weight: 700;
  }

  .product-unit-label {
    margin-left: 4rpx;
    color: #94a3b8;
    font-size: 19rpx;
  }

  .product-stock {
    display: block;
    margin-top: 8rpx;
    color: #0f766e;
    font-size: 19rpx;
  }

  .product-stock-empty {
    color: #94a3b8;
  }

  .catalog-note {
    padding: 30rpx 0 10rpx;
    text-align: center;
    color: #94a3b8;
    font-size: 19rpx;
    line-height: 34rpx;
  }

  .catalog-note text {
    display: block;
  }
  .home-hero { position: relative; overflow: hidden; background: linear-gradient(125deg, #102f40, #167564); }
  .home-hero::after { content: ''; position: absolute; width: 230rpx; height: 230rpx; right: -95rpx; bottom: -80rpx; border: 30rpx solid rgba(255,255,255,.08); border-radius: 50%; pointer-events: none; }
  .industry-primary { min-height: 84rpx; display: flex; align-items: center; justify-content: space-between; border-radius: 14rpx; font-size: 26rpx; font-weight: 600; }
  .hero-purchase { margin: 30rpx 0 0; width: 300rpx; padding: 0 24rpx; color: #123e35; background: #c9eee0; }
  .hero-purchase::after, .quick-actions button::after, .material-select::after, .guide-action::after { border: none; }
  .quick-actions { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14rpx; margin: 26rpx 0; }
  .quick-actions button { margin: 0; padding: 22rpx 8rpx; border-radius: 18rpx; background: #fff; border: 1rpx solid #dfebe7; line-height: 1.6; color: #183b42; font-size: 25rpx; font-weight: 600; }
  .quick-actions text { display: block; }
  .quick-actions .quick-symbol { margin: 0 auto 8rpx; background: #e9f5ef; border-radius: 12rpx; width: 58rpx; height: 58rpx; font-size: 36rpx; color: #177660; }
  .quick-actions .quick-caption { font-size: 19rpx; font-weight: 400; color: #697f7b; margin-top: 5rpx; }
  .material-select { width: 100%; min-height: 72rpx; margin-top: 20rpx; padding: 0 16rpx; display: flex; align-items: center; justify-content: space-between; gap: 6rpx; background: #157762; color: #fff; font-size: 22rpx; font-weight: 600; line-height: 1.5; border-radius: 12rpx; }
  .material-select[disabled] { background: #edf1f2; color: #7b8b91; }
  .material-select:active, .hero-purchase:active, .guide-action:active { opacity: .8; }
  .selection-guide { margin-top: 28rpx; padding: 30rpx; border-radius: 22rpx; background: #edf5f1; border: 1rpx solid #d3e5dc; }
  .guide-kicker { color: #49776b; font-size: 18rpx; letter-spacing: 2rpx; }
  .guide-title { font-size: 32rpx; font-weight: 700; color: #193d35; margin: 12rpx 0 24rpx; }
  .guide-row { display: flex; align-items: flex-start; gap: 20rpx; padding: 18rpx 0; }
  .guide-row > text { color: #28765f; font-size: 30rpx; font-weight: 700; }
  .guide-row view text { display: block; font-size: 25rpx; color: #24483d; }
  .guide-row view text + text { margin-top: 6rpx; font-size: 21rpx; line-height: 1.7; color: #60796f; }
  .guide-action { margin-top: 20rpx; background: #fff; color: #17694f; border-radius: 12rpx; font-size: 25rpx; min-height: 80rpx; }
</style>
