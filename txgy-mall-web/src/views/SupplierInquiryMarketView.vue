<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { enterpriseApi } from '@/api/enterprise'
import { inquiryMarketApi, type InquiryQuote, type PublicInquiry } from '@/api/inquiry-market'
import type { Shop } from '@/types/mall'
import { formatDate, formatMoney } from '@/utils/format'
import { inquiryStatusText, quoteDisplayTime, quoteStatusText } from '@/utils/inquiry-market'

const route = useRoute()
const router = useRouter()
const shopId = computed(() => Number(route.params.shopId))
const view = computed(() => route.query.view === 'mine' ? 'mine' : 'opportunities')
const shop = ref<Shop>()
const opportunities = ref<PublicInquiry[]>([])
const quotes = ref<InquiryQuote[]>([])
const page = ref(1)
const total = ref(0)
const keyword = ref('')
const busy = ref(false)
const failed = ref(false)
let generation = 0
let alive = true

async function load() {
  const ticket = ++generation
  const currentShopId = shopId.value
  busy.value = true; failed.value = false
  try {
    const shops = await enterpriseApi.salesShops()
    if (!alive || ticket !== generation) return
    shop.value = shops.find(row => Number(row.id) === currentShopId)
    if (!shop.value) { await router.replace('/sales'); return }
    if (view.value === 'mine') {
      const result = await inquiryMarketApi.supplierPage({ shopId: currentShopId, pageNo: page.value, pageSize: 12 })
      if (!alive || ticket !== generation) return
      quotes.value = result.list; opportunities.value = []; total.value = result.total
    } else {
      const result = await inquiryMarketApi.publicPage({ pageNo: page.value, pageSize: 12, keyword: keyword.value.trim() || undefined, status: 1, state: 'open' })
      if (!alive || ticket !== generation) return
      opportunities.value = result.list; quotes.value = []; total.value = result.total
    }
  } catch {
    if (alive && ticket === generation) { opportunities.value = []; quotes.value = []; total.value = 0; failed.value = true }
  } finally { if (alive && ticket === generation) busy.value = false }
}

function switchView(next: 'opportunities' | 'mine') {
  page.value = 1
  void router.push({ path: route.path, query: next === 'mine' ? { view: 'mine' } : {} })
}
function search() { page.value = 1; void load() }
watch([shopId, view], () => { page.value = 1; void load() }, { immediate: true })
onBeforeUnmount(() => { alive = false; generation++ })
</script>

<template>
  <div class="container supplier-inquiries">
    <aside><small>SUPPLIER CENTER</small><h2>{{ shop?.name || '销售中心' }}</h2><MallLink to="/sales">销售工作台</MallLink><button :class="{ active: view === 'opportunities' }" @click="switchView('opportunities')">询价机会</button><button :class="{ active: view === 'mine' }" @click="switchView('mine')">我的报价</button><MallLink :to="`/sales/shops/${shopId}/products`">管理商品</MallLink><MallLink :to="`/sales/shops/${shopId}/orders`">销售订单</MallLink></aside>
    <main class="panel">
      <header><div><small>INQUIRY &amp; QUOTATION</small><h1>{{ view === 'mine' ? '我的报价' : '询价机会' }}</h1><p>{{ view === 'mine' ? '查看本店铺的草稿、已提交及已采纳报价。' : '仅展示仍在报价期的企业公开采购需求。' }}</p></div><MallLink to="/inquiries">查看公开专栏</MallLink></header>
      <div v-if="view === 'opportunities'" class="filters"><el-input v-model="keyword" clearable placeholder="搜索标题或物料" @keyup.enter="search"/><el-button type="primary" :loading="busy" @click="search">查询</el-button></div>
      <el-alert v-if="failed" title="数据加载失败，请重试；未显示的记录不代表不存在。" type="error" :closable="false" />
      <el-table v-if="view === 'opportunities'" v-loading="busy" :data="opportunities" empty-text="暂无可报价的公开询价">
        <el-table-column label="采购需求" min-width="280"><template #default="{row}"><strong>{{ row.title }}</strong><small>{{ row.enterpriseName || '已认证采购企业' }} · {{ row.itemCount }} 项物料</small></template></el-table-column>
        <el-table-column label="状态" width="100"><template #default="{row}">{{ inquiryStatusText(row.status) }}</template></el-table-column>
        <el-table-column label="截止时间" width="180"><template #default="{row}">{{ formatDate(row.deadline) }}</template></el-table-column>
        <el-table-column label="操作" width="190"><template #default="{row}"><MallLink :to="`/inquiries/${row.id}`">查看</MallLink><MallLink v-if="row.capabilities.canQuote" class="quote-link" :to="{path:`/inquiries/${row.id}/quote`,query:{shopId}}">参与报价</MallLink></template></el-table-column>
      </el-table>
      <el-table v-else v-loading="busy" :data="quotes" empty-text="本店铺尚未创建报价">
        <el-table-column label="询价编号" min-width="150"><template #default="{row}">#{{ row.inquiryId }}</template></el-table-column>
        <el-table-column label="报价金额" min-width="130"><template #default="{row}">¥{{ formatMoney(row.totalPrice) }}</template></el-table-column>
        <el-table-column label="状态" min-width="110"><template #default="{row}"><el-tag :type="row.status===2?'success':row.status===1?'primary':'info'">{{ quoteStatusText(row.status) }}</el-tag></template></el-table-column>
        <el-table-column label="报价时间" min-width="170"><template #default="{row}">{{ formatDate(quoteDisplayTime(row)) }}</template></el-table-column>
        <el-table-column label="操作" width="150"><template #default="{row}"><MallLink :to="{path:`/inquiries/${row.inquiryId}/quote`,query:{shopId,quoteId:row.id}}">查看 / 沟通</MallLink></template></el-table-column>
      </el-table>
      <el-pagination v-if="total > 12" v-model:current-page="page" :page-size="12" :total="total" layout="total, prev, pager, next" @current-change="load"/>
    </main>
  </div>
</template>

<style scoped>
.supplier-inquiries{display:grid;grid-template-columns:264px minmax(0,1fr);gap:24px;margin:28px auto;min-height:650px}.supplier-inquiries aside{position:sticky;top:20px;align-self:start;padding:28px 20px;border-radius:20px;background:linear-gradient(158deg,#102e3a,#184351 62%,#1b5660);color:white;box-shadow:0 18px 38px rgba(14,43,54,.18)}.supplier-inquiries aside>small{display:block;margin:0 12px;color:#70cbbb;font-size:11px;font-weight:800;letter-spacing:.2em}.supplier-inquiries aside h2{margin:10px 12px 24px;padding-bottom:18px;border-bottom:1px solid rgba(255,255,255,.12);font-size:22px;overflow-wrap:anywhere}.supplier-inquiries aside a,.supplier-inquiries aside button{display:block;width:100%;margin:5px 0;border:0;background:transparent;color:#dce9eb;text-align:left;padding:12px 13px;border-radius:9px;font:inherit;cursor:pointer;transition:.2s ease}.supplier-inquiries aside a:hover,.supplier-inquiries aside button:hover{background:rgba(255,255,255,.08);color:#fff;transform:translateX(2px)}.supplier-inquiries aside .active{background:linear-gradient(135deg,#2472a2,#168577);color:#fff;font-weight:800;box-shadow:0 8px 18px rgba(0,0,0,.15)}
.supplier-inquiries main{padding:0 30px 30px;min-width:0;border:1px solid #dce5e8;border-radius:20px;overflow:hidden;box-shadow:0 16px 38px rgba(20,48,58,.08)}.supplier-inquiries main>header{display:flex;justify-content:space-between;align-items:center;margin:0 -30px 24px;padding:28px 32px;background:linear-gradient(115deg,#f3f8f8,#e6f1ef);border-bottom:1px solid #d9e6e5}.supplier-inquiries main>header small{color:#198276;font-size:11px;font-weight:800;letter-spacing:.2em}.supplier-inquiries main h1{margin:7px 0 0;color:#183641;font-size:28px}.supplier-inquiries main p,.supplier-inquiries td small{display:block;color:#647b82;margin-top:8px}.supplier-inquiries main a{color:#167b6d;font-weight:700}.supplier-inquiries main>header>a{padding:10px 16px;border:1px solid #bad8d2;border-radius:9px;background:#fff}.filters{display:flex;gap:12px;margin:20px 0;padding:15px;border:1px solid #dfe7e9;border-radius:13px;background:#f8faf9}.filters .el-input{max-width:420px}.filters :deep(.el-input__wrapper){min-height:40px;border-radius:8px}.filters :deep(.el-button){min-width:90px;border-radius:8px;background:#167e71;border-color:#167e71;font-weight:700}.supplier-inquiries main :deep(.el-alert){margin-bottom:14px;border-radius:10px}.supplier-inquiries main :deep(.el-table){border:1px solid #dfe7e9;border-radius:13px;overflow:hidden}.supplier-inquiries main :deep(.el-table__header th){height:50px;background:#eff5f4!important;color:#3f5961;font-weight:800}.supplier-inquiries main :deep(.el-table__row:hover>td){background:#f5fbfa!important}.quote-link{margin-left:16px}.el-pagination{justify-content:flex-end;margin-top:22px}
@media(max-width:900px){.supplier-inquiries{grid-template-columns:1fr;margin:16px}.supplier-inquiries aside{position:static;display:flex;flex-wrap:wrap}.supplier-inquiries aside h2,.supplier-inquiries aside>small{width:100%}.supplier-inquiries aside a,.supplier-inquiries aside button{width:auto}.supplier-inquiries main{padding:0 18px 24px}.supplier-inquiries main>header{margin:0 -18px 20px;padding:24px 20px}}
</style>
