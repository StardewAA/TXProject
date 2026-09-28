<script setup lang="ts">
import { computed } from 'vue'
import { useOrderWorkbench } from '@/composables/useOrderWorkbench'
import type { WorkbenchRole } from '@/api/order-workbench'
const props = withDefaults(defineProps<{ role?: WorkbenchRole; shopId?: number }>(), { role: 'BUYER' })
const workbench = useOrderWorkbench(() => props.role, () => props.shopId)
const count = computed(() => props.role === 'BUYER'
  ? workbench.state.value.snapshot?.orderStatusCounts?.allCount
  : workbench.state.value.snapshot?.pendingCount)
</script>
<template>
  <span v-if="workbench.key.value" class="order-workbench-badge" :title="workbench.state.value.error ? '订单数量暂无法更新，请打开订单页面重试' : (props.role === 'BUYER' ? '全部订单数' : '待处理事项数')" :aria-label="workbench.state.value.error ? '订单数量暂无法更新' : `${props.role === 'BUYER' ? '全部订单' : '待处理'} ${count ?? '正在加载'} 项`">{{ workbench.state.value.error ? '!' : (count ?? '…') }}</span>
</template>
<style scoped>.order-workbench-badge{display:inline-flex;align-items:center;justify-content:center;min-width:20px;padding:1px 5px;margin-left:5px;border-radius:10px;background:#b54708;color:#fff;font-size:11px;line-height:18px;vertical-align:middle}</style>
