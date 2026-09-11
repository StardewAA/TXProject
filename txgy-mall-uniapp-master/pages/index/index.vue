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
          <view class="brand-mark">防腐</view>
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
      </view>

      <view class="search-bar" @tap="openSearch">
        <text class="search-icon">⌕</text>
        <text class="search-placeholder">搜索产品名称、编号或规格</text>
        <text class="search-action">搜索</text>
      </view>

      <view class="service-strip">
        <view class="service-item">
          <text class="service-icon">厂</text>
          <view>
            <text class="service-title">厂家直供</text>
            <text class="service-desc">工程采购更省心</text>
          </view>
        </view>
        <view class="service-item">
          <text class="service-icon">溯</text>
          <view>
            <text class="service-title">批次可追溯</text>
            <text class="service-desc">质保资料随货提供</text>
          </view>
        </view>
        <view class="service-item">
          <text class="service-icon">价</text>
          <view>
            <text class="service-title">工程专属价</text>
            <text class="service-desc">按项目灵活报价</text>
          </view>
        </view>
      </view>

      <view class="section-block">
        <view class="section-heading">
          <view>
            <text class="section-title">按分类选购</text>
            <text class="section-subtitle">覆盖防腐保温施工全场景</text>
          </view>
          <text class="section-more" @tap="openCategory()">全部分类 ›</text>
        </view>
        <scroll-view class="category-scroll" scroll-x show-scrollbar="false">
          <view class="category-row">
            <view
              v-for="category in popularCategories"
              :key="category.key"
              class="category-tile"
              @tap="openCategory(category.key)"
            >
              <view class="category-icon" :style="{ backgroundColor: category.color }">
                {{ category.icon }}
              </view>
              <text class="category-name">{{ category.name }}</text>
              <text class="category-count">{{ categoryProductCount(category.key) }} 款</text>
            </view>
          </view>
        </scroll-view>
      </view>

      <view class="section-block product-section">
        <view class="section-heading">
          <view>
            <text class="section-title">工程常用材料</text>
            <text class="section-subtitle">来自产品档案总览的可售商品</text>
          </view>
          <text class="section-more" @tap="openCategory()">查看全部 ›</text>
        </view>
        <view class="product-grid">
          <view
            v-for="product in featuredProducts"
            :key="product.id || product.code"
            class="product-card"
            @tap="openProduct(product)"
          >
            <view class="product-cover" :style="{ background: productGradient(product) }">
              <image
                v-if="product.picUrl"
                class="product-cover-image"
                :src="sheep.$url.cdn(product.picUrl)"
                mode="aspectFill"
              />
              <text class="product-code">{{ product.code }}</text>
              <text class="product-unit">{{ product.unit }}</text>
              <text class="product-mark">工程材料</text>
            </view>
            <view class="product-body">
              <text class="product-name">{{ product.name }}</text>
              <text class="product-spec">{{ product.spec }}</text>
              <view class="product-price-row">
                <text class="product-price">¥{{ formatPrice(product.price) }}</text>
                <text class="product-unit-label">/{{ product.unit }}</text>
              </view>
              <text class="product-stock" :class="{ 'product-stock-empty': product.stock <= 0 }">
                {{ product.stock > 0 ? `库存 ${product.stock}` : '暂时缺货' }}
              </text>
            </view>
          </view>
        </view>
      </view>

      <view class="catalog-note">
        <text>{{ catalogSource === 'api' ? '库存与价格来自商城实时商品接口' : '当前为本地档案展示，接口恢复后自动同步' }}</text>
        <text>商品详情、购物车和订单使用同一套商城业务接口</text>
      </view>
    </view>
  </s-layout>
</template>

<script setup>
  import { onLoad, onPullDownRefresh } from '@dcloudio/uni-app';
  import { computed, ref } from 'vue';
  import sheep from '@/sheep';
  import $share from '@/sheep/platform/share';
  import {
    categoryProducts,
    getMallCategory,
    mallCategories,
    mallProducts,
  } from '@/sheep/data/mall-catalog';
  import { loadMallCatalog } from '@/sheep/data/mall-catalog-runtime';

  // 隐藏原生 tabBar，使用商城自定义 tabBar。
  uni.hideTabBar({
    fail: () => {},
  });

  const popularCategories = mallCategories.slice(0, 8);
  const products = ref(mallProducts);
  const catalogSource = ref('local');
  const featuredProducts = computed(() =>
    (Array.isArray(products.value) ? products.value : []).filter(Boolean).slice(0, 6),
  );

  const categoryProductCount = (categoryKey) =>
    (Array.isArray(products.value) ? products.value : []).filter(
      (product) => product?.categoryKey === categoryKey,
    ).length ||
    categoryProducts(categoryKey).length;
  const categoryColor = (categoryKey) => getMallCategory(categoryKey)?.color || '#0f766e';
  const productGradient = (product) =>
    `linear-gradient(135deg, ${categoryColor(product?.categoryKey)} 0%, #0f172a 100%)`;
  const formatPrice = (price) => {
    const value = Number(price);
    return Number.isFinite(value) ? value.toFixed(2) : '0.00';
  };

  const openCategory = (categoryKey = '') => {
    sheep.$router.go('/pages/index/category', categoryKey ? { categoryKey } : {});
  };

  const openProduct = (product) => {
    if (product?.id) {
      sheep.$router.go('/pages/goods/index', { id: product.id });
      return;
    }
    openCategory(product?.categoryKey || '');
  };

  const openSearch = () => {
    sheep.$router.go('/pages/index/search');
  };

  async function loadCatalog(force = false) {
    try {
      const result = await loadMallCatalog(force);
      if (Array.isArray(result?.products)) {
        products.value = result.products.filter(Boolean);
        catalogSource.value = result.source === 'api' ? 'api' : 'local';
      }
    } catch (error) {
      // 运行时接口异常时继续使用本地档案，首页不应白屏。
      products.value = Array.isArray(products.value) ? products.value : mallProducts;
      catalogSource.value = 'local';
    }
  }

  onLoad((options = {}) => {
    loadCatalog();
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
      await loadCatalog(true);
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
    height: 74rpx;
    margin-right: 18rpx;
    border-radius: 20rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #0f766e;
    font-size: 24rpx;
    font-weight: 700;
    background: #d9f99d;
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
    padding: 22rpx;
    box-sizing: border-box;
    display: flex;
    position: relative;
    flex-direction: column;
    justify-content: space-between;
    color: rgba(255, 255, 255, 0.92);
  }

  .product-cover-image {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
    opacity: 0.28;
    mix-blend-mode: screen;
  }

  .product-code,
  .product-unit,
  .product-mark {
    position: relative;
    z-index: 1;
  }

  .product-code {
    font-size: 20rpx;
    letter-spacing: 1rpx;
    opacity: 0.78;
  }

  .product-unit {
    align-self: flex-end;
    padding: 6rpx 12rpx;
    border: 1rpx solid rgba(255, 255, 255, 0.4);
    border-radius: 999rpx;
    font-size: 19rpx;
  }

  .product-mark {
    font-size: 22rpx;
    font-weight: 600;
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
</style>
