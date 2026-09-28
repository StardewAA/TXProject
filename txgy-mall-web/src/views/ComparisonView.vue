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
    <section class="comparison-hero">
      <div class="comparison-heading">
        <div>
          <p class="eyebrow">MATERIAL SELECTION · 材料选型</p>
          <h1>跨店商品对比</h1>
          <p class="hero-copy">把不同店铺的具体规格放在同一张表中，集中查看价格、参数与交付信息。</p>
        </div>
        <MallLink class="continue-link" to="/goods">继续选择商品 →</MallLink>
      </div>
      <div class="comparison-steps" aria-label="商品对比流程">
        <div class="step active"><b>01</b><span>选择具体规格<small>最多加入 4 项</small></span></div>
        <div class="step" :class="{ active: selection.count >= 2 }"><b>02</b><span>查看参数差异<small>支持仅看差异</small></span></div>
        <div class="step" :class="{ active: !!result && !dirty }"><b>03</b><span>核验采购费用<small>按地址独立估算</small></span></div>
        <strong class="selection-count">{{ selection.count }}<small>/ 4 项</small></strong>
      </div>
    </section>
    <section class="panel comparison-controls">
      <div class="section-title"><div><span>对比条件</span><h2>设置本次采购口径</h2></div><p>{{ selection.signedIn ? '已登录，清单可与移动端同步' : '游客清单仅保存在当前浏览器' }}</p></div>
      <div v-if="selection.hasAnonymousMerge" class="merge-notice">本机还有 {{ selection.anonymousIds.length }} 个游客规格，尚未写入账号清单。<el-button @click="openMerge">选择合并临时清单</el-button></div>
      <p v-if="selection.error" role="alert" class="error">{{ selection.error }}。请刷新清单后确认，不会覆盖另一端修改。</p>
      <!-- Element Plus 2.14 sets input-number aria-disabled on mount only. Remount on real disabled transitions so native and ARIA state agree. -->
      <div class="control-row control-grid"><label><span>各 SKU 采购份数</span><el-input-number :key="String(busy || !selection.ready)" v-model="quantity" :min="1" :max="999" :precision="0" :disabled="busy || !selection.ready" /></label><label><span>比较偏好</span><select v-model="preference" :disabled="busy || !selection.ready"><option value="PRICE">价格优先</option><option value="DELIVERY">交付优先</option><option value="BALANCED">综合考虑</option></select></label><label class="difference-switch"><span>参数显示</span><el-checkbox v-model="differencesOnly">仅看差异</el-checkbox></label><div class="control-actions"><el-button type="primary" :disabled="busy || !selection.ready || !selection.count" @click="updateOptions">应用数量与偏好</el-button><el-button :loading="selection.loading" :disabled="selection.saving" @click="refresh">刷新清单与数据</el-button></div></div>
      <p class="muted control-hint">每列均按该 SKU 的销售份数展示；包装和计量单位未标准化时，不按商品名称直接认定同款。</p>
      <p v-if="dirty" role="status" class="merge-notice">数量或偏好尚未应用，请点击“应用数量与偏好”重新比较；旧估价和加购入口暂不显示。</p>
      <button v-if="selection.count" class="clear-link" type="button" :disabled="busy || !selection.ready" @click="openClear">清空当前对比清单</button>
    </section>
    <div v-if="loading" class="panel loading-box"><el-skeleton :rows="6" animated /></div>
    <section v-else-if="failure" class="panel state" role="alert"><p>{{ failure }}</p><el-button @click="calculate">重试读取对比</el-button></section>
    <section v-else-if="!selection.count" class="panel state empty-state"><span class="empty-icon">＋</span><h2>先选 2—4 个明确规格</h2><p>进入商品详情选择实际规格，再加入对比清单；系统不会自动使用最低价规格。</p><MallLink to="/goods">去选择材料</MallLink></section>
    <template v-else-if="result && !dirty">
      <section class="result-notice" data-testid="comparison-recommendation"><div class="recommendation-badge">选型提示</div><div><b>推荐结果：暂不推荐</b><p>{{ result.recommendationReason || '当前资料尚不足以生成可靠推荐' }}</p><p v-for="warning in result.warnings" :key="warning">{{ warning }}</p><small>规则 {{ result.strategyVersion }} · 数据时间 {{ timeText(result.calculatedAt) }}</small></div></section>
      <section class="comparison-section"><div class="section-title"><div><span>参数对照</span><h2>商品规格一览</h2></div><p>横向滚动可查看全部商品</p></div><div class="comparison-table-wrap panel"><table class="comparison-table"><thead><tr><th scope="col"><span class="table-corner">比较项目</span></th><th v-for="(item, index) in result.items" :key="String(item.skuId)" scope="col"><span class="product-index">0{{ index + 1 }}</span><img v-if="item.available && item.picUrl" :src="productImage(item.spuId, item.picUrl)" alt="商品图片" /><div v-else class="product-placeholder">材料</div><MallLink v-if="item.available && item.spuId" :to="`/goods/${item.spuId}`">{{ item.spuName }}</MallLink><strong v-else>商品不可用</strong><small>SKU {{ item.skuId }}</small><p v-if="!item.available" class="error">{{ item.unavailableReason || '当前不可查看或购买' }}</p><button type="button" class="remove-item" :disabled="busy || !selection.ready" @click="remove(item.skuId)">移除</button></th></tr></thead><tbody><tr v-for="row in rows" :key="row.label" :class="{ difference: row.different }"><th scope="row">{{ row.label }}</th><td v-for="(value, index) in row.values" :key="index">{{ value }}</td></tr><tr class="purchase-row"><th scope="row">采购操作</th><td v-for="item in result.items" :key="String(item.skuId)"><el-button type="primary" :disabled="!item.available || busy || !selection.ready || !!addingSku" :loading="addingSku === String(item.skuId)" @click="addToCart(item)">加入购物车</el-button><p class="muted">结算时核验价格与库存</p></td></tr></tbody></table></div></section>
      <section class="panel quote-panel" data-testid="comparison-quote"><div class="section-title"><div><span>费用核验</span><h2>按收货地址估算费用</h2></div><p>各 SKU 按独立结算规则估算</p></div><p class="quote-intro">估算不代表多店合单总额，不使用积分或优惠券，也不据此承诺最低价、锁价或交期。</p><template v-if="selection.signedIn"><div class="control-row quote-actions"><el-button :loading="addressLoading" :disabled="!selection.ready" @click="loadAddresses">读取本人收货地址</el-button><select v-model="addressId" aria-label="估算收货地址"><option value="">未选择地址，运费与到手总额待确认</option><option v-for="address in addresses" :key="String(address.id)" :value="String(address.id)">{{ address.areaName }} {{ address.detailAddress }}</option></select><el-button type="primary" :loading="quoteLoading" :disabled="!selection.ready || !result" @click="estimate">估算费用</el-button><MallLink to="/member/addresses">管理地址</MallLink></div><p v-if="addressError" class="error" role="alert">{{ addressError }}</p></template><el-button v-else @click="estimate">登录后估算费用</el-button><p v-if="quoteFailure" class="error" role="alert">{{ quoteFailure }}</p><div v-if="quote" class="quote-meta"><p>估算时间 {{ timeText(quote.calculatedAt) }} · 结算口径 CHECKOUT_ESTIMATE</p><p v-for="warning in quote.warnings" :key="warning">{{ warning }}</p></div><div v-if="quote" class="quote-grid"><article v-for="item in quote.items" :key="String(item.skuId)"><span>费用明细</span><h3>SKU {{ item.skuId }}</h3><p>{{ item.message }}</p><dl><dt>商品金额</dt><dd>{{ comparisonMoney(item.goodsAmount) }}</dd><dt>优惠金额</dt><dd>{{ comparisonMoney(item.discountAmount) }}</dd><dt>运费</dt><dd>{{ comparisonMoney(item.shippingFee) }}</dd><dt>预计支付</dt><dd>{{ item.amountsComplete ? comparisonMoney(item.totalAmount) : '待确认（费用不完整）' }}</dd></dl></article></div></section>
    </template>
    <el-dialog v-model="mergeOpen" title="合并游客临时清单" width="480px"><p>将本机当前商城的 {{ selection.anonymousIds.length }} 个游客 SKU 合并到此账号。总数最多 4 个，超过时请先调整清单；不会自动替换已有规格。</p><p>只有确认后才会同步至该账号的 PC 和 H5 清单。</p><template #footer><el-button @click="mergeOpen = false">暂不合并</el-button><el-button type="primary" :loading="selection.saving" :disabled="!selection.ready" @click="confirmMerge">确认合并</el-button></template></el-dialog>
    <el-dialog v-model="clearOpen" title="清空对比清单" width="440px"><p>确定清空当前{{ selection.signedIn ? '账号' : '浏览器' }}的对比清单？商品和购物车不会受到影响。</p><template #footer><el-button @click="clearOpen = false">取消</el-button><el-button type="primary" :loading="selection.saving" :disabled="!selection.ready" @click="confirmClear">确认清空</el-button></template></el-dialog>
  </div>
</template>

<style scoped>
.comparison-page{--compare-navy:#16313a;--compare-green:#0f806f;--compare-mint:#e9f4f1;--compare-warm:#fff7e8;padding-top:28px;padding-bottom:64px}.comparison-hero{overflow:hidden;margin-bottom:22px;border-radius:18px;background:linear-gradient(128deg,#102e38 0%,#174953 60%,#0f806f 100%);color:#fff;box-shadow:0 18px 45px #143a3429}.comparison-heading{display:flex;align-items:flex-end;justify-content:space-between;gap:32px;padding:34px 38px 25px}.comparison-heading h1{margin:7px 0 9px;font-size:34px;letter-spacing:1px}.hero-copy{max-width:700px;margin:0;color:#d6e7e4;line-height:1.7}.eyebrow{margin:0;color:#8ed7c9;font-size:11px;letter-spacing:2.5px}.continue-link{flex:none;padding:12px 18px;border:1px solid #ffffff4f;border-radius:9px;background:#ffffff13;color:#fff;font-weight:700}.comparison-steps{display:flex;align-items:center;gap:0;padding:18px 38px;background:#071d245c;border-top:1px solid #ffffff19}.step{position:relative;display:flex;align-items:center;gap:11px;min-width:205px;color:#9cb6b7}.step:not(:last-of-type)::after{content:'';width:38px;height:1px;margin:0 20px;background:#ffffff2b}.step b{display:grid;place-items:center;width:36px;height:36px;border:1px solid #ffffff35;border-radius:50%;font-size:12px}.step span{display:flex;flex-direction:column;font-size:14px;font-weight:700}.step small{margin-top:3px;color:#91aaab;font-size:11px;font-weight:400}.step.active{color:#fff}.step.active b{border-color:#80d8c8;background:#0d8874}.selection-count{margin-left:auto;font-size:28px}.selection-count small{margin-left:5px;color:#b9d0cf;font-size:12px}.panel{border:1px solid #dce7e4;border-radius:14px;background:#fff;box-shadow:0 8px 24px #183e360d}.comparison-controls,.quote-panel{padding:27px 30px;margin-bottom:24px}.section-title{display:flex;align-items:flex-end;justify-content:space-between;gap:24px;margin-bottom:22px}.section-title span,.quote-grid article>span{color:var(--compare-green);font-size:11px;font-weight:800;letter-spacing:1.7px}.section-title h2{margin:5px 0 0;font-size:21px;color:var(--compare-navy)}.section-title>p{margin:0;color:var(--muted);font-size:13px}.control-row{display:flex;align-items:center;gap:16px;flex-wrap:wrap}.control-grid{display:grid;grid-template-columns:minmax(210px,.75fr) minmax(210px,.9fr) minmax(160px,.55fr) auto;align-items:end;padding:18px;border-radius:12px;background:#f6f9f8}.control-row label{display:flex;gap:9px}.control-grid label{align-items:stretch;flex-direction:column;color:#39544f;font-size:13px;font-weight:700}.control-row select{width:100%;max-width:520px;min-height:40px;padding:9px 12px;border:1px solid #cddbd7;border-radius:6px;background:white;color:var(--ink)}.difference-switch{justify-content:center}.difference-switch .el-checkbox{height:40px}.control-actions{display:flex;gap:8px}.control-hint{margin:14px 0 0}.muted,small{color:var(--muted);font-size:13px}.error{color:#ad3428}.merge-notice,.result-notice{padding:16px 20px;border:1px solid #c8ddd8;background:#eff8f6;margin-bottom:20px;line-height:1.7}.merge-notice{border-radius:9px}.merge-notice .el-button{margin-left:16px}.clear-link{margin-top:16px;padding:0;border:0;background:transparent;color:#9d493d;cursor:pointer}.state{text-align:center;padding:70px 25px}.empty-state{background:linear-gradient(180deg,#fff,#f7faf9)}.empty-state .empty-icon{display:grid;place-items:center;width:58px;height:58px;margin:0 auto 18px;border-radius:50%;background:var(--compare-mint);color:var(--compare-green);font-size:30px}.empty-state h2{margin:0 0 10px}.empty-state p{color:var(--muted)}.empty-state a{display:inline-block;margin-top:12px;padding:11px 20px;border-radius:7px;background:var(--compare-green);color:#fff;font-weight:700}.result-notice{display:flex;gap:18px;align-items:flex-start;padding:20px 24px;border-radius:13px;background:linear-gradient(110deg,#edf8f5,#f8fbfa)}.result-notice p{margin:4px 0;color:#46615b}.recommendation-badge{flex:none;padding:7px 10px;border-radius:6px;background:var(--compare-green);color:#fff;font-size:12px;font-weight:700}.comparison-section{margin-top:30px}.comparison-table-wrap{overflow-x:auto;margin-bottom:27px;border-radius:14px}.comparison-table{width:100%;table-layout:fixed;border-collapse:separate;border-spacing:0;min-width:800px}.comparison-table th,.comparison-table td{padding:17px 20px;text-align:left;vertical-align:top;border-bottom:1px solid #e2ebe8;border-right:1px solid #e2ebe8;line-height:1.6;word-break:break-word}.comparison-table th:first-child{position:sticky;left:0;z-index:2;width:165px;background:#f3f7f6;color:#36524c}.comparison-table thead th{position:relative;background:linear-gradient(180deg,#fbfdfc,#f3f7f6);padding-top:24px}.comparison-table thead th:first-child{z-index:3;background:var(--compare-navy);color:#fff}.comparison-table thead img,.product-placeholder{display:block;width:128px;height:104px;object-fit:contain;margin:0 0 13px;border-radius:9px;background:#fff}.product-placeholder{display:grid;place-items:center;background:linear-gradient(145deg,#e8f4f0,#d9eae6);color:#6f9690;font-size:17px;font-weight:800;letter-spacing:3px}.comparison-table thead a,.comparison-table thead strong,.comparison-table thead small{display:block}.comparison-table thead a,.comparison-table thead strong{min-height:46px;color:var(--compare-navy);font-weight:800}.product-index{position:absolute;right:14px;top:12px;color:#96aaa6;font-size:11px;letter-spacing:1px}.difference{background:var(--compare-warm)}.difference th:first-child{background:#fff0cc}.remove-item{border:0;padding:7px 0;background:transparent;color:#a64539;cursor:pointer}.purchase-row td{background:#fbfdfc}.purchase-row th:first-child{background:#eaf3f0}.quote-intro{margin-top:-8px;color:var(--muted);line-height:1.7}.quote-actions{padding:16px 0;border-top:1px solid #e5edeb;border-bottom:1px solid #e5edeb}.quote-actions select{flex:1;min-width:280px}.quote-meta{margin-top:16px;padding:12px 16px;border-radius:8px;background:#f5f8f7;color:#526761}.quote-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(235px,1fr));gap:15px;margin-top:20px}.quote-grid article{padding:20px;border:1px solid #dce7e4;border-radius:11px;background:linear-gradient(145deg,#fff,#f7faf9)}.quote-grid article h3{margin:6px 0;color:var(--compare-navy)}.quote-grid dl{display:grid;grid-template-columns:1fr 1.5fr;gap:10px;padding-top:13px;border-top:1px solid #e2eae8;font-size:14px}.quote-grid dd{margin:0;text-align:right;font-weight:700}.quote-grid dd:last-child{color:var(--compare-green);font-size:16px}@media(max-width:960px){.control-grid{grid-template-columns:1fr 1fr}.comparison-steps{overflow-x:auto}.selection-count{display:none}.step{min-width:190px}.comparison-heading{align-items:flex-start}.comparison-heading,.comparison-steps{padding-left:24px;padding-right:24px}}@media(max-width:640px){.comparison-heading{flex-direction:column}.control-grid{grid-template-columns:1fr}.section-title{align-items:flex-start;flex-direction:column}.comparison-controls,.quote-panel{padding:20px}}
</style>
