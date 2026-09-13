<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { productApi } from '@/api/mall'
import ProductCard from '@/components/ProductCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import type { Category, Product, Shop } from '@/types/mall'
import { normalizeImage } from '@/utils/format'
import { rootCategories } from '@/utils/catalog'

const route = useRoute()
const shop = ref<Shop | null>(null)
const categories = ref<Category[]>([])
const products = ref<Product[]>([])
const total = ref(0)
const loading = ref(true)
const failed = ref(false)
const productFailed = ref(false)
const productsLoading = ref(false)
const keyword = ref('')
const appliedKeyword = ref('')
const category = ref('')
const page = ref(1)
const sort = ref('default')
const tab = ref('products')
const logoFailed = ref(false)
const roots = computed(() => rootCategories(categories.value))
let requestId = 0
let shopRequestId = 0
async function loadProducts() {
  const current = ++requestId
  productsLoading.value = true
  productFailed.value = false
  const params: Record<string, unknown> = { shopId: route.params.id, pageNo: page.value, pageSize: 12 }
  if (appliedKeyword.value) params.keyword = appliedKeyword.value
  if (category.value) params.categoryId = category.value
  if (sort.value !== 'default') { params.sortField = 'price'; params.sortAsc = sort.value === 'asc' }
  try {
    const result = await productApi.getPage(params)
    if (current !== requestId) return
    products.value = result.list || []
    total.value = result.total || 0
  } catch { if (current === requestId) { productFailed.value = true; products.value = []; total.value = 0 } }
  finally { if (current === requestId) productsLoading.value = false }
}
function search() {
  appliedKeyword.value = keyword.value.trim()
  if (page.value !== 1) page.value = 1
  else void loadProducts()
}
watch([category, sort], () => { if (page.value !== 1) page.value = 1; else void loadProducts() })
watch(page, loadProducts)
async function loadShop() {
  const current = ++shopRequestId
  ++requestId
  loading.value = true
  failed.value = false
  shop.value = null
  products.value = []
  keyword.value = appliedKeyword.value = category.value = ''
  page.value = 1
  sort.value = 'default'
  tab.value = 'products'
  logoFailed.value = false
  try {
    const result = await productApi.getShop(String(route.params.id))
    if (current !== shopRequestId) return
    shop.value = result
    await loadProducts()
  } catch { if (current === shopRequestId) failed.value = true }
  finally { if (current === shopRequestId) loading.value = false }
}
watch(() => route.params.id, loadShop, { immediate: true })
void productApi.getCategories().then(value => { categories.value = value }).catch(() => {})
</script>

<template>
  <div class="container page-section">
    <div class="shop-breadcrumb"><RouterLink to="/">首页</RouterLink> / <RouterLink to="/shops">企业店铺</RouterLink> / {{ shop?.name || '店铺详情' }}</div>
    <div v-if="loading" class="loading-box panel"><el-skeleton :rows="8" animated /></div>
    <EmptyState v-else-if="failed || !shop" title="店铺暂时无法访问" description="店铺可能已停用，请稍后重试或查看其他供应商。"><el-button @click="loadShop">重新加载</el-button><RouterLink to="/shops">返回企业店铺</RouterLink></EmptyState>
    <template v-else>
      <section class="shop-cover">
        <div class="shop-heading"><img v-if="shop.logoUrl && !logoFailed" :src="normalizeImage(shop.logoUrl)" alt="" @error="logoFailed = true" /><span v-else class="shop-logo">{{ shop.name.slice(0, 2) }}</span><div><small>INDUSTRIAL MATERIALS / 企业供应店铺</small><h1>{{ shop.name }}</h1></div></div>
        <p>{{ shop.introduction || '欢迎进入店铺，查看在售材料及规格信息。' }}</p>
        <div class="shop-cover-note">选好材料 · 对准规格 · 服务工程</div>
      </section>
      <nav class="shop-tabs" aria-label="店铺导航"><button :class="{ active: tab === 'products' }" @click="tab = 'products'">供应商品</button><button :class="{ active: tab === 'company' }" @click="tab = 'company'">店铺信息</button><button :class="{ active: tab === 'contact' }" @click="tab = 'contact'">联系方式</button><RouterLink to="/shops">查看其他店铺 →</RouterLink></nav>
      <div class="shop-columns">
        <aside>
          <div class="panel shop-profile"><h3>店铺档案</h3><strong>{{ shop.name }}</strong><p>{{ shop.introduction || '店铺暂未填写简介' }}</p><RouterLink to="/member/customer-service">平台客服 →</RouterLink></div>
          <div v-if="tab === 'products'" class="panel shop-category"><h3>按材料分类</h3><button :class="{ active: !category }" @click="category = ''">全部材料</button><button v-for="item in roots" :key="item.id" :class="{ active: category === String(item.id) }" @click="category = String(item.id)">{{ item.name }}</button></div>
        </aside>
        <section v-if="tab === 'products'" class="shop-products">
          <form class="shop-search panel" @submit.prevent="search"><label for="within-shop">店内搜索</label><input id="within-shop" v-model="keyword" placeholder="输入商品名称" /><button type="submit">搜本店</button><RouterLink :to="{ path: '/goods', query: { q: keyword } }">搜全站</RouterLink></form>
          <div class="shop-toolbar"><span>供应商品 / {{ total }} 件</span><select v-model="sort" aria-label="商品排序"><option value="default">综合排序</option><option value="asc">价格从低到高</option><option value="desc">价格从高到低</option></select></div>
          <div v-if="productsLoading" class="panel loading-box"><el-skeleton :rows="6" animated /></div>
          <EmptyState v-else-if="productFailed" title="商品加载失败"><el-button @click="loadProducts">重新加载</el-button></EmptyState>
          <EmptyState v-else-if="!products.length" title="暂无符合条件的商品" description="可尝试其他分类或清空搜索词。" />
          <div v-else class="shop-product-grid"><ProductCard v-for="product in products" :key="product.id" :product="product" /></div>
          <div v-if="total > 12" class="shop-pagination"><el-pagination v-model:current-page="page" :total="total" :page-size="12" background layout="prev, pager, next" /></div>
        </section>
        <section v-else-if="tab === 'company'" class="panel shop-information"><h2>店铺信息</h2><h3>{{ shop.name }}</h3><p>{{ shop.introduction || '店铺暂未填写简介' }}</p></section>
        <section v-else class="panel shop-information"><h2>联系方式</h2><p>联系人：{{ shop.contactName || '暂未公开' }}</p><p>联系电话：<a v-if="shop.contactMobile" :href="`tel:${shop.contactMobile}`">{{ shop.contactMobile }}</a><span v-else>暂未公开</span></p><RouterLink to="/member/customer-service">联系平台客服 →</RouterLink></section>
      </div>
    </template>
  </div>
</template>

<style scoped>
.shop-breadcrumb{color:#75817e;font-size:13px;margin-bottom:20px}.shop-cover{position:relative;overflow:hidden;padding:40px;background:linear-gradient(115deg,#103440,#235e59);border-radius:8px 8px 0 0;color:white}.shop-cover::after{content:'';position:absolute;right:-60px;top:-130px;width:360px;height:360px;border:50px solid #ffffff07;border-radius:50%;pointer-events:none}.shop-heading{display:flex;align-items:center;gap:20px}.shop-heading img,.shop-logo{width:72px;height:72px;object-fit:contain;background:#ffffffec;border-radius:8px;flex-shrink:0}.shop-logo{display:grid;place-items:center;color:#1d645b;font-size:24px;font-weight:bold}.shop-heading small{letter-spacing:2px;color:#a4c7bf;font-size:11px}.shop-heading h1{font-size:28px;margin:10px 0}.shop-cover p{max-width:800px;color:#d8e6e1;line-height:1.9}.shop-cover-note{color:#8abbb0;font-size:12px;letter-spacing:3px;margin-top:24px}.shop-tabs{background:white;display:flex;border:1px solid var(--line);margin-bottom:24px;align-items:center}.shop-tabs button{border:0;padding:18px 28px;background:white;color:#566860}.shop-tabs button.active{background:var(--brand);color:white}.shop-tabs a{margin-left:auto;padding:0 24px;font-size:13px;color:var(--brand)}.shop-columns{display:grid;grid-template-columns:230px minmax(0,1fr);gap:24px}.shop-profile{padding:24px;margin-bottom:20px}.shop-profile h3,.shop-category h3{font-size:16px;margin:0 0 22px}.shop-profile strong{font-size:14px}.shop-profile p{font-size:13px;line-height:1.9;color:#77827e}.shop-profile a{font-size:13px;color:var(--brand)}.shop-category{padding:24px 0}.shop-category h3{padding:0 24px}.shop-category button{display:block;width:100%;text-align:left;border:0;background:white;padding:12px 24px;color:#56675f}.shop-category button.active{background:#edf5f1;color:var(--brand);border-left:3px solid var(--brand)}.shop-search{display:flex;gap:14px;padding:20px;align-items:center}.shop-search input{flex:1;min-width:0;padding:10px;border:1px solid #d8e1dc}.shop-search button{background:var(--brand);color:white;padding:11px 22px;border:0}.shop-search a{font-size:13px;color:var(--brand)}.shop-toolbar{display:flex;justify-content:space-between;align-items:center;margin:24px 0;font-size:13px;color:#718078}.shop-toolbar select{padding:8px;border:1px solid #d8e1dc;background:white}.shop-product-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:16px}.shop-pagination{display:flex;justify-content:center;margin:30px}.shop-information{padding:32px;min-height:380px}.shop-information p{line-height:2;color:#64756a}.shop-information a{color:var(--brand)}@media(max-width:1180px){.shop-columns{grid-template-columns:200px minmax(0,1fr)}.shop-product-grid{grid-template-columns:repeat(3,minmax(0,1fr))}}
</style>
