<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { productApi } from '@/api/mall'
import ShopCard from '@/components/ShopCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import type { Product, Shop } from '@/types/mall'

const route = useRoute()
const router = useRouter()
const keyword = ref('')
const shops = ref<Shop[]>([])
const previews = ref<Record<string, { products: Product[]; total?: number; failed?: boolean }>>({})
const loading = ref(true)
const failed = ref(false)
const page = ref(1)
const filtered = computed(() => {
  const q = String(route.query.q || '').trim().toLowerCase()
  return shops.value.filter(shop => !q || `${shop.name} ${shop.introduction || ''}`.toLowerCase().includes(q))
})
const visible = computed(() => filtered.value.slice((page.value - 1) * 6, page.value * 6))
const previewPending = new Set<string>()
async function loadPreviews() {
  await Promise.allSettled(visible.value.map(async shop => {
    const key = String(shop.id)
    if (previews.value[key] || previewPending.has(key)) return
    previewPending.add(key)
    try {
      const data = await productApi.getPage({ shopId: shop.id, pageNo: 1, pageSize: 3 })
      previews.value[key] = { products: data.list || [], total: data.total }
    } catch { previews.value[key] = { products: [], failed: true } }
    finally { previewPending.delete(key) }
  }))
}
async function load() {
  loading.value = true
  failed.value = false
  try { shops.value = await productApi.getShops(); await loadPreviews() }
  catch { failed.value = true }
  finally { loading.value = false }
}
watch(() => route.query.q, () => { keyword.value = String(route.query.q || ''); page.value = 1 }, { immediate: true })
watch(visible, loadPreviews)
void load()
</script>

<template>
  <section class="directory-cover"><div class="container"><span>SUPPLIER DIRECTORY / 企业店铺</span><h1>找到材料，也找到合适的供应商</h1><p>按企业或店铺寻找供应材料，进入店铺查看规格与价格。</p></div></section>
  <div class="container page-section">
    <div class="directory-breadcrumb"><MallLink to="/">首页</MallLink> / 企业店铺</div>
    <form class="directory-search panel" @submit.prevent="router.push({ path: '/shops', query: keyword.trim() ? { q: keyword.trim() } : {} })">
      <label for="shop-search">查找企业店铺</label><input id="shop-search" v-model="keyword" placeholder="输入店铺名称或企业简介关键词" /><button type="submit">搜索店铺</button>
      <MallLink v-if="route.query.q" to="/shops">查看全部</MallLink>
    </form>
    <div class="section-heading"><div><h2>{{ route.query.q ? `“${route.query.q}”的搜索结果` : '企业供应目录' }}</h2><p>企业信息与店铺商品集中展示</p></div><span>共 {{ filtered.length }} 家店铺</span></div>
    <div v-if="loading" class="panel loading-box"><el-skeleton :rows="6" animated /></div>
    <EmptyState v-else-if="failed" title="店铺暂时加载失败"><el-button @click="load">重新加载</el-button></EmptyState>
    <EmptyState v-else-if="!filtered.length" title="没有找到相关店铺" description="试试店铺简称或企业名称"><MallLink to="/shops">查看全部店铺</MallLink></EmptyState>
    <div v-else class="directory-grid"><ShopCard v-for="shop in visible" :key="shop.id" :shop="shop" :products="previews[String(shop.id)]?.products" :total="previews[String(shop.id)]?.total" :failed="previews[String(shop.id)]?.failed" /></div>
    <div v-if="filtered.length > 6" class="directory-pagination"><el-pagination v-model:current-page="page" :page-size="6" :total="filtered.length" layout="prev, pager, next" background /></div>
  </div>
</template>

<style scoped>
.directory-cover{background:linear-gradient(115deg,#102f3b,#1a625c);color:white;padding:46px 0}.directory-cover span{font-size:11px;letter-spacing:3px;color:#99c4bd}.directory-cover h1{font-size:32px;margin:18px 0}.directory-cover p{color:#c2d8d1}.directory-breadcrumb{color:#75837d;font-size:13px;margin-bottom:20px}.directory-search{display:flex;align-items:center;gap:18px;padding:24px;margin-bottom:32px}.directory-search input{flex:1;min-width:0;border:1px solid #dbe4e0;border-radius:4px;padding:12px}.directory-search button{padding:12px 24px;border:0;background:var(--brand);color:white;border-radius:4px}.directory-search a{font-size:13px;color:var(--brand)}.directory-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:22px}.directory-pagination{display:flex;justify-content:center;margin:30px 0}
</style>
