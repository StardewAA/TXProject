<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { portalApi, type PortalHotTenant, type PortalProduct, type PortalSku, type PortalTenant } from '@/api/portal'
import { useAuthStore } from '@/stores/auth'
import { useCartStore } from '@/stores/cart'
import { formatMoney, normalizeImage, propertyText } from '@/utils/format'

type SearchMode = 'product' | 'shop' | 'inquiry'
type FeaturedTenant = PortalTenant & { validQuantity?: number; productCount?: number; rank?: number }

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const cartStore = useCartStore()
const selectedProduct = ref<PortalProduct | null>(null)
const selectedSku = ref<PortalSku | null>(null)
const quantity = ref(1)
const adding = ref(false)
const detailLoading = ref(false)
const searchMode = ref<SearchMode>('product')
const state = reactive({
  tenant: null as PortalTenant | null,
  hot: [] as PortalHotTenant[],
  products: [] as PortalProduct[],
  total: 0,
  pageNo: 1,
  pageSize: 24,
  keyword: '',
  window: '7d' as '7d' | '30d',
  loading: false,
  hotLoading: false,
  error: '',
})
let generation = 0
let alive = true

const searchOptions: Array<{ value: SearchMode; label: string }> = [
  { value: 'product', label: '找商品' },
  { value: 'shop', label: '找企业' },
  { value: 'inquiry', label: '找询价' },
]
const shortcutCards = [
  { index: '01', title: '发布询价', description: '发布材料清单，汇集企业报价', to: '/purchase/inquiries/new', action: '立即发布' },
  { index: '02', title: '采购工作台', description: '管理询价、订单与企业采购', to: '/purchase', action: '进入中心' },
  { index: '03', title: '企业店铺', description: '按企业和店铺查找真实货源', to: '/shops', action: '查看店铺' },
  { index: '04', title: '规格比价', description: '同类商品规格与价格集中对照', to: '/compare', action: '开始对比' },
]
const categoryCards = [
  { title: '防腐材料', subtitle: 'COATING', description: '底漆、面漆、防火涂层', keyword: '防腐', image: '/product-images/kedun-real/coating.png' },
  { title: '保温材料', subtitle: 'INSULATION', description: '岩棉、橡塑、耐火纤维', keyword: '保温', image: '/product-images/kedun-real/mineral-wool.png' },
  { title: '管材管件', subtitle: 'PIPE SYSTEM', description: '预制保温管、防腐管及配件', keyword: '管件', image: '/product-images/kedun-real/preinsulated-pipe.png' },
  { title: '施工配套', subtitle: 'ACCESSORIES', description: '玻纤布、金属外护及辅材', keyword: '施工', image: '/product-images/kedun-real/fiberglass-cloth.png' },
]
const tenantSlug = computed(() => typeof route.params.tenantSlug === 'string' ? route.params.tenantSlug : '')
const isTenantMall = computed(() => Boolean(tenantSlug.value))
const requestedProductId = computed(() => {
  const raw = Array.isArray(route.query.product) ? route.query.product[0] : route.query.product
  return typeof raw === 'string' && /^[1-9]\d*$/.test(raw) ? raw : ''
})
const searchPlaceholder = computed(() => {
  if (searchMode.value === 'shop') return '输入企业名称、店铺名称或主营材料'
  if (searchMode.value === 'inquiry') return '输入询价标题、材料名称或采购要求'
  return '输入商品名称、材料类别或规格关键词'
})
const featuredTenants = computed<FeaturedTenant[]>(() => {
  const tenants = new Map<string, FeaturedTenant>()
  state.hot.forEach((tenant) => {
    if (!tenant.tenantSlug) return
    tenants.set(tenant.tenantSlug, { ...tenant })
  })
  state.products.forEach((product) => {
    if (!product.tenantSlug) return
    const current = tenants.get(product.tenantSlug)
    if (current) {
      current.productCount = (current.productCount || 0) + 1
      return
    }
    tenants.set(product.tenantSlug, {
      tenantId: product.tenantId,
      tenantSlug: product.tenantSlug,
      tenantName: product.tenantName,
      mainCategories: product.introduction || '防腐保温材料及工程配套',
      productCount: 1,
    })
  })
  return Array.from(tenants.values()).slice(0, 4)
})

function safeKeyword(value: unknown) {
  return typeof value === 'string' ? value.trim().slice(0, 100) : ''
}

function diversifyProducts(products: PortalProduct[], limit = 24) {
  const groups = new Map<string, PortalProduct[]>()
  products.forEach((product) => {
    const key = product.picUrl || `category-${String(product.categoryId || 'other')}`
    const group = groups.get(key) || []
    group.push(product)
    groups.set(key, group)
  })
  const result: PortalProduct[] = []
  while (result.length < limit) {
    let added = false
    for (const group of groups.values()) {
      const product = group.shift()
      if (!product) continue
      result.push(product)
      added = true
      if (result.length >= limit) break
    }
    if (!added) break
  }
  return result
}

async function loadProducts(pageNo = 1) {
  const version = ++generation
  state.loading = true
  state.error = ''
  state.products = []
  state.pageNo = pageNo
  const keyword = state.keyword || undefined
  try {
    if (isTenantMall.value) {
      state.pageSize = 24
      const slug = tenantSlug.value
      const [tenant, page] = await Promise.all([
        portalApi.getTenant(slug),
        portalApi.getTenantProducts(slug, { pageNo, pageSize: 24, keyword }),
      ])
      if (!alive || version !== generation || slug !== tenantSlug.value) return
      state.tenant = tenant
      state.products = page.list || []
      state.total = Number(page.total || 0)
      await resolveRequestedProduct(version, state.products, state.total)
    } else {
      const curatedCatalog = !keyword
      state.pageSize = curatedCatalog ? 192 : 24
      const page = await portalApi.getProducts({ pageNo, pageSize: state.pageSize, keyword })
      if (!alive || version !== generation || isTenantMall.value) return
      state.tenant = null
      state.products = curatedCatalog ? diversifyProducts(page.list || []) : (page.list || [])
      state.total = Number(page.total || 0)
    }
  } catch (error) {
    if (alive && version === generation) state.error = error instanceof Error ? error.message : '平台商品加载失败'
  } finally {
    if (alive && version === generation) state.loading = false
  }
}

function selectProduct(product: PortalProduct) {
  selectedProduct.value = product
  selectedSku.value = product.skus?.[0] || null
  quantity.value = 1
}

async function resolveRequestedProduct(version: number, current: PortalProduct[], total: number) {
  const id = requestedProductId.value
  if (!id) { selectedProduct.value = null; selectedSku.value = null; return }
  const local = current.find(product => String(product.spuId) === id)
  if (local) { selectProduct(local); return }
  if (!tenantSlug.value) return
  detailLoading.value = true
  try {
    const pages = Math.ceil(Math.max(0, total) / 100)
    for (let pageNo = 1; pageNo <= pages; pageNo += 1) {
      const page = await portalApi.getTenantProducts(tenantSlug.value, { pageNo, pageSize: 100 })
      if (!alive || version !== generation || id !== requestedProductId.value) return
      const found = (page.list || []).find(product => String(product.spuId) === id)
      if (found) { selectProduct(found); return }
    }
  } finally { if (alive && version === generation) detailLoading.value = false }
}

async function loadHot() {
  if (isTenantMall.value) return
  state.hotLoading = true
  try { state.hot = await portalApi.getHotTenants(state.window, 12) }
  catch { state.hot = [] }
  finally { state.hotLoading = false }
}

async function search() {
  const keyword = state.keyword.trim()
  if (isTenantMall.value && searchMode.value === 'product') {
    const query = keyword ? { q: keyword } : {}
    await router.replace({ path: route.path, query })
    window.setTimeout(() => document.querySelector('#portal-products')?.scrollIntoView({ behavior: 'smooth', block: 'start' }), 40)
    return
  }
  if (searchMode.value === 'product') {
    await router.push({ name: 'goods-list', query: keyword ? { keyword } : {} })
    return
  }
  const query = keyword ? { q: keyword } : {}
  await router.push({ path: searchMode.value === 'shop' ? '/shops' : '/inquiries', query })
}

function selectCategory(keyword: string) {
  searchMode.value = 'product'
  state.keyword = keyword
  void search()
}

function productRoute(product: PortalProduct) {
  return { name: 'tenant-product-detail', params: { tenantSlug: product.tenantSlug, spuId: String(product.spuId) } }
}

async function closeProduct() {
  selectedProduct.value = null
  selectedSku.value = null
  const query = { ...route.query }
  delete query.product
  await router.replace({ path: route.path, query })
}

async function addToCart() {
  if (!selectedProduct.value?.saleable || !selectedSku.value) return ElMessage.warning('请选择可购买的商品规格')
  if (!authStore.isLoggedIn) return router.push({ name: 'platform-login', query: { redirect: route.fullPath } })
  adding.value = true
  try {
    await cartStore.add(selectedSku.value.skuId, quantity.value)
    ElMessage.success('已加入平台购物车')
  } catch (error) {
    ElMessage.warning(error instanceof Error ? error.message : '加入购物车失败')
  } finally { adding.value = false }
}

watch(() => [route.params.tenantSlug, route.query.q, route.query.product], () => {
  state.keyword = safeKeyword(route.query.q)
  void loadProducts(1)
  if (!isTenantMall.value) void loadHot()
}, { immediate: true })
watch(() => state.window, () => { void loadHot() })
onBeforeUnmount(() => { alive = false; generation += 1 })
</script>

<template>
  <div class="portal-page">
    <section class="portal-hero" :class="{ 'portal-hero--tenant': isTenantMall }">
      <div class="container portal-hero__body">
        <p class="hero-eyebrow">{{ isTenantMall ? 'TENANT MALL / 企业商城' : 'INDUSTRIAL PROCUREMENT PLATFORM' }}</p>
        <h1>{{ state.tenant?.tenantName || '防腐保温材料，一站式企业采购' }}</h1>
        <p class="hero-summary" v-if="state.tenant?.mainCategories">主营：{{ state.tenant.mainCategories }}</p>
        <p class="hero-summary" v-else>汇集防腐、保温、管材与施工配套，支持企业找货、询价、比价和履约协同。</p>

        <div v-if="!isTenantMall" class="hero-search-tabs" role="tablist" aria-label="平台搜索类型">
          <button v-for="option in searchOptions" :key="option.value" type="button" :class="{ active: searchMode === option.value }" @click="searchMode = option.value">{{ option.label }}</button>
        </div>
        <form class="hero-search" @submit.prevent="search">
          <input v-model="state.keyword" maxlength="100" :placeholder="searchPlaceholder" aria-label="平台搜索" />
          <button type="submit">立即搜索</button>
        </form>
        <div class="hero-trust">
          <span>企业资质审核</span><span>真实店铺经营</span><span>多规格比价</span><span>订单全程可追踪</span>
        </div>
        <RouterLink v-if="isTenantMall" class="back-platform" to="/">← 返回平台首页</RouterLink>
      </div>
    </section>

    <section v-if="!isTenantMall" class="container shortcut-grid" aria-label="企业采购快捷入口">
      <MallLink v-for="item in shortcutCards" :key="item.index" :to="item.to" class="shortcut-card">
        <span class="shortcut-card__index">{{ item.index }}</span>
        <div><h2>{{ item.title }}</h2><p>{{ item.description }}</p></div>
        <b>{{ item.action }} →</b>
      </MallLink>
    </section>

    <section v-if="!isTenantMall" class="container category-section">
      <div class="section-heading">
        <div><small>MATERIAL CATEGORIES</small><h2>按工程材料快速选型</h2></div>
        <p>围绕防腐保温工程常用材料组织，减少重复检索</p>
      </div>
      <div class="category-grid">
        <button v-for="category in categoryCards" :key="category.title" type="button" @click="selectCategory(category.keyword)">
          <img :src="category.image" :alt="category.title" loading="lazy" />
          <span class="category-card__shade"></span>
          <span class="category-card__content"><small>{{ category.subtitle }}</small><strong>{{ category.title }}</strong><em>{{ category.description }}</em><b>查看商品 →</b></span>
        </button>
      </div>
    </section>

    <section id="portal-products" class="container market-section">
      <div class="market-main">
        <div class="section-heading market-heading">
          <div><small>{{ isTenantMall ? 'TENANT PRODUCTS' : 'SELECTED PRODUCTS' }}</small><h2>{{ isTenantMall ? '本企业公开商品' : '工程材料现货与可询商品' }}</h2></div>
          <p>共 {{ state.total }} 件已审核商品</p>
        </div>
        <p v-if="state.error" class="state error" role="alert">{{ state.error }} <button @click="loadProducts(state.pageNo)">重新加载</button></p>
        <div v-else-if="state.loading" class="state"><el-skeleton :rows="8" animated /></div>
        <div v-else-if="state.products.length" class="portal-products" :class="{ 'portal-products--tenant': isTenantMall }">
          <RouterLink v-for="product in state.products" :key="`${product.tenantId}-${product.spuId}`" :to="productRoute(product)" class="portal-product">
            <div class="portal-product__image">
              <img v-if="product.picUrl" :src="normalizeImage(product.picUrl)" :alt="product.spuName" loading="lazy" />
              <span v-else>暂无图片</span>
              <b>{{ product.saleable ? '可采购' : '暂不可售' }}</b>
            </div>
            <div class="portal-product__body">
              <p class="portal-product__owner"><strong>{{ product.tenantName }}</strong><span>{{ product.shopName }}</span></p>
              <h3>{{ product.spuName }}</h3>
              <p class="portal-product__intro">{{ product.introduction || '防腐保温工程适用材料' }}</p>
              <div class="portal-product__meta"><span>{{ product.skus?.length || 0 }} 种规格</span><span>销量 {{ product.salesCount || 0 }}</span></div>
              <div class="portal-product__price"><small>参考价</small><strong>¥{{ formatMoney(product.price) }}</strong><b>查看详情 →</b></div>
            </div>
          </RouterLink>
        </div>
        <p v-else class="state">当前条件下暂无已审核、已上架的公开商品。</p>
        <div class="pagination"><button :disabled="state.pageNo <= 1 || state.loading" @click="loadProducts(state.pageNo - 1)">上一页</button><span>第 {{ state.pageNo }} 页</span><button :disabled="state.pageNo * state.pageSize >= state.total || state.loading" @click="loadProducts(state.pageNo + 1)">下一页</button></div>
      </div>

      <aside v-if="!isTenantMall" class="market-aside">
        <section class="aside-panel tenant-panel">
          <div class="aside-heading"><div><small>FEATURED COMPANIES</small><h2>推荐企业</h2></div><MallLink to="/shops">全部 →</MallLink></div>
          <p v-if="state.hotLoading && !featuredTenants.length" class="aside-state">正在整理企业数据…</p>
          <div v-else class="featured-tenants">
            <RouterLink v-for="(tenant, index) in featuredTenants" :key="String(tenant.tenantId)" :to="{ name: 'tenant-mall', params: { tenantSlug: tenant.tenantSlug } }">
              <span>{{ String(index + 1).padStart(2, '0') }}</span>
              <div><strong>{{ tenant.tenantName }}</strong><small>{{ tenant.mainCategories || '防腐保温材料及工程配套' }}</small><b v-if="tenant.validQuantity">近{{ state.window === '7d' ? '7' : '30' }}天成交 {{ tenant.validQuantity }}</b><b v-else>本页在售 {{ tenant.productCount || 1 }} 件</b></div>
            </RouterLink>
          </div>
        </section>

        <section class="aside-panel inquiry-panel">
          <small>RFQ MARKET</small><h2>没找到合适规格？</h2><p>发布材料名称、规格、数量和交期，让更多企业参与报价。</p>
          <MallLink class="aside-primary" to="/purchase/inquiries/new">发布询价单</MallLink>
          <MallLink class="aside-secondary" to="/inquiries">进入询价专栏 →</MallLink>
        </section>

        <section class="aside-panel assurance-panel">
          <small>SERVICE</small><h2>企业采购保障</h2>
          <ul><li><b>资质</b><span>企业和店铺信息可核验</span></li><li><b>规格</b><span>按真实 SKU 采购与比价</span></li><li><b>履约</b><span>订单、物流、售后全程留痕</span></li></ul>
        </section>
      </aside>
    </section>

    <el-dialog :model-value="Boolean(requestedProductId)" width="min(860px, 92vw)" class="portal-detail-dialog" destroy-on-close @close="closeProduct">
      <div v-if="detailLoading" class="state"><el-skeleton :rows="7" animated /></div>
      <el-result v-else-if="!selectedProduct" icon="warning" title="商品详情不可用" sub-title="商品可能已下架、待审核或不属于当前租户商城"><template #extra><el-button @click="closeProduct">返回商品列表</el-button></template></el-result>
      <section v-else class="portal-detail">
        <div class="portal-detail__image"><img v-if="selectedSku?.picUrl || selectedProduct.picUrl" :src="normalizeImage(selectedSku?.picUrl || selectedProduct.picUrl)" :alt="selectedProduct.spuName" /><span v-else>暂无图片</span></div>
        <div class="portal-detail__body">
          <small>{{ selectedProduct.tenantName }} / {{ selectedProduct.enterpriseName }}</small>
          <h2>{{ selectedProduct.spuName }}</h2><p>{{ selectedProduct.introduction || '防腐保温工程适用材料' }}</p>
          <dl><dt>所属店铺</dt><dd>{{ selectedProduct.shopName }}</dd><dt>真实经营租户</dt><dd>{{ selectedProduct.tenantName }}</dd></dl>
          <strong class="detail-price">¥{{ formatMoney(selectedSku?.price ?? selectedProduct.price) }}</strong>
          <div class="detail-skus"><label>选择规格</label><button v-for="sku in selectedProduct.skus || []" :key="String(sku.skuId)" :class="{ active: selectedSku?.skuId === sku.skuId }" @click="selectedSku = sku">{{ propertyText(sku.properties) || sku.skuName || `SKU ${sku.skuId}` }}</button></div>
          <div class="detail-actions"><el-input-number v-model="quantity" :min="1" :max="99999" /><el-button type="primary" :loading="adding" :disabled="!selectedProduct.saleable || !selectedSku" @click="addToCart">加入平台购物车</el-button><el-button @click="router.push('/cart')">查看购物车</el-button></div>
          <p class="detail-note">加入购物车时只提交真实 SKU 编号；商品所属租户、企业与店铺由服务端重新解析，页面参数不会被当作经营归属。</p>
        </div>
      </section>
    </el-dialog>
  </div>
</template>

<style scoped>
.portal-page{min-height:700px;background:#f4f6f5;color:#102f38}.portal-hero{position:relative;isolation:isolate;min-height:354px;padding:52px 0 84px;overflow:hidden;background:#0b3c43;color:#fff}.portal-hero::before{position:absolute;z-index:-2;inset:0 auto 0 50%;width:min(1320px,calc(100% - 48px));content:"";background:url('/banners/industry-hero-v2.png') center 47%/cover no-repeat;transform:translateX(-50%)}.portal-hero::after{position:absolute;z-index:-1;inset:0;content:"";background:linear-gradient(90deg,rgba(5,31,39,.96) 0%,rgba(8,55,61,.9) 42%,rgba(10,75,70,.37) 72%,rgba(7,37,42,.18) 100%)}.portal-hero--tenant{min-height:318px;padding-bottom:54px}.portal-hero__body{position:relative}.hero-eyebrow{margin:0;color:#70d2bf;font-size:11px;font-weight:700;letter-spacing:2.8px}.portal-hero h1{max-width:780px;margin:15px 0 8px;font-size:38px;line-height:1.2;letter-spacing:-1px}.hero-summary{max-width:760px;margin:0;color:#d0e2df;font-size:15px;line-height:1.8}.hero-search-tabs{display:flex;gap:6px;margin-top:24px}.hero-search-tabs button{padding:7px 17px;border:1px solid #ffffff52;border-radius:6px;background:#052f3573;color:#d6e9e6;cursor:pointer}.hero-search-tabs button.active{border-color:#fff;background:#fff;color:#0d665b;font-weight:700}.hero-search{display:flex;max-width:800px;margin-top:9px;border:4px solid #ffffff2e;border-radius:10px}.hero-search input{flex:1;min-width:0;padding:15px 18px;border:0;border-radius:6px 0 0 6px;background:#fff;font-size:15px;outline:none}.hero-search>button{padding:0 32px;border:0;border-radius:0 6px 6px 0;background:#ec6b31;color:#fff;font-weight:700;cursor:pointer}.hero-trust{display:flex;flex-wrap:wrap;gap:20px;margin-top:18px;color:#c9ddda;font-size:12px}.hero-trust span::before{display:inline-grid;width:17px;height:17px;margin-right:6px;border:1px solid #72c9b8;border-radius:50%;content:"✓";place-items:center;color:#72c9b8;font-size:10px}.back-platform{display:inline-block;margin-top:19px;color:#d9eee9}.shortcut-grid{position:relative;z-index:2;display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px;margin-top:-44px}.shortcut-card{display:grid;grid-template-columns:42px 1fr;gap:5px 12px;min-height:130px;padding:21px;border:1px solid #d9e3e1;background:#fff;box-shadow:0 13px 30px #173c3520;transition:.2s}.shortcut-card:hover{border-color:#89b9ae;transform:translateY(-3px)}.shortcut-card__index{grid-row:1/3;color:#a9bab6;font-family:Georgia,serif;font-size:27px}.shortcut-card h2{margin:0;font-size:18px}.shortcut-card p{min-height:36px;margin:7px 0;color:#71847f;font-size:12px;line-height:1.55}.shortcut-card>b{grid-column:2;color:#0f806d;font-size:12px}.category-section{padding-top:50px}.section-heading{display:flex;align-items:end;justify-content:space-between;margin-bottom:18px}.section-heading small,.aside-panel>small,.aside-heading small{color:#0f806d;font-size:10px;font-weight:700;letter-spacing:2px}.section-heading h2,.aside-heading h2{margin:6px 0 0;font-size:25px}.section-heading>p{margin:0;color:#768985;font-size:12px}.category-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:14px}.category-grid button{position:relative;height:230px;padding:0;overflow:hidden;border:0;background:#173c42;color:#fff;cursor:pointer;text-align:left}.category-grid img{width:100%;height:100%;object-fit:cover;transition:.35s}.category-grid button:hover img{transform:scale(1.045)}.category-card__shade{position:absolute;inset:0;background:linear-gradient(0deg,rgba(5,25,32,.93),rgba(5,25,32,.05) 72%)}.category-card__content{position:absolute;inset:auto 20px 18px}.category-card__content>*{display:block}.category-card__content small{color:#7fd1c2;font-size:9px;letter-spacing:2px}.category-card__content strong{margin-top:5px;font-size:21px}.category-card__content em{margin-top:4px;color:#c9d8d5;font-size:11px;font-style:normal}.category-card__content b{margin-top:12px;color:#fff;font-size:11px}.market-section{display:grid;grid-template-columns:minmax(0,1fr) 286px;gap:20px;padding-top:58px;padding-bottom:30px;scroll-margin-top:140px}.market-heading{padding-bottom:16px;border-bottom:1px solid #d7e2df}.market-heading>p{font-size:13px}.portal-products{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:14px}.portal-products--tenant{grid-template-columns:repeat(4,minmax(0,1fr))}.portal-product{overflow:hidden;border:1px solid #dce5e3;background:#fff;transition:.2s}.portal-product:hover{border-color:#8cb7ad;box-shadow:0 14px 28px #173d371b;transform:translateY(-3px)}.portal-product__image{position:relative;display:grid;height:176px;place-items:center;background:#eef2f1}.portal-product__image img{width:100%;height:100%;object-fit:cover}.portal-product__image>span{color:#90a19e}.portal-product__image>b{position:absolute;top:10px;left:10px;padding:4px 8px;background:#0d6e61e8;color:#fff;font-size:10px}.portal-product__body{padding:14px}.portal-product__owner{display:flex;justify-content:space-between;gap:8px;margin:0;color:#748783;font-size:10px}.portal-product__owner strong,.portal-product__owner span{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.portal-product__owner strong{color:#0f806d}.portal-product h3{height:41px;margin:10px 0 6px;overflow:hidden;font-size:15px;line-height:1.4}.portal-product__intro{height:34px;margin:0;overflow:hidden;color:#74847f;font-size:11px;line-height:1.55}.portal-product__meta{display:flex;gap:14px;margin:11px 0;padding-top:10px;border-top:1px solid #edf1f0;color:#7b8c88;font-size:10px}.portal-product__price{display:flex;align-items:baseline;gap:6px}.portal-product__price small{color:#83918e;font-size:9px}.portal-product__price strong{color:#e75f2b;font-size:19px}.portal-product__price b{margin-left:auto;color:#0f806d;font-size:10px}.market-aside{display:flex;flex-direction:column;gap:14px}.aside-panel{border:1px solid #dce5e3;background:#fff}.aside-heading{display:flex;align-items:end;justify-content:space-between;padding:18px 18px 14px;border-bottom:1px solid #e7eeec}.aside-heading h2{font-size:19px}.aside-heading>a{color:#0f806d;font-size:11px}.featured-tenants>a{display:grid;grid-template-columns:31px 1fr;gap:10px;padding:15px 18px;border-bottom:1px solid #edf1f0}.featured-tenants>a:last-child{border-bottom:0}.featured-tenants>a>span{color:#9caca8;font-family:Georgia,serif;font-size:20px}.featured-tenants strong,.featured-tenants small,.featured-tenants b{display:block}.featured-tenants strong{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:13px}.featured-tenants small{height:31px;margin-top:5px;overflow:hidden;color:#7b8b87;font-size:10px;line-height:1.5}.featured-tenants b{margin-top:6px;color:#0f806d;font-size:10px}.aside-state{padding:22px;color:#72827e;font-size:11px}.inquiry-panel{padding:22px;background:linear-gradient(145deg,#0a3c43,#0e7165);color:#fff}.inquiry-panel>small{color:#85d1c2}.inquiry-panel h2{margin:8px 0;font-size:21px}.inquiry-panel p{margin:0 0 18px;color:#c8dfda;font-size:11px;line-height:1.7}.aside-primary,.aside-secondary{display:block;text-align:center}.aside-primary{padding:10px;background:#ed6a31;color:#fff;font-size:12px;font-weight:700}.aside-secondary{margin-top:12px;color:#d6eae6;font-size:10px}.assurance-panel{padding:20px}.assurance-panel h2{margin:7px 0 15px;font-size:19px}.assurance-panel ul{margin:0;padding:0;list-style:none}.assurance-panel li{display:flex;gap:11px;padding:10px 0;border-top:1px solid #e9efed}.assurance-panel li b{display:grid;flex:0 0 33px;height:33px;border-radius:50%;place-items:center;background:#eaf5f2;color:#0f806d;font-size:10px}.assurance-panel li span{align-self:center;color:#6f817c;font-size:11px}.state{padding:28px;border:1px solid #dce7e4;background:#fff;color:#71817d}.state.error{color:#a64332}.state button,.pagination button{padding:8px 14px;border:1px solid #c7d9d5;border-radius:4px;background:#fff;cursor:pointer}.pagination{display:flex;justify-content:center;align-items:center;gap:14px;padding:28px}.pagination button:disabled{opacity:.45;cursor:not-allowed}.portal-detail{display:grid;grid-template-columns:330px 1fr;gap:30px}.portal-detail__image{display:grid;min-height:330px;place-items:center;background:#f1f5f4;color:#8da09b}.portal-detail__image img{width:100%;height:330px;object-fit:contain}.portal-detail__body>small{color:#0f806d}.portal-detail__body h2{margin:10px 0}.portal-detail__body>p{color:#70817d;line-height:1.6}.portal-detail dl{display:grid;grid-template-columns:100px 1fr;gap:8px;margin:20px 0}.portal-detail dt{color:#82918e}.portal-detail dd{margin:0}.detail-price{display:block;margin:20px 0;color:#ef6a2d;font-size:30px}.detail-skus label{display:block;margin-bottom:8px;color:#70817d}.detail-skus button{margin:0 8px 8px 0;padding:8px 12px;border:1px solid #c9d6d3;background:#fff}.detail-skus button.active{border-color:#0f806d;background:#eef8f5;color:#0f806d}.detail-actions{display:flex;gap:10px;margin-top:20px}.detail-note{font-size:11px!important}.portal-detail-dialog :deep(.el-dialog__body){padding-top:8px}@media(max-width:1180px){.shortcut-grid{grid-template-columns:repeat(2,1fr)}.category-grid{grid-template-columns:repeat(2,1fr)}.market-section{grid-template-columns:minmax(0,1fr) 250px}.portal-products{grid-template-columns:repeat(2,1fr)}.portal-products--tenant{grid-template-columns:repeat(3,1fr)}}@media(max-width:850px){.portal-hero::after{background:rgba(5,39,45,.86)}.market-section{grid-template-columns:1fr}.market-aside{display:grid;grid-template-columns:repeat(2,1fr)}.assurance-panel{grid-column:1/-1}.portal-products--tenant{grid-template-columns:repeat(2,1fr)}}@media(max-width:650px){.portal-hero{padding:38px 14px 64px}.portal-hero h1{font-size:29px}.hero-search>button{padding:0 18px}.shortcut-grid,.category-grid,.portal-products,.portal-products--tenant,.market-aside{grid-template-columns:1fr}.shortcut-grid{padding-inline:14px}.category-section,.market-section{padding-inline:14px}.section-heading{align-items:start;flex-direction:column;gap:8px}.category-grid button{height:205px}.portal-detail{grid-template-columns:1fr}.detail-actions{flex-wrap:wrap}}
/* Keep hero copy clear of the rounded artwork edge. */
.portal-hero__body{box-sizing:border-box;padding-inline:32px}
/* The hero artwork follows the content width; the page gutter stays clean. */
.portal-hero{background:transparent}
.portal-hero::before,.portal-hero::after{inset:0 auto 0 50%;width:min(1320px,calc(100% - 48px));border-radius:22px;transform:translateX(-50%)}
.portal-hero::after{background:linear-gradient(90deg,rgba(5,31,39,.98) 0%,rgba(8,55,61,.91) 38%,rgba(10,75,70,.42) 66%,rgba(7,37,42,.18) 100%)}
.shortcut-card{border-radius:16px}
.category-grid button{border-radius:18px;box-shadow:0 10px 26px rgba(16,47,58,.12)}
.portal-product,.aside-panel,.state{border-radius:15px}
.portal-product__image{overflow:hidden;border-radius:14px 14px 0 0}
.inquiry-panel{border-radius:15px}
.aside-primary{border-radius:9px}
.pagination button,.detail-skus button{border-radius:9px}
@media(max-width:650px){.portal-hero{padding-inline:0}.portal-hero__body{padding-inline:18px}.portal-hero::before,.portal-hero::after{width:calc(100% - 28px);border-radius:16px}}
.portal-product{display:flex;min-width:0;flex-direction:column}
.portal-product__image{position:relative;flex:0 0 176px;height:176px;overflow:hidden}
.portal-product__image img{position:absolute;inset:0;display:block}
.portal-product__image>b{z-index:1}
.portal-product__body{position:relative;z-index:2;flex:1;background:#fff}
.portal-product h3{display:-webkit-box;min-height:42px;height:42px;margin:8px 0 6px;overflow:hidden;color:#17343c;line-height:21px;-webkit-box-orient:vertical;-webkit-line-clamp:2}
</style>
