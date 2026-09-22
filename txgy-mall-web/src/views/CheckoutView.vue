<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { getTenantContext, isCurrentTenantEpoch } from '@/utils/tenant-context'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'

import { memberApi, systemApi, type AreaNode } from '@/api/mall'
import { inquiryTradeApi } from '@/api/inquiry-market'
import {
  platformCheckoutApi,
  type PlatformCheckoutDetail,
  type PlatformCheckoutPreview,
} from '@/api/platform-commerce'
import EmptyState from '@/components/EmptyState.vue'
import ServiceEntry from '@/components/ServiceEntry.vue'
import AddressMapDialog from '@/components/AddressMapDialog.vue'
import type { AddressMapSelection } from '@/components/AddressMapDialog.vue'
import { useCartStore } from '@/stores/cart'
import { useAccountOperation } from '@/composables/useAccountOperation'
import type { Address, Identifier } from '@/types/mall'
import { formatMoney, normalizeImage, propertyText } from '@/utils/format'
import { addressLocationText, manualAddressPayload } from '@/utils/fulfillment'
import { makeRequestKey } from '@/utils/inquiry-market'
import { platformCheckoutDraft } from '@/utils/platform-checkout-draft'

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
const cartItemIds = ref<Identifier[]>([])
const addresses = ref<Address[]>([])
const addressId = ref<Identifier>()
const couponId = ref<Identifier>()
const remark = ref('')
const settlement = ref<SettlementView | null>(null)
const platformPreview = ref<PlatformCheckoutPreview | null>(null)
const platformResult = ref<PlatformCheckoutDetail | null>(null)
const platformSubmitError = ref('')
const platformRequestKey = ref('')
const loading = ref(false)
const submitting = ref(false)
const submitted = ref(false)
const needsVerification = ref(false)
const recoveryError = ref('')
const inquiryQuoteId = computed(() => {
  const query = router.currentRoute?.value.query || {}
  const raw = Array.isArray(query.inquiryQuoteId) ? query.inquiryQuoteId[0] : query.inquiryQuoteId
  const value = Number(raw)
  return Number.isSafeInteger(value) && value > 0 ? value : undefined
})
const inquiryCheckout = computed(() => inquiryQuoteId.value !== undefined)
const inquiryRequestKey = ref('')
const inquiryRequestStorageKey = computed(() => {
  const owner = getTenantContext()
  return inquiryQuoteId.value ? `mall-inquiry-order:${owner.accountTenantId || ''}:${owner.accountUserId || ''}:${inquiryQuoteId.value}` : ''
})

function restoreInquiryRequestKey() {
  if (!inquiryRequestStorageKey.value) return
  const stored = sessionStorage.getItem(inquiryRequestStorageKey.value)
  inquiryRequestKey.value = stored && /^[A-Za-z0-9_-]{8,64}$/.test(stored) ? stored : makeRequestKey('inquiry-order')
  sessionStorage.setItem(inquiryRequestStorageKey.value, inquiryRequestKey.value)
}
const addressDialog = ref(false)
const addressMapDialog = ref(false)
const savingAddress = ref(false)
const areaTree = ref<AreaNode[]>([])
const addressForm = reactive<Address>({ id: 0, name: '', mobile: '', areaId: undefined, detailAddress: '', defaultStatus: false })
const price = computed(() => settlement.value?.price || {})
const payablePrice = computed(() => price.value.payPrice !== undefined
  ? Number(price.value.payPrice)
  : 0)
const customerFacingBlockingReasons = computed(() => (platformPreview.value?.blockingReasons || [])
  .filter((reason) => !reason.includes('原子子订单与聚合支付尚未接通')))
let settlementRequest = 0
function discardStaleDraft() {
  if (isCurrentTenantEpoch(ownerEpoch)) return
  settlementRequest++; cartItemIds.value = []; addresses.value = []; settlement.value = null; platformPreview.value = null
  addressDialog.value = false; addressMapDialog.value = false; addressId.value = undefined; couponId.value = undefined; remark.value = ''
  submitted.value = false; needsVerification.value = false
  recoveryError.value = ''; platformResult.value = null; platformSubmitError.value = ''
  Object.assign(addressForm, { id:0, name:'', mobile:'', areaId:undefined, detailAddress:'', defaultStatus:false,
    longitude:null, latitude:null, coordSystem:null, locationSource:null, locationPrecision:null, locationConfirmed:false })
}
window.addEventListener('mall:token-updated', discardStaleDraft)
onBeforeUnmount(() => window.removeEventListener('mall:token-updated', discardStaleDraft))

async function settle() {
  if (!operation.current()) { discardStaleDraft(); return }
  if (!addressId.value || (!inquiryCheckout.value && !cartItemIds.value.length)) return
  const request = ++settlementRequest
  loading.value = true
  platformSubmitError.value = ''
  try {
    if (inquiryQuoteId.value) {
      const result = await inquiryTradeApi.settlement(inquiryQuoteId.value, addressId.value) as SettlementView
      if (!operation.current() || request !== settlementRequest) return
      settlement.value = result
      platformPreview.value = null
      if (!addressId.value && settlement.value.address?.id) addressId.value = settlement.value.address.id
      return
    }
    const result = await platformCheckoutApi.preview({ cartItemIds: cartItemIds.value, addressId: addressId.value })
    if (!operation.current() || request !== settlementRequest) return
    platformPreview.value = result
    settlement.value = null
  } catch (error) {
    if (operation.current() && request === settlementRequest) {
      settlement.value = null
      platformPreview.value = null
      platformSubmitError.value = error instanceof Error ? error.message : '结算预览失败'
    }
  } finally { if (operation.current() && request === settlementRequest) loading.value = false }
}

function createdOrder(result: { id?: Identifier; payOrderId?: Identifier } | Identifier | null) {
  return result && typeof result === 'object' ? result : { id: result || undefined }
}

async function finishCreatedOrder(
  data: { id?: Identifier; payOrderId?: Identifier },
  regularCheckout: boolean,
  existingOrder = false,
) {
  if (!/^[1-9]\d*$/.test(String(data.id ?? ''))) return false
  submitted.value = true
  if (!regularCheckout && inquiryRequestStorageKey.value) sessionStorage.removeItem(inquiryRequestStorageKey.value)
  if (regularCheckout) {
    platformCheckoutDraft.clear()
    // A successfully created order must not appear failed because the badge refresh is unavailable.
    try { await cartStore.fetchCart() } catch { /* The order is already committed. */ }
  }
  if (!operation.current()) return true
  ElMessage.success(existingOrder ? '该询价已生成订单，已打开订单详情' : '订单创建成功')
  // 恢复查询可能返回已支付、已发货或已取消的历史订单。
  // 只有本次新建成功的订单才根据 payOrderId 进入支付页。
  if (!existingOrder && data.payOrderId) await router.replace(`/pay/${data.payOrderId}?orderId=${data.id || ''}`)
  else if (data.id) await router.replace(`${inquiryCheckout.value ? '/purchase' : '/member'}/orders/${data.id}`)
  else await router.replace(inquiryCheckout.value ? '/purchase/orders' : '/member/orders')
  return true
}

async function submit() {
  if (!operation.current()) { discardStaleDraft(); return }
  if (submitting.value || loading.value || submitted.value || needsVerification.value) return
  if (!addressId.value) return ElMessage.warning('请选择收货地址')
  if (inquiryCheckout.value && (!settlement.value?.items?.length || price.value.payPrice === undefined)) return ElMessage.warning('订单金额尚未计算完成')
  if (!inquiryCheckout.value && (!platformPreview.value?.validationPassed || !cartItemIds.value.length)) return ElMessage.warning('平台结算尚未通过商品校验')
  submitting.value = true
  try {
    if (inquiryQuoteId.value) {
      const result = await inquiryTradeApi.create({ quoteId: inquiryQuoteId.value, addressId: addressId.value, remark: remark.value, requestKey: inquiryRequestKey.value })
      const data = createdOrder(result)
      if (!operation.current()) return
      if (!/^[1-9]\d*$/.test(String(data.id ?? ''))) { needsVerification.value = true; return }
      await finishCreatedOrder(data, false)
      return
    }
    platformSubmitError.value = ''
    const result = await platformCheckoutApi.submit({
      cartItemIds: cartItemIds.value,
      addressId: addressId.value,
      idempotencyKey: platformRequestKey.value,
      remark: remark.value || undefined,
    })
    if (!operation.current()) return
    platformResult.value = result
    if (!result?.checkoutNo) throw new Error('平台结算未返回有效结算单号')
    submitted.value = true
    platformCheckoutDraft.clear()
    try { await cartStore.fetchCart() } catch { /* 已落库的结算不能因角标刷新失败而伪装成失败。 */ }
    if (result.payable && result.platformPayOrderId) {
      ElMessage.success('订单创建成功')
      await router.replace(`/pay/${result.platformPayOrderId}?platformCheckout=${encodeURIComponent(result.checkoutNo)}`)
      return
    }
    ElMessage.success('结算信息已提交')
  } catch (error) {
    const code = Number((error as { code?: unknown })?.code)
    // Domain validation/auth rejection is definite; transport/server errors may lose a committed response.
    const rejected = Number.isInteger(code) && (code >= 1_000_000_000 || [400, 401, 403, 404, 409, 422, 429].includes(code))
    if (!operation.current()) return
    if (inquiryQuoteId.value) {
      // A transport failure may have hidden a committed idempotent order. Query by quote before blocking retries.
      if (!rejected) {
        try {
          const recovered = createdOrder(await inquiryTradeApi.order(inquiryQuoteId.value))
          if (operation.current() && await finishCreatedOrder(recovered, false, true)) return
        } catch { /* Keep the ambiguous result blocked below. */ }
      } else return
    }
    if (!inquiryCheckout.value) {
      platformSubmitError.value = error instanceof Error ? error.message : '平台结算提交失败'
      // 服务端按幂等键原子保存规划；即使响应丢失，保留同一个键重试也只会读取既有快照。
      return
    }
    if (!submitted.value) { needsVerification.value = true; recoveryError.value = error instanceof Error ? error.message : '' }
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
  if (inquiryQuoteId.value) {
    restoreInquiryRequestKey()
    try {
      const existing = createdOrder(await inquiryTradeApi.order(inquiryQuoteId.value))
      if (!operation.current() || await finishCreatedOrder(existing, false, true)) return
    } catch { /* No committed order: continue to the dedicated settlement. */ }
    const list = await memberApi.getAddresses()
    if (!operation.current()) return
    addresses.value = list
    addressId.value = addresses.value.find((item) => item.defaultStatus)?.id || addresses.value[0]?.id
    if (addressId.value) await settle()
    return
  }
  const draft = platformCheckoutDraft.read()
  cartItemIds.value = draft?.cartItemIds || []
  platformRequestKey.value = draft?.idempotencyKey || ''
  if (!cartItemIds.value.length || !platformRequestKey.value) return
  const list = await memberApi.getAddresses()
  if (!operation.current()) return
  addresses.value = list
  addressId.value = addresses.value.find((item) => item.defaultStatus)?.id || addresses.value[0]?.id
  await settle()
})
</script>

<template>
  <div class="container page-section checkout-page">
    <div class="section-heading"><div><h1>{{ inquiryCheckout ? '确认询价成交订单' : '确认订单' }}</h1><p>{{ inquiryCheckout ? '按已采纳报价快照专用结算，并重新校验商品、库存与收货地址' : '请核对收货地址、商品信息和订单金额' }}</p></div></div>
    <ServiceEntry label="平台结算咨询（不代表任一店铺）" :disabled="submitting || loading" />

    <el-result v-if="inquiryCheckout && (submitted || needsVerification)" class="panel" :icon="submitted ? 'success' : 'warning'" :title="submitted ? '订单已创建' : '订单提交结果需要核实'" :sub-title="`${recoveryError} 请先到订单列表核实结果，不要重复提交；有意新增采购请重新发起。`"><template #extra><el-button type="primary" @click="router.push('/purchase/orders')">查看我的订单</el-button></template></el-result>
    <el-result v-else-if="!inquiryCheckout && platformResult" class="panel" icon="success" title="结算信息已提交" :sub-title="`结算单号 ${platformResult.checkoutNo}`"><template #extra><el-button type="primary" @click="router.push('/member/platform-checkouts')">查看结算记录</el-button><el-button @click="router.push('/cart')">返回购物车</el-button></template></el-result>
    <EmptyState v-else-if="!inquiryCheckout && !cartItemIds.length" class="panel" title="没有可结算的商品" description="请返回购物车重新选择"><el-button type="primary" @click="router.push('/cart')">返回购物车</el-button></EmptyState>

    <template v-else>
      <el-alert v-if="inquiryCheckout" class="inquiry-notice" title="这是已采纳报价的专用成交入口，不读取购物车结算草稿，也不会调用普通订单创建接口。" type="success" :closable="false" />

      <section class="panel checkout-section"><div class="checkout-section__title"><h2>收货地址</h2><el-button type="primary" plain @click="openAddressDialog">新增地址</el-button></div><div v-if="addresses.length" class="address-list"><button v-for="address in addresses" :key="address.id" :class="{ active: address.id === addressId }" @click="addressId = address.id"><strong>{{ address.name }}　{{ address.mobile }}</strong><span>{{ address.areaName }} {{ address.detailAddress }}</span><i v-if="address.defaultStatus">默认地址</i></button></div><EmptyState v-else title="暂无收货地址" description="新增地址后即可继续校验结算"><el-button type="primary" @click="openAddressDialog">新增地址</el-button></EmptyState></section>

      <section class="panel checkout-section">
        <h2>商品清单</h2>
        <div v-if="loading" class="loading-box"><el-skeleton :rows="5" animated /></div>
        <div v-else-if="inquiryCheckout" class="settlement-items">
          <div v-for="(item, index) in settlement?.items || []" :key="String(item.skuId || index)">
            <div class="settlement-image"><img v-if="item.picUrl" :src="normalizeImage(String(item.picUrl))" alt="商品" /></div>
            <div><strong>{{ item.spuName || item.name || '商品' }}</strong><small>{{ propertyText((item.properties as []) || []) }}</small><ServiceEntry resource-type="PRODUCT" :resource-id="item.spuId" :disabled="loading || submitting" /></div>
            <span>¥{{ formatMoney(item.price as number) }} × {{ item.count }}</span>
            <b>¥{{ formatMoney(Number(item.price) * Number(item.count || 0)) }}</b>
          </div>
        </div>
        <div v-else-if="platformPreview" class="platform-groups">
          <section v-for="tenant in platformPreview.tenantGroups" :key="String(tenant.tenantId)" class="platform-tenant">
            <header><strong>{{ tenant.tenantName }}</strong><span>租户商品金额 ¥{{ formatMoney(tenant.goodsAmount) }}</span></header>
            <div v-for="shop in tenant.shops" :key="String(shop.shopId)" class="platform-shop">
              <div class="platform-shop__title"><div><b>{{ shop.shopName }}</b><small>{{ shop.enterpriseName }}</small></div><span>¥{{ formatMoney(shop.goodsAmount) }}</span></div>
              <div class="settlement-items"><div v-for="item in shop.items" :key="String(item.cartItemId)"><div class="settlement-image"><img v-if="item.picUrl" :src="normalizeImage(item.picUrl)" alt="商品" /></div><div><strong>{{ item.spuName || '商品' }}</strong><small>{{ propertyText(item.properties) || `SKU ${item.skuId}` }}</small><ServiceEntry resource-type="PRODUCT" :resource-id="item.spuId" :disabled="loading || submitting" /></div><span>¥{{ formatMoney(item.unitPrice) }} × {{ item.quantity }}</span><b>¥{{ formatMoney(item.goodsAmount) }}</b></div></div>
            </div>
          </section>
          <el-alert v-if="platformPreview.failedItems.length" type="error" :closable="false" title="部分购物车商品未通过校验"><ul><li v-for="item in platformPreview.failedItems" :key="String(item.cartItemId)">购物车项 {{ item.cartItemId }}：{{ item.reason }}</li></ul></el-alert>
        </div>
        <el-empty v-else description="选择地址后由服务端生成结算预览" />
      </section>

      <section v-if="inquiryCheckout && settlement?.coupons?.length" class="panel checkout-section"><h2>使用优惠券</h2><el-select v-model="couponId" clearable placeholder="不使用优惠券" style="width:100%;margin-top:16px"><el-option v-for="coupon in settlement.coupons" :key="coupon.id" :value="coupon.id" :disabled="!coupon.match" :label="coupon.match ? coupon.name : `${coupon.name}（${coupon.mismatchReason || '不适用'}）`" /></el-select></section>
      <section class="panel checkout-section"><h2>订单备注</h2><el-input v-model="remark" type="textarea" maxlength="200" show-word-limit placeholder="选填，请与商家协商一致后填写" /></section>

      <section v-if="inquiryCheckout" class="panel checkout-summary">
        <div><span>商品金额</span><b>¥{{ formatMoney(price.totalPrice) }}</b></div><div><span>运费</span><b>+ ¥{{ formatMoney(price.deliveryPrice) }}</b></div><div class="checkout-total"><span>应付金额</span><strong>{{ price.payPrice === undefined ? '待结算' : `¥${formatMoney(payablePrice)}` }}</strong></div><el-button type="primary" size="large" :loading="submitting" :disabled="loading || price.payPrice === undefined" @click="submit">按采纳报价创建订单</el-button>
      </section>
      <section v-else class="panel checkout-summary">
        <el-alert v-if="platformSubmitError" class="submit-error" type="error" :closable="false" title="结算提交失败" :description="platformSubmitError" />
        <el-alert v-if="customerFacingBlockingReasons.length" class="submit-error" type="warning" :closable="false" title="请处理以下问题"><ul><li v-for="reason in customerFacingBlockingReasons" :key="reason">{{ reason }}</li></ul></el-alert>
        <div><span>商品金额</span><b>¥{{ formatMoney(platformPreview?.goodsAmount) }}</b></div><div><span>运费</span><b>{{ platformPreview ? `+ ¥${formatMoney(platformPreview.freightAmount)}` : '待核算' }}</b></div><div><span>优惠</span><b>- ¥{{ formatMoney(platformPreview?.discountAmount) }}</b></div><div class="checkout-total"><span>应付金额</span><strong>{{ platformPreview ? `¥${formatMoney(platformPreview.payAmount)}` : '待结算' }}</strong></div><el-button type="primary" size="large" :loading="submitting" :disabled="loading || !addressId || !platformPreview?.validationPassed" @click="submit">确认结算</el-button>
      </section>

      <el-dialog v-model="addressDialog" title="新增收货地址" width="560px"><el-form label-position="top"><div class="form-row"><el-form-item label="收货人"><el-input v-model="addressForm.name" /></el-form-item><el-form-item label="手机号"><el-input v-model="addressForm.mobile" maxlength="11" /></el-form-item></div><el-form-item label="省/市/区"><el-cascader v-model="addressForm.areaId" :options="areaTree" :props="{ value: 'id', label: 'name', emitPath: false }" filterable clearable style="width:100%" /></el-form-item><el-form-item label="详细地址"><el-input v-model="addressForm.detailAddress" type="textarea" maxlength="120" /></el-form-item><el-form-item label="地图点位（可选）"><div><el-button @click="addressMapDialog = true">地图选点</el-button><p class="address-location">{{ addressLocationText(addressForm) }}</p></div></el-form-item><el-checkbox v-model="addressForm.defaultStatus">设为默认地址</el-checkbox></el-form><template #footer><el-button @click="addressDialog = false">取消</el-button><el-button type="primary" :loading="savingAddress" @click="saveAddress">保存并使用</el-button></template></el-dialog>
      <AddressMapDialog v-model="addressMapDialog" :location="addressForm" :address-text="[addressForm.areaName, addressForm.detailAddress].filter(Boolean).join(' ')" @picked="applyMapSelection" />
    </template>
  </div>
</template>

<style scoped lang="scss">
.checkout-page { max-width: 1220px; }.checkout-section { margin-bottom: 18px; padding: 24px; }.checkout-section h2 { margin: 0; font-size: 18px; }.checkout-section__title { display: flex; align-items: center; justify-content: space-between; margin-bottom: 20px; }.address-list { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; }.address-list button { position: relative; min-height: 110px; padding: 18px; border: 1px solid var(--line); border-radius: 5px; background: white; text-align: left; }.address-list button.active { border: 2px solid var(--brand); background: #fffaf7; }.address-list strong, .address-list span { display: block; }.address-list span { margin-top: 12px; color: var(--muted); line-height: 1.5; }.address-list i { position: absolute; right: 0; bottom: 0; padding: 3px 8px; background: var(--brand); color: white; font-size: 11px; font-style: normal; }.settlement-items > div { display: grid; grid-template-columns: 70px 1fr 180px 140px; gap: 16px; align-items: center; padding: 12px 0; border-top: 1px solid var(--line); }.settlement-image { width: 64px; height: 64px; background: #f5f5f5; }.settlement-image img { width: 100%; height: 100%; object-fit: cover; }.settlement-items small { display: block; margin-top: 7px; color: var(--muted); }.checkout-summary { width: 100%; margin: 0; padding: 26px 30px; border-top: 3px solid var(--brand); box-shadow: 0 8px 22px rgba(31,42,54,.08); }.checkout-summary > div { width: 430px; display: flex; justify-content: space-between; margin: 0 0 14px auto; color: var(--muted); }.checkout-summary b { color: var(--ink); }.checkout-total { align-items: baseline; padding-top: 18px; border-top: 1px solid var(--line); }.checkout-total strong { color: #df421f; font-size: 28px; }.checkout-summary :deep(.el-button) { display: block; width: 260px; margin: 18px 0 0 auto; }.form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.address-location { margin: 8px 0 0; color: var(--muted); font-size: 13px; }
.inquiry-notice { margin-bottom: 18px; }
.platform-groups { margin-top: 18px; }.platform-tenant { overflow: hidden; margin-top: 16px; border: 1px solid var(--line); border-radius: 6px; }.platform-tenant > header { display: flex; justify-content: space-between; padding: 15px 18px; background: #173f47; color: white; }.platform-tenant > header span { color: #bfdbd5; }.platform-shop + .platform-shop { border-top: 6px solid #f2f5f5; }.platform-shop__title { display: flex; justify-content: space-between; align-items: center; padding: 14px 18px; background: #f8faf9; }.platform-shop__title b,.platform-shop__title small { display: block; }.platform-shop__title small { margin-top: 4px; color: var(--muted); }.submit-error { width: 100% !important; margin-bottom: 18px !important; }.submit-error ul { margin: 8px 0 0; padding-left: 20px; line-height: 1.7; }
</style>
