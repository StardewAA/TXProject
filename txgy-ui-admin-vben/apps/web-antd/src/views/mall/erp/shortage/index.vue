<script lang="ts" setup>
import { ref } from 'vue';
import { message } from 'ant-design-vue';
import { Page } from '@vben/common-ui';
import { getShortageDemand, markShortageReady, submitShortageDemand } from '#/api/mall/erp/shortage';

const requestId = ref(`REQ-${Date.now()}`);
const demandNo = ref(`MD-${Date.now()}`);
const orderNo = ref('');
const skuId = ref<number>();
const shortageQuantity = ref(1);
const result = ref<any>();

async function submit() {
  if (!skuId.value) return message.warning('请输入 SKU 编号');
  result.value = await submitShortageDemand({ requestId: requestId.value, mallDemandNo: demandNo.value, orderNo: orderNo.value || undefined, skuId: skuId.value, shortageQuantity: shortageQuantity.value });
  message.success('ERP 已接收，订单进入备货中');
}
async function query() { result.value = await getShortageDemand(requestId.value); }
async function ready() { result.value = await markShortageReady(requestId.value); message.success('已模拟库存就绪'); }
</script>

<template>
  <Page title="缺货采购联调">
    <div class="p-4 max-w-2xl space-y-3">
      <p class="text-gray-500">用于验证：缺货 → ERP 接收 → 备货中 → 库存就绪。正式 ERP 接口接入后保留同一组字段。</p>
      <input v-model="requestId" class="w-full border rounded px-3 py-2" placeholder="requestId" />
      <input v-model="demandNo" class="w-full border rounded px-3 py-2" placeholder="商城采购需求单号" />
      <input v-model="orderNo" class="w-full border rounded px-3 py-2" placeholder="订单号（可选）" />
      <input v-model.number="skuId" class="w-full border rounded px-3 py-2" type="number" placeholder="SKU 编号" />
      <input v-model.number="shortageQuantity" class="w-full border rounded px-3 py-2" type="number" min="1" placeholder="缺货数量" />
      <div class="flex gap-2">
        <button class="border rounded px-3 py-2" @click="submit">提交 ERP</button>
        <button class="border rounded px-3 py-2" @click="query">查询状态</button>
        <button class="border rounded px-3 py-2" @click="ready">模拟库存就绪</button>
      </div>
      <pre v-if="result" class="bg-gray-100 rounded p-3">{{ JSON.stringify(result, null, 2) }}</pre>
    </div>
  </Page>
</template>
