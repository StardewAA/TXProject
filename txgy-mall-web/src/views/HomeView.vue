<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { productApi } from '@/api/mall'
import EmptyState from '@/components/EmptyState.vue'
import ProductCard from '@/components/ProductCard.vue'
import ShopCard from '@/components/ShopCard.vue'
import IndustryCarousel from '@/components/IndustryCarousel.vue'
import IndustrialIcon from '@/components/IndustrialIcon.vue'
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
function categoryIcon(name: string) {
  if (name.includes('防腐') || name.includes('耐火')) return 'shield'
  if (name.includes('管托') || name.includes('管材')) return 'pipe'
  if (name.includes('保温')) return 'thermometer'
  if (name.includes('保冷')) return 'snowflake'
  if (name.includes('防潮') || name.includes('防水')) return 'droplet'
  if (name.includes('外护')) return 'layers'
  if (name.includes('管')) return 'pipe'
  if (name.includes('施工') || name.includes('工具')) return 'tools'
  if (name.includes('劳保')) return 'helmet'
  return 'package'
}
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
      <aside class="material-directory"><div class="material-directory__title"><span class="directory-menu"><IndustrialIcon name="menu" /></span><div><strong>全部商品分类</strong><small>MATERIAL DIRECTORY</small></div></div>
        <MallLink v-for="category in roots.slice(0, 8)" :key="category.id" :to="{ path: '/goods', query: { categoryId: category.id } }"><span><i aria-hidden="true"><IndustrialIcon :name="categoryIcon(category.name)" /></i>{{ category.name }}</span><b>›</b></MallLink>
        <MallLink to="/categories" class="all-materials">查看全部材料分类 <b>↗</b></MallLink>
      </aside>
      <IndustryCarousel />
      <aside class="procurement-rail">
        <div class="procurement-rail__heading"><small>PROCUREMENT SERVICE</small><h2>工程材料采购</h2><p>从选材、库存到订单交付，全流程清晰可查</p></div>
        <MallLink :to="{ path: '/goods', query: { keyword: '防腐' } }"><i><IndustrialIcon name="compass" /></i><span><b>按场景选材</b><small>按工程用途匹配材料</small></span></MallLink>
        <MallLink to="/goods"><i><IndustrialIcon name="warehouse" /></i><span><b>真实库存</b><small>结算时再次核验库存</small></span></MallLink>
        <MallLink to="/member/orders"><i><IndustrialIcon name="clipboard" /></i><span><b>规范交易</b><small>订单与物流状态可查</small></span></MallLink>
        <MallLink to="/member/customer-service/scoped"><i><IndustrialIcon name="headset" /></i><span><b>专业服务</b><small>采购和售后问题咨询</small></span></MallLink>
        <MallLink class="procurement-rail__action" to="/purchase/inquiries/new">发布采购需求 <span>→</span></MallLink>
      </aside>
    </div></section>
    <div class="home-benefits"><div class="container"><div><span><IndustrialIcon name="shield" /></span><p><b>品类齐全</b><small>防腐保温材料集中采购</small></p></div><div><span><IndustrialIcon name="warehouse" /></span><p><b>库存核验</b><small>以下单结算结果为准</small></p></div><div><span><IndustrialIcon name="clipboard" /></span><p><b>规范交易</b><small>订单全流程线上留痕</small></p></div><div><span><IndustrialIcon name="headset" /></span><p><b>售后保障</b><small>专业售后进度可追踪</small></p></div></div></div>
    <section class="container home-section">
      <div class="home-title"><div><small>MATERIAL SELECTION</small><h2>从一份材料，开始工程采购</h2></div><MallLink to="/categories">全部材料分类 ↗</MallLink></div>
      <div class="material-shortcuts"><MallLink v-for="category in roots.slice(0, 6)" :key="category.id" :to="{ path: '/goods', query: { categoryId: category.id } }"><div class="material-thumb"><img v-if="categoryImage(category)" :src="categoryImage(category)" :alt="category.name" loading="lazy" /><span v-else aria-hidden="true"><IndustrialIcon :name="categoryIcon(category.name)" /></span></div><div><strong>{{ category.name }}</strong><small>查看供应商品</small></div><b>›</b></MallLink></div>
      <div v-if="failed" class="panel"><EmptyState title="材料目录暂时加载失败"><el-button @click="load">重新加载</el-button></EmptyState></div>
    </section>
    <section class="container home-section">
      <div class="home-title"><div><small>APPLICATION SCENARIOS</small><h2>为不同工程，找到合适的材料</h2></div><span>先确认工况，再选择规格</span></div>
      <div class="scene-grid"><MallLink v-for="scene in scenes" :key="scene.number" :to="{ path: '/goods', query: { keyword: scene.keyword } }"><span class="scene-index">{{ scene.number }} / APPLICATION</span><h3>{{ scene.name }}</h3><p>{{ scene.text }}</p><b>探索材料 <i>→</i></b></MallLink></div>
    </section>
    <section class="home-suppliers home-section"><div class="container">
      <div class="home-title"><div><small>ENTERPRISE STORES</small><h2>好材料，从了解供应商开始</h2><p>走进企业店铺，查看主营材料与在售商品</p></div><MallLink to="/shops">查看全部企业店铺 ↗</MallLink></div>
      <div v-if="featured.length" class="home-shop-grid"><ShopCard v-for="item in featured" :key="item.shop.id" :shop="item.shop" :products="item.products" :total="item.total" /></div>
      <EmptyState v-else-if="shopsFailed" title="企业店铺暂时加载失败"><el-button @click="loadShops">重新加载</el-button></EmptyState>
      <EmptyState v-else title="企业材料供应目录"><MallLink to="/shops">进入店铺目录 →</MallLink></EmptyState>
    </div></section>
    <div v-if="loading" class="container home-section loading-box"><el-skeleton :rows="6" animated /></div>
    <section v-for="(floor, index) in floors" :key="floor.category.id" class="container home-section material-floor">
      <div class="home-title"><div class="floor-title"><span>{{ String(index + 1).padStart(2, '0') }}</span><div><small>ENGINEERING MATERIALS</small><h2>{{ floor.category.name }}专区</h2></div></div><MallLink :to="{ path: '/goods', query: { categoryId: floor.category.id } }">更多{{ floor.category.name }} ↗</MallLink></div>
      <div v-if="floor.products.length" :class="['floor-content', { 'is-compact': floor.products.length < 3 }]">
        <MallLink v-if="floor.products.length < 3" class="floor-guide" :to="{ path: '/goods', query: { categoryId: floor.category.id } }"><small>MATERIAL GUIDE / 选材指引</small><h3>{{ floor.category.name }}</h3><p>核对材质、规格与使用工况，<br />为项目选择合适的配套材料。</p><span>进入材料专区 →</span></MallLink>
        <div class="product-grid"><ProductCard v-for="product in floor.products" :key="product.id" :product="product" /></div>
      </div>
      <EmptyState v-else title="该分类暂无上架商品"><MallLink to="/goods">浏览其他材料</MallLink></EmptyState>
    </section>
    <section class="container home-section procurement-guide"><div><small>PROCUREMENT SERVICES</small><h2>选材之后，让采购更省心</h2><p>查看平台优惠，按订单结算结果完成采购。</p></div><MallLink to="/marketing#coupon"><b>领取采购优惠券</b><span>查看可领取优惠 →</span></MallLink><MallLink to="/member/customer-service/scoped"><b>采购与售后咨询</b><span>联系平台客服 →</span></MallLink></section>
  </div>
</template>

<style scoped>
.industry-home {
  --home-navy: #102f3a;
  --home-navy-soft: #173f49;
  --home-green: #0f806d;
  --home-orange: #ef6a2d;
  background: linear-gradient(180deg, #f4f7f7 0, #f7f8f7 680px);
  color: #17343c;
}
.home-hero { padding: 18px 0 0; background: linear-gradient(180deg, #eaf1f1, #f4f7f7); }
.home-hero__inner { display: grid; grid-template-columns: 220px minmax(0, 1fr) 226px; min-height: 466px; overflow: hidden; border-radius: 12px 12px 0 0; box-shadow: 0 18px 50px rgba(16,47,58,.13); }
.material-directory { display: flex; flex-direction: column; background: var(--home-navy); color: #fff; }
.material-directory__title { display: flex; align-items: center; gap: 12px; padding: 20px 20px 18px; border-bottom: 1px solid rgba(255,255,255,.1); }
.material-directory__title .directory-menu { display: grid; place-items: center; width: 22px; height: 22px; }
.material-directory__title .directory-menu svg { width: 22px; height: 22px; }
.material-directory__title strong { font-size: 16px; }
.material-directory__title small { display: block; margin-top: 5px; color: #79aca3; font-size: 8px; letter-spacing: 1.8px; }
.material-directory>a { display: flex; align-items: center; justify-content: space-between; min-height: 44px; padding: 7px 18px; color: #dbe8e5; font-size: 13px; transition: background .2s, color .2s; }
.material-directory>a>span { display: flex; align-items: center; gap: 10px; }
.material-directory>a i { --industrial-icon-detail: #15413e; display: grid; place-items: center; width: 30px; height: 30px; border: 1px solid rgba(142,224,208,.12); border-radius: 9px; background: linear-gradient(145deg, rgba(29,126,111,.34), rgba(20,74,70,.2)); color: #9ce3d6; font-style: normal; box-shadow: inset 0 1px 0 rgba(255,255,255,.06); }
.material-directory>a i svg { width: 17px; height: 17px; }
.material-directory>a>b { color: #6f9d96; font-size: 18px; font-weight: 400; }
.material-directory>a:hover { background: #1f5a58; color: #fff; }
.material-directory>a:hover i { --industrial-icon-detail: #0e6a5c; border-color: #76d0bf; background: linear-gradient(145deg,#28a58f,#0f806d); color: #fff; box-shadow: 0 4px 12px rgba(2,23,28,.25); }
.material-directory a.all-materials { margin-top: auto; padding: 15px 18px; border-top: 1px solid rgba(255,255,255,.1); color: #94bdb5; font-size: 11px; }
.procurement-rail { display: flex; flex-direction: column; padding: 22px 20px 18px; background: linear-gradient(155deg, #173945, #0e2b35); color: #fff; }
.procurement-rail__heading { padding-bottom: 14px; border-bottom: 1px solid rgba(255,255,255,.1); }
.procurement-rail__heading small { color: #79ada4; font-size: 8px; letter-spacing: 1.5px; }
.procurement-rail__heading h2 { margin: 8px 0 5px; font-size: 20px; }
.procurement-rail__heading p { margin: 0; color: #9db6b3; font-size: 11px; line-height: 1.6; }
.procurement-rail>a:not(.procurement-rail__action) { display: flex; align-items: center; gap: 12px; padding: 13px 0; border-bottom: 1px solid rgba(255,255,255,.08); }
.procurement-rail>a>i { --industrial-icon-detail: #17443f; display: grid; place-items: center; width: 38px; height: 38px; flex: none; border: 1px solid rgba(132,222,205,.22); border-radius: 11px; background: linear-gradient(145deg,rgba(34,143,124,.42),rgba(17,79,73,.25)); color: #aff3e5; font-style: normal; box-shadow: inset 0 1px 0 rgba(255,255,255,.08); }
.procurement-rail>a>i svg { width: 21px; height: 21px; }
.procurement-rail>a>span { display: flex; flex-direction: column; }
.procurement-rail>a b { font-size: 13px; }
.procurement-rail>a small { margin-top: 4px; color: #8da8a5; font-size: 10px; }
.procurement-rail>a:hover i { --industrial-icon-detail: #0d6759; border-color: #86decd; background: linear-gradient(145deg,#2bb29a,#0f806d); color: #fff; box-shadow: 0 5px 14px rgba(2,22,27,.3); }
.procurement-rail .procurement-rail__action { display: flex; align-items: center; justify-content: space-between; margin-top: auto; padding: 13px 15px; border-radius: 6px; background: #fff; color: #173a42; font-size: 13px; font-weight: 700; }
.home-benefits { border-bottom: 1px solid #e0e8e6; background: #fff; }
.home-benefits>.container { display: grid; grid-template-columns: repeat(4, 1fr); padding: 19px 0; }
.home-benefits>.container>div { display: flex; align-items: center; justify-content: center; gap: 14px; border-right: 1px solid #e1e8e6; }
.home-benefits>.container>div:last-child { border: 0; }
.home-benefits span { --industrial-icon-detail: #fff; display: grid; place-items: center; width: 42px; height: 42px; border: 1px solid #c0ddd6; border-radius: 12px; background: linear-gradient(145deg,#edf8f5,#dcefeb); color: var(--home-green); box-shadow: 0 5px 12px rgba(15,128,109,.1), inset 0 1px 0 #fff; }
.home-benefits span svg { width: 23px; height: 23px; }
.home-benefits p { margin: 0; }
.home-benefits b, .home-benefits small { display: block; }
.home-benefits b { font-size: 14px; }
.home-benefits small { margin-top: 4px; color: #85938f; font-size: 10px; }
.home-section { margin-top: 58px; }
.home-title { display: flex; align-items: flex-end; justify-content: space-between; gap: 20px; margin-bottom: 24px; }
.home-title small { color: var(--home-green); font-size: 9px; font-weight: 700; letter-spacing: 2.2px; }
.home-title h2 { margin: 7px 0 0; color: #17343c; font-size: 25px; font-weight: 700; letter-spacing: .5px; }
.home-title p { margin-bottom: 0; color: #7c8985; font-size: 12px; }
.home-title>a, .home-title>span { color: #57706a; font-size: 12px; }
.home-title>a:hover { color: var(--home-green); }
.material-shortcuts { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 12px; }
.material-shortcuts>a { display: grid; grid-template-columns: 68px minmax(0,1fr) 14px; align-items: center; gap: 12px; min-height: 100px; padding: 13px; border: 1px solid #e0e8e6; border-radius: 9px; background: #fff; box-shadow: 0 6px 18px rgba(25,57,54,.035); transition: transform .2s, border-color .2s, box-shadow .2s; }
.material-shortcuts>a:hover { transform: translateY(-3px); border-color: #84b9ad; box-shadow: 0 12px 28px rgba(20,76,67,.1); }
.material-thumb { display: grid; place-items: center; width: 68px; height: 68px; overflow: hidden; border-radius: 8px; background: #eff5f3; }
.material-thumb img { width: 100%; height: 100%; object-fit: contain; mix-blend-mode: multiply; }
.material-thumb span { display: grid; place-items: center; color: #579588; }
.material-thumb span svg { width: 28px; height: 28px; }
.material-shortcuts strong { display: block; font-size: 13px; }
.material-shortcuts small { display: block; margin-top: 7px; color: #8b9793; font-size: 9px; }
.material-shortcuts>a>b { color: #83a49c; font-size: 20px; font-weight: 400; }
.scene-grid { display: grid; grid-template-columns: repeat(3, minmax(0,1fr)); gap: 16px; }
.scene-grid>a { position: relative; min-height: 245px; overflow: hidden; padding: 28px; border-radius: 9px; background-position: center; background-size: cover; color: #fff; box-shadow: 0 8px 24px rgba(13,43,50,.1); }
.scene-grid>a:nth-child(1) { background-image: linear-gradient(110deg, rgba(9,39,47,.93), rgba(9,39,47,.28)), url('/banners/industry-hero-v2.png'); }
.scene-grid>a:nth-child(2) { background-image: linear-gradient(110deg, rgba(10,48,53,.92), rgba(10,48,53,.25)), url('/banners/industry-insulation-v2.png'); }
.scene-grid>a:nth-child(3) { background-image: linear-gradient(110deg, rgba(16,47,58,.94), rgba(16,47,58,.3)), url('/banners/industry-pipes-v2.png'); }
.scene-index { color: #86c9bb; font-size: 9px; letter-spacing: 1.5px; }
.scene-grid h3 { position: relative; z-index: 1; max-width: 210px; margin: 62px 0 10px; font-size: 22px; }
.scene-grid p { position: relative; z-index: 1; color: #cedddb; font-size: 12px; }
.scene-grid b { position: relative; z-index: 1; display: flex; align-items: center; justify-content: space-between; width: 125px; margin-top: 28px; color: #fff; font-size: 11px; }
.scene-grid b i { display: grid; place-items: center; width: 28px; height: 28px; border: 1px solid rgba(255,255,255,.5); border-radius: 50%; font-style: normal; }
.home-suppliers { padding: 46px 0; border-block: 1px solid #dfe8e5; background: #eaf1ef; }
.home-shop-grid { display: grid; grid-template-columns: repeat(2, minmax(0,1fr)); gap: 18px; }
.floor-title { display: flex; align-items: center; gap: 18px; }
.floor-title>span { padding-right: 18px; border-right: 1px solid #d5e1dd; color: #aac0ba; font-size: 36px; font-weight: 300; }
.floor-content.is-compact { display: grid; grid-template-columns: minmax(280px,.8fr) minmax(0,1.2fr); gap: 18px; }
.is-compact .product-grid { grid-template-columns: repeat(2,minmax(0,1fr)); }
.floor-guide { position: relative; overflow: hidden; padding: 42px; border-radius: 10px; background: linear-gradient(130deg,#173c46,#0e6c61); color: #fff; }
.floor-guide::after { content: ''; position: absolute; right: -80px; bottom: -90px; width: 280px; height: 280px; border: 42px solid rgba(255,255,255,.06); border-radius: 50%; pointer-events: none; }
.floor-guide small { color: #87c7ba; font-size: 9px; letter-spacing: 2px; }
.floor-guide h3 { margin-top: 30px; font-size: 29px; }
.floor-guide p { color: #bed3ce; font-size: 13px; line-height: 2; }
.floor-guide span { display: block; margin-top: 34px; color: #fff; font-size: 12px; }
.procurement-guide { display: grid; grid-template-columns: 1.45fr 1fr 1fr; gap: 20px; overflow: hidden; padding: 34px 36px; border-radius: 12px; background: linear-gradient(120deg,#102f3a,#0e5e58); color: #fff; box-shadow: 0 16px 35px rgba(14,52,57,.16); }
.procurement-guide small { color: #82bdb1; font-size: 9px; letter-spacing: 2px; }
.procurement-guide h2 { margin-bottom: 9px; font-size: 23px; }
.procurement-guide p { color: #acc6c0; font-size: 11px; }
.procurement-guide>a { display: flex; flex-direction: column; justify-content: center; padding: 20px 26px; border-left: 1px solid rgba(255,255,255,.13); }
.procurement-guide span { margin-top: 16px; color: #9ed3c7; font-size: 11px; }
@media (max-width: 1220px) {
  .home-hero__inner { grid-template-columns: 194px minmax(0,1fr) 205px; }
  .material-directory>a { padding-inline: 14px; }
  .procurement-rail { padding-inline: 16px; }
  .material-shortcuts { grid-template-columns: repeat(3,minmax(0,1fr)); }
  .home-title h2 { font-size: 22px; }
}
</style>
