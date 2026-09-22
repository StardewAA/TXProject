<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { enterpriseApi } from '@/api/enterprise'
import { inquiryMarketApi, type InquiryCapabilities, type InquiryMessage, type InquiryQuote, type PublicInquiry } from '@/api/inquiry-market'
import { supplierApi, type SupplierQuoteSku } from '@/api/supplier'
import InquiryConversation from '@/components/InquiryConversation.vue'
import type { Shop } from '@/types/mall'
import { useAccountMessageBox, useAccountOperation } from '@/composables/useAccountOperation'
import { formatDate, formatMoney } from '@/utils/format'
import { inquiryQuoteActions, makeRequestKey, quoteStatusText, yuanToCents } from '@/utils/inquiry-market'

interface EditorLine { inquiryItemId?: number; name: string; specification: string; quantity: number; unit: string; skuId?: number; orderCount: number; unitPriceYuan: number }
interface Editor { id?: number; status: number; version: number; freightYuan: number; validUntil?: string | number; deliveryDate?: string | number; remark: string; lines: EditorLine[] }

const route = useRoute()
const router = useRouter()
const operation = useAccountOperation()
const messageBox = useAccountMessageBox()
const inquiryId = computed(() => Number(route.params.id))
const selectedShopId = ref<number>()
const shops = ref<Shop[]>([])
const inquiry = ref<PublicInquiry>()
const quoteSkus = ref<SupplierQuoteSku[]>([])
const messages = ref<InquiryMessage[]>([])
const busy = ref(true)
const saving = ref(false)
const messageBusy = ref(false)
const messageFailed = ref(false)
const sending = ref(false)
const pendingMessage = ref<{ quoteId: number; content: string; clientMessageId: string }>()
const failed = ref(false)
const editor = reactive<Editor>({ status: 0, version: 0, freightYuan: 0, remark: '', lines: [] })
const quoteRequestKey = ref(makeRequestKey('quote-save'))
const deniedCapabilities: InquiryCapabilities = { canQuote: false, canEdit: false, canSubmit: false, canWithdraw: false, canManage: false, canViewQuotes: false, canMessage: false, canAccept: false, canConvert: false, canCheckout: false }
const quoteCapabilities = ref<InquiryCapabilities>({ ...deniedCapabilities })
let generation = 0
let messageGeneration = 0

const actions = computed(() => inquiryQuoteActions(editor.id ? quoteCapabilities.value : inquiry.value?.capabilities, Boolean(editor.id)))
const canEdit = computed(() => actions.value.create || actions.value.edit)
const canSubmit = computed(() => actions.value.submit)
const canWithdraw = computed(() => actions.value.withdraw)
const readonly = computed(() => !canEdit.value)
const skuOptions = computed(() => quoteSkus.value.map(sku => ({
  value: Number(sku.id), label: `${sku.productName} · ${sku.specification || sku.barCode || `SKU ${sku.id}`} · ¥${formatMoney(sku.price)}`,
})))
const totalCents = computed(() => editor.lines.reduce(
  (sum, line) => sum + Math.round(line.unitPriceYuan * 100) * line.orderCount,
  Math.round(editor.freightYuan * 100),
))

function resetEditor(row?: InquiryQuote) {
  if (row) {
    quoteCapabilities.value = row.capabilities
    editor.id = row.id; editor.status = row.status; editor.version = row.version; editor.freightYuan = row.freightPrice / 100
    editor.validUntil = row.validUntil; editor.deliveryDate = row.deliveryDate; editor.remark = row.remark
    editor.lines = row.items.map((item, index) => {
      const requested = inquiry.value?.items.find(value => value.id === item.inquiryItemId) || inquiry.value?.items[index]
      const quantity = requested?.quantity || item.quantity
      return { inquiryItemId: item.inquiryItemId || requested?.id, name: requested?.name || item.name || '物料', specification: requested?.specification || item.specification, quantity, unit: requested?.unit || item.unit, skuId: item.skuId, orderCount: item.orderCount || 1, unitPriceYuan: item.unitPrice / 100 }
    })
    return
  }
  quoteCapabilities.value = { ...deniedCapabilities, canQuote: Boolean(inquiry.value?.capabilities.canQuote) }
  editor.id = undefined; editor.status = 0; editor.version = 0; editor.freightYuan = 0; editor.validUntil = undefined; editor.deliveryDate = undefined; editor.remark = ''
  editor.lines = (inquiry.value?.items || []).map(item => ({ inquiryItemId: item.id, name: item.name, specification: item.specification, quantity: item.quantity, unit: item.unit, skuId: item.skuId, orderCount: Number.isInteger(item.quantity) ? item.quantity : 1, unitPriceYuan: 0 }))
}

async function loadQuoteForShop(ticket: number) {
  if (!selectedShopId.value) return
  const messageTicket = ++messageGeneration
  messages.value = []
  pendingMessage.value = undefined
  messageFailed.value = false
  quoteSkus.value = await supplierApi.quoteSkus(selectedShopId.value)
  if (!operation.current() || ticket !== generation) return
  // 报价由“询价单 + 店铺”唯一定位。不信任 URL 中可篡改的 quoteId，
  // 否则旧书签可能把本账号另一店铺的报价载入当前编辑器。
  const existing: InquiryQuote | undefined = await inquiryMarketApi.mine(inquiryId.value, selectedShopId.value) || undefined
  if (!operation.current() || ticket !== generation) return
  resetEditor(existing)
  if (existing?.id) await loadMessages(existing.id, messageTicket)
}

async function load() {
  const ticket = ++generation
  busy.value = true; failed.value = false
  try {
    const [publicRow, allowedShops] = await Promise.all([inquiryMarketApi.publicGet(inquiryId.value), enterpriseApi.salesShops()])
    if (!operation.current() || ticket !== generation) return
    inquiry.value = publicRow; shops.value = allowedShops
    const requested = Number(route.query.shopId)
    selectedShopId.value = Number(allowedShops.find(shop => Number(shop.id) === requested)?.id || allowedShops[0]?.id)
    if (!selectedShopId.value) { await router.replace('/sales'); return }
    await loadQuoteForShop(ticket)
  } catch { if (operation.current() && ticket === generation) failed.value = true }
  finally { if (operation.current() && ticket === generation) busy.value = false }
}

async function save(showSuccess = true) {
  if (!selectedShopId.value || saving.value || !canEdit.value) return editor.id
  if (!editor.lines.length) { ElMessage.warning('询价单没有可报价物料'); return undefined }
  if (editor.lines.some(line => !line.inquiryItemId || !line.skuId || !Number.isSafeInteger(line.orderCount) || line.orderCount <= 0)) { ElMessage.warning('保存前请为每项物料选择有效 SKU，并填写正整数的 SKU 包装件数'); return undefined }
  const skuIds = editor.lines.map(line => line.skuId)
  if (new Set(skuIds).size !== skuIds.length) { ElMessage.warning('不同询价明细不能映射同一个商品 SKU'); return undefined }
  if (editor.lines.some(line => !Number.isFinite(line.unitPriceYuan) || line.unitPriceYuan <= 0)) { ElMessage.warning('每项含税单价必须大于 0 元'); return undefined }
  if (!Number.isSafeInteger(Number(editor.validUntil)) || Number(editor.validUntil) <= Date.now()) { ElMessage.warning('报价有效期须晚于当前时间'); return undefined }
  if (!Number.isSafeInteger(Number(editor.deliveryDate)) || Number(editor.deliveryDate) <= 0) { ElMessage.warning('请选择预计交货日期'); return undefined }
  saving.value = true
  try {
    const id = await inquiryMarketApi.save({ id: editor.id, inquiryId: inquiryId.value, shopId: selectedShopId.value, version: editor.version, requestKey: quoteRequestKey.value, freightPrice: yuanToCents(editor.freightYuan, '运费'), validUntil: Number(editor.validUntil), deliveryDate: Number(editor.deliveryDate), remark: editor.remark.trim(), items: editor.lines.map(line => ({ inquiryItemId: line.inquiryItemId!, skuId: line.skuId!, count: line.orderCount, unitPrice: yuanToCents(line.unitPriceYuan, `${line.name}单价`) })) })
    if (!operation.current()) return undefined
    const detail = await inquiryMarketApi.get(Number(id))
    if (!operation.current()) return undefined
    resetEditor(detail)
    quoteRequestKey.value = makeRequestKey('quote-save')
    if (showSuccess) ElMessage.success('报价草稿已保存')
    await router.replace({ path: route.path, query: { shopId: selectedShopId.value } })
    return editor.id
  } finally { if (operation.current()) saving.value = false }
}

async function submit() {
  if (!canSubmit.value || saving.value) return
  if (editor.lines.some(line => !line.skuId || !Number.isSafeInteger(line.orderCount) || line.orderCount <= 0)) return ElMessage.warning('每项物料均须映射有效 SKU，并填写正整数的 SKU 包装件数')
  if (editor.lines.some(line => line.unitPriceYuan <= 0)) return ElMessage.warning('请填写大于 0 元的有效报价')
  try { await messageBox.confirm('提交后采购方可查看本店铺报价，确认提交？', '提交报价', { confirmButtonText: '确认提交', cancelButtonText: '取消' }) } catch { return }
  const id = await save(false)
  if (!id || !operation.current()) return
  if (!quoteCapabilities.value.canSubmit) return ElMessage.warning('报价权限或版本已变化，请刷新后重试')
  saving.value = true
  try {
    await inquiryMarketApi.submit(id, editor.version)
    if (!operation.current()) return
    const fresh = await inquiryMarketApi.get(id)
    if (!operation.current()) return
    resetEditor(fresh); ElMessage.success('报价已提交')
  }
  finally { if (operation.current()) saving.value = false }
}

async function withdraw() {
  if (!editor.id || !canWithdraw.value || saving.value) return
  try { await messageBox.confirm('撤回后采购方不可继续采纳该报价，确认撤回？', '撤回报价', { confirmButtonText: '确认撤回', cancelButtonText: '取消' }) } catch { return }
  saving.value = true
  try {
    await inquiryMarketApi.withdraw(editor.id, editor.version)
    if (!operation.current()) return
    const fresh = await inquiryMarketApi.get(editor.id)
    if (!operation.current()) return
    resetEditor(fresh); ElMessage.success('报价已撤回，可修改后重新提交')
  }
  finally { if (operation.current()) saving.value = false }
}

async function loadMessages(quoteId = editor.id, existingTicket?: number) {
  if (!quoteId) return
  const ticket = existingTicket ?? ++messageGeneration
  messages.value = []
  messageFailed.value = false
  messageBusy.value = true
  try {
    const result = await inquiryMarketApi.messages(quoteId)
    if (operation.current() && ticket === messageGeneration && editor.id === quoteId) messages.value = result
  }
  catch {
    if (operation.current() && ticket === messageGeneration && editor.id === quoteId) {
      messages.value = []
      messageFailed.value = true
    }
  }
  finally { if (operation.current() && ticket === messageGeneration) messageBusy.value = false }
}
async function sendMessage(content: string, complete: (success: boolean) => void) {
  if (!editor.id || !quoteCapabilities.value.canMessage || sending.value) return
  const quoteId = editor.id
  const normalized = content.trim()
  if (!normalized) return
  const pending = pendingMessage.value?.quoteId === quoteId && pendingMessage.value.content === normalized
    ? pendingMessage.value
    : { quoteId, content: normalized, clientMessageId: makeRequestKey('quote-message') }
  pendingMessage.value = pending
  sending.value = true
  try {
    await inquiryMarketApi.sendMessage(pending)
    if (operation.current() && editor.id === quoteId) {
      await loadMessages(quoteId)
      pendingMessage.value = undefined
      complete(true)
    }
  }
  catch { complete(false) }
  finally { if (operation.current()) sending.value = false }
}

watch(selectedShopId, async (value, previous) => {
  if (!value || previous === undefined || value === previous) return
  const ticket = ++generation
  busy.value = true
  try { await router.replace({ path: route.path, query: { shopId: value } }); await loadQuoteForShop(ticket) }
  catch { if (operation.current() && ticket === generation) failed.value = true }
  finally { if (operation.current() && ticket === generation) busy.value = false }
})
watch(inquiryId, load, { immediate: true })
onBeforeUnmount(() => { generation++; messageGeneration++ })
</script>

<template>
  <div class="container quote-page">
    <MallLink :to="selectedShopId ? `/sales/shops/${selectedShopId}/inquiries` : '/inquiries'">← 返回询价机会</MallLink>
    <el-result v-if="failed" class="panel" icon="error" title="报价工作台加载失败" sub-title="请确认店铺授权、询价状态和报价访问权限"><template #extra><el-button @click="load">重试</el-button></template></el-result>
    <div v-else v-loading="busy">
      <section class="panel heading"><div><p>{{ inquiry?.no || `询价 #${inquiryId}` }}</p><h1>{{ inquiry?.title || '店铺报价' }}</h1><small>报价截止：{{ formatDate(inquiry?.deadline) }}</small></div><div><label for="quote-shop">报价店铺</label><el-select id="quote-shop" v-model="selectedShopId" :disabled="saving || shops.length < 2"><el-option v-for="shop in shops" :key="shop.id" :value="Number(shop.id)" :label="shop.name"/></el-select><el-tag v-if="editor.id">{{ quoteStatusText(editor.status) }}</el-tag></div></section>
      <el-alert v-if="editor.status === 2" title="该报价已被采购方采纳，成交信息以已创建的平台订单为准。" type="success" :closable="false"/>
      <el-alert title="提交前须将每项需求映射为本店铺已上架 SKU。需求可按吨、立方米等小数数量表达；成交订单请填写对应 SKU 的整数包装件数。" :closable="false"/>
      <section class="panel lines"><h2>逐项报价</h2><div v-for="(line,index) in editor.lines" :key="line.inquiryItemId || index" class="line"><div><strong>{{ line.name }}</strong><p>需求：{{ line.specification || '未填写规格' }} · {{ line.quantity }} {{ line.unit }}</p></div><el-select v-model="line.skuId" filterable clearable :disabled="readonly" placeholder="选择本店铺有效 SKU"><el-option v-for="option in skuOptions" :key="option.value" :value="option.value" :label="option.label"/></el-select><div class="amount-input"><small>SKU 包装件数</small><el-input-number v-model="line.orderCount" :min="1" :precision="0" :step="1" :disabled="readonly"/></div><div class="amount-input"><small>每件含税单价（元）</small><el-input-number v-model="line.unitPriceYuan" :min="0" :precision="2" :step="1" :disabled="readonly"/></div></div></section>
      <section class="panel quote-form"><div><label>运费（元）</label><el-input-number v-model="editor.freightYuan" :min="0" :precision="2" :disabled="readonly"/></div><div><label>报价有效期</label><el-date-picker v-model="editor.validUntil" type="datetime" value-format="x" :disabled="readonly" placeholder="选择有效期"/></div><div><label>预计交货日期</label><el-date-picker v-model="editor.deliveryDate" type="date" value-format="x" :disabled="readonly" placeholder="选择日期"/></div><div class="remark"><label>报价说明</label><el-input v-model="editor.remark" type="textarea" maxlength="500" show-word-limit :disabled="readonly"/></div><p class="total">报价合计 <strong>¥{{ formatMoney(totalCents) }}</strong></p><div class="commands"><el-button v-if="canEdit" :loading="saving" @click="save()">{{ editor.id ? '保存修改' : '保存报价草稿' }}</el-button><el-button v-if="canSubmit" type="primary" :loading="saving" @click="submit">提交报价</el-button><el-button v-if="canWithdraw" type="danger" plain :loading="saving" @click="withdraw">撤回报价</el-button></div></section>
      <el-alert v-if="editor.id && messageFailed" title="沟通记录加载失败，请单独刷新；不会显示其他店铺或报价的历史会话。" type="error" :closable="false"/>
      <InquiryConversation v-if="editor.id" :messages="messages" :loading="messageBusy" :sending="sending" :disabled="!quoteCapabilities.canMessage" side="SUPPLIER" @refresh="loadMessages()" @send="sendMessage" />
    </div>
  </div>
</template>

<style scoped>
.quote-page{margin:30px auto 56px}.quote-page>a{color:#167664}.heading{display:flex;justify-content:space-between;align-items:center;padding:26px;margin:18px 0}.heading h1{margin:8px 0}.heading p,.heading small{color:#64748b}.heading>div:last-child{display:flex;align-items:center;gap:12px}.heading label,.quote-form label{display:block;color:#64748b;font-size:12px}.lines,.quote-form{padding:26px;margin:16px 0}.lines h2{margin-top:0}.line{display:grid;grid-template-columns:minmax(160px,.8fr) minmax(230px,1.2fr) 145px 160px;gap:12px;align-items:center;padding:16px 0;border-top:1px solid #edf1f0}.line p{margin:6px 0 0;color:#64748b;font-size:13px}.amount-input{display:grid;gap:5px}.amount-input small{color:#64748b}.quote-form{display:grid;grid-template-columns:repeat(3,1fr);gap:18px}.quote-form>div:not(.commands){display:grid;gap:7px}.quote-form .remark{grid-column:1/-1}.total{grid-column:1/-1;text-align:right;font-size:18px}.total strong{color:#df421f;font-size:26px}.commands{grid-column:1/-1;display:flex;justify-content:flex-end;gap:10px}@media(max-width:1100px){.line{grid-template-columns:1fr 1fr}.quote-form{grid-template-columns:1fr 1fr}}@media(max-width:650px){.heading{align-items:flex-start;flex-direction:column}.line,.quote-form{grid-template-columns:1fr}}
</style>
