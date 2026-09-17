<script setup lang="ts">
import { useOrderWorkbench } from '@/composables/useOrderWorkbench'
import type { WorkbenchRole } from '@/api/order-workbench'
const props = withDefaults(defineProps<{ role?: WorkbenchRole; shopId?: number }>(), { role: 'BUYER' })
const workbench = useOrderWorkbench(() => props.role, () => props.shopId)
</script>
<template>
  <span v-if="workbench.key.value" class="order-workbench-badge" :title="workbench.state.value.error ? '待办暂无法更新，请打开订单工作台重试' : '待处理事项数；未读与待办分别统计'" :aria-label="workbench.state.value.error ? '待办暂无法更新' : `待处理 ${workbench.state.value.snapshot?.pendingCount ?? '正在加载'} 项`">{{ workbench.state.value.error ? '!' : (workbench.state.value.snapshot?.pendingCount ?? '…') }}</span>
</template>
<style scoped>.order-workbench-badge{display:inline-flex;align-items:center;justify-content:center;min-width:20px;padding:1px 5px;margin-left:5px;border-radius:10px;background:#b54708;color:#fff;font-size:11px;line-height:18px;vertical-align:middle}</style>
