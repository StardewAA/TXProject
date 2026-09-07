<script lang="ts" setup>
import { onMounted, ref, watch } from 'vue';

import { Descriptions, DescriptionsItem, Spin } from 'ant-design-vue';

import { getPurchaseOrder } from '#/api/erp/purchase/order';

const props = defineProps<{ id: number | string }>();
const loading = ref(false);
const order = ref<any>();

async function loadOrder() {
  if (!props.id) return;
  loading.value = true;
  try {
    order.value = await getPurchaseOrder(Number(props.id));
  } finally {
    loading.value = false;
  }
}

function formatMoney(value?: number) {
  return `¥${Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}`;
}

watch(() => props.id, loadOrder);
onMounted(loadOrder);
</script>

<template>
  <Spin :spinning="loading">
    <Descriptions v-if="order" bordered :column="2" size="small" title="采购订单">
      <DescriptionsItem label="订单编号">{{ order.no }}</DescriptionsItem>
      <DescriptionsItem label="供应商">{{ order.supplierName || '-' }}</DescriptionsItem>
      <DescriptionsItem label="下单时间">{{ order.orderTime || '-' }}</DescriptionsItem>
      <DescriptionsItem label="订单金额">{{ formatMoney(order.totalPrice) }}</DescriptionsItem>
      <DescriptionsItem label="产品" :span="2">{{ order.productNames || '-' }}</DescriptionsItem>
      <DescriptionsItem label="备注" :span="2">{{ order.remark || '-' }}</DescriptionsItem>
    </Descriptions>
  </Spin>
</template>
