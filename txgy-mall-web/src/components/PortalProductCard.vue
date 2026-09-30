<script setup lang="ts">
import type { PortalProduct } from '@/api/portal'
import { formatMoney, normalizeImage } from '@/utils/format'
defineProps<{ product: PortalProduct }>()
</script>

<template>
  <RouterLink class="product-card" :to="{ name: 'tenant-product-detail', params: { tenantSlug: product.tenantSlug, spuId: String(product.spuId) } }">
    <div class="product-card__image"><img v-if="product.picUrl" :src="normalizeImage(product.picUrl)" :alt="product.spuName" loading="lazy" /><span v-else>暂无商品图片</span></div>
    <div class="product-card__body"><h3>{{ product.spuName }}</h3><p>{{ product.introduction || '防腐保温工程适用材料' }}</p><div class="product-card__meta"><strong>¥{{ formatMoney(product.price) }}</strong><span>{{ product.saleable ? '公开可售' : '暂不可售' }}</span></div><small>{{ product.shopName }}</small><div class="product-card__footer"><span>选规格并加入对比</span><b>↗</b></div></div>
  </RouterLink>
</template>
