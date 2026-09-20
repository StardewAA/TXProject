<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { checkoutDraft } from '@/utils/checkout-draft'
import { openCheckoutIntent, type CheckoutIntent } from '@/utils/checkout-intent.mjs'
import { getTenantContext, isCurrentTenantEpoch } from '@/utils/tenant-context'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'

import { memberApi, systemApi, tradeApi, type AreaNode, type SettlementItem } from '@/api/mall'
import EmptyState from '@/components/EmptyState.vue'
import ServiceEntry from '@/components/ServiceEntry.vue'
import AddressMapDialog from '@/components/AddressMapDialog.vue'
import type { AddressMapSelection } from '@/components/AddressMapDialog.vue'
import { useCartStore } from '@/stores/cart'
import { useAccountOperation } from '@/composables/useAccountOperation'
import type { Address, Identifier } from '@/types/mall'
import { formatMoney, normalizeImage, propertyText } from '@/utils/format'
import { addressLocationText, manualAddressPayload } from '@/utils/fulfillment'

interface SettlementView {
  items?: Array<Record<string, unknown>>
  price?: Record<string, number>
  address?: Address
  coupons?: Array<{ id: Identifier; name: string; match: boolean; mismatchReason?: string }>
}

const router = useRouter()
const ownerEpoch = getTenantContext().epoch
const operation = useAccountOperation()
const cartStore = useCartStore()
const items = ref<SettlementItem[]>([])
const addresses = ref<Address[]>([])
const addressId = ref<Identifier>()
const couponId = ref<Identifier>()
const remark = ref('')
const settlement = ref<SettlementView | null>(null)
const loading = ref(false)
const submitting = ref(false)
const submitted = ref(false)
const needsVerification = ref(false)
const recoveryError = ref('')
let checkoutIntent: CheckoutIntent | undefined
const addressDialog = ref(false)
const addressMapDialog = ref(false)
const savingAddress = ref(false)
const areaTree = ref<AreaNode[]>([])
const addressForm = reactive<Address>({ id: 0, name: '', mobile: '', areaId: undefined, detailAddress: '', defaultStatus: false })
const price = computed(() => settlement.value?.price || {})
const payablePrice = computed(() => price.value.payPrice !== undefined
  ? Number(price.value.payPrice)
  : 0)
let settlementRequest = 0
function discardStaleDraft() {
  if (isCurrentTenantEpoch(ownerEpoch)) return
  settlementRequest++; items.value = []; addresses.value = []; settlement.value = null
  addressDialog.value = false; addressMapDialog.value = false; addressId.value = undefined; couponId.value = undefined; remark.value = ''
  submitted.value = false; needsVerification.value = false
  recoveryError.value = ''; checkoutIntent = undefined
  Object.assign(addressForm, { id:0, name:'', mobile:'', areaId:undefined, detailAddress:'', defaultStatus:false,
    longitude:null, latitude:null, coordSystem:null, locationSource:null, locationPrecision:null, locationConfirmed:false })
}
window.addEventListener('mall:token-updated', discardStaleDraft)
onBeforeUnmount(() => window.removeEventListener('mall:token-updated', discardStaleDraft))

async function settle() {
  if (!operation.current()) { discardStaleDraft(); return }
  if (!items.value.length) return
  const request = ++settlementRequest
  loading.value = true
  try {
    const result = await tradeApi.settlement({
      items: items.value,
      deliveryType: 1,
      pointStatus: false,
      addressId: addressId.value,
      couponId: couponId.value,
    }) as SettlementView
    if (!operation.current() || request !== settlementRequest) return
    settlement.value = result
    if (!addressId.value && settlement.value.address?.id) addressId.value = settlement.value.address.id
  } catch {
    if (operation.current() && request === settlementRequest) settlement.value = null
  } finally { if (operation.current() && request === settlementRequest) loading.value = false }
}

async function submit() {
  if (!operation.current()) { discardStaleDraft(); return }
  if (submitting.value || loading.value || submitted.value || needsVerification.value) return
  if (!addressId.value) return ElMessage.warning('请选择收货地址')
  if (!settlement.value?.items?.length || price.value.payPrice === undefined) return ElMessage.warning('订单金额尚未计算完成')
  submitting.value = true
  let attempt: ReturnType<CheckoutIntent['begin']> | undefined
  try {
    if (!checkoutIntent) throw new Error('结算状态尚未核验，请从购物车重新进入')
    attempt = checkoutIntent.begin()
    const result = await tradeApi.createOrder({ items: items.value.map(item => ({ ...item })), addressId: addressId.value, couponId: couponId.value, deliveryType: 1, pointStatus: false, remark: remark.value })
    const data = result && typeof result === 'object' ? result : { id: result }
    if (/^[1-9]\d*$/.test(String(data.id ?? ''))) { try { attempt.created() } catch { /* Existing PENDING remains blocking. */ } }
    if (!operation.current()) return
    if (!/^[1-9]\d*$/.test(String(data.id ?? ''))) { needsVerification.value = true; return }
    submitted.value = true
    checkoutDraft.clear()
    // A successfully created order must not appear failed because the badge refresh is unavailable.
    try { await cartStore.fetchCart() } catch { /* Cart can be refreshed separately. Never retry order creation here. */ }
    if (!operation.current()) return
    ElMessage.success('订单创建成功')
    if (data.payOrderId) await router.replace(`/pay/${data.payOrderId}?orderId=${data.id || ''}`)
    else if (data.id) await router.replace(`/member/orders/${data.id}`)
    else await router.replace('/member/orders')
  } catch (error) {
    const code = Number((error as { code?: unknown })?.code)
    // Domain validation/auth rejection is definite; transport/server errors may lose a committed response.
    const rejected = Number.isInteger(code) && (code >= 1_000_000_000 || [400, 401, 403, 404, 409, 422, 429].includes(code))
    let released = false
    if (rejected && attempt) { try { released = attempt.reject() } catch {} }
    if (!operation.current()) return
    if (!submitted.value && !released) { needsVerification.value = true; recoveryError.value = error instanceof Error ? error.message : '' }
  } finally { if (operation.current()) submitting.value = false }
}

async function openAddressDialog() {
  if (!operation.current() || savingAddress.value) return
  Object.assign(addressForm, { id: 0, name: '', mobile: '', areaId: undefined, detailAddress: '', defaultStatus: addresses.value.length === 0,
    longitude: null, latitude: null, coordSystem: null, locationSource: null, locationPrecision: null, locationConfirmed: false })
  addressDialog.value = true
  if (!areaTree.value.length) {
    const tree = await systemApi.getAreaTree()
    if (operation.current()) areaTree.value = tree
  }
}

function applyMapSelection(selection: AddressMapSelection) {
  Object.assign(addressForm, {
    ...(selection.areaId ? { areaId: selection.areaId } : {}),
    ...(selection.areaName ? { areaName: selection.areaName } : {}),
    ...(selection.formattedAddress ? { detailAddress: selection.formattedAddress } : {}),
    longitude: selection.longitude, latitude: selection.latitude, coordSystem: 'GCJ02',
    locationSource: 'MAP_PICKER', locationPrecision: selection.locationPrecision || 'ADDRESS', locationConfirmed: true,
  })
}

async function saveAddress() {
  if (!operation.current() || savingAddress.value) return
  if (!addressForm.name.trim() || !/^1\d{10}$/.test(addressForm.mobile) || !addressForm.areaId || !addressForm.detailAddress.trim()) {
    return ElMessage.warning('请完整填写收货地址')
  }
  savingAddress.value = true
  try {
    const id = await memberApi.createAddress(manualAddressPayload(addressForm))
    if (!operation.current()) return
    addressId.value = id
    addressDialog.value = false
    ElMessage.success('收货地址已新增')
    // Creating the address succeeded; a later list error must not leave the same draft ready to create twice.
    try {
      const list = await memberApi.getAddresses()
      if (operation.current()) addresses.value = list
    } catch { if (operation.current()) ElMessage.warning('地址已创建，地址列表暂时刷新失败，请重新进入结算页核验') }
  } finally { if (operation.current()) savingAddress.value = false }
}

watch([addressId, couponId], settle)
onMounted(async () => {
  if (!operation.current()) return
  const draft = checkoutDraft.snapshot()
  items.value = draft?.items || []
  if (!items.value.length) return
  try {
    const owner = getTenantContext()
    checkoutIntent = openCheckoutIntent(localStorage, draft!.intentId, { tenantId: owner.accountTenantId, memberId: owner.accountUserId })
    const status = checkoutIntent.state()
    submitted.value = status === 'CREATED'; needsVerification.value = status === 'PENDING'
    if (submitted.value || needsVerification.value) return
  } catch (error) { needsVerification.value = true; recoveryError.value = error instanceof Error ? error.message : '结算状态无法核验'; return }
  const list = await memberApi.getAddresses()
  if (!operation.current()) return
  addresses.value = list
  addressId.value = addresses.value.find((item) => item.defaultStatus)?.id || addresses.value[0]?.id
  await settle()
})
</script>

<template>
  <div class="container page-section checkout-page">
    <div class="section-heading"><div><h1>确认订单</h1><p>价格、库存、配送费用均由后端重新结算</p></div></div>
    <ServiceEntry label="平台结算咨询（不代表任一店铺）" :disabled="submitting || loading" />
    <el-result v-if="submitted || needsVerification" class="panel" :icon="submitted ? 'success' : 'warning'" :title="submitted ? '订单已创建' : '订单提交结果需要核实'" :sub-title="`${recoveryError} 请先到订单列表核实结果，不要重复提交；有意新增采购请从商品或购物车重新发起。`"><template #extra><el-button type="primary" @click="router.push('/member/orders')">查看我的订单</el-button></template></el-result>
    <EmptyState v-else-if="!items.length" class="panel" title="没有可结算的商品" description="请返回购物车重新选择"><el-button type="primary" @click="router.push('/cart')">返回购物车</el-button></EmptyState>
    <template v-else>
      <section class="panel checkout-section"><div class="checkout-section__title"><h2>收货地址</h2><el-button type="primary" plain @click="openAddressDialog">新增地址</el-button></div><div v-if="addresses.length" class="address-list"><button v-for="address in addresses" :key="address.id" :class="{ active: address.id === addressId }" @click="addressId = address.id"><strong>{{ address.name }}　{{ address.mobile }}</strong><span>{{ address.areaName }} {{ address.detailAddress }}</span><i v-if="address.defaultStatus">默认地址</i></button></div><EmptyState v-else title="暂无收货地址" description="新增地址后即可继续提交订单"><el-button type="primary" @click="openAddressDialog">新增地址</el-button></EmptyState></section>
      <section class="panel checkout-section">
        <h2>商品清单</h2>
        <div v-if="loading" class="loading-box"><el-skeleton :rows="5" animated /></div>
        <div v-else class="settlement-items">
          <div v-for="(item, index) in settlement?.items || []" :key="String(item.skuId || index)">
            <div class="settlement-image"><img v-if="item.picUrl" :src="normalizeImage(String(item.picUrl))" alt="商品" /></div>
            <div><strong>{{ item.spuName || item.name || '商品' }}</strong><small>{{ propertyText((item.properties as []) || []) }}</small><ServiceEntry resource-type="PRODUCT" :resource-id="item.spuId" :disabled="loading || submitting" /></div>
            <span>¥{{ formatMoney(item.price as number) }} × {{ item.count }}</span>
            <b>¥{{ formatMoney(Number(item.price) * Number(item.count || 0)) }}</b>
          </div>
        </div>
      </section>
      <section v-if="settlement?.coupons?.length" class="panel checkout-section">
        <h2>使用优惠券</h2>
        <el-select v-model="couponId" clearable placeholder="不使用优惠券" style="width: 100%; margin-top: 16px">
          <el-option v-for="coupon in settlement.coupons" :key="coupon.id" :value="coupon.id" :disabled="!coupon.match" :label="coupon.match ? coupon.name : `${coupon.name}（${coupon.mismatchReason || '不适用'}）`" />
        </el-select>
      </section>
      <section class="panel checkout-section"><h2>订单备注</h2><el-input v-model="remark" type="textarea" maxlength="200" show-word-limit placeholder="选填，请与商家协商一致后填写" /></section>
      <section class="panel checkout-summary">
        <div><span>商品金额</span><b>¥{{ formatMoney(price.totalPrice) }}</b></div>
        <div><span>运费</span><b>+ ¥{{ formatMoney(price.deliveryPrice) }}</b></div>
        <div v-if="price.discountPrice"><span>活动优惠</span><b>- ¥{{ formatMoney(price.discountPrice) }}</b></div>
        <div v-if="price.couponPrice"><span>优惠券抵扣</span><b>- ¥{{ formatMoney(price.couponPrice) }}</b></div>
        <div v-if="price.vipPrice"><span>会员优惠</span><b>- ¥{{ formatMoney(price.vipPrice) }}</b></div>
        <div v-if="price.pointPrice"><span>积分抵扣</span><b>- ¥{{ formatMoney(price.pointPrice) }}</b></div>
        <div class="checkout-total"><span>应付金额</span><strong>{{ price.payPrice === undefined ? '待结算' : `¥${formatMoney(payablePrice)}` }}</strong></div>
        <el-button type="primary" size="large" :loading="submitting" :disabled="loading || price.payPrice === undefined" @click="submit">提交订单</el-button>
      </section>
      <el-dialog v-model="addressDialog" title="新增收货地址" width="560px"><el-form label-position="top"><div class="form-row"><el-form-item label="收货人"><el-input v-model="addressForm.name" /></el-form-item><el-form-item label="手机号"><el-input v-model="addressForm.mobile" maxlength="11" /></el-form-item></div><el-form-item label="省/市/区"><el-cascader v-model="addressForm.areaId" :options="areaTree" :props="{ value: 'id', label: 'name', emitPath: false }" filterable clearable style="width: 100%" /></el-form-item><el-form-item label="详细地址"><el-input v-model="addressForm.detailAddress" type="textarea" maxlength="120" /></el-form-item><el-form-item label="地图点位（可选）"><div><el-button @click="addressMapDialog = true">地图选点</el-button><p class="address-location">{{ addressLocationText(addressForm) }}</p></div></el-form-item><el-checkbox v-model="addressForm.defaultStatus">设为默认地址</el-checkbox></el-form><template #footer><el-button @click="addressDialog = false">取消</el-button><el-button type="primary" :loading="savingAddress" @click="saveAddress">保存并使用</el-button></template></el-dialog>
      <AddressMapDialog v-model="addressMapDialog" :location="addressForm" :address-text="[addressForm.areaName, addressForm.detailAddress].filter(Boolean).join(' ')" @picked="applyMapSelection" />
    </template>
  </div>
</template>

<style scoped lang="scss">
.checkout-page { max-width: 1220px; }.checkout-section { margin-bottom: 18px; padding: 24px; }.checkout-section h2 { margin: 0; font-size: 18px; }.checkout-section__title { display: flex; align-items: center; justify-content: space-between; margin-bottom: 20px; }.address-list { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; }.address-list button { position: relative; min-height: 110px; padding: 18px; border: 1px solid var(--line); border-radius: 5px; background: white; text-align: left; }.address-list button.active { border: 2px solid var(--brand); background: #fffaf7; }.address-list strong, .address-list span { display: block; }.address-list span { margin-top: 12px; color: var(--muted); line-height: 1.5; }.address-list i { position: absolute; right: 0; bottom: 0; padding: 3px 8px; background: var(--brand); color: white; font-size: 11px; font-style: normal; }.settlement-items > div { display: grid; grid-template-columns: 70px 1fr 180px 140px; gap: 16px; align-items: center; padding: 12px 0; border-top: 1px solid var(--line); }.settlement-image { width: 64px; height: 64px; background: #f5f5f5; }.settlement-image img { width: 100%; height: 100%; object-fit: cover; }.settlement-items small { display: block; margin-top: 7px; color: var(--muted); }.checkout-summary { width: 100%; margin: 0; padding: 26px 30px; border-top: 3px solid var(--brand); box-shadow: 0 8px 22px rgba(31,42,54,.08); }.checkout-summary > div { width: 430px; display: flex; justify-content: space-between; margin: 0 0 14px auto; color: var(--muted); }.checkout-summary b { color: var(--ink); }.checkout-total { align-items: baseline; padding-top: 18px; border-top: 1px solid var(--line); }.checkout-total strong { color: #df421f; font-size: 28px; }.checkout-summary :deep(.el-button) { display: block; width: 260px; margin: 18px 0 0 auto; }.form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.address-location { margin: 8px 0 0; color: var(--muted); font-size: 13px; }
</style>
