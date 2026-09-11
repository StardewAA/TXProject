<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { productApi } from '@/api/mall'
import EmptyState from '@/components/EmptyState.vue'
import ProductCard from '@/components/ProductCard.vue'
import type { Category, Product } from '@/types/mall'

const categories = ref<Category[]>([])
const products = ref<Product[]>([])
const loading = ref(true)
const failed = ref(false)

async function load() {
  loading.value = true
  failed.value = false
  try {
    const [categoryList, productPage] = await Promise.all([
      productApi.getCategories(),
      productApi.getPage({ pageNo: 1, pageSize: 15 }),
    ])
    categories.value = categoryList || []
    products.value = productPage?.list || []
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div>
    <section class="hero">
      <div class="container hero__grid">
        <aside class="hero-categories">
          <RouterLink v-for="category in categories.slice(0, 10)" :key="category.id" :to="{ path: '/goods', query: { categoryId: category.id } }">
            <span>{{ category.name }}</span><b>›</b>
          </RouterLink>
          <RouterLink v-if="!categories.length && !loading" to="/categories">查看全部商品分类</RouterLink>
        </aside>
        <div class="hero-banner">
          <div class="hero-banner__content">
            <span class="eyebrow">CORROSION & INSULATION</span>
            <h1>防腐保温材料<br />一站式采购平台</h1>
            <p>连接真实商品、库存、订单与售后，满足工程材料规范化采购需求</p>
            <RouterLink class="hero-action" to="/goods">立即选购</RouterLink>
          </div>
          <div class="hero-shape"><i></i><i></i><i></i></div>
        </div>
        <div class="hero-services">
          <div><strong>工程采购</strong><span>集中查找项目所需材料</span></div>
          <div><strong>规格清晰</strong><span>SKU、计量单位与库存可查</span></div>
          <div><strong>订单可追踪</strong><span>从下单到售后全程留痕</span></div>
        </div>
      </div>
    </section>

    <section class="container category-shortcuts page-section">
      <RouterLink v-for="category in categories.slice(0, 8)" :key="category.id" :to="{ path: '/goods', query: { categoryId: category.id } }">
        <span>{{ category.name.slice(0, 1) }}</span><b>{{ category.name }}</b><small>查看产品</small>
      </RouterLink>
    </section>

    <section class="container marketing-shortcuts page-section">
      <RouterLink to="/marketing#discount"><b>采购优惠</b><span>符合活动范围的商品自动使用限时折扣</span><em>了解优惠 →</em></RouterLink>
      <RouterLink to="/marketing#reward"><b>满额优惠</b><span>结算达到活动门槛后自动减免或包邮</span><em>查看规则 →</em></RouterLink>
      <RouterLink to="/marketing#coupon"><b>采购优惠券</b><span>领取后可在确认订单时选择使用</span><em>立即领券 →</em></RouterLink>
    </section>

    <section class="container page-section">
      <div class="section-heading">
        <div><h2>平台商品</h2><p>商品价格、规格和库存以结算结果为准</p></div>
        <RouterLink to="/goods">查看全部 →</RouterLink>
      </div>
      <div v-if="loading" class="loading-box"><el-skeleton :rows="6" animated /></div>
      <EmptyState v-else-if="failed" title="商城服务暂不可用" description="请确认网关及商品服务已经启动">
        <el-button type="primary" @click="load">重新加载</el-button>
      </EmptyState>
      <EmptyState v-else-if="!products.length" title="暂无上架商品" description="请先在管理后台为租户 1 上架商品" />
      <div v-else class="product-grid"><ProductCard v-for="product in products" :key="product.id" :product="product" /></div>
    </section>
  </div>
</template>

<style scoped lang="scss">
.hero { background: #e9ecec; }
.hero__grid { height: 430px; display: grid; grid-template-columns: 220px 1fr 220px; }
.hero-categories { padding: 10px 0; background: #27343d; color: white; }
.hero-categories a { min-height: 40px; display: flex; justify-content: space-between; align-items: center; padding: 8px 20px; color: #e9edef; font-size: 14px; }
.hero-categories a:hover { background: var(--brand); }
.hero-banner { position: relative; overflow: hidden; background: linear-gradient(115deg, #eef1ee 0%, #d5dcd7 58%, #a9b5ac 100%); }
.hero-banner__content { position: relative; z-index: 2; width: 64%; padding: 62px 54px; }
.eyebrow { color: var(--brand); font-size: 13px; letter-spacing: 3px; font-weight: 700; }
.hero h1 { margin: 14px 0; color: #1e2d33; font-size: 42px; line-height: 1.25; }
.hero p { color: #59666c; line-height: 1.8; }
.hero-action { display: inline-block; margin-top: 22px; padding: 12px 30px; border-radius: 4px; background: var(--brand); color: white; font-weight: 700; }
.hero-shape { position: absolute; inset: 0 0 0 auto; width: 45%; opacity: .7; }
.hero-shape i { position: absolute; right: -70px; display: block; width: 360px; height: 74px; border: 13px solid rgba(86,102,91,.32); border-radius: 50%; transform: rotate(-20deg); }
.hero-shape i:nth-child(1) { top: 100px; }.hero-shape i:nth-child(2) { top: 185px; right: -20px; }.hero-shape i:nth-child(3) { top: 275px; right: -100px; }
.hero-services { padding: 32px 24px; background: white; }
.hero-services div { padding: 20px 0; border-bottom: 1px solid var(--line); }
.hero-services div:last-child { border: 0; }
.hero-services strong, .hero-services span { display: block; }
.hero-services strong { color: #2b3942; }
.hero-services span { margin-top: 7px; color: var(--muted); font-size: 13px; line-height: 1.5; }
.category-shortcuts { display: grid; grid-template-columns: repeat(8, 1fr); gap: 12px; }
.category-shortcuts a { min-height: 116px; display: flex; flex-direction: column; align-items: center; justify-content: center; border: 1px solid var(--line); border-radius: 6px; background: white; }
.category-shortcuts span { width: 38px; height: 38px; display: grid; place-items: center; border-radius: 50%; background: #f4e6df; color: var(--brand); font-weight: 800; }
.category-shortcuts b { margin-top: 8px; font-size: 14px; }
.category-shortcuts small { margin-top: 3px; color: var(--muted); }
.marketing-shortcuts { display: grid; grid-template-columns: repeat(3, 1fr); gap: 18px; }.marketing-shortcuts a { min-height: 126px; padding: 26px 30px; border-radius: 8px; background: linear-gradient(135deg, #2a3740, #46545b); color: white; }.marketing-shortcuts a:nth-child(2) { background: linear-gradient(135deg, #b54022, #df6b3e); }.marketing-shortcuts a:nth-child(3) { background: linear-gradient(135deg, #9a711f, #c99c39); }.marketing-shortcuts b,.marketing-shortcuts span,.marketing-shortcuts em { display: block; }.marketing-shortcuts b { font-size: 20px; }.marketing-shortcuts span { margin-top: 10px; opacity: .8; font-size: 13px; }.marketing-shortcuts em { margin-top: 15px; font-style: normal; font-size: 14px; }
</style>
