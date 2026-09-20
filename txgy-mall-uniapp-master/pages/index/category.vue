<!-- 商品分类列表 -->
<template>
  <s-layout
    :bgStyle="{ color: '#f4f7fb' }"
    tenantEntry
    tabbar="/pages/index/category"
    title="商品分类"
  >
    <view class="mall-category">
      <view class="category-intro">
        <view>
          <text class="intro-title">商品分类</text>
          <text class="intro-subtitle">按产品档案快速找到工程材料</text>
        </view>
        <text class="intro-count">{{ products.length }} 款可售档案</text>
      </view>

      <view class="category-layout">
        <!-- 分类菜单 -->
        <scroll-view class="category-sidebar" scroll-y :style="{ height: pageHeight + 'px' }">
          <view
            v-for="(category, index) in catalogCategories"
            :key="category.key"
            class="sidebar-item"
            :class="{ 'sidebar-item-active': index === state.activeMenu }"
            @tap="onMenu(index)"
          >
            <view class="sidebar-icon" :style="{ backgroundColor: category.color }">
              {{ category.icon }}
            </view>
            <text class="sidebar-name">{{ category.name }}</text>
            <text class="sidebar-count">{{ categoryProductCount(category.key) }} 款</text>
          </view>
        </scroll-view>

        <!-- 商品档案 -->
        <scroll-view class="category-products" scroll-y :style="{ height: pageHeight + 'px' }">
          <view
            v-if="activeCategory"
            class="active-category"
            :style="{ background: categoryGradient(activeCategory) }"
          >
            <view>
              <text class="active-category-name">{{ activeCategory.name }}</text>
              <text class="active-category-hint">{{ activeCategory.hint }}</text>
            </view>
            <text class="active-category-count">
              {{ activeProducts.length }} 款
            </text>
          </view>

          <view class="group-label">
            <text>{{ activeCategory?.group || '工程材料' }}</text>
            <text class="group-label-line" />
          </view>

          <view v-if="activeProducts.length" class="product-list">
            <view
              v-for="product in activeProducts"
              :key="product.id || product.code"
              class="catalog-product"
              @tap="openProduct(product)"
            >
              <view class="catalog-cover" :style="{ background: productGradient(product) }">
                <image
                  v-if="product.picUrl"
                  class="catalog-cover-image"
                  :src="sheep.$url.cdn(product.picUrl)"
                  mode="aspectFill"
                />
                <text class="catalog-code">{{ product.code }}</text>
                <text class="catalog-unit">{{ product.unit }}</text>
                <text class="catalog-cover-label">产品档案</text>
              </view>
              <view class="catalog-product-body">
                <text class="catalog-product-name">{{ product.name }}</text>
                <text class="catalog-product-spec">{{ product.spec }}</text>
                <view class="catalog-price-row">
                  <text class="catalog-price">¥{{ formatPrice(product.price) }}</text>
                  <text class="catalog-price-unit">/{{ product.unit }}</text>
                </view>
                <text class="catalog-stock" :class="{ 'catalog-stock-empty': product.stock <= 0 }">
                  {{ product.stock > 0 ? `库存 ${product.stock}` : '暂时缺货' }}
                </text>
              </view>
            </view>
          </view>

          <view v-else class="catalog-empty">
            <text class="catalog-empty-title">暂无可售商品</text>
            <text class="catalog-empty-desc">该分类的商品档案正在补充</text>
          </view>

          <view class="integration-tip">
            <text>{{ catalogSource === 'api' ? '点击商品进入详情，可选择规格并加入购物车' : catalogSource === 'loading' ? '正在加载商城商品' : '商品暂无法加载，请刷新重试' }}</text>
          </view>
        </scroll-view>
      </view>
    </view>
  </s-layout>
</template>

<script setup>
  import { computed, reactive, ref } from 'vue';
  import { onLoad, onShow, onPullDownRefresh } from '@dcloudio/uni-app';
  import sheep from '@/sheep';
  import { getMallCategory } from '@/sheep/data/mall-catalog';
  import { loadMallCatalog } from '@/sheep/data/mall-catalog-runtime';

  const state = reactive({
    activeMenu: 0,
  });
  const products = ref([]);
  const catalogCategories = ref([]);
  const catalogSource = ref('loading');

  const safeArea = sheep.$platform.device?.safeArea || {
    height: typeof window === 'undefined' ? 667 : window.innerHeight,
  };
  const pageHeight = computed(() => Math.max(320, safeArea.height - 44 - 50));
  const activeCategory = computed(() =>
    catalogCategories.value[state.activeMenu] || catalogCategories.value[0],
  );
  const activeProducts = computed(() =>
    activeCategory.value && Array.isArray(products.value)
      ? products.value.filter((product) => product?.categoryKey === activeCategory.value.key)
      : [],
  );

  const categoryProductCount = (categoryKey) =>
    (Array.isArray(products.value) ? products.value : []).filter(
      (product) => product?.categoryKey === categoryKey,
    ).length;
  const categoryColor = (categoryKey) =>
    catalogCategories.value.find((category) => category.key === categoryKey)?.color ||
    getMallCategory(categoryKey)?.color ||
    '#0f766e';
  const categoryGradient = (item) => {
    const color = typeof item === 'string' ? categoryColor(item) : item?.color || '#0f766e';
    return `linear-gradient(135deg, ${color} 0%, #0f172a 100%)`;
  };
  const productGradient = (product) => categoryGradient(product?.categoryKey);
  const formatPrice = (price) => {
    const value = Number(price);
    return Number.isFinite(value) ? value.toFixed(2) : '0.00';
  };

  const onMenu = (index) => {
    state.activeMenu = index;
  };

  const openProduct = (product) => {
    if (product?.id) {
      sheep.$router.go('/pages/goods/index', { id: product.id });
      return;
    }
    uni.showToast({
      title: '商品接口暂不可用，请稍后重试',
      icon: 'none',
    });
  };

  async function loadCatalog(force = false) {
    try {
      const result = await loadMallCatalog(force);
      if (Array.isArray(result?.products)) {
        products.value = result.products.filter(Boolean);
        catalogCategories.value = Array.isArray(result?.categories) ? result.categories.filter(Boolean) : [];
        if (state.activeMenu >= catalogCategories.value.length) state.activeMenu = 0;
        catalogSource.value = 'api';
        applyCategoryParam();
      }
    } catch (error) {
      products.value = [];
      catalogSource.value = 'error';
    }
  }

  function applyCategoryParam(options = {}) {
    // switchTab cannot carry a query string. The router stores those
    // parameters in the app store, so consume them on every show (the page
    // may already be mounted when a category tile is clicked).
    const appStore = sheep.$store('app');
    const tabbarParams = appStore?.paramsForTabbar || {};
    const params = { ...options, ...tabbarParams };
    if (!catalogCategories.value.length) return;
    let index = catalogCategories.value.findIndex((item) => item.key === params.categoryKey);
    const categoryId = Number(params.categoryId || params.id);
    if (index < 0 && Number.isFinite(categoryId) && categoryId > 0) {
      index = catalogCategories.value.findIndex((item) => Number(item.apiId) === categoryId);
    }
    if (index >= 0) {
      state.activeMenu = index;
    }
    if (Object.keys(tabbarParams).length) {
      appStore?.clearParamsForTabbar?.();
    }
  }

  onLoad((options = {}) => {
    loadCatalog();
    applyCategoryParam(options);
  });

  onShow(() => applyCategoryParam());

  onPullDownRefresh(async () => {
    try {
      await loadCatalog(true);
    } finally {
      uni.stopPullDownRefresh();
    }
  });
</script>

<style lang="scss" scoped>
  .mall-category {
    padding: 24rpx 20rpx calc(120rpx + env(safe-area-inset-bottom));
  }

  .category-intro {
    padding: 24rpx 26rpx;
    border-radius: 22rpx;
    display: flex;
    align-items: flex-end;
    justify-content: space-between;
    background: #fff;
    box-shadow: 0 8rpx 24rpx rgba(15, 23, 42, 0.05);
  }

  .intro-title,
  .intro-subtitle {
    display: block;
  }

  .intro-title {
    color: #0f172a;
    font-size: 34rpx;
    font-weight: 700;
  }

  .intro-subtitle {
    margin-top: 8rpx;
    color: #64748b;
    font-size: 21rpx;
  }

  .intro-count {
    color: #0f766e;
    font-size: 21rpx;
    font-weight: 600;
  }

  .category-layout {
    min-height: 320px;
    margin-top: 20rpx;
    display: flex;
    overflow: hidden;
    border-radius: 22rpx;
    background: #fff;
    box-shadow: 0 8rpx 24rpx rgba(15, 23, 42, 0.05);
  }

  .category-sidebar {
    width: 184rpx;
    flex: 0 0 184rpx;
    box-sizing: border-box;
    padding: 12rpx 0;
    background: #f1f5f9;
  }

  .sidebar-item {
    min-height: 112rpx;
    padding: 14rpx 8rpx;
    box-sizing: border-box;
    display: flex;
    align-items: center;
    flex-direction: column;
    position: relative;
  }

  .sidebar-item-active {
    background: #fff;
  }

  .sidebar-item-active::before {
    content: '';
    width: 8rpx;
    height: 58rpx;
    border-radius: 0 8rpx 8rpx 0;
    position: absolute;
    left: 0;
    top: 28rpx;
    background: #0f766e;
  }

  .sidebar-icon {
    width: 48rpx;
    height: 48rpx;
    border-radius: 16rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    font-size: 17rpx;
    font-weight: 700;
  }

  .sidebar-name {
    max-width: 100%;
    margin-top: 9rpx;
    overflow: hidden;
    color: #334155;
    font-size: 22rpx;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .sidebar-count {
    margin-top: 4rpx;
    color: #94a3b8;
    font-size: 18rpx;
  }

  .category-products {
    flex: 1;
    min-width: 0;
    box-sizing: border-box;
    padding: 20rpx;
    background: #fff;
  }

  .active-category {
    min-height: 128rpx;
    padding: 22rpx 24rpx;
    box-sizing: border-box;
    border-radius: 18rpx;
    display: flex;
    align-items: flex-end;
    justify-content: space-between;
    color: #fff;
  }

  .active-category-name,
  .active-category-hint {
    display: block;
  }

  .active-category-name {
    font-size: 32rpx;
    font-weight: 700;
  }

  .active-category-hint {
    margin-top: 8rpx;
    color: rgba(255, 255, 255, 0.78);
    font-size: 20rpx;
  }

  .active-category-count {
    color: rgba(255, 255, 255, 0.9);
    font-size: 22rpx;
  }

  .group-label {
    margin: 24rpx 0 16rpx;
    display: flex;
    align-items: center;
    color: #334155;
    font-size: 23rpx;
    font-weight: 600;
  }

  .group-label-line {
    height: 1rpx;
    flex: 1;
    margin-left: 14rpx;
    background: #e2e8f0;
  }

  .product-list {
    display: flex;
    flex-wrap: wrap;
    justify-content: space-between;
  }

  .catalog-product {
    width: calc(50% - 8rpx);
    margin-bottom: 18rpx;
    overflow: hidden;
    border-radius: 18rpx;
    background: #fff;
    box-shadow: 0 8rpx 20rpx rgba(15, 23, 42, 0.08);
  }

  .catalog-cover {
    height: 144rpx;
    padding: 16rpx;
    box-sizing: border-box;
    display: flex;
    position: relative;
    flex-direction: column;
    justify-content: space-between;
    color: rgba(255, 255, 255, 0.92);
  }

  .catalog-cover-image {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
    opacity: 0.28;
    mix-blend-mode: screen;
  }

  .catalog-code,
  .catalog-unit,
  .catalog-cover-label {
    position: relative;
    z-index: 1;
  }

  .catalog-code {
    color: rgba(255, 255, 255, 0.72);
    font-size: 17rpx;
    letter-spacing: 1rpx;
  }

  .catalog-unit {
    align-self: flex-end;
    padding: 4rpx 10rpx;
    border: 1rpx solid rgba(255, 255, 255, 0.4);
    border-radius: 999rpx;
    font-size: 17rpx;
  }

  .catalog-cover-label {
    font-size: 19rpx;
    font-weight: 600;
  }

  .catalog-product-body {
    padding: 14rpx;
  }

  .catalog-product-name,
  .catalog-product-spec {
    display: block;
  }

  .catalog-product-name {
    overflow: hidden;
    color: #1e293b;
    font-size: 23rpx;
    font-weight: 600;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .catalog-product-spec {
    height: 32rpx;
    margin-top: 7rpx;
    overflow: hidden;
    color: #64748b;
    font-size: 18rpx;
    line-height: 32rpx;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .catalog-price-row {
    display: flex;
    align-items: baseline;
    margin-top: 12rpx;
  }

  .catalog-price {
    color: #dc2626;
    font-size: 25rpx;
    font-weight: 700;
  }

  .catalog-price-unit {
    margin-left: 3rpx;
    color: #94a3b8;
    font-size: 17rpx;
  }

  .catalog-stock {
    display: block;
    margin-top: 7rpx;
    color: #0f766e;
    font-size: 18rpx;
  }

  .catalog-stock-empty {
    color: #94a3b8;
  }

  .catalog-empty {
    padding: 100rpx 10rpx;
    text-align: center;
  }

  .catalog-empty-title,
  .catalog-empty-desc {
    display: block;
  }

  .catalog-empty-title {
    color: #334155;
    font-size: 26rpx;
    font-weight: 600;
  }

  .catalog-empty-desc {
    margin-top: 10rpx;
    color: #94a3b8;
    font-size: 20rpx;
  }

  .integration-tip {
    padding: 26rpx 0 12rpx;
    text-align: center;
    color: #94a3b8;
    font-size: 18rpx;
  }
</style>
