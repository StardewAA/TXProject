<script setup lang="ts">
import { useBuyerNavigation } from '@/composables/useBuyerNavigation'
const { buyerTo, paymentTo } = useBuyerNavigation()
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'

import { tradeApi } from '@/api/mall'
import MemberPage from '@/components/MemberPage.vue'
import type { TradeOrder } from '@/types/mall'
import { formatDate, formatMoney, normalizeImage, propertyText } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const order = ref<TradeOrder | null>(null)
const loading = ref(true)
const statusNames: Record<number, string> = { 0: '待支付', 10: '待发货', 20: '待收货', 30: '已完成', 40: '已取消' }

async function load(sync = false) {
  loading.value = true
  try { order.value = await tradeApi.getOrder(String(route.params.id), sync) } finally { loading.value = false }
}

async function cancel() {
  if (!order.value) return
  await ElMessageBox.confirm('确定取消该订单吗？', '取消订单', { type: 'warning', confirmButtonText: '确定取消', cancelButtonText: '返回' })
  await tradeApi.cancelOrder(order.value.id); ElMessage.success('订单已取消'); await load()
}

async function receive() {
  if (!order.value) return
  await ElMessageBox.confirm('请确认已收到全部商品，确认后订单将完成。', '确认收货', { type: 'warning', confirmButtonText: '确认已收货', cancelButtonText: '暂不确认' })
  await tradeApi.receiveOrder(order.value.id); ElMessage.success('已确认收货'); await load(true)
}

onMounted(() => load(true))
</script>

<template>
  <MemberPage title="订单详情" description="状态会从服务端重新同步">
    <template #actions><RouterLink :to="buyerTo('/member/orders')">返回订单列表</RouterLink></template>
    <div v-if="loading" class="loading-box"><el-skeleton :rows="10" animated /></div>
    <template v-else-if="order">
      <div class="status-card"><div><span>当前状态</span><strong>{{ order.statusName || statusNames[order.status] || order.status }}</strong><small>订单号：{{ order.no || order.id }}　创建时间：{{ formatDate(order.createTime) }}</small></div><div class="toolbar"><el-button v-if="order.status === 0" @click="cancel">取消订单</el-button><el-button v-if="order.status === 0 && order.payOrderId" type="primary" @click="router.push(paymentTo(order.payOrderId, order.id))">立即支付</el-button><el-button v-if="order.status === 20" type="primary" @click="receive">确认收货</el-button><el-button v-if="order.status === 30" type="primary" @click="router.push(buyerTo(`/member/orders/${order.id}/comment`))">评价商品</el-button></div></div>
      <section class="detail-block"><h2>收货信息</h2><p>{{ order.receiverName || '-' }}　{{ order.receiverMobile || '-' }}</p><p>{{ order.receiverAreaName }} {{ order.receiverDetailAddress }}</p></section>
      <section class="detail-block"><h2>商品清单</h2><div class="detail-item" v-for="item in order.items || []" :key="item.id"><div class="detail-image"><img v-if="item.picUrl" :src="normalizeImage(item.picUrl)" alt="商品" /></div><div><RouterLink :to="`/goods/${item.spuId}`">{{ item.spuName || '商品' }}</RouterLink><small>{{ propertyText(item.properties) }}</small><RouterLink v-if="[10,20,30].includes(order.status) && Number(item.afterSaleStatus || 0) === 0" class="after-link" :to="{ path: buyerTo('/member/after-sales/apply'), query: { orderId: order.id, itemId: item.id } }">申请售后</RouterLink><RouterLink v-else-if="Number(item.afterSaleStatus || 0) > 0" class="after-link" :to="buyerTo('/member/after-sales')">查看售后</RouterLink></div><span>¥{{ formatMoney(item.price) }} × {{ item.count }}</span><strong>¥{{ formatMoney(item.payPrice ?? item.price * item.count) }}</strong></div></section>
      <section class="money-block"><span>订单实付：</span><strong>¥{{ formatMoney(order.payPrice) }}</strong></section>
    </template>
  </MemberPage>
</template>

<style scoped lang="scss">
.status-card { display: flex; justify-content: space-between; align-items: center; padding: 28px; border-radius: 8px; background: linear-gradient(100deg, #fff2eb, #f7f8f9); }.status-card span, .status-card strong, .status-card small { display: block; }.status-card strong { margin: 9px 0; color: var(--brand); font-size: 26px; }.status-card small { color: var(--muted); }.status-card .toolbar :deep(.el-button) { min-width: 106px; }.detail-block { margin-top: 20px; padding: 22px; border: 1px solid var(--line); }.detail-block h2 { margin: 0 0 18px; font-size: 17px; }.detail-block p { color: #5e6b74; }.detail-item { display: grid; grid-template-columns: 74px 1fr 180px 130px; gap: 14px; align-items: center; padding: 12px 0; border-top: 1px solid var(--line); }.detail-image { width: 66px; height: 66px; background: #f5f5f5; }.detail-image img { width: 100%; height: 100%; object-fit: cover; }.detail-item small { display: block; margin-top: 7px; color: var(--muted); }.after-link { display: table; margin-top: 10px; padding: 6px 13px; border: 1px solid var(--brand); border-radius: 4px; color: var(--brand); font-size: 13px; }.after-link:hover { background: var(--brand); color: white; }.detail-item strong { color: #df421f; }.money-block { padding: 26px 0; text-align: right; }.money-block strong { color: #df421f; font-size: 28px; }
</style>
