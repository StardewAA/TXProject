<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import { tradeApi } from '@/api/mall'
import EmptyState from '@/components/EmptyState.vue'
import MemberPage from '@/components/MemberPage.vue'
import type { TradeOrder } from '@/types/mall'
import { formatDate, formatMoney, normalizeImage, propertyText } from '@/utils/format'

const status = ref<number | undefined>()
const orders = ref<TradeOrder[]>([])
const loading = ref(true)
const total = ref(0)
const pageNo = ref(1)
const statusNames: Record<number, string> = { 0: '待支付', 10: '待发货', 20: '待收货', 30: '已完成', 40: '已取消' }

async function load() {
  loading.value = true
  try {
    const page = await tradeApi.getOrders({ pageNo: pageNo.value, pageSize: 10, status: status.value })
    orders.value = page?.list || []
    total.value = page?.total || 0
  } finally { loading.value = false }
}

async function cancel(id: string | number) {
  await ElMessageBox.confirm('确定取消该订单吗？', '取消订单', { type: 'warning', confirmButtonText: '确定取消', cancelButtonText: '返回' })
  await tradeApi.cancelOrder(id); ElMessage.success('订单已取消'); await load()
}

async function receive(id: string | number) {
  await ElMessageBox.confirm('请确认已收到全部商品，确认后订单将完成。', '确认收货', { type: 'warning', confirmButtonText: '确认已收货', cancelButtonText: '暂不确认' })
  await tradeApi.receiveOrder(id); ElMessage.success('已确认收货'); await load()
}

watch([status, pageNo], load)
onMounted(load)
</script>

<template>
  <MemberPage title="我的订单" description="订单数据与移动端实时共用">
    <div class="order-tabs"><button :class="{ active: status === undefined }" @click="status = undefined">全部</button><button v-for="item in [0,10,20,30,40]" :key="item" :class="{ active: status === item }" @click="status = item">{{ statusNames[item] }}</button></div>
    <div v-if="loading" class="loading-box"><el-skeleton :rows="8" animated /></div>
    <EmptyState v-else-if="!orders.length" title="暂无相关订单" description="完成采购后，订单会显示在这里"><el-button @click="$router.push('/goods')">去选购</el-button></EmptyState>
    <div v-else class="order-list"><article v-for="order in orders" :key="order.id"><header><span>{{ formatDate(order.createTime) }}</span><span>订单号：{{ order.no || order.id }}</span><strong>{{ order.statusName || statusNames[order.status] || `状态 ${order.status}` }}</strong></header><div class="order-body"><div class="order-goods"><div v-for="item in order.items || []" :key="item.id"><div class="order-image"><img v-if="item.picUrl" :src="normalizeImage(item.picUrl)" alt="商品" /></div><div><RouterLink :to="`/goods/${item.spuId}`">{{ item.spuName || '商品' }}</RouterLink><small>{{ propertyText(item.properties) }}</small><RouterLink v-if="[10,20,30].includes(order.status) && Number(item.afterSaleStatus || 0) === 0" class="order-after-link" :to="{ path: '/member/after-sales/apply', query: { orderId: order.id, itemId: item.id } }">申请售后</RouterLink><RouterLink v-else-if="Number(item.afterSaleStatus || 0) > 0" class="order-after-link" to="/member/after-sales">查看售后</RouterLink></div><span>× {{ item.count }}</span></div></div><div class="order-money"><span>实付金额</span><strong>¥{{ formatMoney(order.payPrice) }}</strong></div><div class="order-actions"><RouterLink :to="`/member/orders/${order.id}`">订单详情</RouterLink><RouterLink v-if="order.status === 0 && order.payOrderId" class="primary" :to="`/pay/${order.payOrderId}?orderId=${order.id}`">立即支付</RouterLink><button v-if="order.status === 0" @click="cancel(order.id)">取消订单</button><button v-if="order.status === 20" class="primary" @click="receive(order.id)">确认收货</button></div></div></article></div>
    <div v-if="total > 10" class="pagination"><el-pagination v-model:current-page="pageNo" background layout="prev, pager, next" :page-size="10" :total="total" /></div>
  </MemberPage>
</template>

<style scoped lang="scss">
.order-tabs { display: flex; gap: 28px; margin-bottom: 18px; border-bottom: 1px solid var(--line); }.order-tabs button { padding: 10px 4px 13px; border: 0; border-bottom: 2px solid transparent; background: white; color: #56636d; }.order-tabs button.active { border-color: var(--brand); color: var(--brand); }.order-list article { margin-bottom: 16px; border: 1px solid var(--line); }.order-list header { display: flex; gap: 30px; padding: 12px 16px; background: #f7f8f9; color: var(--muted); font-size: 13px; }.order-list header strong { margin-left: auto; color: var(--brand); }.order-body { display: grid; grid-template-columns: 1fr 140px 125px; min-height: 110px; }.order-goods { padding: 8px 16px; }.order-goods > div { display: grid; grid-template-columns: 70px 1fr 50px; gap: 12px; align-items: center; padding: 8px 0; }.order-image { width: 64px; height: 64px; background: #f5f5f5; }.order-image img { width: 100%; height: 100%; object-fit: cover; }.order-goods small { display: block; margin-top: 6px; color: var(--muted); }.order-after-link { display: inline-block; margin-top: 7px; color: var(--brand); font-size: 12px; }.order-money, .order-actions { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8px; border-left: 1px solid var(--line); }.order-money span { color: var(--muted); font-size: 13px; }.order-money strong { color: #df421f; }.order-actions a, .order-actions button { border: 0; background: transparent; color: #53616b; }.order-actions .primary { padding: 6px 12px; border-radius: 3px; background: var(--brand); color: white; }.pagination { display: flex; justify-content: center; margin-top: 28px; }
</style>
