<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import QRCode from 'qrcode'
import { useRoute, useRouter } from 'vue-router'

import { payApi, tradeApi } from '@/api/mall'
import PaymentDeadlineNotice from '@/components/PaymentDeadlineNotice.vue'
import { usePaymentClock } from '@/composables/usePaymentClock'
import type { TradeOrder } from '@/types/mall'
import { formatMoney } from '@/utils/format'
import { getPaymentOptions, type PaymentOption } from '@/utils/payment'
import { cashierPaymentState, isWalletCashierEntry, paymentTimestamp } from '@/utils/payment-deadline'
import { createRefreshQueue } from '@/utils/refresh-queue'

const route = useRoute()
const router = useRouter()
const order = ref<Record<string, unknown> | null>(null)
const tradeOrder = ref<TradeOrder | null>(null)
const previewMode = import.meta.env.VITE_PAYMENT_PREVIEW === 'true'
const channels = ref<PaymentOption[]>([])
const selected = ref('')
const loading = ref(true)
const paying = ref(false)
const paymentVisible = ref(false)
const qrImage = ref('')
const paymentForm = ref('')
const checking = ref(false)
const previewVisible = ref(false)
const previewOption = ref<PaymentOption | null>(null)
const paymentStatus = computed(() => Number(order.value?.status))
const selectedOption = computed(() => channels.value.find((option) => option.code === selected.value))
let pollTimer = 0
let channelsKey = ''
let disposed = false
let lastPoll = 0
const platformCheckoutEntry = computed(() => typeof route.query.platformCheckout === 'string' && route.query.platformCheckout.trim() !== '')
const returnPath = computed(() => {
  const redirect = route.query.redirect
  return typeof redirect === 'string' && /^\/(?!\/)/.test(redirect)
    ? redirect
    : platformCheckoutEntry.value
      ? '/member/platform-checkouts'
      : route.query.orderId ? `/member/orders/${route.query.orderId}` : '/member/orders'
})
const amount = computed(() => Number(order.value?.price ?? order.value?.amount ?? 0))
const walletEntry = computed(() => isWalletCashierEntry(route.query))
const requiresTradeOrder = computed(() => !walletEntry.value && !platformCheckoutEntry.value)
const routeKey = computed(() => `${route.params.orderId}|${route.query.orderId || ''}|${route.query.platformCheckout || ''}|${returnPath.value}`)
const clock = usePaymentClock(() => load())
const deadlineState = computed(() => cashierPaymentState(order.value, clock.now.value, tradeOrder.value, requiresTradeOrder.value))

function closePayment() {
  paymentVisible.value = false
  previewVisible.value = false
  qrImage.value = ''
  paymentForm.value = ''
}

const refreshQueue = createRefreshQueue(async (isCurrent) => {
  const requestGeneration = clock.capture()
  loading.value = !order.value
  try {
    const id = String(route.params.orderId)
    const tradeId = route.query.orderId
    const needsTrade = requiresTradeOrder.value
    const wallet = walletEntry.value
    const result = await payApi.getOrder({ id, sync: true })
    const payReceivedAt = performance.now()
    if (!isCurrent() || !clock.isCurrent(requestGeneration)) return
    if (!result || String(result.id) !== id) throw new Error('支付订单关联异常，请返回订单重新进入')
    let relatedOrder: TradeOrder | null = null
    if (needsTrade) {
      if (typeof tradeId === 'string' && /^\d+$/.test(tradeId)) {
        relatedOrder = await tradeApi.getOrder(tradeId, true)
        if (!isCurrent() || !clock.isCurrent(requestGeneration)) return
      }
    }
    order.value = result
    tradeOrder.value = relatedOrder
    const payServerTime = paymentTimestamp(result.serverTime)
    clock.sync(payServerTime === undefined ? relatedOrder?.serverTime : payServerTime + performance.now() - payReceivedAt, requestGeneration)
    const appId = Number(result.appId || 0)
    const nextKey = `${appId}|${wallet}`
    if (nextKey !== channelsKey) {
      const enabled = appId ? await payApi.getChannels(appId) : []
      if (!isCurrent() || !clock.isCurrent(requestGeneration)) return
      channels.value = getPaymentOptions(enabled, previewMode, wallet)
      channelsKey = nextKey
      if (!channels.value.some((option) => option.code === selected.value)) selected.value = channels.value[0]?.code || ''
    }
  } catch (error) {
    if (isCurrent() && clock.isCurrent(requestGeneration)) {
      clock.invalidate()
      closePayment()
      if (error instanceof Error && /关联异常|编号无效/.test(error.message)) ElMessage.error(error.message)
    }
  } finally { if (isCurrent()) loading.value = false }
})
function load() { return refreshQueue.refresh() }

function beginPayment() {
  clock.sample()
  if (paying.value || loading.value || !deadlineState.value.canPay || !selectedOption.value) return
  if (selectedOption.value.preview) {
    previewOption.value = selectedOption.value
    previewVisible.value = true
  } else void submit(selectedOption.value)
}

async function submit(option: PaymentOption) {
  if (option.preview || paying.value || !deadlineState.value.canPay || !channels.value.some((item) => item.code === option.code)) return
  const paymentRoute = routeKey.value
  paying.value = true
  qrImage.value = ''
  paymentForm.value = ''
  try {
    // Recheck immediately before submission; an old QR/page does not preserve payment eligibility.
    await load()
    clock.sample()
    if (disposed || paymentRoute !== routeKey.value || !deadlineState.value.canPay) return
    const result = await payApi.submit({ id: String(route.params.orderId), channelCode: option.channelCode, channelExtras: {} })
    if (disposed || paymentRoute !== routeKey.value) return
    if (Number(result.status) === 10) {
      await checkPayment()
      return
    }
    clock.sample()
    if (!deadlineState.value.canPay) { closePayment(); await checkPayment(true); return }
    const content = typeof result.displayContent === 'string' ? result.displayContent : ''
    if (result.displayMode === 'url' && /^https?:\/\//i.test(content)) {
      window.location.assign(content)
      return
    }
    if (result.displayMode === 'qr_code' && content) {
      const image = await QRCode.toDataURL(content, { width: 280, margin: 2 })
      clock.sample()
      if (disposed || paymentRoute !== routeKey.value || !deadlineState.value.canPay) return
      qrImage.value = image
    }
    else if (result.displayMode === 'qr_code_url' && /^https?:\/\//i.test(content)) qrImage.value = content
    else if (result.displayMode === 'form' && content) paymentForm.value = content
    else {
      ElMessage.warning('支付尚未完成，请稍后查询订单状态')
      return
    }
    paymentVisible.value = true
  } catch (error) {
    if (error instanceof Error) ElMessage.error(error.message)
  } finally { paying.value = false }
}

async function checkPayment(silent = false) {
  if (checking.value) return
  const paymentRoute = routeKey.value
  checking.value = true
  try {
    await load()
    if (disposed || paymentRoute !== routeKey.value) return
    if (Number(order.value?.status) === 10) {
      closePayment()
      ElMessage.success('支付成功')
      await router.replace(returnPath.value)
    } else if (Number(order.value?.status) === 30) {
      closePayment()
      if (!silent) ElMessage.warning('支付订单已关闭，请查看订单或钱包记录')
    } else if (!silent) ElMessage.info(deadlineState.value.canPay ? '暂未收到支付成功结果，请完成支付后再次查询' : deadlineState.value.message || '请查看最新支付记录')
  } catch { /* 请求层已提示失败，保留当前收银台供重试。 */ }
  finally { checking.value = false }
}

watch(() => deadlineState.value.canPay, (canPay) => { if (!canPay) closePayment() })
watch(() => deadlineState.value.expired, (expired, before) => { if (expired && !before) void checkPayment(true) })
watch(routeKey, () => {
  closePayment()
  order.value = null
  tradeOrder.value = null
  channels.value = []
  channelsKey = ''
  clock.invalidate()
  void load()
}, { immediate: true })
onMounted(() => {
  pollTimer = window.setInterval(() => {
    const interval = paymentVisible.value || deadlineState.value.expired ? 5000 : 30000
    if (!document.hidden && paymentStatus.value === 0 && performance.now() - lastPoll >= interval) {
      lastPoll = performance.now()
      void checkPayment(true)
    }
  }, 1000)
})
onBeforeUnmount(() => { disposed = true; refreshQueue.dispose(); window.clearInterval(pollTimer) })
</script>

<template>
  <div class="container pay-page page-section">
    <div class="section-heading"><div><h1>订单收银台</h1><p>确认金额，选择支付方式后完成付款</p></div></div>
    <section class="panel pay-card" v-loading="loading">
      <template v-if="order">
        <div class="pay-order"><div><span>支付订单</span><strong>{{ order.no || route.params.orderId }}</strong></div><div><span>{{ paymentStatus === 10 ? '已付金额' : '应付金额' }}</span><b>¥{{ formatMoney(amount) }}</b></div></div>
        <el-result v-if="paymentStatus === 10" icon="success" :title="order.channelCode === 'mock' ? '模拟付款成功' : '支付成功'" :sub-title="order.channelCode === 'mock' ? '测试付款已完成，没有扣除支付宝或微信资金。可进入订单查看最新进度。' : '付款已确认，可查看订单或账户余额。'">
          <template #extra><el-button @click="router.push('/goods')">继续采购</el-button><el-button type="primary" @click="router.push(returnPath)">{{ returnPath === '/member/wallet' ? '查看钱包' : '查看订单' }}</el-button></template>
        </el-result>
        <el-result v-else-if="paymentStatus === 20 || paymentStatus === 30" icon="info" :title="paymentStatus === 20 ? '订单已发生退款' : '支付订单已关闭'" sub-title="请进入订单或钱包查看最新记录。"><template #extra><el-button type="primary" @click="router.push(returnPath)">查看记录</el-button></template></el-result>
        <template v-else>
          <PaymentDeadlineNotice :state="deadlineState" />
          <div v-if="previewMode" class="preview-notice"><b>第三方支付暂未开通</b><span>可查看支付宝、微信的扫码收银台；当前无法扫码付款，订单将保持待付款状态。</span></div>
          <h2>选择支付方式</h2>
          <div v-if="channels.length" class="channels">
            <button v-for="channel in channels" :key="channel.code" :class="{ active: selected === channel.code }" :disabled="paying || !deadlineState.canPay" :aria-pressed="selected === channel.code" @click="selected = channel.code"><i :class="channel.tone">{{ channel.icon }}</i><span><b>{{ channel.label }}</b><small>{{ channel.description }}</small></span></button>
          </div>
          <div v-else class="channel-empty">当前没有可用的电脑网页支付方式，请联系客服。</div>
          <div class="pay-actions"><el-button @click="router.push(returnPath)">返回订单或钱包</el-button><el-button :loading="checking" @click="checkPayment(false)">查询支付结果</el-button><el-button type="primary" size="large" :disabled="!selectedOption || !deadlineState.canPay || loading" :loading="paying" @click="beginPayment">{{ selectedOption?.preview ? '进入扫码收银台' : selectedOption?.channelCode === 'mock' ? '模拟付款' : '确认支付' }} ¥{{ formatMoney(amount) }}</el-button></div>
        </template>
      </template>
      <el-empty v-else-if="!loading" description="支付订单加载失败"><el-button @click="load">重新加载</el-button></el-empty>
    </section>
    <el-dialog v-model="previewVisible" :title="`${previewOption?.label || '第三方支付'}收银台`" width="520px" align-center class="preview-dialog">
      <div class="preview-cashier">
        <div class="provider-heading"><span class="provider-badge" :class="previewOption?.tone">{{ previewOption?.icon }}</span><span>{{ previewOption?.tone === 'wechat' ? '微信' : '支付宝' }}扫码支付</span></div>
        <p>防腐保温购物平台 · 订单付款</p><strong>¥{{ formatMoney(amount) }}</strong>
        <div class="scan-placeholder" role="img" aria-label="二维码待开通，当前无可扫描的支付二维码"><div class="scan-corners"><span class="provider-badge" :class="previewOption?.tone">{{ previewOption?.icon }}</span><b>二维码待开通</b></div></div>
        <div class="preview-explanation"><b>暂未接通{{ previewOption?.tone === 'wechat' ? '微信' : '支付宝' }}支付服务</b><span>当前无法生成付款二维码，请勿扫码或转账。<br />接通第三方支付后，这里将展示正式付款二维码。</span></div>
        <p class="pending-tip">关闭此窗口不会付款，订单仍为待付款。</p>
      </div>
      <template #footer><el-button @click="previewVisible = false; router.push(returnPath)">返回订单或钱包</el-button><el-button type="primary" @click="previewVisible = false">选择其他支付方式</el-button></template>
    </el-dialog>
    <el-dialog v-model="paymentVisible" title="完成支付" width="680px">
      <div v-if="qrImage" class="payment-code"><img :src="qrImage" alt="支付二维码" /><p>请使用对应支付应用扫码，支付完成后状态将自动更新。</p></div>
      <iframe v-if="paymentForm" :srcdoc="paymentForm" sandbox="allow-forms allow-scripts allow-popups allow-top-navigation-by-user-activation" title="支付渠道页面" class="payment-frame" />
      <template #footer><el-button @click="paymentVisible = false">稍后支付</el-button><el-button type="primary" :loading="checking" @click="checkPayment(false)">我已支付，查询结果</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.preview-notice { display: flex; flex-direction: column; gap: 6px; margin-top: 22px; padding: 16px 20px; color: #865721; background: #fff8e8; border-radius: 8px; line-height: 1.7; }
.preview-cashier { text-align: center; padding: 8px 12px; }.provider-heading { display: flex; justify-content: center; align-items: center; gap: 12px; font-size: 20px; font-weight: 600; }.provider-badge { display: grid; place-items: center; width: 40px; height: 40px; border-radius: 10px; color: white; background: var(--brand); font-size: 24px; font-weight: 700; }.preview-cashier p { margin-top: 18px; color: var(--muted); }.preview-cashier strong { display: block; margin: 10px 0 20px; font-size: 36px; color: #24323b; }.scan-placeholder { width: 210px; height: 210px; margin: 0 auto 22px; border: 1px solid #e4e9ee; border-radius: 12px; background: #f7f9fb; display: grid; place-items: center; }.scan-corners { width: 155px; height: 155px; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 18px; border: 2px dashed #b8c5d0; border-radius: 6px; color: #6a7986; }.scan-corners .provider-badge { opacity: .45; }.preview-explanation { padding: 16px; border-radius: 8px; background: #fff8e8; color: #865721; line-height: 1.8; }.preview-explanation b, .preview-explanation span { display: block; }.preview-explanation span { margin-top: 6px; font-size: 13px; }.pending-tip { font-size: 12px; }.channels i.alipay, .provider-badge.alipay { background: #1677ff; }.channels i.wechat, .provider-badge.wechat { background: #16a45c; }
.payment-code { text-align: center; }.payment-code img { width: 280px; height: 280px; }.payment-frame { width: 100%; height: 420px; border: 0; }
.pay-page { max-width: 1000px; }.pay-card { min-height: 400px; padding: 34px; }.pay-order { display: flex; justify-content: space-between; align-items: center; padding: 24px; background: #f7f8f9; }.pay-order span, .pay-order strong, .pay-order b { display: block; }.pay-order strong { margin-top: 8px; }.pay-order b { margin-top: 8px; color: #df421f; font-size: 30px; }.pay-card h2 { margin: 30px 0 18px; font-size: 18px; }.channels { display: grid; grid-template-columns: repeat(2, 1fr); gap: 14px; }.channels button { min-height: 82px; display: flex; align-items: center; gap: 14px; padding: 12px 20px; border: 1px solid var(--line); background: white; text-align: left; }.channels button.active { border: 2px solid var(--brand); background: #fff8f4; }.channels i { width: 38px; height: 38px; flex: 0 0 auto; display: grid; place-items: center; border-radius: 50%; background: var(--brand); color: white; font-style: normal; }.channels span b, .channels span small { display: block; }.channels span small { margin-top: 5px; color: var(--muted); }.channel-empty { padding: 30px; background: #fff8e8; color: #8a6d31; }.pay-actions { margin-top: 40px; text-align: right; }
</style>
