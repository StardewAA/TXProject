<template>
  <view class="shop-showcase">
    <view v-if="loading" class="shop-state">正在加载企业店铺…</view>
    <view v-else-if="failed" class="shop-state" @tap="load">店铺加载失败，点击重试</view>
    <view v-else-if="!visibleShops.length" class="shop-state">{{ keyword ? '没有找到匹配的店铺' : '暂无已开通的企业店铺' }}</view>
    <view v-for="shop in visibleShops" :key="shop.id" class="shop-card">
      <view class="shop-heading" @tap="openShop(shop)">
        <view class="shop-mark">{{ shop.name?.slice(0, 1) || '店' }}</view>
        <view class="shop-identity"><text class="shop-name">{{ shop.name }}</text><text class="shop-caption">企业材料供应商</text></view>
        <text class="shop-entry">进店 ›</text>
      </view>
      <view class="shop-intro" @tap="openShop(shop)">{{ shop.introduction || '进入店铺，查看在售材料、规格与库存' }}</view>
      <template v-if="limit">
        <view v-if="previews[shop.id]?.products.length" class="shop-products">
          <view v-for="product in previews[shop.id].products" :key="product.id" class="shop-product" @tap="sheep.$router.go('/pages/goods/index', { id: product.id })">
            <image v-if="product.picUrl && !brokenImages[product.id]" :src="sheep.$url.cdn(product.picUrl)" mode="aspectFit" @error="brokenImages[product.id] = true" />
            <view v-else class="product-placeholder">材料图片</view>
            <text class="product-name">{{ product.name }}</text><text class="product-price">¥{{ fen2yuan(product.price) }}</text>
          </view>
        </view>
        <view v-if="previews[shop.id]" class="shop-count" @tap="openShop(shop)">共 {{ previews[shop.id].total }} 款在售商品 · 查看全部 ›</view>
        <view v-else class="shop-count" @tap="openShop(shop)">进店查看供应商品 ›</view>
      </template>
    </view>
  </view>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import sheep from '@/sheep';
import ShopApi from '@/sheep/api/product/shop';
import SpuApi from '@/sheep/api/product/spu';
import { fen2yuan } from '@/sheep/hooks/useGoods';
const props = defineProps({ limit: { type: Number, default: 0 }, keyword: { type: String, default: '' } });
const shops = ref([]);
const previews = ref({});
const brokenImages = ref({});
const loading = ref(false);
const failed = ref(false);
const visibleShops = computed(() => {
  const query = props.keyword.trim().toLowerCase();
  const list = shops.value.filter(shop => !query || `${shop.name} ${shop.introduction || ''}`.toLowerCase().includes(query));
  return props.limit ? list.slice(0, props.limit) : list;
});
const openShop = shop => sheep.$router.go('/pages/goods/list', { shopId: shop.id });
async function load() {
  if (loading.value) return;
  loading.value = true;
  failed.value = false;
  try {
    const result = await ShopApi.getList();
    if (result?.code !== 0 || !Array.isArray(result.data)) throw new Error('店铺加载失败');
    shops.value = result.data.filter(shop => shop?.id).sort((a, b) => Number(/测试/.test(a.name)) - Number(/测试/.test(b.name)));
    previews.value = {};
    if (props.limit) await Promise.allSettled(visibleShops.value.map(async shop => {
      const result = await SpuApi.getSpuPage({ shopId: shop.id, pageNo: 1, pageSize: 3 });
      if (result?.code === 0 && result.data) previews.value[shop.id] = { products: result.data.list || [], total: result.data.total || 0 };
    }));
  } catch { failed.value = true; shops.value = []; }
  finally { loading.value = false; }
}
defineExpose({ refresh: load });
onMounted(load);
</script>

<style scoped>
.shop-card{padding:26rpx;background:white;border:1rpx solid #dce8e2;border-radius:18rpx;margin-bottom:20rpx}.shop-heading{display:flex;align-items:center;gap:16rpx}.shop-mark{width:76rpx;height:76rpx;border-radius:14rpx;background:#e7f2ec;color:#147d6b;display:flex;align-items:center;justify-content:center;font-size:32rpx;font-weight:600;flex-shrink:0}.shop-identity{flex:1;min-width:0}.shop-name{display:block;font-size:28rpx;font-weight:600;color:#203b34;line-height:1.5}.shop-caption{display:block;font-size:21rpx;color:#81958d;margin-top:5rpx}.shop-entry{font-size:23rpx;color:#147d6b;flex-shrink:0}.shop-intro{font-size:24rpx;line-height:1.7;color:#74867d;margin-top:20rpx}.shop-products{display:flex;gap:16rpx;margin-top:24rpx}.shop-product{width:calc((100% - 32rpx)/3);min-width:0}.shop-product image,.product-placeholder{width:100%;height:140rpx;background:#f3f7f4;border-radius:8rpx}.product-placeholder{display:flex;align-items:center;justify-content:center;color:#82938b;font-size:21rpx}.product-name{display:block;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;font-size:23rpx;margin-top:12rpx;color:#263b33}.product-price{display:block;font-size:25rpx;font-weight:600;color:#c04d2b;margin-top:8rpx}.shop-count{padding-top:20rpx;margin-top:20rpx;border-top:1rpx solid #edf2ee;color:#147d6b;font-size:23rpx}.shop-state{padding:45rpx 20rpx;text-align:center;color:#73877d;font-size:26rpx}
</style>
