<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import QRCode from 'qrcode'
import { useRoute, useRouter } from 'vue-router'

import { payApi } from '@/api/mall'
import { formatMoney } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const order = ref<Record<string, unknown> | null>(null)
const channels = ref<string[]>([])
const selected = ref('mock')
const loading = ref(true)
const paying = ref(false)
const paymentVisible = ref(false)
const qrImage = ref('')
const paymentForm = ref('')
const checking = ref(false)
let pollTimer = 0
const returnPath = computed(() => {
  const redirect = route.query.redirect
  return typeof redirect === 'string' && /^\/(?!\/)/.test(redirect)
    ? redirect : route.query.orderId ? `/member/orders/${route.query.orderId}` : '/member/orders'
})
const amount = computed(() => Number(order.value?.price ?? order.value?.amount ?? 0))
const labels: Record<string, string> = { mock: '模拟支付（测试）', wallet: '账户余额支付', alipay_pc: '支付宝电脑支付', wx_native: '微信扫码支付' }
const descriptions: Record<string, string> = { mock: '仅用于本地联调，不会真实扣款', wallet: '从商城账户余额中扣款', alipay_pc: '跳转支付宝电脑网站完成支付', wx_native: '使用微信扫描二维码完成支付' }
const pcChannelCodes = new Set(Object.keys(labels))

async function load() {
  loading.value = true
  try {
    order.value = await payApi.getOrder({ id: String(route.params.orderId), sync: true })
    const appId = Number(order.value?.appId || 0)
    if (appId) channels.value = (await payApi.getChannels(appId)).filter((code) => pcChannelCodes.has(code)
      && !(returnPath.value === '/member/wallet' && code === 'wallet'))
    if (channels.value.length && !channels.value.includes(selected.value)) selected.value = channels.value[0] || ''
  } finally { loading.value = false }
}

async function submit() {
  if (paying.value || !channels.value.includes(selected.value)) return
  paying.value = true
  qrImage.value = ''
  paymentForm.value = ''
  try {
    const result = await payApi.submit({ id: String(route.params.orderId), channelCode: selected.value, channelExtras: {} })
    if (Number(result.status) === 10) {
      await checkPayment()
      return
    }
    const content = typeof result.displayContent === 'string' ? result.displayContent : ''
    if (result.displayMode === 'url' && /^https?:\/\//i.test(content)) {
      window.location.assign(content)
      return
    }
    if (result.displayMode === 'qr_code' && content) qrImage.value = await QRCode.toDataURL(content, { width: 280, margin: 2 })
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
  checking.value = true
  try {
    order.value = await payApi.getOrder({ id: String(route.params.orderId), sync: true })
    if (Number(order.value?.status) === 10) {
      paymentVisible.value = false
      ElMessage.success('支付成功')
      await router.replace(returnPath.value)
    } else if (Number(order.value?.status) === 30) {
      paymentVisible.value = false
      ElMessage.warning('支付订单已关闭，请重新下单')
    } else if (!silent) ElMessage.info('暂未收到支付成功结果，请完成支付后再次查询')
  } catch { /* 请求层已提示失败，保留当前收银台供重试。 */ }
  finally { checking.value = false }
}

watch(paymentVisible, (visible) => {
  window.clearInterval(pollTimer)
  if (visible) pollTimer = window.setInterval(() => void checkPayment(true), 5000)
})
onBeforeUnmount(() => window.clearInterval(pollTimer))
onMounted(load)
</script>

<template>
  <div class="container pay-page page-section">
    <div class="section-heading"><div><h1>订单收银台</h1><p>支付结果最终以后端订单状态为准</p></div></div>
    <section class="panel pay-card" v-loading="loading"><template v-if="order"><div class="pay-order"><div><span>支付订单</span><strong>{{ order.no || route.params.orderId }}</strong></div><div><span>应付金额</span><b>¥{{ formatMoney(amount) }}</b></div></div><h2>选择支付方式</h2><div v-if="channels.length" class="channels"><button v-for="channel in channels" :key="channel" :class="{ active: selected === channel }" @click="selected = channel"><i>¥</i><span><b>{{ labels[channel] }}</b><small>{{ descriptions[channel] }}</small></span></button></div><div v-else class="channel-empty">当前没有适用于电脑网页的支付渠道，请在后台启用支付宝电脑支付、微信扫码支付、余额支付或模拟支付</div><div class="pay-actions"><el-button @click="router.push(returnPath)">返回订单或钱包</el-button><el-button type="primary" size="large" :disabled="!channels.length || Number(order.status) !== 0" :loading="paying" @click="submit">确认支付 ¥{{ formatMoney(amount) }}</el-button></div></template></section>
    <el-dialog v-model="paymentVisible" title="完成支付" width="680px">
      <div v-if="qrImage" class="payment-code"><img :src="qrImage" alt="支付二维码" /><p>请使用对应支付应用扫码，支付完成后状态将自动更新。</p></div>
      <iframe v-if="paymentForm" :srcdoc="paymentForm" sandbox="allow-forms allow-scripts allow-popups allow-top-navigation-by-user-activation" title="支付渠道页面" class="payment-frame" />
      <template #footer><el-button @click="paymentVisible = false">稍后支付</el-button><el-button type="primary" :loading="checking" @click="checkPayment(false)">我已支付，查询结果</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.payment-code { text-align: center; }.payment-code img { width: 280px; height: 280px; }.payment-frame { width: 100%; height: 420px; border: 0; }
.pay-page { max-width: 1000px; }.pay-card { min-height: 400px; padding: 34px; }.pay-order { display: flex; justify-content: space-between; align-items: center; padding: 24px; background: #f7f8f9; }.pay-order span, .pay-order strong, .pay-order b { display: block; }.pay-order strong { margin-top: 8px; }.pay-order b { margin-top: 8px; color: #df421f; font-size: 30px; }.pay-card h2 { margin: 30px 0 18px; font-size: 18px; }.channels { display: grid; grid-template-columns: repeat(2, 1fr); gap: 14px; }.channels button { min-height: 82px; display: flex; align-items: center; gap: 14px; padding: 12px 20px; border: 1px solid var(--line); background: white; text-align: left; }.channels button.active { border: 2px solid var(--brand); background: #fff8f4; }.channels i { width: 38px; height: 38px; flex: 0 0 auto; display: grid; place-items: center; border-radius: 50%; background: var(--brand); color: white; font-style: normal; }.channels span b, .channels span small { display: block; }.channels span small { margin-top: 5px; color: var(--muted); }.channel-empty { padding: 30px; background: #fff8e8; color: #8a6d31; }.pay-actions { margin-top: 40px; text-align: right; }
</style>
