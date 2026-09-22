<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { platformCheckoutApi, type PlatformCheckoutDetail, type PlatformCheckoutSummary } from '@/api/platform-commerce'
import EmptyState from '@/components/EmptyState.vue'
import MemberPage from '@/components/MemberPage.vue'
import { formatMoney } from '@/utils/format'

const rows = ref<PlatformCheckoutSummary[]>([])
const total = ref(0)
const pageNo = ref(1)
const loading = ref(false)
const loadFailed = ref(false)
const detail = ref<PlatformCheckoutDetail | null>(null)
const detailVisible = ref(false)
const detailLoading = ref(false)

const payStatusLabels: Record<string, string> = {
  NOT_PAYABLE: '暂不可支付', WAITING: '待支付', PAID: '已支付', PART_PAID: '部分支付',
  REFUNDED: '已退款', PART_REFUNDED: '部分退款', CLOSED: '已关闭',
}
const aggregateStatusLabels: Record<string, string> = {
  DRAFT_ONLY: '拆单规划', CREATED: '已创建子订单', PROCESSING: '处理中',
  ALL_UNPAID: '全部待支付', PARTIALLY_PAID: '部分已支付', ALL_UNDELIVERED: '全部待发货',
  PARTIALLY_DELIVERED: '部分已发货', ALL_DELIVERED: '全部已发货',
  PARTIALLY_COMPLETED: '部分完成', ALL_COMPLETED: '全部完成',
  PARTIALLY_CANCELED: '部分取消', ALL_CANCELED: '全部取消',
  PARTIALLY_REFUNDED: '部分退款', ALL_REFUNDED: '全部退款',
  PART_COMPLETED: '部分完成', COMPLETED: '全部完成', PART_REFUNDED: '部分退款',
  REFUNDED: '全部退款', FAILED: '失败',
}

function statusLabel(row: { aggregateStatus?: string | null }) {
  const status = row.aggregateStatus || ''
  return aggregateStatusLabels[status] || status || '未知状态'
}

async function load() {
  loading.value = true
  loadFailed.value = false
  try {
    const result = await platformCheckoutApi.page({ pageNo: pageNo.value, pageSize: 10 })
    rows.value = result.list || []
    total.value = Number(result.total || 0)
  } catch {
    rows.value = []
    total.value = 0
    loadFailed.value = true
  } finally {
    loading.value = false
  }
}

async function openDetail(row: { checkoutNo?: string }) {
  if (!row.checkoutNo) return
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await platformCheckoutApi.get(row.checkoutNo)
  } finally {
    detailLoading.value = false
  }
}

onMounted(load)
</script>

<template>
  <MemberPage title="平台结算记录" description="查看跨租户购物车提交后的结算与拆单结果">
    <el-alert
      title="当前仅保存不可支付的拆单规划；未生成正式订单、支付单，也不会清除购物车。"
      type="warning"
      :closable="false"
      show-icon
    />
    <p v-if="loadFailed" class="error" role="alert">结算记录加载失败，请刷新重试；这不代表没有历史记录。</p>
    <el-table v-else :data="rows" v-loading="loading" class="checkout-history">
      <el-table-column prop="checkoutNo" label="平台结算单号" min-width="210" />
      <el-table-column label="创建时间" min-width="170"><template #default="{ row }">{{ row.createTime ? new Date(row.createTime).toLocaleString() : '-' }}</template></el-table-column>
      <el-table-column label="应付金额" width="130"><template #default="{ row }">¥{{ formatMoney(row.payAmount) }}</template></el-table-column>
      <el-table-column label="结算状态" width="130"><template #default="{ row }"><el-tag :type="row.planningOnly ? 'warning' : 'info'">{{ statusLabel(row) }}</el-tag></template></el-table-column>
      <el-table-column label="支付状态" width="130"><template #default="{ row }">{{ payStatusLabels[row.payStatus] || row.payStatus }}</template></el-table-column>
      <el-table-column label="操作" width="100"><template #default="{ row }"><el-button link type="primary" @click="openDetail(row)">查看拆单</el-button></template></el-table-column>
    </el-table>
    <EmptyState v-if="!loading && !loadFailed && !rows.length" title="暂无平台结算记录" description="跨租户购物车提交结算后会显示在这里" />
    <el-pagination
      v-if="total > 10"
      v-model:current-page="pageNo"
      :page-size="10"
      :total="total"
      layout="prev, pager, next, total"
      @current-change="load"
    />

    <el-dialog v-model="detailVisible" title="平台结算拆单详情" width="900px">
      <div v-loading="detailLoading" class="checkout-detail">
        <template v-if="detail">
          <div class="detail-heading"><div><small>平台结算单</small><strong>{{ detail.checkoutNo }}</strong></div><el-tag type="warning">{{ statusLabel(detail) }}</el-tag></div>
          <dl><dt>商品金额</dt><dd>¥{{ formatMoney(detail.goodsAmount) }}</dd><dt>运费</dt><dd>¥{{ formatMoney(detail.freightAmount) }}</dd><dt>优惠</dt><dd>-¥{{ formatMoney(detail.discountAmount) }}</dd><dt>应付合计</dt><dd class="amount">¥{{ formatMoney(detail.payAmount) }}</dd></dl>
          <el-alert v-if="detail.planningOnly" title="这是拆单规划快照，当前不可支付、不可发货，不代表订单已经创建。" type="warning" :closable="false" />
          <article v-for="plan in detail.childPlans || []" :key="plan.planNo" class="child-plan">
            <header><div><b>{{ plan.tenantName || `租户 ${plan.tenantId}` }}</b><span>{{ plan.enterpriseName || `企业 ${plan.sellerEnterpriseId}` }} · {{ plan.shopName || `店铺 ${plan.shopId}` }}</span></div><strong>¥{{ formatMoney(plan.payAmount) }}</strong></header>
            <p>规划号 {{ plan.planNo }} · {{ plan.itemCount }} 种商品 / {{ plan.totalQuantity }} 件 · 状态 {{ plan.planStatus }}</p>
            <ul><li v-for="item in plan.items" :key="String(item.cartItemId)"><span>{{ item.spuName || `商品 ${item.spuId}` }} <small>{{ item.properties?.map(value => value.valueName).filter(Boolean).join(' / ') }}</small></span><b>× {{ item.quantity }}</b><strong>¥{{ formatMoney(item.goodsAmount) }}</strong></li></ul>
          </article>
        </template>
      </div>
    </el-dialog>
  </MemberPage>
</template>

<style scoped>
.error{padding:12px 14px;background:#fff1f0;color:#a33;border-radius:6px}.checkout-history{margin-top:20px}.el-pagination{justify-content:flex-end;margin-top:22px}.checkout-detail{min-height:180px}.detail-heading{display:flex;justify-content:space-between;align-items:center}.detail-heading small,.detail-heading strong{display:block}.detail-heading small{margin-bottom:6px;color:var(--muted)}.detail-heading strong{font-size:19px}.checkout-detail dl{display:grid;grid-template-columns:repeat(4,auto 1fr);gap:8px 12px;padding:18px;margin:20px 0;background:#f5f8f7}.checkout-detail dt{color:var(--muted)}.checkout-detail dd{margin:0;font-weight:700}.checkout-detail dd.amount{color:#d85b2c}.child-plan{margin-top:18px;padding:18px;border:1px solid var(--line);border-radius:8px}.child-plan header{display:flex;justify-content:space-between}.child-plan header b,.child-plan header span{display:block}.child-plan header span,.child-plan p,.child-plan small{margin-top:5px;color:var(--muted);font-size:12px}.child-plan header>strong{color:#d85b2c}.child-plan ul{padding:0;margin:14px 0 0;list-style:none}.child-plan li{display:grid;grid-template-columns:1fr 80px 110px;gap:12px;padding:10px 0;border-top:1px dashed var(--line)}.child-plan li>strong{text-align:right}
</style>
