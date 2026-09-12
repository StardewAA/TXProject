<script setup lang="ts">
import type { Product } from '@/types/mall'
import { formatMoney, productImage } from '@/utils/format'

defineProps<{ product: Product }>()
</script>

<template>
  <RouterLink class="product-card" :to="`/goods/${product.id}`">
    <div class="product-card__image">
      <img v-if="productImage(product.id, product.picUrl)" :src="productImage(product.id, product.picUrl)" :alt="product.name" loading="lazy" />
      <span v-else>暂无商品图片</span>
      <b v-if="Number(product.stock || 0) <= 0" class="sold-out">暂时售罄</b>
    </div>
    <div class="product-card__body">
      <h3>{{ product.name }}</h3>
      <p>{{ product.introduction || '防腐保温工程适用材料' }}</p>
      <div class="product-card__meta">
        <strong>¥{{ formatMoney(product.price) }}</strong>
        <span>{{ Number(product.stock || 0) > 0 ? `库存 ${product.stock}` : '不可购买' }}</span>
      </div>
      <small>{{ product.shopName || '店铺信息以详情为准' }}</small>
      <div class="product-card__footer"><span>查看规格与详情</span><b aria-hidden="true">↗</b></div>
    </div>
  </RouterLink>
</template>

<style scoped>
.product-card__image { position: relative; }
.sold-out { position: absolute; inset: auto 0 0; padding: 9px; background: rgba(31, 42, 54, .82); color: white; text-align: center; font-size: 14px; }
</style>
