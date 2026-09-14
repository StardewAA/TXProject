<script setup lang="ts">
import type { WmsItemSkuApi } from '#/api/wms/md/item/sku';
import { ref } from 'vue';
import { useVbenModal } from '@vben/common-ui';
import { Alert, Button, Input, InputNumber, Select, message } from 'ant-design-vue';
import { requestClient } from '#/api/request';
import { WmsWarehouseSelect } from '#/views/wms/md/warehouse/components';
import { WmsItemSkuSelect } from '#/views/wms/md/item/sku/components';
const emit = defineEmits<{ success: [] }>();
const orderType = ref('PURCHASE_RECEIPT');
const warehouseId = ref<number>();
const sourceOrderNo = ref('');
const details = ref<(WmsItemSkuApi.ItemSku & { quantity: number })[]>([]);
const skuSelect = ref<InstanceType<typeof WmsItemSkuSelect>>();
function addSkus(skus: WmsItemSkuApi.ItemSku[]) {
  for (const sku of skus)
    if (sku.id && !details.value.some((row) => row.id === sku.id))
      details.value.push({ ...sku, quantity: 1 });
}
const [Modal, modalApi] = useVbenModal({
  onOpenChange(open) {
    if (open) {
      warehouseId.value = undefined;
      sourceOrderNo.value = `ERP-TEST-${Date.now()}`;
      details.value = [];
    }
  },
  async onConfirm() {
    if (
      !warehouseId.value ||
      !sourceOrderNo.value.trim() ||
      !details.value.length ||
      details.value.some((row) => !row.quantity || row.quantity <= 0)
    ) {
      message.warning('请选择仓库、商品，填写来源单号及大于零的数量');
      return;
    }
    modalApi.lock();
    try {
      const result = await requestClient.post<{ id: number; targetOrderNo: string }>('/wms/receipt-order/simulate-source', {
        orderType: orderType.value,
        warehouseId: warehouseId.value,
        sourceOrderNo: sourceOrderNo.value.trim(),
        details: details.value.map((row) => ({ skuId: row.id!, quantity: row.quantity })),
      });
      message.success(`已生成 WMS 草稿 ${result.targetOrderNo}，请到${orderType.value.endsWith('SHIPMENT') ? '出库管理确认' : '入库管理验收'}后提交审批`);
      emit('success');
      await modalApi.close();
    } finally {
      modalApi.unlock();
    }
  },
});
</script>
<template>
  <Modal title="模拟 ERP / MES 申请" class="w-2/3" confirm-text="生成 WMS 草稿">
    <div class="space-y-4 p-4">
      <Alert
        type="info"
        show-icon
        message="开发验证入口：仅使用专用测试物料，不发送 ERP/MES 通知。入库请验收后审批；出库请到出库管理确认并提交人工 BPM 审批。"
      />
      <div>业务类型<Select v-model:value="orderType" class="w-full" :options="[
        { label: 'ERP 采购入库（原料）', value: 'PURCHASE_RECEIPT' },
        { label: 'ERP 销售出库（成品）', value: 'SALE_SHIPMENT' },
        { label: 'MES 完工入库（成品）', value: 'PRODUCTION_RECEIPT' },
        { label: 'MES 领料出库（原料）', value: 'PRODUCTION_SHIPMENT' },
      ]" /></div>
      <div>来源单号<Input v-model:value="sourceOrderNo" :maxlength="40" /></div>
      <div>业务仓库<WmsWarehouseSelect v-model="warehouseId" /></div>
      <Button :disabled="!warehouseId" @click="skuSelect?.open(details.map((row) => row.id!))"
        >选择商品规格</Button
      >
      <div v-for="(row, index) in details" :key="row.id" class="flex items-center gap-4">
        <span class="flex-1">{{ row.itemName }} / {{ row.name }}（{{ row.unit }}）</span>
        <InputNumber v-model:value="row.quantity" :min="0.01" :precision="2" />
        <Button danger @click="details.splice(index, 1)">移除</Button>
      </div>
      <WmsItemSkuSelect ref="skuSelect" @change="addSkus" />
    </div>
  </Modal>
</template>

