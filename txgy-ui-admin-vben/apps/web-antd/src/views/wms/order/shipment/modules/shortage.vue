<script setup lang="ts">
import type { ShipmentShortage } from '#/api/wms/order/shipment/shortage';
import { ref, watch } from 'vue';
import { Alert, message } from 'ant-design-vue';
import { TableAction } from '#/adapter/vxe-table';
import { getShipmentShortages, recheckShipmentStock, retryShipmentShortage } from '#/api/wms/order/shipment/shortage';
const props = defineProps<{ orderId?: number | string; canRecheck?: boolean }>();
const emit = defineEmits<{ checked: [] }>();
const rows = ref<ShipmentShortage[]>([]);
const busy = ref(false);
const labels: Record<string,string> = {
  WAITING_INTEGRATION: '待对接', PENDING: '待发送', SENDING: '发送中',
  FAILED: '发送失败', SUCCEEDED: '已成功通知', SIMULATED: '模拟，不发送',
};
const gapLabels = { OPEN: '待补货', RESOLVED: '缺口已消除', CANCELLED: '已取消' };
async function refresh() {
  rows.value = props.orderId ? await getShipmentShortages(Number(props.orderId)) : [];
}
async function recheck() {
  if (!props.orderId || !props.canRecheck) return;
  busy.value = true;
  try {
    rows.value = await recheckShipmentStock(Number(props.orderId));
    message.info(rows.value.some(row => row.shortageStatus === 'OPEN')
      ? '仍有库存缺口，请等待补货' : '当前检查无缺口，可按正常流程提交仓库审批');
    emit('checked');
  } finally { busy.value = false; }
}
async function retry(row: ShipmentShortage) {
  busy.value = true;
  try {
    const accepted = await retryShipmentShortage(row.eventId);
    await refresh();
    if (accepted) message.success('接收系统已确认收到消息');
    else message.info('尚未确认接收，请查看通知状态和最近错误');
  } finally { busy.value = false; }
}
watch(() => props.orderId, refresh, { immediate: true });
</script>
<template>
  <section class="space-y-3">
    <div class="flex items-center justify-between">
      <h3>库存缺口通知</h3>
      <TableAction :actions="[
        { label: '刷新通知', onClick: refresh, disabled: busy, auth: ['wms:shipment-order:query'] },
        { label: '重新检查库存', onClick: recheck, disabled: busy || !props.canRecheck, auth: ['wms:shipment-order:submit-approval'] },
      ]" />
    </div>
    <Alert type="info" show-icon message="通知成功仅表示对方收到消息。补货后请重新检查库存，再提交仓库审批；当前未启用库存预留。" />
    <div v-if="rows.length" class="overflow-x-auto">
      <table class="w-full border-collapse text-left text-sm">
        <thead><tr><th>来源行 / 物料</th><th>仓库</th><th>申请</th><th>当前可用</th><th>行缺口</th><th>目标 / 通知</th><th>缺口状态</th><th>发送记录</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="row in rows" :key="row.eventId" class="border-t">
            <td class="p-2">{{ row.sourceLineNo }} / {{ row.skuName }}<br />{{ row.skuCode }}</td>
            <td>{{ row.warehouseName }}</td><td>{{ row.requestedQuantity }} {{ row.unit }}</td>
            <td>{{ row.availableQuantity }}</td><td>{{ row.shortageQuantity }}</td>
            <td>{{ row.targetSystem }} / {{ labels[row.notificationStatus] || row.notificationStatus }}</td>
            <td>{{ gapLabels[row.shortageStatus] }}</td>
            <td>版本 {{ row.eventVersion }}；发送 {{ row.attempts }} 次<br />{{ row.lastSentTime || '尚未发送' }}<br />{{ row.lastError }}</td>
            <td><TableAction :actions="[{ label: '重试通知', onClick: () => retry(row), disabled: busy || ['SUCCEEDED','SIMULATED'].includes(row.notificationStatus), auth: ['wms:shipment-order:submit-approval'] }]" /></td>
          </tr>
        </tbody>
      </table>
      <p class="text-gray-500">同一规格多行按来源行顺序分配库存，行缺口为分配后的缺少数量。发送次数包含首次发送。</p>
    </div>
    <p v-else>暂无已记录缺口。可重新检查当前库存。</p>
  </section>
</template>
