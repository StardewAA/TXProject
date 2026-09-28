<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { inquiryMarketApi, type PublicInquiry } from '@/api/inquiry-market'
import { formatDate } from '@/utils/format'
import { inquiryStatusText } from '@/utils/inquiry-market'

const rows = ref<PublicInquiry[]>([])
const total = ref(0)
const page = ref(1)
const keyword = ref('')
const state = ref<'open' | 'ended'>('open')
const busy = ref(false)
const failed = ref(false)
let generation = 0
let alive = true

async function load() {
  const ticket = ++generation
  busy.value = true
  failed.value = false
  try {
    const result = await inquiryMarketApi.publicPage({ pageNo: page.value, pageSize: 12, keyword: keyword.value.trim() || undefined, state: state.value })
    if (!alive || ticket !== generation) return
    rows.value = result.list
    total.value = result.total
  } catch {
    if (alive && ticket === generation) { rows.value = []; total.value = 0; failed.value = true }
  } finally {
    if (alive && ticket === generation) busy.value = false
  }
}

function search() { page.value = 1; void load() }
onMounted(load)
onBeforeUnmount(() => { alive = false; generation++ })
</script>

<template>
  <div class="container inquiry-market">
    <section class="market-hero">
      <div><small>ENTERPRISE REQUEST FOR QUOTATION</small><h1>平台询价专栏</h1><p>汇总所有开放租户明确发布到平台的采购需求；供应商仍须在本店权限范围内匹配、沟通和报价。</p></div>
      <MallLink class="publish" to="/purchase/inquiries/new">发布询价单</MallLink>
    </section>
    <section class="panel filters" aria-label="询价筛选">
      <el-input v-model="keyword" maxlength="50" show-word-limit clearable placeholder="搜索询价标题或物料" @keyup.enter="search" />
      <el-select v-model="state" aria-label="询价状态"><el-option value="open" label="报价中"/><el-option value="ended" label="已结束（截止或成交）"/></el-select>
      <el-button type="primary" :loading="busy" @click="search">查询</el-button>
    </section>
    <el-alert v-if="failed" title="公开询价加载失败，请刷新重试；不会以空列表代替失败结果。" type="error" :closable="false" />
    <div v-loading="busy" class="cards">
      <article v-for="row in rows" :key="row.id" class="panel inquiry-card">
        <header><el-tag :type="row.status === 1 ? 'success' : 'info'">{{ inquiryStatusText(row.status) }}</el-tag><span>{{ row.no || `询价 #${row.id}` }}</span></header>
        <h2><MallLink :to="`/inquiries/${row.id}`">{{ row.title }}</MallLink></h2>
        <p class="company">{{ row.enterpriseName || '已认证采购企业' }}<span v-if="row.addressSummary"> · {{ row.addressSummary }}</span></p>
        <p class="remark">{{ row.itemSummary || '采购物料待查看' }}</p>
        <div class="facts"><span><b>{{ row.itemCount }}</b> 项物料</span><span><b>{{ row.quoteCount }}</b> 份报价</span><span>报价截止 {{ formatDate(row.deadline) }}</span></div>
        <MallLink class="detail" :to="`/inquiries/${row.id}`">查看清单与报价入口 →</MallLink>
      </article>
    </div>
    <div v-if="!busy && !failed && !rows.length" class="panel empty">暂无符合条件的公开询价</div>
    <el-pagination v-if="total > 12" v-model:current-page="page" :page-size="12" :total="total" layout="total, prev, pager, next" @current-change="load" />
  </div>
</template>

<style scoped>
.inquiry-market{margin:32px auto 56px}.market-hero{display:flex;justify-content:space-between;align-items:center;padding:34px 38px;border-radius:10px;background:linear-gradient(125deg,#123f45,#176b5e);color:white}.market-hero small{letter-spacing:2px;color:#b9d8d3}.market-hero h1{margin:10px 0 8px;font-size:34px}.market-hero p{margin:0;color:#dcebe8}.publish{background:#fff;color:#126b59;padding:13px 22px;border-radius:6px;font-weight:700;white-space:nowrap}.filters{display:flex;gap:14px;margin:20px 0;padding:18px}.filters .el-input{max-width:460px}.filters .el-select{width:180px}.cards{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:18px;min-height:220px}.inquiry-card{padding:22px;min-width:0}.inquiry-card header{display:flex;align-items:center;justify-content:space-between;color:#64748b;font-size:12px}.inquiry-card h2{font-size:19px;line-height:1.5;margin:16px 0 8px}.inquiry-card h2 a{color:#183b39}.company{color:#126b59;font-size:13px}.remark{color:#64748b;line-height:1.65;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden;min-height:3.3em}.facts{display:grid;gap:7px;padding:14px 0;border-top:1px solid #edf1f0;color:#64748b;font-size:13px}.facts b{color:#173e46}.detail{display:inline-block;margin-top:10px;color:#167664;font-weight:600}.empty{padding:80px;text-align:center;color:#64748b}.el-pagination{margin-top:24px;justify-content:center}@media(max-width:1000px){.cards{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:680px){.cards{grid-template-columns:1fr}.market-hero{align-items:flex-start;gap:20px}.filters{flex-wrap:wrap}}
</style>
