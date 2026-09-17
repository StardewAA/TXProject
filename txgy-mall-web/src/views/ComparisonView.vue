<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { comparisonApi } from '@/api/comparison'
import { memberApi } from '@/api/mall'
import { useComparisonStore } from '@/stores/comparison'
import { useCartStore } from '@/stores/cart'
import { comparisonQuantity } from '@/utils/comparison-selection'
import { assertComparisonQuote, assertComparisonResult, comparisonMoney, comparisonRows } from '@/utils/comparison-view'
import { productImage } from '@/utils/format'
import type { ComparisonItem, ComparisonPreference, ComparisonQuote, ComparisonResult } from '@/types/comparison'
import type { Address } from '@/types/mall'

const selection = useComparisonStore(), cart = useCartStore(), route = useRoute(), router = useRouter()
const result = ref<ComparisonResult | null>(null), quote = ref<ComparisonQuote | null>(null)
const loading = ref(false), quoteLoading = ref(false), failure = ref(''), quoteFailure = ref('')
const quantity = ref(selection.quantity), preference = ref<ComparisonPreference>(selection.preference), differencesOnly = ref(false)
const addresses = ref<Address[]>([]), addressId = ref(''), addressLoading = ref(false), addressError = ref('')
const mergeOpen = ref(false), clearOpen = ref(false), addingSku = ref('')
let active = true, generation = 0, quoteGeneration = 0, addressGeneration = 0, mergeOwner = ''
let pageOwner = selection.ownerKey()
const signature = () => JSON.stringify([selection.ownerKey(), selection.ids, selection.quantity, selection.preference])
const rows = computed(() => comparisonRows(result.value?.items || []).filter(row => !differencesOnly.value || row.different))
const busy = computed(() => selection.loading || selection.saving)
const dirty = computed(() => quantity.value !== selection.quantity || preference.value !== selection.preference)
const timeText = (value: number | undefined) => value && Number.isFinite(value) ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '待确认'

function invalidate() {
  generation++; quoteGeneration++; addressGeneration++
  result.value = null; quote.value = null; addresses.value = []; addressId.value = ''
  loading.value = false; quoteLoading.value = false; addressLoading.value = false
  failure.value = ''; quoteFailure.value = ''; addressError.value = ''; mergeOpen.value = false; clearOpen.value = false; addingSku.value = ''
}
async function calculate() {
  const currentSignature = signature(), request = ++generation
  result.value = null; quote.value = null; quoteGeneration++; failure.value = ''; quoteFailure.value = ''; quoteLoading.value = false
  if (!selection.ids.length || !selection.ready) { loading.value = false; return }
  const ids = [...selection.ids], amount = selection.quantity, preferred = selection.preference
  const current = () => active && request === generation && currentSignature === signature()
  loading.value = true
  try {
    const response = await comparisonApi.calculate({ skuIds: ids, quantity: amount, preference: preferred })
    if (current()) result.value = assertComparisonResult(response, ids, amount, preferred)
  } catch (reason) { if (current()) failure.value = reason instanceof Error ? reason.message : '对比读取失败，请重试' }
  finally { if (current()) loading.value = false }
}
async function refresh() { await selection.refresh(); if (active) await calculate() }
async function updateOptions() {
  const owner = selection.ownerKey()
  try { await selection.replace([...selection.ids], comparisonQuantity(quantity.value), preference.value); if (owner === selection.ownerKey()) await calculate() }
  catch (reason) { if (owner === selection.ownerKey()) ElMessage.warning(reason instanceof Error ? reason.message : '保存失败') }
}
async function remove(id: string | number) {
  const owner = selection.ownerKey()
  try { await selection.remove(String(id)) } catch (reason) { if (owner === selection.ownerKey()) ElMessage.warning(reason instanceof Error ? reason.message : '移除失败') }
}
function openMerge() { mergeOwner = selection.ownerKey(); mergeOpen.value = true }
function openClear() { mergeOwner = selection.ownerKey(); clearOpen.value = true }
async function confirmClear() {
  if (mergeOwner !== selection.ownerKey()) { clearOpen.value = false; return }
  try { await selection.replace([]); if (active && mergeOwner === selection.ownerKey()) { clearOpen.value = false; ElMessage.success('对比清单已清空') } }
  catch (reason) { if (active && mergeOwner === selection.ownerKey()) ElMessage.warning(reason instanceof Error ? reason.message : '清空失败') }
}
async function confirmMerge() {
  if (mergeOwner !== selection.ownerKey()) { mergeOpen.value = false; return }
  try { await selection.mergeAnonymous(); if (active && mergeOwner === selection.ownerKey()) { mergeOpen.value = false; ElMessage.success('临时清单已合并，同账号 PC/H5 刷新后同步') } }
  catch (reason) { if (active && mergeOwner === selection.ownerKey()) ElMessage.warning(reason instanceof Error ? reason.message : '合并失败') }
}
async function loadAddresses() {
  const owner = selection.ownerKey(), request = ++addressGeneration
  if (!selection.signedIn || !selection.ready) return
  addressLoading.value = true; addressError.value = ''
  try { const data = await memberApi.getAddresses(); if (active && owner === selection.ownerKey() && request === addressGeneration) addresses.value = data }
  catch { if (active && owner === selection.ownerKey() && request === addressGeneration) addressError.value = '收货地址读取失败，请重试' }
  finally { if (active && owner === selection.ownerKey() && request === addressGeneration) addressLoading.value = false }
}
async function estimate() {
  if (!selection.signedIn) return router.push({ name: 'login', query: { redirect: route.fullPath } })
  if (!selection.ready || !result.value || quoteLoading.value || dirty.value) return
  const currentSignature = signature(), selectedAddress = addressId.value, ids = [...selection.ids], amount = selection.quantity, request = ++quoteGeneration
  quote.value = null; quoteFailure.value = ''; quoteLoading.value = true
  const current = () => active && request === quoteGeneration && currentSignature === signature() && selectedAddress === addressId.value
  try {
    const response = await comparisonApi.quote({ skuIds: ids, quantity: amount, ...(selectedAddress ? { addressId: selectedAddress } : {}) })
    if (current()) quote.value = assertComparisonQuote(response, ids, amount, selectedAddress)
  } catch (reason) { if (current()) quoteFailure.value = reason instanceof Error ? reason.message : '费用估算失败，请重试' }
  finally { if (current()) quoteLoading.value = false }
}
async function addToCart(item: ComparisonItem) {
  if (!item.available || addingSku.value || !selection.ready || dirty.value) return
  if (!selection.signedIn) return router.push({ name: 'login', query: { redirect: route.fullPath } })
  const owner = selection.ownerKey(); addingSku.value = String(item.skuId)
  try { await cart.add(item.skuId, selection.quantity); if (active && owner === selection.ownerKey()) ElMessage.success('已加入购物车，结算时将重新核验价格与库存') }
  catch { /* Existing cart API reports the actual validation error. */ }
  finally { if (active && owner === selection.ownerKey()) addingSku.value = '' }
}
watch(() => [selection.ids.join(','), selection.quantity, selection.preference, selection.ready], () => {
  quantity.value = selection.quantity; preference.value = selection.preference; void calculate()
})
watch(addressId, () => { quoteGeneration++; quote.value = null; quoteLoading.value = false; quoteFailure.value = '' })
watch([quantity, preference], () => { quoteGeneration++; quote.value = null; quoteLoading.value = false; quoteFailure.value = '' })
function contextChanged() { if (pageOwner !== selection.ownerKey()) { pageOwner = selection.ownerKey(); invalidate() } }
window.addEventListener('mall:tenant-context-updated', contextChanged)
window.addEventListener('mall:token-updated', contextChanged)
onMounted(refresh)
onBeforeUnmount(() => { active = false; invalidate(); window.removeEventListener('mall:tenant-context-updated', contextChanged); window.removeEventListener('mall:token-updated', contextChanged) })
</script>

<template>
  <div class="container page-section comparison-page" data-testid="comparison-page">
    <div class="comparison-heading"><div><p class="eyebrow">材料选型 / 当前商城</p><h1>跨店商品对比</h1></div><MallLink to="/goods">继续选择商品 →</MallLink></div>
    <section class="panel comparison-controls">
      <p>{{ selection.signedIn ? '账号清单：与同租户、同账号的移动端共用；刷新可读取另一端更新。' : '游客临时清单：仅保存在当前浏览器、当前商城。登录后需明确确认才能合并。' }}</p>
      <div v-if="selection.hasAnonymousMerge" class="merge-notice">本机还有 {{ selection.anonymousIds.length }} 个游客规格，尚未写入账号清单。<el-button @click="openMerge">选择合并临时清单</el-button></div>
      <p v-if="selection.error" role="alert" class="error">{{ selection.error }}。请刷新清单后确认，不会覆盖另一端修改。</p>
      <!-- Element Plus 2.14 sets input-number aria-disabled on mount only. Remount on real disabled transitions so native and ARIA state agree. -->
      <div class="control-row"><label>各 SKU 采购份数 <el-input-number :key="String(busy || !selection.ready)" v-model="quantity" :min="1" :max="999" :precision="0" :disabled="busy || !selection.ready" /></label><label>比较偏好 <select v-model="preference" :disabled="busy || !selection.ready"><option value="PRICE">价格优先</option><option value="DELIVERY">交付优先</option><option value="BALANCED">综合考虑</option></select></label><el-button :disabled="busy || !selection.ready || !selection.count" @click="updateOptions">应用数量与偏好</el-button><el-button :loading="selection.loading" :disabled="selection.saving" @click="refresh">刷新清单与数据</el-button><el-checkbox v-model="differencesOnly">仅看差异</el-checkbox></div>
      <p class="muted">每列均按该 SKU 的销售份数；包装、计量单位尚未标准化时，不能将一份视为相同数量，也不按商品名称认定同款。</p>
      <p v-if="dirty" role="status" class="merge-notice">数量或偏好尚未应用，请点击“应用数量与偏好”重新比较；旧估价和加购入口暂不显示。</p>
      <el-button v-if="selection.count" :disabled="busy || !selection.ready" @click="openClear">清空对比清单</el-button>
    </section>
    <div v-if="loading" class="panel loading-box"><el-skeleton :rows="6" animated /></div>
    <section v-else-if="failure" class="panel state" role="alert"><p>{{ failure }}</p><el-button @click="calculate">重试读取对比</el-button></section>
    <section v-else-if="!selection.count" class="panel state"><h2>先选 2—4 个明确规格</h2><p>从商品详情选择实际规格，再点击“加入对比”。不会自动使用最低价规格。</p><MallLink to="/goods">去选择材料</MallLink></section>
    <template v-else-if="result && !dirty">
      <section class="result-notice" data-testid="comparison-recommendation"><b>推荐结果：暂不推荐</b><p>{{ result.recommendationReason || '当前资料尚不足以生成可靠推荐' }}</p><p v-for="warning in result.warnings" :key="warning">{{ warning }}</p><small>规则 {{ result.strategyVersion }} · 数据时间 {{ timeText(result.calculatedAt) }}</small></section>
      <div class="comparison-table-wrap panel"><table class="comparison-table"><thead><tr><th scope="col">比较项目</th><th v-for="item in result.items" :key="String(item.skuId)" scope="col"><img v-if="item.available && item.picUrl" :src="productImage(item.spuId, item.picUrl)" alt="商品图片" /><MallLink v-if="item.available && item.spuId" :to="`/goods/${item.spuId}`">{{ item.spuName }}</MallLink><strong v-else>商品不可用</strong><small>SKU {{ item.skuId }}</small><p v-if="!item.available" class="error">{{ item.unavailableReason || '当前不可查看或购买' }}</p><button type="button" class="remove-item" :disabled="busy || !selection.ready" @click="remove(item.skuId)">移除</button></th></tr></thead><tbody><tr v-for="row in rows" :key="row.label" :class="{ difference: row.different }"><th scope="row">{{ row.label }}</th><td v-for="(value, index) in row.values" :key="index">{{ value }}</td></tr><tr><th scope="row">采购操作</th><td v-for="item in result.items" :key="String(item.skuId)"><el-button :disabled="!item.available || busy || !selection.ready || !!addingSku" :loading="addingSku === String(item.skuId)" @click="addToCart(item)">加入购物车</el-button><p class="muted">结算与 ERP 最终核验</p></td></tr></tbody></table></div>
      <section class="panel quote-panel" data-testid="comparison-quote"><h2>按收货地址估算费用</h2><p>分别按每个 SKU 独立结算规则估算，不代表多店合单总额；不使用积分或优惠券，不据此承诺最低价、锁价或交期。</p><template v-if="selection.signedIn"><div class="control-row"><el-button :loading="addressLoading" :disabled="!selection.ready" @click="loadAddresses">读取本人收货地址</el-button><select v-model="addressId" aria-label="估算收货地址"><option value="">未选择地址，运费与到手总额待确认</option><option v-for="address in addresses" :key="String(address.id)" :value="String(address.id)">{{ address.areaName }} {{ address.detailAddress }}</option></select><el-button type="primary" :loading="quoteLoading" :disabled="!selection.ready || !result" @click="estimate">估算费用</el-button><MallLink to="/member/addresses">管理地址</MallLink></div><p v-if="addressError" class="error" role="alert">{{ addressError }}</p></template><el-button v-else @click="estimate">登录后估算费用</el-button><p v-if="quoteFailure" class="error" role="alert">{{ quoteFailure }}</p><div v-if="quote" class="quote-meta"><p>估算时间 {{ timeText(quote.calculatedAt) }} · 结算口径 CHECKOUT_ESTIMATE</p><p v-for="warning in quote.warnings" :key="warning">{{ warning }}</p></div><div v-if="quote" class="quote-grid"><article v-for="item in quote.items" :key="String(item.skuId)"><h3>SKU {{ item.skuId }}</h3><p>{{ item.message }}</p><dl><dt>商品金额</dt><dd>{{ comparisonMoney(item.goodsAmount) }}</dd><dt>优惠金额</dt><dd>{{ comparisonMoney(item.discountAmount) }}</dd><dt>运费</dt><dd>{{ comparisonMoney(item.shippingFee) }}</dd><dt>预计支付</dt><dd>{{ item.amountsComplete ? comparisonMoney(item.totalAmount) : '待确认（费用不完整）' }}</dd></dl></article></div></section>
    </template>
    <el-dialog v-model="mergeOpen" title="合并游客临时清单" width="480px"><p>将本机当前商城的 {{ selection.anonymousIds.length }} 个游客 SKU 合并到此账号。总数最多 4 个，超过时请先调整清单；不会自动替换已有规格。</p><p>只有确认后才会同步至该账号的 PC 和 H5 清单。</p><template #footer><el-button @click="mergeOpen = false">暂不合并</el-button><el-button type="primary" :loading="selection.saving" :disabled="!selection.ready" @click="confirmMerge">确认合并</el-button></template></el-dialog>
    <el-dialog v-model="clearOpen" title="清空对比清单" width="440px"><p>确定清空当前{{ selection.signedIn ? '账号' : '浏览器' }}的对比清单？商品和购物车不会受到影响。</p><template #footer><el-button @click="clearOpen = false">取消</el-button><el-button type="primary" :loading="selection.saving" :disabled="!selection.ready" @click="confirmClear">确认清空</el-button></template></el-dialog>
  </div>
</template>

<style scoped>
.comparison-heading,.control-row{display:flex;align-items:center;gap:18px;flex-wrap:wrap}.comparison-heading{justify-content:space-between;margin-bottom:20px}.comparison-heading h1{margin:8px 0;font-size:28px}.eyebrow{color:var(--brand);font-size:12px;letter-spacing:2px}.comparison-controls,.quote-panel{padding:24px;margin-bottom:20px}.comparison-controls p,.quote-panel p{line-height:1.7}.control-row label{display:flex;align-items:center;gap:10px}.control-row select{max-width:480px;padding:10px;border:1px solid var(--line);background:white;color:var(--ink)}.muted,small{color:var(--muted);font-size:13px}.error{color:#ad3428}.merge-notice,.result-notice{padding:16px 20px;border:1px solid #cbdcda;background:#eff8f6;margin-bottom:20px;line-height:1.7}.merge-notice .el-button{margin-left:16px}.state{text-align:center;padding:70px 25px}.comparison-table-wrap{overflow-x:auto;margin-bottom:24px}.comparison-table{width:100%;table-layout:fixed;border-collapse:collapse;min-width:740px}.comparison-table th,.comparison-table td{padding:18px;text-align:left;vertical-align:top;border-bottom:1px solid var(--line);border-right:1px solid var(--line);line-height:1.6;word-break:break-word}.comparison-table th:first-child{width:180px}.comparison-table thead th{background:#fafcfc}.comparison-table thead img{display:block;width:120px;height:110px;object-fit:contain;margin-bottom:12px}.comparison-table thead a,.comparison-table thead strong,.comparison-table thead small{display:block}.difference{background:#fffaf0}.remove-item{border:0;padding:5px 0;background:transparent;color:#ad3428;cursor:pointer}.quote-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(230px,1fr));gap:16px;margin-top:20px}.quote-grid article{padding:16px;border:1px solid var(--line);background:#fbfcfc}.quote-grid dl{display:grid;grid-template-columns:1fr 1.5fr;gap:10px;font-size:14px}.quote-grid dd{margin:0;text-align:right;font-weight:600}
</style>
