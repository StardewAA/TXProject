<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { inquiryMarketApi, type InquiryMessage, type InquiryQuote } from '@/api/inquiry-market'
import InquiryConversation from '@/components/InquiryConversation.vue'
import { useAccountMessageBox, useAccountOperation } from '@/composables/useAccountOperation'
import { formatDate, formatMoney } from '@/utils/format'
import { inquiryQuoteActions, makeRequestKey, quoteDisplayTime, quoteStatusText } from '@/utils/inquiry-market'

const route = useRoute()
const router = useRouter()
const operation = useAccountOperation()
const messageBox = useAccountMessageBox()
const inquiryId = computed(() => {
  const value = Number(route.params.id)
  return Number.isSafeInteger(value) && value > 0 ? value : undefined
})
const rows = ref<InquiryQuote[]>([])
const selected = ref<InquiryQuote>()
const messages = ref<InquiryMessage[]>([])
const page = ref(1)
const total = ref(0)
const busy = ref(false)
const detailBusy = ref(false)
const messageBusy = ref(false)
const messageFailed = ref(false)
const sending = ref(false)
const pendingMessage = ref<{ quoteId: number; content: string; clientMessageId: string }>()
const accepting = ref(false)
const failed = ref(false)
const actions = computed(() => inquiryQuoteActions(selected.value?.capabilities, Boolean(selected.value?.id)))
let generation = 0
let messageGeneration = 0

async function load() {
  const ticket = ++generation
  busy.value = true; failed.value = false
  try {
    const result = await inquiryMarketApi.page({ inquiryId: inquiryId.value, pageNo: page.value, pageSize: 12 })
    if (!operation.current() || ticket !== generation) return
    rows.value = result.list; total.value = result.total
    if (selected.value && !rows.value.some(row => row.id === selected.value?.id)) selected.value = undefined
    if (!selected.value && rows.value[0]?.id) await selectQuote(rows.value[0])
  } catch { if (operation.current() && ticket === generation) { rows.value = []; total.value = 0; failed.value = true } }
  finally { if (operation.current() && ticket === generation) busy.value = false }
}

async function selectQuote(row: InquiryQuote) {
  if (!row.id || detailBusy.value) return
  const quoteId = row.id
  const ticket = ++messageGeneration
  selected.value = undefined
  messages.value = []
  pendingMessage.value = undefined
  messageFailed.value = false
  detailBusy.value = true
  try {
    const detail = await inquiryMarketApi.get(quoteId)
    if (!operation.current() || ticket !== messageGeneration) return
    selected.value = detail
    await loadMessages(detail.id, ticket)
  } finally { if (operation.current()) detailBusy.value = false }
}

async function loadMessages(quoteId = selected.value?.id, existingTicket?: number) {
  if (!quoteId) return
  const ticket = existingTicket ?? ++messageGeneration
  messages.value = []
  messageFailed.value = false
  messageBusy.value = true
  try {
    const result = await inquiryMarketApi.messages(quoteId)
    if (operation.current() && ticket === messageGeneration && selected.value?.id === quoteId) messages.value = result
  }
  catch {
    if (operation.current() && ticket === messageGeneration && selected.value?.id === quoteId) {
      messages.value = []
      messageFailed.value = true
    }
  }
  finally { if (operation.current() && ticket === messageGeneration) messageBusy.value = false }
}
async function sendMessage(content: string, complete: (success: boolean) => void) {
  if (!selected.value?.id || !selected.value.capabilities.canMessage || sending.value) return
  const quoteId = selected.value.id
  const normalized = content.trim()
  if (!normalized) return
  const pending = pendingMessage.value?.quoteId === quoteId && pendingMessage.value.content === normalized
    ? pendingMessage.value
    : { quoteId, content: normalized, clientMessageId: makeRequestKey('quote-message') }
  pendingMessage.value = pending
  sending.value = true
  try {
    await inquiryMarketApi.sendMessage(pending)
    if (operation.current() && selected.value?.id === quoteId) {
      await loadMessages(quoteId)
      pendingMessage.value = undefined
      complete(true)
    }
  }
  catch { complete(false) }
  finally { if (operation.current()) sending.value = false }
}

async function accept() {
  if (!selected.value?.id || !actions.value.accept || accepting.value) return
  try { await messageBox.confirm('采纳后将锁定该店铺报价；创建订单前仍会重新校验 SKU、库存、地址与报价快照。确认采纳？', '采纳报价', { confirmButtonText: '确认采纳', cancelButtonText: '取消' }) } catch { return }
  accepting.value = true
  try {
    await inquiryMarketApi.accept(selected.value.id, selected.value.version)
    if (!operation.current()) return
    selected.value = await inquiryMarketApi.get(selected.value.id)
    if (!operation.current()) return
    const listRow = rows.value.find(row => row.id === selected.value?.id)
    if (listRow) Object.assign(listRow, selected.value)
    ElMessage.success('报价已采纳，请确认收货地址并创建平台订单')
  } finally { if (operation.current()) accepting.value = false }
}
function checkout() { if (selected.value?.id && actions.value.convert) void router.push({ path: '/checkout', query: { inquiryQuoteId: selected.value.id } }) }

watch(inquiryId, () => { selected.value = undefined; messages.value = []; messageFailed.value = false; messageGeneration++; page.value = 1; void load() }, { immediate: true })
onBeforeUnmount(() => { generation++; messageGeneration++ })
</script>

<template>
  <section class="received-quotes">
    <header><div><h1>{{ inquiryId ? `询价 #${inquiryId} · 收到的报价` : '收到的报价' }}</h1><p>每份报价和沟通均按店铺隔离；采纳后从专用结算入口转为平台订单。</p></div><MallLink to="/purchase/inquiries">管理询价单</MallLink></header>
    <el-alert v-if="failed" title="报价加载失败，请刷新重试；不会将失败显示为没有报价。" type="error" :closable="false"/>
    <div class="workspace">
      <aside v-loading="busy" class="panel quote-list">
        <button v-for="row in rows" :key="row.id" :class="{active:selected?.id===row.id}" @click="selectQuote(row)"><span><strong>{{ row.shopName || `店铺 #${row.shopId || '-'}` }}</strong><em v-if="!inquiryId">{{ row.inquiryTitle || row.inquiryNo || `询价 #${row.inquiryId}` }}</em><small>{{ quoteStatusText(row.status) }} · {{ formatDate(quoteDisplayTime(row)) }}</small></span><b>¥{{ formatMoney(row.totalPrice) }}</b></button>
        <p v-if="!busy&&!rows.length">暂未收到报价</p>
        <el-pagination v-if="total>12" v-model:current-page="page" small :page-size="12" :total="total" layout="prev, next" @current-change="load"/>
      </aside>
      <main v-if="selected" v-loading="detailBusy" class="panel quote-detail">
        <div class="detail-title"><div><h2>{{ selected.shopName || '店铺报价' }}</h2><el-tag :type="selected.status===2?'success':selected.status===1?'primary':'info'">{{ quoteStatusText(selected.status) }}</el-tag></div><strong>报价参考合计 ¥{{ formatMoney(selected.totalPrice) }}</strong></div>
        <el-table :data="selected.items"><el-table-column label="供应商实际商品" min-width="220"><template #default="{row}"><div class="sku-mapping"><strong>{{ row.productName || row.name || '未返回商品名' }}</strong><small>SKU ID：{{ row.skuId || '-' }}<template v-if="row.skuName"> · {{ row.skuName }}</template></small><small>规格：{{ row.skuSpecification || row.specification || '未返回规格' }}</small></div></template></el-table-column><el-table-column label="需求 / 成交数量" width="150"><template #default="{row}">{{row.quantity}} {{row.unit}} / {{row.orderCount}}</template></el-table-column><el-table-column label="单价" width="120"><template #default="{row}">¥{{formatMoney(row.unitPrice)}}</template></el-table-column><el-table-column label="小计" width="120"><template #default="{row}">¥{{formatMoney(row.amount)}}</template></el-table-column></el-table>
        <dl><div><dt>预计运费</dt><dd>¥{{ formatMoney(selected.freightPrice) }}</dd></div><div><dt>预计交货</dt><dd>{{ formatDate(selected.deliveryDate) }}</dd></div><div><dt>报价有效期</dt><dd>{{ formatDate(selected.validUntil) }}</dd></div><div><dt>提交时间</dt><dd>{{ formatDate(quoteDisplayTime(selected)) }}</dd></div></dl>
        <p class="remark">报价说明：{{ selected.remark || '无' }}</p>
        <el-alert title="报价中的运费仅供沟通参考；转为订单时会按当前收货地址和店铺运费模板重新计算，以结算页应付金额为准。" type="info" :closable="false"/>
        <div class="commands"><el-button v-if="actions.accept" type="primary" :loading="accepting" @click="accept">采纳该报价</el-button><el-button v-if="actions.convert" type="primary" @click="checkout">确认地址并创建订单</el-button></div>
        <el-alert v-if="messageFailed" title="沟通记录加载失败，请单独刷新；不会显示其他报价的历史会话。" type="error" :closable="false"/>
        <InquiryConversation :messages="messages" :loading="messageBusy" :sending="sending" :disabled="!selected.capabilities.canMessage" side="BUYER" @refresh="loadMessages()" @send="sendMessage"/>
      </main>
      <div v-else class="panel empty">选择一份报价查看明细与沟通记录</div>
    </div>
  </section>
</template>

<style scoped>
.received-quotes{min-height:560px}.received-quotes>header{display:flex;align-items:center;justify-content:space-between;margin-bottom:18px}.received-quotes h1{margin:0}.received-quotes header p{color:#64748b}.received-quotes a{color:#167664}.workspace{display:grid;grid-template-columns:300px minmax(0,1fr);gap:18px}.quote-list{padding:10px;align-self:start;min-height:220px}.quote-list button{display:flex;width:100%;justify-content:space-between;gap:10px;text-align:left;border:0;border-bottom:1px solid #edf1f0;background:white;padding:16px 12px;cursor:pointer}.quote-list button.active{background:#edf8f4;border-left:3px solid #167664}.quote-list small{display:block;margin-top:7px;color:#64748b}.quote-list b{color:#df421f;white-space:nowrap}.quote-list>p,.empty{text-align:center;color:#64748b;padding:55px 16px}.quote-detail{padding:24px;min-width:0}.detail-title,.detail-title>div{display:flex;align-items:center;justify-content:space-between;gap:12px}.detail-title h2{margin:0}.detail-title>strong{font-size:23px;color:#df421f}.quote-detail dl{display:grid;grid-template-columns:repeat(4,1fr);background:#f8fbfa;margin:18px 0}.quote-detail dl div{padding:14px}.quote-detail dt{color:#64748b;font-size:12px}.quote-detail dd{margin:7px 0 0}.sku-mapping small{display:block;margin-top:6px;color:#64748b}.remark{color:#64748b;white-space:pre-wrap}.commands{display:flex;justify-content:flex-end;margin:18px 0}.empty{display:grid;place-items:center}@media(max-width:900px){.workspace{grid-template-columns:1fr}.quote-list{display:grid;grid-template-columns:repeat(2,1fr)}.quote-detail dl{grid-template-columns:repeat(2,1fr)}}
.quote-list em{display:block;margin-top:6px;color:#334155;font-size:12px;font-style:normal}
</style>
