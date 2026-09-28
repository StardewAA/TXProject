<script setup lang="ts">
import { onBeforeUnmount, reactive, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { publicMarketClient } from '@/api/public-market'
import { createPublicMarketBrowser, publicMarketState, publicPrice } from '@/utils/public-market.mjs'
const route = useRoute(), router = useRouter()
const state = reactive(publicMarketState())
const browser = createPublicMarketBrowser(state, publicMarketClient)
watch(() => route.params.marketId, id => { void browser.open(typeof id === 'string' ? id : '') }, { immediate: true, flush: 'sync' })
function selectMarket(event: Event) { void router.replace('/markets/' + encodeURIComponent((event.target as HTMLSelectElement).value)) }
onBeforeUnmount(browser.dispose)
</script>

<template>
  <main class="public-market">
    <header><MallLink to="/">← 返回原商城</MallLink><h1>公开商城浏览</h1>
      <p>您正在匿名浏览其他商城。原账号登录保留；此处不提供加购、下单或企业经营操作。</p>
    </header>
    <section class="market-toolbar">
      <label v-if="state.directory.length">选择商城 <select :value="state.tenantId" aria-label="选择商城" @change="selectMarket"><option value="" disabled>请选择</option><option v-for="item in state.directory" :key="item.tenantId" :value="item.tenantId">{{ item.name }}</option></select></label>
      <h2 v-if="state.name">{{ state.name }}</h2>
      <form v-if="state.tenantId" @submit.prevent="browser.search(1)">
        <select v-model="state.shopId" aria-label="按店铺筛选"><option value="">全部店铺</option><option v-for="shop in state.shops" :key="shop.id" :value="String(shop.id)">{{ shop.name }}</option></select>
        <select v-model="state.categoryId" aria-label="按分类筛选"><option value="">全部分类</option><option v-for="category in state.categories" :key="category.id" :value="String(category.id)">{{ category.name }}</option></select>
        <input v-model="state.keyword" maxlength="100" placeholder="搜索商品名称或规格" aria-label="搜索商品" />
        <button :disabled="state.loading">查询</button>
      </form>
    </section>
    <section v-if="state.error" role="alert" class="market-state">{{ state.error }} <button @click="browser.open(state.tenantId)">重新加载</button></section>
    <p v-else-if="state.loading" role="status">正在加载商城…</p>
    <template v-else-if="state.tenantId">
      <p>公开商品 {{ state.total }} 件 · 当前第 {{ state.pageNo }} 页</p>
      <p v-if="!state.products.length" class="market-state">当前条件下暂无可展示商品，请调整筛选。</p>
      <div class="market-products"><button v-for="product in state.products" :key="product.id" class="market-product" @click="browser.detail(product.id)"><strong>{{ product.name }}</strong><span>{{ product.shopName || '店铺信息以详情为准' }}</span><b>{{ publicPrice(product.price) }}</b><small>查看规格详情 →</small></button></div>
      <div class="market-pages"><button :disabled="state.pageNo <= 1" @click="browser.search(state.pageNo - 1)">上一页</button><button :disabled="state.pageNo * 12 >= state.total" @click="browser.search(state.pageNo + 1)">下一页</button></div>
    </template>
    <section v-if="state.detail || state.detailLoading || state.detailError" class="market-detail" aria-label="商品详情">
      <button class="close" @click="browser.closeDetail">关闭详情</button>
      <p v-if="state.detailLoading">正在加载详情…</p><p v-else-if="state.detailError" role="alert">{{ state.detailError }}</p>
      <template v-else-if="state.detail"><h2>{{ state.detail.name }}</h2><p>{{ state.detail.shopName }}</p><p>{{ state.detail.introduction }}</p><b>{{ publicPrice(state.detail.price) }}</b>
        <ul><li v-for="sku in state.detail.skus || []" :key="sku.id">{{ sku.properties?.map(p => `${p.propertyName || ''}：${p.valueName || ''}`).join(' / ') || '默认规格' }} · {{ publicPrice(sku.price) }} · 库存 {{ sku.stock ?? '待确认' }}</li></ul>
        <p>仅供选材浏览，价格与库存以后续目标商城下单核验为准。跨商城交易暂未开放。</p>
      </template>
    </section>
  </main>
</template>
<style scoped>
.public-market{max-width:1200px;margin:32px auto;padding:24px;color:#173d37}header p,.market-detail p{color:#64748b;line-height:1.8}.market-toolbar,.market-detail,.market-state{background:#fff;border:1px solid #dce8e5;border-radius:12px;padding:24px;margin:20px 0}form{display:flex;gap:12px;flex-wrap:wrap}select,input,button{padding:10px 14px;border:1px solid #cedbd7;border-radius:6px;background:#fff}button{cursor:pointer}button:disabled{opacity:.5;cursor:default}.market-products{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:18px}.market-product{text-align:left;display:grid;gap:16px;padding:24px;overflow-wrap:anywhere}.market-product b{color:#c7562b}.market-product span{color:#64748b}.market-pages{display:flex;gap:12px;justify-content:center;margin-top:24px}.close{float:right}@media(max-width:700px){.market-products{grid-template-columns:1fr}.public-market{margin:0;padding:16px}}
</style>
