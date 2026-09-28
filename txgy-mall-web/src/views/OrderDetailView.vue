<script setup lang="ts">
import { useBuyerNavigation } from '@/composables/useBuyerNavigation'
const { buyerTo, paymentTo } = useBuyerNavigation()
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useAccountMessageBox } from '@/composables/useAccountOperation'
const ElMessageBox = useAccountMessageBox()
import { useRoute, useRouter } from 'vue-router'

import { memberApi, tradeApi } from '@/api/mall'
import MemberPage from '@/components/MemberPage.vue'
import PaymentDeadlineNotice from '@/components/PaymentDeadlineNotice.vue'
import OrderLogisticsPanel from '@/components/OrderLogisticsPanel.vue'
import { addressLocationText, hasAddressLocation, orderLocation } from '@/utils/fulfillment'
import { usePaymentClock } from '@/composables/usePaymentClock'
import type { Address, TradeOrder } from '@/types/mall'
import { formatDate, formatMoney, normalizeImage, propertyText } from '@/utils/format'
import { tradePaymentState } from '@/utils/payment-deadline'
import { createRefreshQueue } from '@/utils/refresh-queue'
import { hasRejectedAfterSale, rejectedAfterSaleTitle } from '@/utils/after-sale-display'

const route = useRoute()
const router = useRouter()
const enterpriseMode = computed(() => route.path.startsWith('/purchase/orders'))
const order = ref<TradeOrder | null>(null)
const loading = ref(true)
const statusNames: Record<number, string> = { 0: '待支付', 10: '待发货', 20: '待收货', 30: '已完成', 40: '已取消' }

const clock = usePaymentClock(() => load())
const paymentState = computed(() => tradePaymentState(order.value, clock.now.value))
const canUpdateAddress = computed(() => order.value?.status === 0 && order.value.deliveryType === 1
  && Boolean(order.value.payOrderId) && paymentState.value.canPay)
const addressDialog = ref(false)
const addresses = ref<Address[]>([])
const selectedAddressId = ref<string>('')
const addressLoading = ref(false)
const addressSaving = ref(false)
let refreshTimer = 0
const refreshQueue = createRefreshQueue(async (isCurrent) => {
  const requestGeneration = clock.capture()
  loading.value = !order.value
  try {
    const result = await (enterpriseMode.value
      ? tradeApi.getPurchaseOrder(String(route.params.id), true)
      : tradeApi.getOrder(String(route.params.id), true))
    if (!isCurrent() || !clock.isCurrent(requestGeneration)) return
    order.value = result
    clock.sync(result.serverTime, requestGeneration)
  } catch { if (isCurrent() && clock.isCurrent(requestGeneration)) clock.invalidate() }
  finally { if (isCurrent()) loading.value = false }
})
function load() { return refreshQueue.refresh() }

async function cancel() {
  if (!order.value) return
  try { await ElMessageBox.confirm('确定取消该订单吗？', '取消订单', { type: 'warning', confirmButtonText: '确定取消', cancelButtonText: '返回' }) } catch { return }
  await (enterpriseMode.value ? tradeApi.cancelPurchaseOrder(order.value.id) : tradeApi.cancelOrder(order.value.id)); ElMessage.success('订单已取消'); await load()
}

async function receive() {
  if (!order.value) return
  try { await ElMessageBox.confirm('请确认已收到全部商品，确认后订单将完成。', '确认收货', { type: 'warning', confirmButtonText: '确认已收货', cancelButtonText: '暂不确认' }) } catch { return }
  await (enterpriseMode.value ? tradeApi.receivePurchaseOrder(order.value.id) : tradeApi.receiveOrder(order.value.id)); ElMessage.success('已确认收货'); await load()
}

async function openAddressDialog() {
  if (!canUpdateAddress.value) return ElMessage.warning('当前订单不能修改收货地址')
  addressDialog.value = true
  addressLoading.value = true
  selectedAddressId.value = ''
  try { addresses.value = await memberApi.getAddresses() }
  catch { addresses.value = [] }
  finally { addressLoading.value = false }
}

async function updateAddress() {
  if (!order.value || !selectedAddressId.value) return ElMessage.warning('请选择收货地址')
  try {
    await ElMessageBox.confirm('修改地址后将按新地址重新计算运费和待支付金额，是否继续？', '修改收货地址', {
      type: 'warning', confirmButtonText: '确认修改', cancelButtonText: '返回',
    })
  } catch { return }
  addressSaving.value = true
  try {
    await (enterpriseMode.value
      ? tradeApi.updatePurchaseOrderAddress({ id: order.value.id, addressId: selectedAddressId.value })
      : tradeApi.updateOrderAddress({ id: order.value.id, addressId: selectedAddressId.value }))
    addressDialog.value = false
    ElMessage.success('收货地址已更新，请核对新的运费和待支付金额')
    await load()
  } finally { addressSaving.value = false }
}

watch([() => route.params.id, enterpriseMode], () => { order.value = null; clock.invalidate(); void load() }, { immediate: true })
watch(() => paymentState.value.expired, (expired, before) => { if (expired && !before) void load() })
onMounted(() => { refreshTimer = window.setInterval(() => { if (!document.hidden && order.value?.status === 0) void load() }, 30000) })
onBeforeUnmount(() => { window.clearInterval(refreshTimer); refreshQueue.dispose() })
</script>

<template>
  <MemberPage title="订单详情" description="状态会从服务端重新同步">
    <template #actions><MallLink :to="buyerTo('/member/orders')">返回订单列表</MallLink></template>
    <div v-if="loading" class="loading-box"><el-skeleton :rows="10" animated /></div>
    <template v-else-if="order">
      <PaymentDeadlineNotice :state="paymentState" />
      <el-alert v-if="order.sourceType === 'INQUIRY'" class="inquiry-source" type="success" :closable="false" title="该订单由企业询价成交生成">
        <template #default><MallLink v-if="order.inquiryId" :to="`/purchase/inquiries/${order.inquiryId}/quotes`">返回询价单查看采纳报价与沟通记录 →</MallLink></template>
      </el-alert>
      <el-alert v-if="enterpriseMode && order.createdByCurrentMember === false" class="enterprise-collaboration" type="info" :closable="false" title="该订单由本企业其他采购员创建；您可查看订单与企业售后进度、取消订单、修改地址和确认收货，支付、评价及售后变更由实际下单人操作。" />
      <p v-if="order.createdByCurrentMember !== false"><MallLink v-if="order.shopId" :to="{ path: `/member/customer-service/shop/${order.shopId}`, query: { resourceType: 'ORDER', resourceId: order.id } }">联系商家客服（发送本人订单前需确认） →</MallLink><MallLink v-else :to="{ path: '/member/customer-service/scoped', query: { resourceType: 'ORDER', resourceId: order.id } }">旧订单店铺信息不足，明确选择平台客服咨询 →</MallLink></p>
      <div class="status-card"><div><span>当前状态</span><strong>{{ paymentState.kind === 'erp' ? '库存确认中' : (order.statusName || statusNames[order.status] || order.status) }}</strong><em v-if="order.items?.some(hasRejectedAfterSale)">售后申请被拒绝，原订单继续履约；可联系店铺协商或咨询平台。</em><small>订单号：{{ order.no || order.id }}　创建时间：{{ formatDate(order.createTime) }}</small></div><div class="toolbar"><el-button @click="load">刷新状态</el-button><el-button v-if="order.status === 0 && order.paymentBlockReason !== 'PAYMENT_UNCONFIRMED' && !paymentState.expired" @click="cancel">取消订单</el-button><el-button v-if="paymentState.canPay && order.payOrderId && order.createdByCurrentMember !== false" type="primary" @click="router.push(paymentTo(order.payOrderId, order.id))">立即支付</el-button><el-button v-if="order.status === 20" type="primary" @click="receive">确认收货</el-button><el-button v-if="order.status === 30 && order.commentStatus === false && order.createdByCurrentMember !== false && order.items?.some(item => item.commentStatus === false)" type="primary" @click="router.push(buyerTo(`/member/orders/${order.id}/comment`))">评价商品</el-button></div></div>
      <section class="detail-block"><div class="detail-title"><h2>收货信息</h2><el-button v-if="canUpdateAddress" type="primary" plain @click="openAddressDialog">修改收货地址</el-button></div><p>{{ order.receiverName || '-' }}　{{ order.receiverMobile || '-' }}</p><p>{{ order.receiverAreaName }} {{ order.receiverDetailAddress }}</p><p v-if="hasAddressLocation(orderLocation(order))" class="location-note">收货点位快照：{{ addressLocationText(orderLocation(order)) }}。地址簿修改不会改变本订单快照；此点位不是车辆实时位置。</p><p v-else class="location-note">本订单无地图点位，按下单时保存的文字地址履约。</p></section>
      <OrderLogisticsPanel :key="`${order.id}-${enterpriseMode}`" :order-id="order.id" :enterprise="enterpriseMode" :receiver-location="orderLocation(order)" :receiver-address="[order.receiverAreaName, order.receiverDetailAddress].filter(Boolean).join(' ')" :dispatch-origin-address="[20, 30].includes(order.status) ? order.dispatchOriginAddress : undefined" />
      <section class="detail-block"><h2>商品清单</h2><div class="detail-item" v-for="item in order.items || []" :key="item.id"><div class="detail-image"><img v-if="item.picUrl" :src="normalizeImage(item.picUrl)" alt="商品" /></div><div><MallLink :to="`/products/${item.spuId}`">{{ item.spuName || '商品' }}</MallLink><small>{{ propertyText(item.properties) }}</small><div v-if="hasRejectedAfterSale(item)" class="after-rejected"><b>{{ rejectedAfterSaleTitle(item) }}</b><span>{{ item.latestAfterSaleReason || '商家未填写补充说明，请联系客服进一步核实。' }}</span><MallLink v-if="item.latestAfterSaleId" :to="{ path: buyerTo('/member/after-sales'), query: { openAfter: item.latestAfterSaleId } }">查看售后记录</MallLink><MallLink v-if="(item.shopId || order.shopId) && order.createdByCurrentMember !== false" :to="{ path: `/member/customer-service/shop/${item.shopId || order.shopId}`, query: { resourceType: 'ORDER', resourceId: order.id } }">联系店铺协商</MallLink><MallLink v-if="order.createdByCurrentMember !== false" :to="{ path: '/member/customer-service/scoped', query: { resourceType: 'ORDER', resourceId: order.id } }">联系平台客服</MallLink></div><MallLink v-if="order.createdByCurrentMember !== false && [10,20,30].includes(order.status) && Number(item.afterSaleStatus || 0) === 0" class="after-link" :to="{ path: buyerTo('/member/after-sales/apply'), query: { orderId: order.id, itemId: item.id } }">{{ hasRejectedAfterSale(item) ? '再次申请售后' : '申请售后' }}</MallLink><MallLink v-else-if="Number(item.afterSaleStatus || 0) > 0" class="after-link" :to="buyerTo('/member/after-sales')">查看售后</MallLink></div><span>¥{{ formatMoney(item.price) }} × {{ item.count }}</span><strong>¥{{ formatMoney(item.payPrice ?? item.price * item.count) }}</strong></div></section>
      <section class="money-block"><span>订单实付：</span><strong>¥{{ formatMoney(order.payPrice) }}</strong></section>
      <el-dialog v-model="addressDialog" title="修改收货地址" width="620px">
        <div v-loading="addressLoading" class="address-options">
          <el-radio-group v-if="addresses.length" v-model="selectedAddressId">
            <el-radio v-for="address in addresses" :key="String(address.id)" :value="String(address.id)" border>
              <strong>{{ address.name }}　{{ address.mobile }}</strong>
              <span>{{ address.areaName }} {{ address.detailAddress }}</span>
            </el-radio>
          </el-radio-group>
          <p v-else-if="!addressLoading">地址簿暂无可用地址，请先到收货地址页面新增。</p>
        </div>
        <p class="address-warning">提交后将重新计算运费和待支付金额；一旦发起过支付或支付期限已到，将不允许修改。</p>
        <template #footer><el-button @click="addressDialog = false">取消</el-button><el-button type="primary" :loading="addressSaving" :disabled="!selectedAddressId" @click="updateAddress">确认修改</el-button></template>
      </el-dialog>
    </template>
  </MemberPage>
</template>

<style scoped lang="scss">
.enterprise-collaboration{margin-bottom:16px}
.inquiry-source{margin-bottom:16px}.inquiry-source a{color:#126b59;font-weight:700}
.status-card { display: flex; justify-content: space-between; align-items: center; padding: 28px; border-radius: 8px; background: linear-gradient(100deg, #fff2eb, #f7f8f9); }.status-card span, .status-card strong, .status-card small { display: block; }.status-card strong { margin: 9px 0; color: var(--brand); font-size: 26px; }.status-card em{display:block;margin:0 0 9px;color:#9a3412;font-style:normal;font-weight:700}.status-card small { color: var(--muted); }.status-card .toolbar :deep(.el-button) { min-width: 106px; }.detail-block { margin-top: 20px; padding: 22px; border: 1px solid var(--line); }.detail-title{display:flex;align-items:center;justify-content:space-between}.detail-block h2 { margin: 0 0 18px; font-size: 17px; }.detail-block p { color: #5e6b74; }.detail-item { display: grid; grid-template-columns: 74px 1fr 180px 130px; gap: 14px; align-items: center; padding: 12px 0; border-top: 1px solid var(--line); }.detail-image { width: 66px; height: 66px; background: #f5f5f5; }.detail-image img { width: 100%; height: 100%; object-fit: cover; }.detail-item small { display: block; margin-top: 7px; color: var(--muted); }.after-rejected{margin-top:9px;padding:10px;border-left:3px solid #d97706;background:#fff7ed;font-size:13px}.after-rejected b,.after-rejected span{display:block}.after-rejected b{color:#9a3412}.after-rejected span{margin:4px 0;color:#7c2d12}.after-rejected a{margin-right:14px;color:var(--brand)}.after-link { display: table; margin-top: 10px; padding: 6px 13px; border: 1px solid var(--brand); border-radius: 4px; color: var(--brand); font-size: 13px; }.after-link:hover { background: var(--brand); color: white; }.detail-item strong { color: #df421f; }.money-block { padding: 26px 0; text-align: right; }.money-block strong { color: #df421f; font-size: 28px; }.address-options{min-height:120px}.address-options :deep(.el-radio-group){display:grid;gap:12px;width:100%}.address-options :deep(.el-radio){width:100%;height:auto;margin:0;padding:15px}.address-options strong,.address-options span{display:block;line-height:1.7}.address-warning{margin:16px 0 0;color:#9a3412;font-size:13px}
</style>
