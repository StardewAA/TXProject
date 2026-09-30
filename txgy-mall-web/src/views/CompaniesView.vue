<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { portalApi, type PortalHotTenant, type PortalProduct } from '@/api/portal'
import { normalizeImage } from '@/utils/format'

interface CompanySummary extends PortalHotTenant {
  productCount: number
  shopCount: number
  sampleProducts: PortalProduct[]
}

const route = useRoute()
const router = useRouter()
const keyword = ref('')
const companies = ref<CompanySummary[]>([])
const loading = ref(true)
const failed = ref(false)
let alive = true

const filtered = computed(() => {
  const query = String(route.query.q || '').trim().toLowerCase()
  if (!query) return companies.value
  return companies.value.filter(company =>
    `${company.tenantName} ${company.mainCategories || ''}`.toLowerCase().includes(query),
  )
})

async function load() {
  loading.value = true
  failed.value = false
  try {
    const [hotTenants, productPage, allShops] = await Promise.all([
      portalApi.getHotTenants('30d', 100),
      portalApi.getProducts({ pageNo: 1, pageSize: 200 }),
      portalApi.getShops(),
    ])
    if (!alive) return
    const map = new Map<string, CompanySummary>()
    hotTenants.forEach(tenant => {
      if (!tenant.tenantSlug) return
      map.set(tenant.tenantSlug, {
        ...tenant,
        productCount: 0,
        shopCount: 0,
        sampleProducts: [],
      })
    })
    const shopSets = new Map<string, Set<string>>()
    // Counts come from the complete shop catalogue, never a truncated product page.
    for (const shop of allShops) {
      if (!shop.tenantSlug) continue
      const company = map.get(shop.tenantSlug) || {
        ...shop, rank: Number.MAX_SAFE_INTEGER, validQuantity: 0,
        productCount: 0, shopCount: 0, sampleProducts: [],
      }
      const shops = shopSets.get(shop.tenantSlug) || new Set<string>()
      if (!shops.has(String(shop.shopId))) company.productCount += Number(shop.productCount) || 0
      shops.add(String(shop.shopId))
      shopSets.set(shop.tenantSlug, shops)
      map.set(shop.tenantSlug, company)
    }
    ;(productPage.list || []).forEach(product => {
      if (!product.tenantSlug) return
      const company = map.get(product.tenantSlug) || {
        tenantId: product.tenantId,
        tenantSlug: product.tenantSlug,
        tenantName: product.tenantName,
        tenantLogo: product.tenantLogo,
        mainCategories: product.introduction || '防腐保温材料及工程配套',
        rank: Number.MAX_SAFE_INTEGER,
        validQuantity: 0,
        productCount: 0,
        shopCount: 0,
        sampleProducts: [],
      }
      if (company.sampleProducts.length < 3) company.sampleProducts.push(product)
      map.set(product.tenantSlug, company)
    })
    companies.value = Array.from(map.values())
      .map(company => ({ ...company, shopCount: shopSets.get(company.tenantSlug)?.size || 0 }))
      .sort((a, b) => (a.rank || Number.MAX_SAFE_INTEGER) - (b.rank || Number.MAX_SAFE_INTEGER)
        || b.productCount - a.productCount)
  } catch {
    if (alive) failed.value = true
  } finally {
    if (alive) loading.value = false
  }
}

function search() {
  const query = keyword.value.trim()
  void router.push({ path: '/companies', query: query ? { q: query } : {} })
}

watch(() => route.query.q, value => { keyword.value = String(value || '') }, { immediate: true })
onBeforeUnmount(() => { alive = false })
void load()
</script>

<template>
  <section class="company-cover">
    <div class="container">
      <span>COMPANY DIRECTORY / 企业目录</span>
      <h1>认证企业与供应能力</h1>
      <p>按企业查找主营材料，进入企业商城查看旗下店铺与公开商品。</p>
    </div>
  </section>
  <main class="container company-page">
    <div class="breadcrumb"><MallLink to="/">首页</MallLink> / 企业目录</div>
    <form class="company-search panel" @submit.prevent="search">
      <label for="company-search">查找企业</label>
      <input id="company-search" v-model="keyword" placeholder="输入企业名称或主营材料" />
      <button type="submit">搜索企业</button>
      <MallLink v-if="route.query.q" to="/companies">查看全部</MallLink>
    </form>
    <div class="section-heading">
      <div><h2>{{ route.query.q ? `“${route.query.q}”的企业搜索结果` : '认证企业目录' }}</h2><p>企业维度展示，不混入单独店铺</p></div>
      <span>共 {{ filtered.length }} 家企业</span>
    </div>
    <div v-if="loading" class="panel state"><el-skeleton :rows="8" animated /></div>
    <div v-else-if="failed" class="panel state"><h3>企业目录暂时加载失败</h3><el-button @click="load">重新加载</el-button></div>
    <div v-else-if="!filtered.length" class="panel state"><h3>没有找到相关企业</h3><MallLink to="/companies">查看全部企业</MallLink></div>
    <div v-else class="company-grid">
      <RouterLink v-for="company in filtered" :key="String(company.tenantId)" class="company-card" :to="{ name: 'tenant-mall', params: { tenantSlug: company.tenantSlug } }">
        <header>
          <img v-if="company.tenantLogo" :src="normalizeImage(company.tenantLogo)" :alt="company.tenantName" />
          <span v-else>{{ company.tenantName.slice(0, 2) }}</span>
          <div><small>认证企业</small><h3>{{ company.tenantName }}</h3></div>
          <b>进入企业商城 →</b>
        </header>
        <p>{{ company.mainCategories || '防腐保温材料及工程配套' }}</p>
        <div class="company-facts"><span>{{ company.shopCount }} 家店铺</span><span>{{ company.productCount }} 件公开商品</span><span v-if="company.validQuantity">近 30 天成交 {{ company.validQuantity }}</span></div>
        <div v-if="company.sampleProducts.length" class="product-preview">
          <div v-for="product in company.sampleProducts" :key="String(product.spuId)">
            <img v-if="product.picUrl" :src="normalizeImage(product.picUrl)" :alt="product.spuName" loading="lazy" />
            <span v-else>暂无图片</span>
            <small>{{ product.spuName }}</small>
          </div>
        </div>
      </RouterLink>
    </div>
  </main>
</template>

<style scoped>
.company-cover{padding:46px 0;background:linear-gradient(115deg,#102f3b,#1a625c);color:#fff}.company-cover span{color:#99c4bd;font-size:11px;letter-spacing:3px}.company-cover h1{margin:18px 0;font-size:32px}.company-cover p{color:#c2d8d1}.company-page{padding-top:28px;padding-bottom:56px}.breadcrumb{margin-bottom:20px;color:#75837d;font-size:13px}.company-search{display:flex;align-items:center;gap:18px;margin-bottom:32px;padding:24px}.company-search input{min-width:0;flex:1;padding:12px;border:1px solid #dbe4e0;border-radius:4px}.company-search button{padding:12px 24px;border:0;border-radius:4px;background:var(--brand);color:#fff}.company-search a{color:var(--brand);font-size:13px}.section-heading{display:flex;align-items:end;justify-content:space-between;margin-bottom:18px}.section-heading h2{margin:0 0 6px;padding-left:12px;border-left:4px solid var(--brand);font-size:24px}.section-heading p{margin:0;color:#788985}.company-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:22px}.company-card{padding:26px;border:1px solid #d8e3df;border-radius:8px;background:#fff;transition:.2s}.company-card:hover{border-color:#86b7ab;box-shadow:0 14px 30px #173d371a;transform:translateY(-2px)}.company-card header{display:grid;grid-template-columns:52px 1fr auto;gap:14px;align-items:center}.company-card header>img,.company-card header>span{display:grid;width:52px;height:52px;border-radius:8px;background:#eaf4f0;object-fit:contain;place-items:center;color:#0d7b68;font-weight:700}.company-card header small{color:#7c8d89}.company-card h3{margin:5px 0 0;color:#13383d;font-size:18px}.company-card header b{color:#0b7c68;font-size:12px}.company-card>p{min-height:24px;margin:20px 0;color:#647773}.company-facts{display:flex;flex-wrap:wrap;gap:18px;padding:13px 0;border-top:1px solid #e8efed;color:#0c7867;font-size:12px}.product-preview{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px;margin-top:10px}.product-preview div{min-width:0}.product-preview img,.product-preview div>span{display:grid;width:100%;height:96px;background:#eef2f1;object-fit:cover;place-items:center;color:#8a9996;font-size:11px}.product-preview small{display:block;overflow:hidden;margin-top:7px;color:#374b4c;text-overflow:ellipsis;white-space:nowrap}.state{padding:70px;text-align:center}@media(max-width:800px){.company-grid{grid-template-columns:1fr}.company-search{align-items:stretch;flex-direction:column}.company-card header{grid-template-columns:52px 1fr}.company-card header b{grid-column:2}}
</style>
