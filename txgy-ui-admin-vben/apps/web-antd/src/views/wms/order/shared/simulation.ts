import type { WmsInventoryApi } from '#/api/wms/inventory';
import type { WmsItemSkuApi } from '#/api/wms/md/item/sku';
import type { WmsWarehouseApi } from '#/api/wms/md/warehouse';
import { materialTypeLabel } from '../../utils/material-type';

export type SimulationDirection = 'receipt' | 'shipment';
export function getRequiredMaterialType(orderType: string): string {
  if (['PURCHASE_RECEIPT', 'PRODUCTION_SHIPMENT'].includes(orderType)) return 'RAW_MATERIAL';
  if (['PRODUCTION_RECEIPT', 'SALE_SHIPMENT'].includes(orderType)) return 'FINISHED_GOODS';
  throw new Error('业务类型不支持');
}
export function getWarehouseMismatchMessage(orderType: string, warehouseType?: string): string | undefined {
  const requiredType = getRequiredMaterialType(orderType);
  if (warehouseType === requiredType) return undefined;
  return `当前业务需要${requiredType === 'RAW_MATERIAL' ? '原材料仓' : '成品仓'}，请选择匹配的业务仓库`;
}
export function buildReceiptSelection(sku: WmsItemSkuApi.ItemSku, orderType: string): SimulationDetail {
  const requiredType = getRequiredMaterialType(orderType);
  if (!sku.id || sku.materialType !== requiredType) {
    throw new Error(`当前业务只支持${materialTypeLabel(requiredType)}，请重新选择商品`);
  }
  return { ...sku, quantity: 1 };
}
export type SimulationDetail = WmsItemSkuApi.ItemSku & {
  quantity: number;
  price?: number;
  warehouseId?: number;
  stockQuantity?: number;
};

const businessTypes = {
  receipt: [
    { label: 'ERP 采购入库（原料）', value: 'PURCHASE_RECEIPT' },
    { label: 'MES 完工入库（成品）', value: 'PRODUCTION_RECEIPT' },
  ],
  shipment: [
    { label: 'ERP 销售出库（成品）', value: 'SALE_SHIPMENT' },
    { label: 'MES 领料出库（原料）', value: 'PRODUCTION_SHIPMENT' },
  ],
};

export function getSimulationBusinessTypes(direction: SimulationDirection) {
  return businessTypes[direction];
}

export function getShipmentWarehouses(orderType: string, warehouses: WmsWarehouseApi.Warehouse[]) {
  const warehouseType = orderType === 'SALE_SHIPMENT' ? 'FINISHED_GOODS'
    : orderType === 'PRODUCTION_SHIPMENT' ? 'RAW_MATERIAL' : undefined;
  return warehouseType ? warehouses.filter(w => w.id && w.warehouseType === warehouseType) : [];
}

export function buildShipmentSelection(row: WmsInventoryApi.Inventory, warehouseId: number): SimulationDetail {
  if (!warehouseId || row.warehouseId !== warehouseId) {
    throw new Error('库存不属于当前出库仓库');
  }
  if (!row.skuId || !Number.isFinite(row.quantity) || row.quantity! <= 0) {
    throw new Error('请选择有账面库存的商品规格');
  }
  return { id: row.skuId, name: row.skuName, code: row.skuCode, itemId: row.itemId,
    itemName: row.itemName, itemCode: row.itemCode, unit: row.unit,
    warehouseId, stockQuantity: row.quantity, quantity: Math.min(1, row.quantity!) };
}

export function buildSimulationRequest(direction: SimulationDirection, input: {
  orderType: string;
  warehouseId: number;
  sourceOrderNo: string;
  details: { skuId: number; quantity: number; price?: number }[];
}) {
  if (!businessTypes[direction].some(type => type.value === input.orderType)) {
    throw new Error('业务类型与当前入口不匹配');
  }
  return { ...input, sourceOrderNo: input.sourceOrderNo.trim() };
}
