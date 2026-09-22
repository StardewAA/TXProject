<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import { portalApi, type PortalProduct } from '@/api/portal'
import EmptyState from '@/components/EmptyState.vue'

const route = useRoute()
const products = ref<PortalProduct[]>([])
const loading = ref(false)
const total = ref(0)
const pageNo = ref(1)
const pageSize = 20
const sort = ref('default')

async function load(reset = false) {
  if (reset) pageNo.value = 1
  loading.value = true
  try {
    const params: Record<string, unknown> = { pageNo: pageNo.value, pageSize }
    const keyword = String(route.query.q || route.query.keyword || '').trim()
    if (keyword) params.keyword = keyword
    if (route.query.categoryId) params.categoryId = Number(route.query.categoryId)
    if (sort.value === 'priceAsc') { params.sortField = 'price'; params.sortAsc = true }
    if (sort.value === 'priceDesc') { params.sortField = 'price'; params.sortAsc = false }
    if (sort.value === 'sales') { params.sortField = 'sales'; params.sortAsc = false }
    const page = await portalApi.getProducts(params as Parameters<typeof portalApi.getProducts>[0])
    products.value = page?.list || []
    total.value = page?.total || 0
  } finally { loading.value = false }
}

watch(() => route.query, () => void load(true), { deep: true })
watch(sort, () => void load(true))
watch(pageNo, () => void load())
onMounted(() => void load())

function productRoute(product: PortalProduct) {
  return { name: 'tenant-product-detail', params: { tenantSlug: product.tenantSlug, spuId: String(product.spuId) } }
}

function formatPrice(value: number) {
  return (Number(value || 0) / 100).toFixed(2)
}
</script>

<template>
  <div class="container page-section">
    <div class="breadcrumbs"><MallLink to="/">首页</MallLink><span>›</span><span>商品列表</span></div>
    <div class="list-toolbar panel">
      <div class="toolbar"><button :class="{ active: sort === 'default' }" @click="sort = 'default'">综合</button><button :class="{ active: sort === 'sales' }" @click="sort = 'sales'">销量</button><button :class="{ active: sort === 'priceAsc' }" @click="sort = 'priceAsc'">价格升序</button><button :class="{ active: sort === 'priceDesc' }" @click="sort = 'priceDesc'">价格降序</button></div>
      <span>共 {{ total }} 件商品</span>
    </div>
    <p class="compare-tip">跨店比较请先进入商品详情，选择具体规格后“加入对比”。<MallLink to="/compare">打开对比清单 →</MallLink></p>
    <div v-if="loading" class="loading-box"><el-skeleton :rows="8" animated /></div>
    <EmptyState v-else-if="!products.length" title="没有找到匹配商品" description="请更换关键词或分类后重试" />
    <div v-else class="product-grid portal-product-grid">
      <MallLink v-for="product in products" :key="`${product.tenantId}-${product.spuId}`" class="portal-product-card panel" :to="productRoute(product)">
        <div class="portal-product-image"><img v-if="product.picUrl" :src="product.picUrl" :alt="product.spuName" loading="lazy" /><span v-else>暂无图片</span><b>{{ product.skus?.length || 0 }} 个规格</b></div>
        <div class="portal-product-body"><small>{{ product.tenantName }} · {{ product.shopName }}</small><h3>{{ product.spuName }}</h3><p>{{ product.enterpriseName }}</p><div><strong>¥{{ formatPrice(product.price) }}</strong><span>已售 {{ product.salesCount || 0 }}</span></div></div>
      </MallLink>
    </div>
    <div v-if="total > pageSize" class="pagination"><el-pagination v-model:current-page="pageNo" background layout="prev, pager, next" :page-size="pageSize" :total="total" /></div>
  </div>
</template>

<style scoped lang="scss">
.breadcrumbs { display: flex; gap: 10px; margin-bottom: 16px; color: var(--muted); font-size: 14px; }
.list-toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; padding: 12px 18px; color: var(--muted); }
.list-toolbar button { padding: 7px 14px; border: 1px solid var(--line); background: white; color: #59656e; }
.list-toolbar button.active { border-color: var(--brand); background: var(--brand); color: white; }
.pagination { display: flex; justify-content: center; margin-top: 34px; }
.portal-product-grid { grid-template-columns: repeat(4, minmax(0, 1fr)); }
.portal-product-card { overflow: hidden; color: inherit; transition: transform .18s ease, box-shadow .18s ease; }
.portal-product-card:hover { transform: translateY(-3px); box-shadow: 0 14px 32px rgba(15, 50, 54, .12); }
.portal-product-image { position: relative; height: 220px; background: #eef2f1; }
.portal-product-image img { width: 100%; height: 100%; object-fit: cover; }
.portal-product-image > span { display: grid; height: 100%; place-items: center; color: var(--muted); }
.portal-product-image b { position: absolute; right: 12px; bottom: 12px; padding: 5px 9px; border-radius: 999px; background: rgba(9, 42, 47, .78); color: white; font-size: 12px; }
.portal-product-body { padding: 16px; }
.portal-product-body small, .portal-product-body p { display: block; overflow: hidden; color: var(--muted); text-overflow: ellipsis; white-space: nowrap; }
.portal-product-body h3 { height: 48px; margin: 8px 0 6px; overflow: hidden; font-size: 17px; line-height: 24px; }
.portal-product-body p { margin: 0 0 14px; font-size: 13px; }
.portal-product-body > div { display: flex; justify-content: space-between; align-items: baseline; }
.portal-product-body strong { color: #ef5b2a; font-size: 22px; }
.portal-product-body span { color: var(--brand); font-size: 13px; }
@media (max-width: 1100px) { .portal-product-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
</style>
