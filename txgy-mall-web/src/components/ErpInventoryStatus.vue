<script setup lang="ts">
export interface ErpEntry { id: string; operation: string; mode: string; status: string; message: string; attempts: number }
defineProps<{ entries?: ErpEntry[] }>()
const operations: Record<string,string> = { RESERVE:'库存预占',RELEASE:'释放预占',OUTBOUND:'出库',RETURN:'退货入库' }
const statuses: Record<string,string> = { PENDING:'待 ERP 确认',CONFIRMED:'已确认',REJECTED:'处理失败' }
</script>
<template>
  <section v-if="entries?.length" class="erp-status">
    <h3>ERP 库存处理</h3>
    <p>WMS 库存仅供查询，实际库存变更以 ERP 确认为准。</p>
    <div v-for="entry in entries" :key="entry.id" class="erp-entry">
      <strong>{{entry.mode==='mock'?'【模拟测试】':'【待对接 / 正式回传】'}} {{operations[entry.operation]||entry.operation}} · {{statuses[entry.status]||entry.status}}</strong>
      <span>{{entry.message}}</span><small>请求号：{{entry.id}}</small>
    </div>
  </section>
</template>
<style scoped>
.erp-status{padding:18px;margin:16px 0;background:#fff8e8;border:1px solid #f3d18b;border-radius:6px}
h3{margin:0 0 10px;font-size:16px}p,small{color:#715c36}.erp-entry{display:grid;gap:6px;margin:12px 0;overflow-wrap:anywhere}
</style>
