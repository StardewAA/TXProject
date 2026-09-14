<script setup lang="ts">
import type { ReceiptExecution, WmsReceiptOrderApi } from '#/api/wms/order/receipt';
import { computed, ref, watch } from 'vue';
import OrderReference from '../shared/order-reference.vue';
import { useRoute, useRouter } from 'vue-router';
import { Alert, Button, Image, message } from 'ant-design-vue';
import {
  getReceiptOrder,
  getReceiptExecution,
  retryReceiptNotification,
} from '#/api/wms/order/receipt';
import { getInventoryList } from '#/api/wms/inventory';
import { useDescription } from '#/components/description';
import { useDetailSchema } from './data';
const props = defineProps<{ id?: number | string }>();
const emit = defineEmits<{ navigate: [] }>();
const router = useRouter();
const route = useRoute();
const order = ref<WmsReceiptOrderApi.ReceiptOrder>({});
const execution = ref<ReceiptExecution>();
const loading = ref(false);
const inventory = ref<Record<number, number>>();
const inventoryLoading = ref(false);
const onCurrentApproval = computed(() =>
  route.path === '/bpm/process-instance/detail' &&
  route.query.id === order.value.processInstanceId
);
async function viewApproval() {
  await router.push({
    path: '/bpm/process-instance/detail',
    query: { id: order.value.processInstanceId },
  });
  emit('navigate');
}
async function queryInventory() {
  if (!order.value.warehouseId) {
    message.warning('该入库单尚未指定收货仓库');
    return;
  }
  inventoryLoading.value = true;
  inventory.value = undefined;
  try {
  const rows = await getInventoryList({ warehouseId: order.value.warehouseId });
  inventory.value = Object.fromEntries(
    rows.filter((row) => row.skuId != null).map((row) => [row.skuId!, row.quantity ?? 0])
  );
  } finally {
    inventoryLoading.value = false;
  }
}
const approvalLabels: Record<number, string> = {
  '-1': '待提交',
  0: '待提交',
  1: '审批中',
  2: '已通过',
  3: '已拒绝',
  4: '已取消',
};
const executionLabels: Record<string, string> = {
  SUCCESS: '入库成功',
  REJECTED: '审批拒绝',
  CANCELLED: '已取消',
  WAITING_EXECUTION: '等待执行',
  WAITING_APPROVAL: '等待审批',
  DRAFT: '草稿',
};
const notificationLabels: Record<string, string> = {
  NOT_REQUIRED: '无需通知',
  POLLING_ONLY: '仅结果查询',
  SIMULATED: '模拟申请',
  WAITING_EXECUTION: '等待执行',
  PENDING: '待通知',
  FAILED: '通知失败',
  DELIVERED: '已送达',
};
const [Descriptions] = useDescription({
  bordered: true,
  column: 2,
  schema: useDetailSchema(),
  useCard: false,
});
async function refresh() {
  if (!props.id) return;
  loading.value = true;
  try {
    order.value = await getReceiptOrder(Number(props.id));
    inventory.value = undefined;
    execution.value = await getReceiptExecution(Number(props.id));
  } finally {
    loading.value = false;
  }
}
async function retry() {
  await retryReceiptNotification(Number(props.id));
  message.success('已重试通知');
  await refresh();
}
watch(() => props.id, refresh, { immediate: true });
</script>
<template>
  <div class="space-y-4 p-4">
    <OrderReference :no="order.no" :business-no="order.bizOrderNo" />
    <div class="flex flex-wrap items-center gap-3">
      <span>审批：{{ approvalLabels[order.approvalStatus ?? 0] || '未知' }}</span>
      <span>执行：{{ order.executionStatus === 1 ? '已入库' : '待执行' }}</span>
      <Button :loading="loading" @click="refresh">刷新执行结果</Button>
      <Button
        v-if="order.processInstanceId && !onCurrentApproval"
        @click="viewApproval"
        >查看 BPM 审批</Button
      >
      <span v-if="onCurrentApproval" class="text-gray-500">当前已在本单 BPM 审批页</span>
    </div>
    <Button :loading="inventoryLoading" @click="queryInventory">查询收货仓库当前库存</Button>
    <Alert v-if="inventory" type="info" show-icon message="收货仓库当前库存（本单商品）">
      <template #description>
        <div v-for="(row, index) in order.details" :key="row.id ?? index">
          {{ row.itemName }} / {{ row.skuName }}：{{ row.skuId ? inventory[row.skuId] ?? 0 : '无法匹配规格' }} {{ row.unit }}
        </div>
        <div v-if="!order.details?.length">本单暂无商品明细。</div>
        <div>这是当前现存量，包含其他已执行单据；本单审批通过前不增加库存。</div>
      </template>
    </Alert>
    <Descriptions :data="order" />
    <Alert v-if="execution" type="info" show-icon>
      <template #message
        >执行单：{{ execution.targetOrderNo || order.no }}；状态：{{
          executionLabels[execution.status ?? ''] || execution.status || '待执行'
        }}；已入库合格数量：{{ execution.completedQuantity ?? 0 }}；来源系统通知：{{
          notificationLabels[execution.notificationStatus ?? ''] ||
          execution.notificationStatus ||
          '尚未通知'
        }}</template
      >
      <template #description>
        <span v-if="execution.notificationStatus === 'SIMULATED'"
          >模拟申请，不向真实 ERP/MES 发送通知。</span
        >
        <span v-if="execution.notificationError"
          >{{ execution.notificationError }}
          <Button size="small" @click="retry">重试通知</Button></span
        >
      </template>
    </Alert>
    <div v-for="(row, index) in order.details" :key="row.id ?? index" class="rounded border p-4">
      <div class="mb-2 font-semibold">
        {{ index + 1 }}. {{ row.itemName }} / {{ row.skuName }}（{{ row.unit }}）
      </div>
      <div>
        申请：{{ row.quantity }}；实收：{{ row.actualQuantity ?? '未验收' }}；合格：{{
          row.qualifiedQuantity ?? '未验收'
        }}；不合格：{{ row.unqualifiedQuantity ?? '未验收' }}
      </div>
      <div>
        质检要求：{{ row.qualityRequiredSnapshot === false ? '免检' : '需质检图片' }}；验收说明：{{
          row.qualityRemark || '-'
        }}
      </div>
      <div v-if="inventory && row.skuId">
        当前仓库库存：{{ inventory[row.skuId] ?? 0 }} {{ row.unit }}（包含其他已执行单据）
      </div>
      <div class="my-2 flex flex-wrap gap-2">
        <span>来源单价（元）：{{ row.price ?? '未提供' }}；来源金额（元）：{{ row.totalPrice ?? '未提供' }}</span>
        <Image v-for="url in row.qualityImages" :key="url" :src="url" :width="100" />
      </div>
      <div v-if="row.sourceSnapshot" class="mt-3 bg-gray-50 p-3">
        <div class="font-medium">来源原申请快照</div>
        <div>
          物料：{{ row.sourceSnapshot.materialNameSnapshot }}；规格：{{
            row.sourceSnapshot.specificationSnapshot || '-'
          }}；单位：{{ row.sourceSnapshot.unitNameSnapshot }}；批次：{{
            row.sourceSnapshot.batchNo || '-'
          }}
        </div>
        <div v-for="(file, fileIndex) in row.sourceSnapshot.attachments" :key="fileIndex">
          <a :href="file.url" target="_blank" rel="noopener noreferrer">{{
            file.fileName || file.name || '来源附件'
          }}</a>
        </div>
      </div>
    </div>
  </div>
</template>
