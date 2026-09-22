<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'

import { portalApi, type PortalProduct, type PortalSku } from '@/api/portal'
import { useAuthStore } from '@/stores/auth'
import { useCartStore } from '@/stores/cart'
import { useComparisonStore } from '@/stores/comparison'
import { formatMoney, normalizeImage, propertyText, sanitizeRichHtml } from '@/utils/format'
import { platformCheckoutDraft } from '@/utils/platform-checkout-draft'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const cartStore = useCartStore()
const comparison = useComparisonStore()
const product = ref<PortalProduct | null>(null)
const selectedSku = ref<PortalSku | null>(null)
const compareSkuChosen = ref(false)
const quantity = ref(1)
const loading = ref(false)
const adding = ref(false)
const comparing = ref(false)
const error = ref('')
let generation = 0
let alive = true

const tenantSlug = computed(() => String(route.params.tenantSlug || ''))
const spuId = computed(() => String(route.params.spuId || ''))
const safeDescription = computed(() => sanitizeRichHtml(product.value?.description))
const gallery = computed(() => [...new Set([
  selectedSku.value?.picUrl,
  product.value?.picUrl,
  ...(product.value?.skus || []).map(sku => sku.picUrl),
].filter(Boolean).map(value => normalizeImage(String(value))))])
const displayPrice = computed(() => selectedSku.value?.price ?? product.value?.price ?? 0)

async function load() {
  const current = ++generation
  const slug = tenantSlug.value
  const id = spuId.value
  product.value = null
  selectedSku.value = null
  compareSkuChosen.value = false
  error.value = ''
  if (!slug || !/^[1-9]\d*$/.test(id)) { error.value = '商品链接无效'; return }
  loading.value = true
  try {
    const result = await portalApi.getTenantProduct(slug, id)
    if (!alive || current !== generation || slug !== tenantSlug.value || id !== spuId.value) return
    if (String(result.spuId) !== id || result.tenantSlug !== slug) throw new Error('商品经营归属与当前租户商城不一致')
    product.value = result
    selectedSku.value = result.skus?.[0] || null
    compareSkuChosen.value = result.skus?.length === 1
  } catch (reason) {
    if (alive && current === generation) error.value = reason instanceof Error ? reason.message : '商品详情加载失败'
  } finally { if (alive && current === generation) loading.value = false }
}

function selectSku(sku: PortalSku) {
  selectedSku.value = sku
  compareSkuChosen.value = true
}

async function addToComparison() {
  if (comparing.value) return
  if (!selectedSku.value || !compareSkuChosen.value) return ElMessage.warning('请先点击选择需要对比的实际规格，不会自动使用最低价规格')
  const owner = comparison.ownerKey(), slug = tenantSlug.value, id = spuId.value
  comparing.value = true
  try {
    await comparison.add(selectedSku.value.skuId)
    if (alive && owner === comparison.ownerKey() && slug === tenantSlug.value && id === spuId.value) ElMessage.success('已加入对比，可从顶部或右下角查看')
  } catch (reason) {
    if (alive && owner === comparison.ownerKey()) ElMessage.warning(reason instanceof Error ? reason.message : '加入对比失败')
  } finally {
    if (alive && owner === comparison.ownerKey()) comparing.value = false
  }
}

async function add(goCheckout = false) {
  if (!product.value?.saleable || !selectedSku.value) return ElMessage.warning('请选择可购买的商品规格')
  if (!Number.isSafeInteger(quantity.value) || quantity.value < 1) return ElMessage.warning('请输入有效采购数量')
  if (!authStore.isLoggedIn) return router.push({ name: 'platform-login', query: { redirect: route.fullPath } })
  adding.value = true
  try {
    // Only the immutable SKU id and quantity leave the browser. The server resolves
    // tenant, enterprise, shop and current saleability again before writing the cart.
    const cartId = await cartStore.add(selectedSku.value.skuId, quantity.value)
    if (goCheckout) {
      if (!cartId) throw new Error('购物车未返回有效商品编号')
      platformCheckoutDraft.save([cartId])
      await router.push('/checkout')
    } else ElMessage.success('已加入平台购物车')
  } catch (reason) {
    ElMessage.warning(reason instanceof Error ? reason.message : '加入购物车失败')
  } finally { adding.value = false }
}

watch(() => [route.params.tenantSlug, route.params.spuId], load, { immediate: true })
onBeforeUnmount(() => { alive = false; generation += 1 })
</script>

<template>
  <div class="platform-detail-page">
    <div class="container breadcrumbs"><RouterLink to="/">平台首页</RouterLink><span>›</span><RouterLink :to="`/tenants/${tenantSlug}`">{{ product?.tenantName || '租户商城' }}</RouterLink><span>›</span><span>{{ product?.spuName || '商品详情' }}</span></div>
    <div v-if="loading" class="container panel loading"><el-skeleton :rows="10" animated /></div>
    <el-result v-else-if="error || !product" class="container panel" icon="warning" title="商品详情不可用" :sub-title="error || '商品可能已下架、待审核或所属租户未开放'"><template #extra><el-button type="primary" @click="router.push('/')">返回平台首页</el-button></template></el-result>
    <template v-else>
      <section class="container panel detail-main">
        <div class="gallery"><div class="gallery-main"><img v-if="gallery[0]" :src="gallery[0]" :alt="product.spuName" /><span v-else>暂无图片</span></div><div class="gallery-list"><img v-for="image in gallery" :key="image" :src="image" :alt="product.spuName" /></div></div>
        <div class="detail-body"><small>{{ product.tenantName }} / {{ product.enterpriseName }}</small><h1>{{ product.spuName }}</h1><p>{{ product.introduction || '防腐保温工程适用材料' }}</p><div class="price"><span>平台公开价</span><strong>¥{{ formatMoney(displayPrice) }}</strong></div><dl><dt>所属店铺</dt><dd>{{ product.shopName }}</dd><dt>经营租户</dt><dd>{{ product.tenantName }}</dd><dt>累计销量</dt><dd>{{ product.salesCount || 0 }}</dd></dl><div class="shop-service"><div><b>店铺客服</b><span>咨询商品规格、价格、库存与配送</span></div><MallLink v-if="selectedSku" :to="{ path: `/member/customer-service/shop/${product.shopId}`, query: { resourceType: 'PRODUCT', resourceId: String(product.spuId), productSkuId: String(selectedSku.skuId) } }">联系店铺客服 →</MallLink><span v-else>暂无可咨询规格</span></div><div class="skus"><label>规格选择</label><button v-for="sku in product.skus || []" :key="String(sku.skuId)" :class="{ active: selectedSku?.skuId === sku.skuId }" @click="selectSku(sku)">{{ propertyText(sku.properties) || sku.skuName || `SKU ${sku.skuId}` }}</button></div><div class="actions"><el-input-number v-model="quantity" :min="1" :max="99999" /><el-button :loading="adding" :disabled="!selectedSku" @click="add(false)">加入平台购物车</el-button><el-button type="primary" :loading="adding" :disabled="!selectedSku" @click="add(true)">立即采购</el-button></div><div class="compare-actions"><el-button :loading="comparing" :disabled="!selectedSku || comparison.saving || comparison.loading" @click="addToComparison">加入对比</el-button><MallLink to="/compare">查看对比（{{ comparison.count }} / 4）</MallLink><small v-if="!compareSkuChosen">请先点击选择上方实际规格</small></div><p class="ownership-note">购物车与结算不会信任页面中的租户参数，服务端将依据 SKU 重新核验租户、企业、店铺、审核状态、价格与库存。</p></div>
      </section>
      <section class="container panel description"><h2>商品详情</h2><div v-if="safeDescription" class="rich" v-html="safeDescription"></div><div v-else class="empty">暂无详细介绍</div></section>
    </template>
  </div>
</template>

<style scoped>
.platform-detail-page{min-height:700px;padding:28px 0 60px;background:#f4f7f7;color:#17343c}.breadcrumbs{display:flex;gap:10px;margin-bottom:16px;color:#71817d}.loading{padding:30px}.detail-main{display:grid;grid-template-columns:500px 1fr;gap:38px;padding:28px}.gallery-main{display:grid;place-items:center;height:460px;background:#eef3f2;color:#8ba09b}.gallery-main img{width:100%;height:100%;object-fit:contain}.gallery-list{display:flex;gap:8px;margin-top:10px;overflow:auto}.gallery-list img{width:64px;height:64px;object-fit:cover;border:1px solid #d7e2df}.detail-body>small{color:#0f806d}.detail-body h1{margin:10px 0;font-size:28px}.detail-body>p{color:#71817d;line-height:1.7}.price{display:flex;align-items:baseline;gap:20px;margin:24px 0;padding:18px;background:#f7f3f0}.price span{color:#71817d}.price strong{color:#ef6a2d;font-size:34px}.detail-body dl{display:grid;grid-template-columns:100px 1fr;gap:12px}.detail-body dt{color:#71817d}.detail-body dd{margin:0}.shop-service{display:flex;align-items:center;justify-content:space-between;gap:18px;margin-top:20px;padding:14px 16px;border:1px solid #d7e5e1;border-radius:8px;background:#f4faf8}.shop-service b,.shop-service span{display:block}.shop-service span{margin-top:4px;color:#71817d;font-size:13px}.shop-service a{flex:0 0 auto;color:#0f806d;font-weight:700}.skus{margin-top:24px}.skus label{display:block;margin-bottom:10px;color:#71817d}.skus button{margin:0 9px 9px 0;padding:9px 13px;border:1px solid #cad7d4;background:#fff}.skus button.active{border-color:#0f806d;background:#edf8f5;color:#0f806d}.actions{display:flex;gap:12px;margin-top:22px}.ownership-note{font-size:12px!important}.description{margin-top:20px;padding:26px}.description h2{margin-top:0;border-bottom:2px solid #0f806d;padding-bottom:16px}.rich{overflow:hidden;line-height:1.8}.rich :deep(img){max-width:100%;height:auto}.empty{padding:70px;text-align:center;color:#879792}@media(max-width:960px){.detail-main{grid-template-columns:1fr}.gallery-main{height:360px}}
.compare-actions{display:flex;align-items:center;gap:16px;flex-wrap:wrap;margin-top:18px}.compare-actions small{color:#71817d;font-size:13px}
</style>
