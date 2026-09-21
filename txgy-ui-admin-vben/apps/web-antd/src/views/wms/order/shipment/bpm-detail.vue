<script setup lang="ts">
import type { ShipmentExecution, WmsShipmentOrderApi } from '#/api/wms/order/shipment';
import { computed, ref, watch } from 'vue';
import OrderReference from '../shared/order-reference.vue';
import Shortage from './modules/shortage.vue';
import { useRoute, useRouter } from 'vue-router';
import { Alert, Button, message } from 'ant-design-vue';
import { getShipmentOrder, getShipmentExecution } from '#/api/wms/order/shipment';
import { getInventoryList } from '#/api/wms/inventory';
import { buildApprovalDetailLocation } from '#/views/bpm/approval-route';
const props = defineProps<{ id?: number | string }>();
const emit = defineEmits<{ navigate: [] }>();
const router = useRouter();
const route = useRoute();
const onCurrentApproval = computed(() => route.name === 'WmsApprovalDetail' && route.query.id === order.value.processInstanceId);
async function viewApproval() {
  await router.push(buildApprovalDetailLocation('WmsApprovalDetail', order.value.processInstanceId!));
  emit('navigate');
}
const order = ref<WmsShipmentOrderApi.ShipmentOrder>({});
const execution = ref<ShipmentExecution>();
const inventory = ref<Record<number, number>>({});
const inventoryLoading = ref(false);
const inventoryQueried = ref(false);
const statusLabels: Record<string, string> = { DRAFT: '草稿', WAITING_APPROVAL: '等待审批', WAITING_EXECUTION: '等待执行', SUCCESS: '出库成功', EXECUTION_FAILED: '执行失败', REJECTED: '审批拒绝', CANCELLED: '已取消' };
async function refresh() {
  if (!props.id) return;
  inventory.value = {};
  inventoryQueried.value = false;
  order.value = await getShipmentOrder(Number(props.id));
  execution.value = await getShipmentExecution(Number(props.id));
}
async function queryInventory() {
  if (!order.value.warehouseId) {
    message.warning('该出库单尚未指定出货仓库');
    return;
  }
  inventoryLoading.value = true;
  inventoryQueried.value = false;
  inventory.value = {};
  try {
    const rows = await getInventoryList({ warehouseId: order.value.warehouseId });
    inventory.value = Object.fromEntries(rows.map(row => [row.skuId!, row.quantity ?? 0]));
    inventoryQueried.value = true;
  } finally {
    inventoryLoading.value = false;
  }
}
watch(() => props.id, refresh, { immediate: true });
</script>
<template>
  <div class="space-y-4 p-4">
    <OrderReference :no="order.no" :business-no="order.bizOrderNo" />
    <div class="flex flex-wrap gap-3">
      <Button @click="refresh">刷新执行结果</Button>
      <Button v-if="order.processInstanceId && !onCurrentApproval" @click="viewApproval">查看 BPM 审批</Button>
      <span v-if="onCurrentApproval">当前已在本单 BPM 审批页</span>
      <Button :loading="inventoryLoading" @click="queryInventory">查询出货仓库账面库存</Button>
    </div>
    <Alert v-if="inventoryQueried" type="info" show-icon :message="`出货仓库：${order.warehouseName || '未命名仓库'}（本单商品当前库存）`">
      <template #description>
        <div v-for="(line, index) in order.details" :key="line.id ?? index">
          {{ line.itemName }} / {{ line.skuName || line.skuCode }}：{{ line.skuId ? inventory[line.skuId] ?? 0 : '无法匹配规格' }} {{ line.unit }}
        </div>
        <div v-if="!order.details?.length">本单暂无商品明细。</div>
        <div>这是当前现存量，包含其他已执行单据；本单审批通过并执行前不扣减库存。</div>
      </template>
    </Alert>
    <p>单号：{{ order.no }}；来源：{{ order.sourceModule || 'WMS' }} / {{ order.bizOrderNo }}；仓库：{{ order.warehouseName }}</p>
    <Shortage v-if="order.sourceModule" :order-id="props.id" :can-recheck="order.status === 0 && order.approvalStatus !== 1" @checked="refresh" />
    <p v-for="line in order.details" :key="`price-${line.id}`">{{ line.itemName }} / {{ line.skuName }}：来源单价（元）{{ line.price ?? '未提供' }}；来源金额（元）{{ line.totalPrice ?? '未提供' }}</p>
    <Alert v-if="execution" :type="execution.status === 'EXECUTION_FAILED' ? 'error' : 'info'" show-icon>
      <template #message>状态：{{ statusLabels[execution.status ?? ''] || execution.status }}；已出库数量：{{ execution.completedQuantity ?? 0 }}；通知：{{ execution.notificationStatus }}</template>
      <template #description>{{ order.executionError || execution.notificationError }}<span v-if="execution.notificationStatus === 'SIMULATED'">模拟来源，不向 ERP/MES 发送通知。</span></template>
    </Alert>
    <table class="w-full border-collapse text-left">
      <thead><tr><th class="p-2">商品 / SKU</th><th>确认出库数量</th><th>当前账面库存</th></tr></thead>
      <tbody><tr v-for="line in order.details" :key="line.id" class="border-t"><td class="p-2">{{ line.itemName }} / {{ line.skuName || line.skuCode }}</td><td>{{ line.quantity }} {{ line.unit }}</td><td>{{ inventory[line.skuId!] ?? '待查询' }}</td></tr></tbody>
    </table>
  </div>
</template>
