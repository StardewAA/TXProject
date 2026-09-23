<script setup lang="ts">
import type { PortalProduct, PortalShop } from '@/api/portal'
import { formatMoney, normalizeImage } from '@/utils/format'
defineProps<{ shop: PortalShop; products?: PortalProduct[]; failed?: boolean }>()
</script>

<template>
  <article class="supplier-card">
    <header><RouterLink :to="`/shops/${shop.shopId}`" class="identity"><img v-if="shop.logoUrl" :src="normalizeImage(shop.logoUrl)" alt="" /><span v-else>{{ shop.shopName.slice(0, 2) }}</span><div><small>{{ shop.tenantName }} / {{ shop.enterpriseName }}</small><h3>{{ shop.shopName }}</h3></div></RouterLink><RouterLink :to="`/shops/${shop.shopId}`">进入店铺 ↗</RouterLink></header>
    <p>{{ shop.introduction || '查看店铺公开供应材料、规格与价格。' }}</p><div class="count">{{ shop.productCount }} 件公开在售商品</div>
    <div v-if="products?.length" class="products"><RouterLink v-for="product in products" :key="String(product.spuId)" :to="{ name: 'tenant-product-detail', params: { tenantSlug: product.tenantSlug, spuId: String(product.spuId) } }"><img v-if="product.picUrl" :src="normalizeImage(product.picUrl)" :alt="product.spuName" /><span>{{ product.spuName }}</span><strong>¥{{ formatMoney(product.price) }}</strong></RouterLink></div>
    <p v-else class="empty">{{ failed ? '商品预览加载失败，请进入店铺重试' : '店铺正在准备商品预览' }}</p>
  </article>
</template>

<style scoped>
.supplier-card{min-width:0;padding:26px;border:1px solid #dde6e5;border-radius:8px;background:#fff}.supplier-card header{display:flex;align-items:center;justify-content:space-between;gap:12px}.identity{display:flex;align-items:center;gap:12px;min-width:0}.identity>img,.identity>span{display:grid;width:48px;height:48px;flex:none;border-radius:6px;background:#edf5f2;object-fit:contain;place-items:center;color:#147d6b;font-weight:700}.identity small{color:#738580;font-size:11px}.identity h3{margin:5px 0;font-size:17px}.supplier-card header>a:last-child{color:#147d6b;font-size:12px;white-space:nowrap}.supplier-card>p{min-height:46px;margin:18px 0 6px;color:#65736f;font-size:13px;line-height:1.8}.count{margin-bottom:18px;color:#89948f;font-size:12px}.products{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:14px}.products img{width:100%;aspect-ratio:1.2;border:1px solid #eef1ef;background:#f5f7f6;object-fit:contain}.products span{display:block;overflow:hidden;margin:10px 0 6px;text-overflow:ellipsis;white-space:nowrap;font-size:12px}.products strong{color:#b85531;font-size:14px}.empty{padding:30px 0!important;color:#7e8b86!important}
</style>
