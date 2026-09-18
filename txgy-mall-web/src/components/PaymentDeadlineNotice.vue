<script setup lang="ts">
import { formatDate } from '@/utils/format'
import { formatPaymentRemaining, type PaymentDeadlineState } from '@/utils/payment-deadline'
defineProps<{ state: PaymentDeadlineState }>()
</script>

<template>
  <div v-if="state.kind !== 'none'" class="payment-deadline" :class="{ expired: state.expired }" role="status">
    <strong v-if="state.remaining !== undefined">{{ state.kind === 'erp' ? '库存确认等待' : '付款剩余' }}：{{ formatPaymentRemaining(state.remaining) }}</strong>
    <span>{{ state.message }}</span>
    <small v-if="state.deadline">{{ state.kind === 'erp' ? '等待' : '支付' }}截止：{{ formatDate(state.deadline) }}（本地时间）</small>
  </div>
</template>

<style scoped>
.payment-deadline { display: flex; flex-wrap: wrap; align-items: center; gap: 6px 16px; padding: 12px 16px; background: #fff8e8; color: #805719; font-size: 13px; line-height: 1.7; }
.payment-deadline strong { font-variant-numeric: tabular-nums; }.payment-deadline small { color: #697680; }.payment-deadline.expired { background: #fff1ef; color: #a43727; }
</style>
