<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import { productApi } from '@/api/mall'
import EmptyState from '@/components/EmptyState.vue'
import ProductCard from '@/components/ProductCard.vue'
import type { Category, Product } from '@/types/mall'

const route = useRoute()
const products = ref<Product[]>([])
const categories = ref<Category[]>([])
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
    if (sort.value === 'sales') { params.sortField = 'salesCount'; params.sortAsc = false }
    const page = await productApi.getPage(params)
    products.value = page?.list || []
    total.value = page?.total || 0
  } finally { loading.value = false }
}

watch(() => route.query, () => void load(true), { deep: true })
watch(sort, () => void load(true))
watch(pageNo, () => void load())
onMounted(async () => {
  const [, categoryList] = await Promise.allSettled([load(), productApi.getCategories()])
  if (categoryList.status === 'fulfilled') categories.value = categoryList.value
})
</script>

<template>
  <div class="container page-section">
    <div class="breadcrumbs"><MallLink to="/">首页</MallLink><span>›</span><span>商品列表</span></div>
    <div class="filter-panel panel">
      <div><b>商品分类</b><MallLink :to="{ path: '/goods', query: { q: route.query.q } }">全部</MallLink><MallLink v-for="item in categories.slice(0, 14)" :key="item.id" :to="{ path: '/goods', query: { ...route.query, categoryId: item.id } }">{{ item.name }}</MallLink></div>
    </div>
    <div class="list-toolbar panel">
      <div class="toolbar"><button :class="{ active: sort === 'default' }" @click="sort = 'default'">综合</button><button :class="{ active: sort === 'sales' }" @click="sort = 'sales'">销量</button><button :class="{ active: sort === 'priceAsc' }" @click="sort = 'priceAsc'">价格升序</button><button :class="{ active: sort === 'priceDesc' }" @click="sort = 'priceDesc'">价格降序</button></div>
      <span>共 {{ total }} 件商品</span>
    </div>
    <p class="compare-tip">跨店比较请先进入商品详情，选择具体规格后“加入对比”。<MallLink to="/compare">打开对比清单 →</MallLink></p>
    <div v-if="loading" class="loading-box"><el-skeleton :rows="8" animated /></div>
    <EmptyState v-else-if="!products.length" title="没有找到匹配商品" description="请更换关键词或分类后重试" />
    <div v-else class="product-grid"><ProductCard v-for="product in products" :key="product.id" :product="product" /></div>
    <div v-if="total > pageSize" class="pagination"><el-pagination v-model:current-page="pageNo" background layout="prev, pager, next" :page-size="pageSize" :total="total" /></div>
  </div>
</template>

<style scoped lang="scss">
.breadcrumbs { display: flex; gap: 10px; margin-bottom: 16px; color: var(--muted); font-size: 14px; }
.filter-panel { padding: 20px; margin-bottom: 16px; }
.filter-panel div { display: flex; flex-wrap: wrap; align-items: center; gap: 14px 24px; }
.filter-panel b { width: 80px; }.filter-panel a:hover { color: var(--brand); }
.list-toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; padding: 12px 18px; color: var(--muted); }
.list-toolbar button { padding: 7px 14px; border: 1px solid var(--line); background: white; color: #59656e; }
.list-toolbar button.active { border-color: var(--brand); background: var(--brand); color: white; }
.pagination { display: flex; justify-content: center; margin-top: 34px; }
</style>
