<script setup lang="ts">
import { useBuyerNavigation } from '@/composables/useBuyerNavigation'
const { buyerTo, paymentTo } = useBuyerNavigation()
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAccountMessageBox } from '@/composables/useAccountOperation'
const ElMessageBox = useAccountMessageBox()

import { tradeApi } from '@/api/mall'
import EmptyState from '@/components/EmptyState.vue'
import MemberPage from '@/components/MemberPage.vue'
import PaymentDeadlineNotice from '@/components/PaymentDeadlineNotice.vue'
import { usePaymentClock } from '@/composables/usePaymentClock'
import type { TradeOrder } from '@/types/mall'
import { formatDate, formatMoney, normalizeImage, propertyText } from '@/utils/format'
import { tradePaymentState } from '@/utils/payment-deadline'
import { createRefreshQueue } from '@/utils/refresh-queue'
import OrderWorkbench from '@/components/OrderWorkbench.vue'
import { useOrderWorkbench, useOrderWorkbenchRows } from '@/composables/useOrderWorkbench'
import { orderTabs, workbenchRowEntries } from '@/utils/order-workbench'

const route = useRoute(), router = useRouter()
const status = computed(() => ['0','10','20','30','40'].includes(String(route.query.status)) ? Number(route.query.status) : undefined)
const commentStatus = computed(() => route.query.commentStatus === 'false' ? false : undefined)
const orders = ref<TradeOrder[]>([])
const loading = ref(true)
const total = ref(0)
const pageNo = ref(1)
const statusNames: Record<number, string> = { 0: '待支付', 10: '待发货', 20: '待收货', 30: '已完成', 40: '已取消' }
const workbench = useOrderWorkbench()
const counts = useOrderWorkbenchRows(workbench, computed(() => ({ orderIds: orders.value.map(order=>order.id).join(','), commentStatus: commentStatus.value })))
function rowNotices(id: string | number) { return workbenchRowEntries(counts.snapshot.value?.rowEntries || [], id) }
function readRow(id: string | number) { void workbench.read(rowNotices(id)).catch(()=>{}) }
function selectStatus(value: string, comment = false) {
  const query = { ...route.query }
  if (value === 'all') delete query.status; else query.status = value
  if (comment) query.commentStatus = 'false'; else delete query.commentStatus
  void router.push({ path: route.path, query })
}

const clock = usePaymentClock(() => load())
const paymentStates = computed(() => new Map(orders.value.map((order) => [order.id, tradePaymentState(order, clock.now.value)])))
let refreshTimer = 0
const refreshQueue = createRefreshQueue(async (isCurrent) => {
  const requestGeneration = clock.capture()
  loading.value = orders.value.length === 0
  try {
    const page = await tradeApi.getOrders({ pageNo: pageNo.value, pageSize: 10, status: status.value, commentStatus: commentStatus.value })
    if (!isCurrent() || !clock.isCurrent(requestGeneration)) return
    orders.value = page?.list || []
    total.value = page?.total || 0
    clock.sync(orders.value.find((order) => order.serverTime)?.serverTime, requestGeneration)
  } catch { if (isCurrent() && clock.isCurrent(requestGeneration)) clock.invalidate() }
  finally { if (isCurrent()) loading.value = false }
})
function load() { return refreshQueue.refresh() }

async function cancel(id: string | number) {
  const key=workbench.key.value
  try { await ElMessageBox.confirm('确定取消该订单吗？', '取消订单', { type: 'warning', confirmButtonText: '确定取消', cancelButtonText: '返回' }) } catch { return }
  if(!key||key!==workbench.key.value)return
  await tradeApi.cancelOrder(id); ElMessage.success('订单已取消'); await load(); await workbench.refresh()
}

async function receive(id: string | number) {
  const key=workbench.key.value
  try { await ElMessageBox.confirm('请确认已收到全部商品，确认后订单将完成。', '确认收货', { type: 'warning', confirmButtonText: '确认已收货', cancelButtonText: '暂不确认' }) } catch { return }
  if(!key||key!==workbench.key.value)return
  await tradeApi.receiveOrder(id); ElMessage.success('已确认收货'); await load(); await workbench.refresh()
}

watch([status, commentStatus], () => { pageNo.value = 1 })
watch([status, commentStatus, pageNo], load, { immediate: true })
watch(workbench.key, (key) => { orders.value=[];total.value=0;if(key)void load() }, { flush:'sync' })
watch(() => workbench.state.value.updatedAt, () => { if(!loading.value)void load() })
watch(() => [...paymentStates.value].filter(([, state]) => state.expired).map(([id]) => id).join(','), (expired, old) => {
  if (expired && expired !== old) void load()
})
onMounted(() => { refreshTimer = window.setInterval(() => { if (!document.hidden && orders.value.some((order) => order.status === 0)) void load() }, 30000) })
onBeforeUnmount(() => { window.clearInterval(refreshTimer); refreshQueue.dispose() })
</script>

<template>
  <MemberPage title="我的订单" description="订单数据与移动端实时共用">
    <OrderWorkbench />
    <div class="order-tabs" role="tablist" aria-label="买家订单状态"><button v-for="tab in orderTabs" :key="tab.value" role="tab" :aria-selected="commentStatus!==false && (status===undefined?'all':String(status))===tab.value" :class="{active:commentStatus!==false && (status===undefined?'all':String(status))===tab.value}" @click="selectStatus(tab.value)">{{tab.label}} ({{counts.snapshot.value?.orderStatusCounts[tab.count] ?? '—'}})</button><button role="tab" :aria-selected="commentStatus===false" :class="{active:commentStatus===false}" @click="selectStatus('30',true)">待评价 ({{counts.snapshot.value?.orderStatusCounts.uncommentedCount ?? '—'}})</button></div>
    <p class="count-scope">页签为当前共同筛选下的订单数量；待评价与已完成重叠，不与主状态相加。<span v-if="counts.error.value">状态数量暂无法更新，显示上次结果。</span></p>
    <div v-if="loading" class="loading-box"><el-skeleton :rows="8" animated /></div>
    <EmptyState v-else-if="!orders.length" title="暂无相关订单" description="完成采购后，订单会显示在这里"><el-button @click="$router.push('/goods')">去选购</el-button></EmptyState>
    <div v-else class="order-list">
      <article v-for="order in orders" :key="order.id">
        <header><span>{{ formatDate(order.createTime) }}</span><span>订单号：{{ order.no || order.id }}</span><b v-if="rowNotices(order.id).length" class="new-order">新待办</b><strong>{{ order.statusName || statusNames[order.status] || `状态 ${order.status}` }}</strong></header>
        <PaymentDeadlineNotice :state="paymentStates.get(order.id)!" />
        <div class="order-body">
          <div class="order-goods"><div v-for="item in order.items || []" :key="item.id"><div class="order-image"><img v-if="item.picUrl" :src="normalizeImage(item.picUrl)" alt="商品" /></div><div><MallLink :to="`/goods/${item.spuId}`">{{ item.spuName || '商品' }}</MallLink><small>{{ propertyText(item.properties) }}</small><MallLink v-if="[10,20,30].includes(order.status) && Number(item.afterSaleStatus || 0) === 0" class="order-after-link" :to="{ path: buyerTo('/member/after-sales/apply'), query: { orderId: order.id, itemId: item.id } }">申请售后</MallLink><MallLink v-else-if="Number(item.afterSaleStatus || 0) > 0" class="order-after-link" :to="buyerTo('/member/after-sales')">查看售后</MallLink></div><span>× {{ item.count }}</span></div></div>
          <div class="order-money"><span>实付金额</span><strong>¥{{ formatMoney(order.payPrice) }}</strong></div>
          <div class="order-actions">
            <MallLink :to="buyerTo(`/member/orders/${order.id}`)" @click="readRow(order.id)">订单详情</MallLink>
            <MallLink v-if="order.status===30 && !order.commentStatus" :to="buyerTo(`/member/orders/${order.id}/comment`)" @click="readRow(order.id)">评价商品</MallLink>
            <MallLink v-if="paymentStates.get(order.id)?.canPay && order.payOrderId" class="primary" :to="paymentTo(order.payOrderId, order.id)">立即支付</MallLink>
            <button v-else-if="order.status === 0" @click="load">刷新状态</button>
            <button v-if="order.status === 0 && order.paymentBlockReason !== 'PAYMENT_UNCONFIRMED' && !paymentStates.get(order.id)?.expired" @click="cancel(order.id)">取消订单</button>
            <button v-if="order.status === 20" class="primary" @click="receive(order.id)">确认收货</button>
          </div>
        </div>
      </article>
    </div>
    <div v-if="total > 10" class="pagination"><el-pagination v-model:current-page="pageNo" background layout="prev, pager, next" :page-size="10" :total="total" /></div>
  </MemberPage>
</template>

<style scoped lang="scss">
.order-tabs{flex-wrap:wrap;gap:12px!important}.order-tabs button:focus-visible{outline:2px solid var(--brand);outline-offset:2px}.count-scope{font-size:12px;color:#64748b;margin:-8px 0 18px}.count-scope span{color:#a13e15}.new-order{font-size:12px;color:#a74410;border:1px solid #a74410;padding:1px 5px}
.order-tabs { display: flex; gap: 28px; margin-bottom: 18px; border-bottom: 1px solid var(--line); }.order-tabs button { padding: 10px 4px 13px; border: 0; border-bottom: 2px solid transparent; background: white; color: #56636d; }.order-tabs button.active { border-color: var(--brand); color: var(--brand); }.order-list article { margin-bottom: 16px; border: 1px solid var(--line); }.order-list header { display: flex; gap: 30px; padding: 12px 16px; background: #f7f8f9; color: var(--muted); font-size: 13px; }.order-list header strong { margin-left: auto; color: var(--brand); }.order-body { display: grid; grid-template-columns: 1fr 140px 125px; min-height: 110px; }.order-goods { padding: 8px 16px; }.order-goods > div { display: grid; grid-template-columns: 70px 1fr 50px; gap: 12px; align-items: center; padding: 8px 0; }.order-image { width: 64px; height: 64px; background: #f5f5f5; }.order-image img { width: 100%; height: 100%; object-fit: cover; }.order-goods small { display: block; margin-top: 6px; color: var(--muted); }.order-after-link { display: inline-block; margin-top: 7px; color: var(--brand); font-size: 12px; }.order-money, .order-actions { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8px; border-left: 1px solid var(--line); }.order-money span { color: var(--muted); font-size: 13px; }.order-money strong { color: #df421f; }.order-actions a, .order-actions button { border: 0; background: transparent; color: #53616b; }.order-actions .primary { padding: 6px 12px; border-radius: 3px; background: var(--brand); color: white; }.pagination { display: flex; justify-content: center; margin-top: 28px; }
</style>
