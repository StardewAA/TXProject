<script setup lang="ts">
import type { WmsItemSkuApi } from '#/api/wms/md/item/sku';
import type { WmsWarehouseApi } from '#/api/wms/md/warehouse';
import type { InventorySelectRow } from '#/views/wms/inventory/components';
import { computed, ref } from 'vue';
import { buildSimulationRequest, buildShipmentSelection, buildReceiptSelection, getRequiredMaterialType, getShipmentWarehouses, getSimulationBusinessTypes, type SimulationDetail, type SimulationDirection } from './simulation';
import { materialTypeLabel } from '#/views/wms/utils/material-type';
import { formatPrice, multiplyPrice } from '#/views/wms/utils/format';
import { confirm, useVbenModal } from '@vben/common-ui';
import { Alert, Button, Input, InputNumber, Select, message } from 'ant-design-vue';
import { requestClient } from '#/api/request';
import { WmsWarehouseSelect } from '#/views/wms/md/warehouse/components';
import { WmsItemSkuSelect } from '#/views/wms/md/item/sku/components';
import { WmsInventorySelect } from '#/views/wms/inventory/components';
import { getWarehouseSimpleList } from '#/api/wms/md/warehouse';
const direction = ref<SimulationDirection>('receipt');
const businessTypes = computed(() => getSimulationBusinessTypes(direction.value));
const title = computed(() => direction.value === 'receipt' ? '模拟 ERP / MES 入库申请' : '模拟 ERP / MES 出库申请');
const emit = defineEmits<{ success: [] }>();
const orderType = ref('PURCHASE_RECEIPT');
const warehouseId = ref<number>();
const sourceOrderNo = ref('');
const details = ref<SimulationDetail[]>([]);
const warehouses = ref<WmsWarehouseApi.Warehouse[]>([]);
const loadingWarehouses = ref(false);
const shipmentWarehouses = computed(() => getShipmentWarehouses(orderType.value, warehouses.value));
const warehouseOptions = computed(() => shipmentWarehouses.value.map(w => ({ value: w.id!, label: w.name })));
const selectedWarehouseName = computed(() => shipmentWarehouses.value.find(w => w.id === warehouseId.value)?.name);
const skuSelect = ref<InstanceType<typeof WmsItemSkuSelect>>();
const inventorySelect = ref<InstanceType<typeof WmsInventorySelect>>();
async function changeOrderType(value: string) {
  if (value === orderType.value) return;
  if (direction.value === 'shipment') {
    if (details.value.length) await confirm('切换出库业务类型将清空已选仓库及商品，是否继续？');
    details.value = [];
    warehouseId.value = undefined;
  } else if (details.value.length) {
    await confirm('切换入库业务类型将清空已选商品，是否继续？');
    details.value = [];
  }
  orderType.value = value;
}
async function changeWarehouse(value?: number) {
  if (value === warehouseId.value) return;
  if (details.value.length) await confirm('切换出库仓库将清空已选商品，是否继续？');
  details.value = [];
  warehouseId.value = value;
}
function selectGoods() {
  if (direction.value === 'shipment') {
    if (!shipmentWarehouses.value.some(w => w.id === warehouseId.value)) {
      message.warning('请先选择与出库业务类型匹配的仓库');
      return;
    }
    inventorySelect.value?.open(details.value.map(row => `${row.id}-${warehouseId.value}`));
  } else skuSelect.value?.open(details.value.map(row => row.id!), { materialType: getRequiredMaterialType(orderType.value) });
}
function addInventory(rows: InventorySelectRow[]) {
  try {
    const selected = rows.map(row => buildShipmentSelection(row, warehouseId.value!));
    for (const row of selected) {
      if (!details.value.some(detail => detail.id === row.id)) details.value.push(row);
    }
  } catch (error) {
    message.warning(error instanceof Error ? error.message : '库存选择无效，请重新选择');
  }
}
function addSkus(skus: WmsItemSkuApi.ItemSku[]) {
  try {
    const selected = skus.map(sku => buildReceiptSelection(sku, orderType.value));
    for (const sku of selected)
      if (!details.value.some((row) => row.id === sku.id)) details.value.push(sku);
  } catch (error) {
    message.warning(error instanceof Error ? error.message : '商品类型不匹配，请重新选择');
  }
}
const [Modal, modalApi] = useVbenModal({
  async onOpenChange(open) {
    if (open) {
      direction.value = modalApi.getData<{ direction: SimulationDirection }>().direction;
      orderType.value = getSimulationBusinessTypes(direction.value)[0]!.value;
      warehouseId.value = undefined;
      sourceOrderNo.value = `TEST-${Date.now()}`;
      details.value = [];
      if (direction.value === 'shipment') {
        warehouses.value = [];
        loadingWarehouses.value = true;
        try { warehouses.value = await getWarehouseSimpleList(); }
        finally { loadingWarehouses.value = false; }
      }
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
    if (direction.value === 'shipment' &&
      (!shipmentWarehouses.value.some(w => w.id === warehouseId.value) ||
       details.value.some(row => row.warehouseId !== warehouseId.value))) {
      message.warning('出库仓库或商品与当前业务不匹配，请重新选择');
      return;
    }
    modalApi.lock();
    try {
      if (direction.value === 'receipt') {
        for (const row of details.value) buildReceiptSelection(row, orderType.value);
      }
      const result = await requestClient.post<{ id: number; targetOrderNo: string }>('/wms/receipt-order/simulate-source', buildSimulationRequest(direction.value, {
        orderType: orderType.value,
        warehouseId: warehouseId.value,
        sourceOrderNo: sourceOrderNo.value.trim(),
        details: details.value.map((row) => ({ skuId: row.id!, quantity: row.quantity, price: row.price })),
      }));
      message.success(`已生成 WMS 草稿 ${result.targetOrderNo}，请到${orderType.value.endsWith('SHIPMENT') ? '出库管理确认' : '入库管理验收'}后提交审批`);
      emit('success');
      await modalApi.close();
    } catch (error) {
      if (error instanceof Error && error.message.startsWith('当前业务只支持')) message.warning(error.message);
      else throw error;
    } finally {
      modalApi.unlock();
    }
  },
});
</script>
<template>
  <Modal :title="title" class="w-[720px] max-w-[95vw]" confirm-text="生成 WMS 草稿">
    <div class="space-y-4 p-4">
      <Alert
        type="info"
        show-icon
        message="模拟来源系统已审批通过的申请。仅使用专用测试物料，不发送 ERP/MES 通知；生成草稿后，由仓库验收或确认，再提交仓库负责人审批。"
      />
      <div>业务类型<Select :value="orderType" class="w-full" :options="businessTypes" @change="changeOrderType" /></div>
      <div>来源单号<Input v-model:value="sourceOrderNo" :maxlength="40" /></div>
      <div>业务仓库
        <Select v-if="direction === 'shipment'" :value="warehouseId" class="w-full"
          :loading="loadingWarehouses" :options="warehouseOptions" allow-clear show-search option-filter-prop="label"
          placeholder="请选择出库仓库" @change="changeWarehouse" />
        <WmsWarehouseSelect v-else v-model="warehouseId" />
      </div>
      <div v-if="direction === 'shipment'" class="text-sm text-gray-500">
        {{ orderType === 'SALE_SHIPMENT' ? '销售出库从成品仓取货' : '生产领料从原料仓取料' }}；选料仅显示所选仓库中账面库存大于 0 的商品。
      </div>
      <Button :disabled="!warehouseId" @click="selectGoods"
        >{{ direction === 'shipment' ? '选择出库库存' : '选择商品规格' }}</Button
      >
      <div v-if="direction === 'receipt'" class="text-sm text-gray-500">
        当前业务接收：{{ materialTypeLabel(getRequiredMaterialType(orderType)) }}。商品类型以主数据为准，与目标仓库分别校验。
      </div>
      <div v-for="(row, index) in details" :key="row.id" class="flex items-center gap-4">
        <span class="flex-1">{{ row.itemName }} / {{ row.name }}（{{ row.unit }}）
          <span v-if="direction === 'receipt'" class="block text-sm">物料类型：{{ materialTypeLabel(row.materialType) }}</span>
          <span v-if="direction === 'shipment'" class="block text-sm text-gray-500">{{ selectedWarehouseName }} · 账面库存：{{ row.stockQuantity }}</span>
        </span>
        <div><div>申请数量</div><InputNumber v-model:value="row.quantity" :min="0.01" :precision="2" /></div>
        <div><div>来源单价（元）</div><InputNumber v-model:value="row.price" :min="0" :precision="2" placeholder="未提供" /></div>
        <div>金额（元）：{{ formatPrice(multiplyPrice(row.quantity, row.price)) || '未提供' }}</div>
        <Button danger @click="details.splice(index, 1)">移除</Button>
      </div>
      <WmsInventorySelect v-if="direction === 'shipment'" ref="inventorySelect" :warehouse-id="warehouseId"
        quantity-title="账面库存" @change="addInventory" />
      <WmsItemSkuSelect v-else ref="skuSelect" show-material-type @change="addSkus" />
    </div>
  </Modal>
</template>

