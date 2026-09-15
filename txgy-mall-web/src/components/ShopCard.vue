<script setup lang="ts">
import { ref } from 'vue'
import type { Product, Shop } from '@/types/mall'
import { formatMoney, normalizeImage, productImage } from '@/utils/format'
defineProps<{ shop: Shop; products?: Product[]; total?: number; failed?: boolean }>()
const logoFailed = ref(false)
</script>

<template>
  <article class="supplier-card">
    <div class="supplier-card__heading">
      <RouterLink :to="`/shops/${shop.id}`" class="supplier-identity">
        <img v-if="shop.logoUrl && !logoFailed" :src="normalizeImage(shop.logoUrl)" alt="" @error="logoFailed = true" />
        <span v-else class="supplier-monogram" aria-hidden="true">{{ shop.name.slice(0, 2) }}</span>
        <div><small>企业店铺</small><h3>{{ shop.name }}</h3></div>
      </RouterLink>
      <RouterLink class="supplier-entry" :to="`/shops/${shop.id}`">进入店铺 ↗</RouterLink>
    </div>
    <p class="supplier-description">{{ shop.introduction || '了解店铺供应材料，按规格选购工程所需。' }}</p>
    <div class="supplier-count">{{ total === undefined ? '查看店内供应商品' : `${total} 件在售商品` }}</div>
    <div v-if="products?.length" class="supplier-products">
      <RouterLink v-for="product in products.slice(0, 3)" :key="product.id" :to="`/goods/${product.id}`">
        <img :src="productImage(product.id, product.picUrl)" :alt="product.name" loading="lazy" />
        <span>{{ product.name }}</span><strong>¥{{ formatMoney(product.price) }}</strong>
      </RouterLink>
    </div>
    <p v-else class="supplier-empty">{{ failed ? '商品暂时加载失败，请进入店铺重试' : '店铺正在准备供应商品' }}</p>
  </article>
</template>

<style scoped>
.supplier-card{padding:26px;background:#fff;border:1px solid #dde6e5;border-radius:8px;min-width:0}.supplier-card__heading{display:flex;align-items:center;justify-content:space-between;gap:12px}.supplier-identity{display:flex;align-items:center;gap:12px;min-width:0}.supplier-identity img,.supplier-monogram{width:48px;height:48px;flex-shrink:0;object-fit:contain;background:#edf5f2;border-radius:6px}.supplier-monogram{display:grid;place-items:center;color:#147d6b;font-weight:700}.supplier-identity small{font-size:11px;color:#738580;letter-spacing:2px}.supplier-identity h3{font-size:17px;margin:5px 0;line-height:1.4}.supplier-entry{color:#147d6b;white-space:nowrap;font-size:12px}.supplier-description{font-size:13px;color:#65736f;line-height:1.8;min-height:46px;margin:18px 0 6px;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}.supplier-count{font-size:12px;color:#89948f;margin-bottom:18px}.supplier-products{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:14px}.supplier-products img{width:100%;aspect-ratio:1.2;object-fit:contain;background:#f5f7f6;border:1px solid #eef1ef}.supplier-products span{display:block;font-size:12px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;margin:10px 0 6px}.supplier-products strong{color:#b85531;font-size:14px}.supplier-empty{padding:30px 0;color:#7e8b86;font-size:13px}
</style>
