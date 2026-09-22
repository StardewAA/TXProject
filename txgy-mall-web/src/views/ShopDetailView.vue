<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { portalApi, type PortalProduct, type PortalShop } from '@/api/portal'
import PortalProductCard from '@/components/PortalProductCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import { normalizeImage } from '@/utils/format'

const route = useRoute()
const shop = ref<PortalShop | null>(null)
const products = ref<PortalProduct[]>([])
const total = ref(0)
const loading = ref(true)
const productsLoading = ref(false)
const failed = ref(false)
const productFailed = ref(false)
const keyword = ref('')
const appliedKeyword = ref('')
const page = ref(1)
const sort = ref('default')
let generation = 0

async function loadProducts() {
  if (!shop.value) return
  const current = ++generation
  productsLoading.value = true; productFailed.value = false
  try {
    const result = await portalApi.getTenantProducts(shop.value.tenantSlug, {
      shopId: shop.value.shopId, pageNo: page.value, pageSize: 12,
      keyword: appliedKeyword.value || undefined,
      sortField: sort.value === 'default' ? undefined : 'price',
      sortAsc: sort.value === 'asc' ? true : sort.value === 'desc' ? false : undefined,
    })
    if (current !== generation) return
    products.value = result.list || []; total.value = Number(result.total || 0)
  } catch { if (current === generation) { productFailed.value = true; products.value = []; total.value = 0 } }
  finally { if (current === generation) productsLoading.value = false }
}
async function loadShop() {
  ++generation; loading.value = true; failed.value = false; shop.value = null; products.value = []
  try { shop.value = await portalApi.getShop(String(route.params.id)); await loadProducts() }
  catch { failed.value = true }
  finally { loading.value = false }
}
function search() { appliedKeyword.value = keyword.value.trim(); if (page.value !== 1) page.value = 1; else void loadProducts() }
watch(() => route.params.id, loadShop, { immediate: true })
watch(page, loadProducts)
watch(sort, () => { if (page.value !== 1) page.value = 1; else void loadProducts() })
</script>

<template>
  <div class="container page-section">
    <div class="breadcrumb"><MallLink to="/">首页</MallLink> / <MallLink to="/shops">企业店铺</MallLink> / {{ shop?.shopName || '店铺详情' }}</div>
    <div v-if="loading" class="panel loading-box"><el-skeleton :rows="8" animated /></div>
    <EmptyState v-else-if="failed || !shop" title="店铺暂时无法访问" description="店铺可能已停用或没有公开在售商品。"><el-button @click="loadShop">重新加载</el-button><MallLink to="/shops">返回企业店铺</MallLink></EmptyState>
    <template v-else>
      <section class="shop-cover"><div class="heading"><img v-if="shop.logoUrl" :src="normalizeImage(shop.logoUrl)" alt="" /><span v-else>{{ shop.shopName.slice(0, 2) }}</span><div><small>{{ shop.tenantName }} / {{ shop.enterpriseName }}</small><h1>{{ shop.shopName }}</h1></div></div><p>{{ shop.introduction || '欢迎进入店铺，查看公开在售材料及规格信息。' }}</p><RouterLink :to="{ path: `/member/customer-service/shop/${shop.shopId}` }">联系店铺客服 →</RouterLink></section>
      <div class="shop-main"><aside class="panel"><h3>店铺档案</h3><dl><dt>所属商城</dt><dd>{{ shop.tenantName }}</dd><dt>经营企业</dt><dd>{{ shop.enterpriseName }}</dd><dt>公开商品</dt><dd>{{ shop.productCount }} 件</dd></dl><RouterLink :to="{ name: 'tenant-mall', params: { tenantSlug: shop.tenantSlug } }">进入企业商城 →</RouterLink></aside>
        <section><form class="shop-search panel" @submit.prevent="search"><label for="within-shop">店内搜索</label><input id="within-shop" v-model="keyword" placeholder="输入商品名称" /><button type="submit">搜本店</button></form><div class="toolbar"><span>供应商品 / {{ total }} 件</span><select v-model="sort"><option value="default">综合排序</option><option value="asc">价格从低到高</option><option value="desc">价格从高到低</option></select></div>
          <div v-if="productsLoading" class="panel loading-box"><el-skeleton :rows="6" animated /></div><EmptyState v-else-if="productFailed" title="商品加载失败"><el-button @click="loadProducts">重新加载</el-button></EmptyState><EmptyState v-else-if="!products.length" title="暂无符合条件的商品" />
          <div v-else class="product-grid"><PortalProductCard v-for="product in products" :key="String(product.spuId)" :product="product" /></div><div v-if="total > 12" class="pagination"><el-pagination v-model:current-page="page" :total="total" :page-size="12" background layout="prev, pager, next" /></div>
        </section>
      </div>
    </template>
  </div>
</template>

<style scoped>
.breadcrumb{margin-bottom:18px;color:#74847f}.shop-cover{position:relative;padding:34px;border-radius:10px;background:linear-gradient(120deg,#123842,#1b6e64);color:#fff}.heading{display:flex;align-items:center;gap:16px}.heading>img,.heading>span{display:grid;width:64px;height:64px;border-radius:8px;background:#e9f4f0;object-fit:contain;place-items:center;color:#117867;font-weight:700}.heading small{color:#acd0c8}.heading h1{margin:7px 0}.shop-cover>p{max-width:720px;color:#d2e3df}.shop-cover>a{position:absolute;right:30px;bottom:30px;padding:10px 16px;border:1px solid #ffffff66;border-radius:6px;color:#fff}.shop-main{display:grid;grid-template-columns:250px 1fr;gap:22px;margin-top:22px}.shop-main aside{height:max-content;padding:24px}.shop-main dl{display:grid;grid-template-columns:72px 1fr;gap:12px;font-size:13px}.shop-main dt{color:#788985}.shop-main dd{margin:0}.shop-main aside>a{color:var(--brand)}.shop-search{display:flex;align-items:center;gap:14px;padding:18px}.shop-search input{min-width:0;flex:1;padding:11px;border:1px solid #d5e0dd}.shop-search button{padding:11px 18px;border:0;background:var(--brand);color:#fff}.toolbar{display:flex;justify-content:space-between;padding:20px 2px}.toolbar select{padding:8px}.product-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:18px}.pagination{display:flex;justify-content:center;margin:28px}@media(max-width:900px){.shop-main{grid-template-columns:1fr}.product-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.shop-cover>a{position:static;display:inline-block;margin-top:12px}}
</style>
