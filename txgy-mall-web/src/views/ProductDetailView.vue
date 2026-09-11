<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'

import { productApi } from '@/api/mall'
import { useAuthStore } from '@/stores/auth'
import { useCartStore } from '@/stores/cart'
import type { Product, ProductSku, Shop } from '@/types/mall'
import { formatMoney, normalizeImage, productImage, propertyText, sanitizeRichHtml } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const cartStore = useCartStore()
const product = ref<Product | null>(null)
const shop = ref<Shop | null>(null)
const comments = ref<Array<Record<string, unknown>>>([])
const selectedSku = ref<ProductSku | null>(null)
const quantity = ref(1)
const loading = ref(true)
const adding = ref(false)
const displayPrice = computed(() => selectedSku.value?.price ?? product.value?.price ?? 0)
const displayStock = computed(() => selectedSku.value?.stock ?? product.value?.stock ?? 0)
const safeDescription = computed(() => sanitizeRichHtml(product.value?.description))
const detailImage = computed(() => productImage(product.value?.id, product.value?.picUrl))
const galleryImages = computed(() => [...new Set([
  detailImage.value,
  ...(product.value?.sliderPicUrls || []).map(normalizeImage),
].filter(Boolean))])
const selectedImage = ref('')
const activeImage = computed(() => selectedImage.value || galleryImages.value[0] || '')

async function load() {
  loading.value = true
  try {
    const data = await productApi.getDetail(String(route.params.id))
    product.value = data
    selectedImage.value = ''
    selectedSku.value = data.skus?.find((sku) => Number(sku.stock) > 0) || data.skus?.[0] || null
    shop.value = null
    if (data.shopId) {
      try { shop.value = await productApi.getShop(data.shopId) } catch { shop.value = null }
    }
    try { comments.value = (await productApi.getComments({ spuId: data.id, pageNo: 1, pageSize: 5, type: 0 })).list || [] } catch { comments.value = [] }
  } finally { loading.value = false }
}

function selectSku(sku: ProductSku) {
  selectedSku.value = sku
  if (quantity.value > sku.stock) quantity.value = Math.max(1, sku.stock)
}

async function addToCart(goCheckout = false) {
  if (!selectedSku.value) return ElMessage.warning('请选择完整的商品规格')
  if (displayStock.value <= 0) return ElMessage.warning('当前规格库存不足')
  if (!authStore.isLoggedIn) return router.push({ name: 'login', query: { redirect: route.fullPath } })
  adding.value = true
  try {
    if (goCheckout) {
      sessionStorage.setItem('mall-checkout-items', JSON.stringify([{ skuId: selectedSku.value.id, count: quantity.value }]))
      await router.push('/checkout')
    } else {
      await cartStore.add(selectedSku.value.id, quantity.value)
      ElMessage.success('已加入购物车')
    }
  } finally { adding.value = false }
}

async function toggleFavorite() {
  if (!product.value) return
  if (!authStore.isLoggedIn) return router.push({ name: 'login', query: { redirect: route.fullPath } })
  if (product.value.favorite) await productApi.deleteFavorite(product.value.id)
  else await productApi.addFavorite(product.value.id)
  product.value.favorite = !product.value.favorite
  ElMessage.success(product.value.favorite ? '收藏成功' : '已取消收藏')
}

watch(() => route.params.id, load)
onMounted(load)
</script>

<template>
  <div class="container page-section">
    <div class="breadcrumbs"><RouterLink to="/">首页</RouterLink><span>›</span><RouterLink to="/goods">商品列表</RouterLink><span>›</span><span>{{ product?.name || '商品详情' }}</span></div>
    <div v-if="loading" class="panel loading-box"><el-skeleton :rows="10" animated /></div>
    <template v-else-if="product">
      <section class="goods-main panel">
        <div class="gallery">
          <div class="gallery__main"><img v-if="activeImage" :src="activeImage" :alt="product.name" /><span v-else>暂无商品图片</span></div>
          <div class="gallery__thumbs"><button v-for="(pic, index) in galleryImages" :key="pic" type="button" :class="{ active: activeImage === pic }" :aria-label="`查看第 ${index + 1} 张商品图片`" :aria-pressed="activeImage === pic" @click="selectedImage = pic"><img :src="pic" :alt="`${product.name} 主图 ${index + 1}`" loading="lazy" /></button></div>
        </div>
        <div class="goods-info">
          <h1>{{ product.name }}</h1><p class="intro">{{ product.introduction }}</p>
          <div class="price-box"><span>平台价格</span><strong>¥{{ formatMoney(displayPrice) }}</strong><del v-if="product.marketPrice">¥{{ formatMoney(product.marketPrice) }}</del></div>
          <dl><dt>所属店铺</dt><dd>{{ shop?.name || product.shopName || '店铺信息加载失败' }}</dd><dt>累计销量</dt><dd>{{ product.salesCount || 0 }}</dd><dt>配送说明</dt><dd>配送范围与费用以订单结算为准</dd></dl>
          <div v-if="product.skus?.length" class="sku-area"><label>规格选择</label><div><button v-for="sku in product.skus" :key="sku.id" :class="{ active: selectedSku?.id === sku.id, disabled: sku.stock <= 0 }" :disabled="sku.stock <= 0" @click="selectSku(sku)">{{ propertyText(sku.properties) || `规格 ${sku.id}` }}</button></div></div>
          <div class="quantity"><label>采购数量</label><el-input-number v-model="quantity" :min="1" :max="Math.max(1, displayStock)" /><span>当前可售 {{ displayStock }}</span></div>
          <div class="goods-actions"><el-button size="large" :loading="adding" :disabled="displayStock <= 0" @click="addToCart(false)">{{ displayStock > 0 ? '加入购物车' : '暂时售罄' }}</el-button><el-button type="primary" size="large" :loading="adding" :disabled="displayStock <= 0" @click="addToCart(true)">立即采购</el-button><button class="favorite" @click="toggleFavorite">{{ product.favorite ? '★ 已收藏' : '☆ 收藏商品' }}</button></div>
        </div>
      </section>
      <section class="goods-detail panel page-section">
        <div class="detail-title">商品详情</div>
        <div class="product-intro-image"><img :src="detailImage" :alt="`${product.name} 图片介绍`" /></div>
        <div v-if="safeDescription" class="rich-content" v-html="safeDescription"></div>
        <div v-else class="empty-detail">暂无文字介绍</div>
      </section>
      <section class="goods-detail panel page-section"><div class="detail-title">商品评价（{{ comments.length }}）</div><div v-if="comments.length" class="comment-list"><article v-for="(comment, index) in comments" :key="String(comment.id || index)"><header><strong>{{ comment.userNickname || '商城用户' }}</strong><el-rate :model-value="Number(comment.scores || 5)" disabled /></header><p>{{ comment.content || '用户未填写文字评价' }}</p><small>{{ comment.createTime || '' }}</small><div v-if="Array.isArray(comment.picUrls)" class="comment-images"><img v-for="pic in comment.picUrls" :key="String(pic)" :src="normalizeImage(String(pic))" alt="评价图片" /></div><blockquote v-if="comment.replyContent">商家回复：{{ comment.replyContent }}</blockquote></article></div><div v-else class="empty-detail">暂无商品评价</div></section>
    </template>
  </div>
</template>

<style scoped lang="scss">
.breadcrumbs { display: flex; gap: 10px; margin-bottom: 16px; color: var(--muted); font-size: 14px; }
.goods-main { display: grid; grid-template-columns: 520px 1fr; gap: 44px; padding: 28px; }
.gallery__main { height: 490px; display: grid; place-items: center; overflow: hidden; background: #f5f6f6; color: var(--muted); }.gallery__main img { width: 100%; height: 100%; object-fit: contain; }
.gallery__thumbs { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 12px; }.gallery__thumbs button { width: 68px; height: 68px; padding: 2px; border: 1px solid var(--line); background: white; cursor: pointer; }.gallery__thumbs button.active { border: 2px solid var(--brand); }.gallery__thumbs img { width: 100%; height: 100%; object-fit: cover; }
.goods-info h1 { margin: 4px 0 10px; color: #1d2931; font-size: 26px; line-height: 1.5; }.intro { min-height: 24px; color: var(--muted); }
.price-box { display: flex; align-items: baseline; gap: 18px; margin: 22px 0; padding: 20px; background: #f7f3f0; }.price-box span { color: var(--muted); }.price-box strong { color: #dc3f1d; font-size: 34px; }.price-box del { color: #9ba2a7; }
dl { display: grid; grid-template-columns: 90px 1fr 90px 1fr; gap: 16px 8px; padding-bottom: 20px; border-bottom: 1px solid var(--line); }dt { color: var(--muted); }dd { margin: 0; }
.sku-area, .quantity { display: grid; grid-template-columns: 90px 1fr; align-items: start; margin-top: 20px; }.sku-area label, .quantity label { color: var(--muted); padding-top: 9px; }.sku-area button { min-width: 90px; margin: 0 10px 10px 0; padding: 9px 13px; border: 1px solid #ccd2d6; background: white; }.sku-area button.active { border-color: var(--brand); color: var(--brand); background: #fff6f2; }.sku-area button.disabled { color: #aaa; border-style: dashed; }
.quantity { grid-template-columns: 90px 160px 1fr; align-items: center; }.quantity label { padding: 0; }.quantity span { color: var(--muted); font-size: 13px; }
.goods-actions { display: flex; align-items: center; gap: 12px; margin-top: 30px; }.goods-actions :deep(.el-button) { min-width: 140px; }.favorite { margin-left: 8px; border: 0; background: transparent; color: #68757e; }
.goods-detail { min-height: 240px; padding: 0 30px 40px; }.detail-title { padding: 20px 0; border-bottom: 2px solid var(--brand); font-size: 19px; font-weight: 700; }.rich-content { padding: 28px 10px; line-height: 1.8; overflow: hidden; }.rich-content :deep(img) { max-width: 100%; }.empty-detail { padding: 80px; text-align: center; color: var(--muted); }.comment-list article { padding: 22px 8px; border-bottom: 1px solid var(--line); }.comment-list header { display: flex; gap: 18px; align-items: center; }.comment-list p { line-height: 1.7; }.comment-list small { color: var(--muted); }.comment-images { display: flex; gap: 10px; margin-top: 12px; }.comment-images img { width: 82px; height: 82px; object-fit: cover; }.comment-list blockquote { margin: 14px 0 0; padding: 12px; background: #f7f8f9; color: #59666f; }
.product-intro-image { max-width: 920px; margin: 30px auto 0; padding: 28px; background: #f7f8f8; text-align: center; }.product-intro-image img { max-width: 100%; max-height: 720px; object-fit: contain; }
</style>
