<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { productApi } from '@/api/mall'
import EmptyState from '@/components/EmptyState.vue'
import ProductCard from '@/components/ProductCard.vue'
import ShopCard from '@/components/ShopCard.vue'
import IndustryCarousel from '@/components/IndustryCarousel.vue'
import type { Category, Product, Shop } from '@/types/mall'
import { rootCategories, categoryIds } from '@/utils/catalog'
import { productImage } from '@/utils/format'

const categories = ref<Category[]>([])
const products = ref<Product[]>([])
const featured = ref<Array<{ shop: Shop; products: Product[]; total: number }>>([])
const floors = ref<Array<{ category: Category; products: Product[] }>>([])
const loading = ref(true)
const failed = ref(false)
const shopsFailed = ref(false)
const roots = computed(() => rootCategories(categories.value))
const scenes = [
  { name: '管道与钢结构防腐', keyword: '防腐', text: '从基材防护到涂层配套', number: '01' },
  { name: '设备与管线保温', keyword: '保温', text: '关注温度、厚度与工况', number: '02' },
  { name: '管材与施工配套', keyword: '管', text: '核对材质、口径与连接方式', number: '03' },
]
function categoryImage(category: Category) {
  const ids = categoryIds(categories.value, category.id)
  const match = products.value.find(item => ids.has(String(item.categoryId)))
  return match ? productImage(match.id, match.picUrl) : ''
}
async function loadShops() {
  shopsFailed.value = false
  try {
    const shops = (await productApi.getShops()).sort((a, b) => Number(/测试/.test(a.name)) - Number(/测试/.test(b.name)))
    const result = await Promise.allSettled(shops.slice(0, 6).map(async shop => {
      const page = await productApi.getPage({ shopId: shop.id, pageNo: 1, pageSize: 3 })
      return { shop, products: page.list || [], total: page.total }
    }))
    featured.value = result.flatMap(item => item.status === 'fulfilled' && item.value.products.length ? [item.value] : []).slice(0, 4)
    if (result.length && result.every(item => item.status === 'rejected')) shopsFailed.value = true
  } catch { shopsFailed.value = true }
}
async function load() {
  loading.value = true
  failed.value = false
  try {
    const [list, page] = await Promise.all([productApi.getCategories(), productApi.getPage({ pageNo: 1, pageSize: 40 })])
    categories.value = list || []
    products.value = page.list || []
    const selected = ['防腐材料', '保温材料', '管材管件', '施工辅材'].map(name => roots.value.find(category => category.name === name)).filter((item): item is Category => Boolean(item))
    const result = await Promise.allSettled(selected.map(async category => {
      const data = await productApi.getPage({ categoryId: category.id, pageNo: 1, pageSize: 5 })
      return { category, products: data.list || [] }
    }))
    floors.value = result.flatMap((item, index) => item.status === 'fulfilled' ? [item.value] : [{ category: selected[index]!, products: products.value.filter(product => categoryIds(categories.value, selected[index]!.id).has(String(product.categoryId))).slice(0, 5) }])
  } catch { failed.value = true }
  finally { loading.value = false }
}
onMounted(() => { void load(); void loadShops() })
</script>

<template>
  <div class="industry-home">
    <section class="home-hero"><div class="container home-hero__inner">
      <aside class="material-directory"><div class="material-directory__title"><strong>材料采购目录</strong><small>MATERIAL DIRECTORY</small></div>
        <RouterLink v-for="category in roots.slice(0, 9)" :key="category.id" :to="{ path: '/goods', query: { categoryId: category.id } }"><span>{{ category.name }}</span><span>›</span></RouterLink>
        <RouterLink to="/categories" class="all-materials">查看全部分类 ↗</RouterLink>
      </aside>
      <IndustryCarousel />
    </div></section>
    <div class="home-benefits"><div class="container"><div><span>01</span><p><b>按工况选材</b><small>分类清晰，规格可查</small></p></div><div><span>02</span><p><b>企业店铺直达</b><small>集中查看供应材料</small></p></div><div><span>03</span><p><b>采购流程贯通</b><small>购物车、订单与物流</small></p></div><div><span>04</span><p><b>售后进度可查</b><small>收货、评价与售后</small></p></div></div></div>
    <section class="container home-section">
      <div class="home-title"><div><small>MATERIAL SELECTION</small><h2>从一份材料，开始工程采购</h2></div><RouterLink to="/categories">全部材料分类 ↗</RouterLink></div>
      <div class="material-shortcuts"><RouterLink v-for="category in roots.slice(0, 8)" :key="category.id" :to="{ path: '/goods', query: { categoryId: category.id } }"><div class="material-thumb"><img v-if="categoryImage(category)" :src="categoryImage(category)" :alt="category.name" loading="lazy" /><span v-else aria-hidden="true">▱</span></div><strong>{{ category.name }}</strong><small>查看供应商品 →</small></RouterLink></div>
      <div v-if="failed" class="panel"><EmptyState title="材料目录暂时加载失败"><el-button @click="load">重新加载</el-button></EmptyState></div>
    </section>
    <section class="container home-section">
      <div class="home-title"><div><small>APPLICATION SCENARIOS</small><h2>为不同工程，找到合适的材料</h2></div><span>先确认工况，再选择规格</span></div>
      <div class="scene-grid"><RouterLink v-for="scene in scenes" :key="scene.number" :to="{ path: '/goods', query: { keyword: scene.keyword } }"><span class="scene-index">{{ scene.number }}</span><h3>{{ scene.name }}</h3><p>{{ scene.text }}</p><b>探索材料 →</b><div class="scene-lines" aria-hidden="true"></div></RouterLink></div>
    </section>
    <section class="home-suppliers home-section"><div class="container">
      <div class="home-title"><div><small>ENTERPRISE STORES</small><h2>好材料，从了解供应商开始</h2><p>走进企业店铺，查看主营材料与在售商品</p></div><RouterLink to="/shops">查看全部企业店铺 ↗</RouterLink></div>
      <div v-if="featured.length" class="home-shop-grid"><ShopCard v-for="item in featured" :key="item.shop.id" :shop="item.shop" :products="item.products" :total="item.total" /></div>
      <EmptyState v-else-if="shopsFailed" title="企业店铺暂时加载失败"><el-button @click="loadShops">重新加载</el-button></EmptyState>
      <EmptyState v-else title="企业材料供应目录"><RouterLink to="/shops">进入店铺目录 →</RouterLink></EmptyState>
    </div></section>
    <div v-if="loading" class="container home-section loading-box"><el-skeleton :rows="6" animated /></div>
    <section v-for="(floor, index) in floors" :key="floor.category.id" class="container home-section material-floor">
      <div class="home-title"><div class="floor-title"><span>{{ String(index + 1).padStart(2, '0') }}</span><div><small>ENGINEERING MATERIALS</small><h2>{{ floor.category.name }}专区</h2></div></div><RouterLink :to="{ path: '/goods', query: { categoryId: floor.category.id } }">更多{{ floor.category.name }} ↗</RouterLink></div>
      <div v-if="floor.products.length" :class="['floor-content', { 'is-compact': floor.products.length < 3 }]">
        <RouterLink v-if="floor.products.length < 3" class="floor-guide" :to="{ path: '/goods', query: { categoryId: floor.category.id } }"><small>MATERIAL GUIDE / 选材指引</small><h3>{{ floor.category.name }}</h3><p>核对材质、规格与使用工况，<br />为项目选择合适的配套材料。</p><span>进入材料专区 →</span></RouterLink>
        <div class="product-grid"><ProductCard v-for="product in floor.products" :key="product.id" :product="product" /></div>
      </div>
      <EmptyState v-else title="该分类暂无上架商品"><RouterLink to="/goods">浏览其他材料</RouterLink></EmptyState>
    </section>
    <section class="container home-section procurement-guide"><div><small>PROCUREMENT SERVICES</small><h2>选材之后，让采购更省心</h2><p>查看平台优惠，按订单结算结果完成采购。</p></div><RouterLink to="/marketing#coupon"><b>领取采购优惠券</b><span>查看可领取优惠 →</span></RouterLink><RouterLink to="/member/customer-service"><b>采购与售后咨询</b><span>联系平台客服 →</span></RouterLink></section>
  </div>
</template>

<style scoped>
.industry-home{background:#f5f7f6}.home-hero{padding:22px 0;background:#e7eeed}.home-hero__inner{display:grid;grid-template-columns:224px minmax(0,1fr);gap:0}.material-directory{background:#153b45;color:white;padding:10px 0}.material-directory__title{padding:20px 24px 18px;border-bottom:1px solid #ffffff19}.material-directory__title strong{font-size:17px}.material-directory__title small{display:block;color:#80a99f;font-size:9px;letter-spacing:2px;margin-top:8px}.material-directory a{display:flex;justify-content:space-between;padding:11px 24px;font-size:14px;color:#d7e6e1}.material-directory a:hover{background:#246457;color:white}.material-directory a.all-materials{font-size:12px;color:#91bcaf;border-top:1px solid #ffffff19;margin-top:5px}.home-benefits{background:white;border-bottom:1px solid #e1e7e3}.home-benefits>.container{display:grid;grid-template-columns:repeat(4,1fr);padding:22px 0}.home-benefits>.container>div{display:flex;align-items:center;justify-content:center;gap:16px;border-right:1px solid #e4eae6}.home-benefits>.container>div:last-child{border:0}.home-benefits span{font-size:27px;color:#87a89c;font-weight:300}.home-benefits p{margin:0}.home-benefits b,.home-benefits small{display:block}.home-benefits b{font-size:14px}.home-benefits small{font-size:11px;color:#8a968f;margin-top:6px}.home-section{margin-top:52px}.home-title{display:flex;align-items:center;justify-content:space-between;gap:20px;margin-bottom:24px}.home-title small{font-size:10px;color:#72968b;letter-spacing:2px}.home-title h2{font-size:25px;margin:9px 0 0;font-weight:600;letter-spacing:1px}.home-title p{font-size:13px;color:#7c8982}.home-title>a,.home-title>span{color:#526e63;font-size:13px}.material-shortcuts{display:grid;grid-template-columns:repeat(8,minmax(0,1fr));gap:14px}.material-shortcuts>a{background:#fff;padding:14px;border:1px solid #e4eae5;text-align:center;transition:transform .2s}.material-shortcuts>a:hover{transform:translateY(-3px);border-color:#147d6b}.material-thumb{aspect-ratio:1.25;display:grid;place-items:center;margin-bottom:16px;overflow:hidden;background:#f5f7f5}.material-thumb img{width:100%;height:100%;object-fit:contain}.material-thumb span{color:#8dad9e;font-size:40px}.material-shortcuts strong{display:block;font-size:14px}.material-shortcuts small{display:block;font-size:10px;color:#8a978f;margin-top:8px}.scene-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:20px}.scene-grid>a{position:relative;overflow:hidden;padding:30px;background:#e4eeeb;border:1px solid #d9e5df;min-height:214px}.scene-grid>a:nth-child(2){background:#e8eded}.scene-grid>a:nth-child(3){background:#efece5}.scene-index{font-size:11px;color:#658b7e;letter-spacing:3px}.scene-grid h3{font-size:20px;position:relative;z-index:1}.scene-grid p{font-size:12px;color:#74857b;position:relative;z-index:1}.scene-grid b{display:block;margin-top:24px;color:#286b58;font-size:12px}.scene-lines{position:absolute;right:-45px;top:40px;width:160px;height:160px;border:24px solid #8eaaa118;border-radius:50%;box-shadow:0 0 0 18px #8eaaa10b}.home-suppliers{padding:40px 0;background:#edf2ee;border-block:1px solid #e2e9e3}.home-shop-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:20px}.floor-title{display:flex;align-items:center;gap:20px}.floor-title>span{font-size:40px;font-weight:300;color:#a1b6aa;border-right:1px solid #dbe4dc;padding-right:20px}.procurement-guide{display:grid;grid-template-columns:1.5fr 1fr 1fr;gap:30px;padding:34px;background:#153e43;color:white}.procurement-guide small{font-size:10px;letter-spacing:2px;color:#8bb7a9}.procurement-guide h2{font-size:23px}.procurement-guide p{font-size:12px;color:#a9c1b7}.procurement-guide>a{display:flex;flex-direction:column;justify-content:center;padding:24px;border-left:1px solid #ffffff22}.procurement-guide span{margin-top:18px;color:#96c9b7;font-size:12px}@media(max-width:1180px){.home-hero__inner{grid-template-columns:200px minmax(0,1fr)}.material-shortcuts{grid-template-columns:repeat(4,minmax(0,1fr))}.home-title h2{font-size:22px}}
</style>
<style scoped>
.floor-content.is-compact{display:grid;grid-template-columns: minmax(260px, .8fr) minmax(0, 1.2fr);gap:22px}.is-compact .product-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.floor-guide{background:linear-gradient(120deg,#e1ece7,#edf2ee);padding:44px;position:relative;overflow:hidden}.floor-guide::after{content:'';position:absolute;right:-80px;bottom:-90px;width:280px;height:280px;border:42px solid #9fbaad22;border-radius:50%;pointer-events:none}.floor-guide small{font-size:10px;letter-spacing:2px;color:#709485}.floor-guide h3{font-size:30px;margin-top:30px}.floor-guide p{font-size:14px;line-height:2;color:#6f8578}.floor-guide span{display:block;margin-top:36px;font-size:13px;color:var(--brand)}
</style>
