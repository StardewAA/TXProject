import { describe, expect, it } from 'vitest';
import { buildSimulationRequest, getSimulationBusinessTypes, getShipmentWarehouses, buildShipmentSelection, getWarehouseMismatchMessage } from './simulation';
import { getRequiredMaterialType, buildReceiptSelection } from './simulation';
import { materialTypeLabel } from '../../utils/material-type';

describe('receipt material visibility and selection', () => {
  it('shows Chinese labels without guessing unknown master data', () => {
    expect(materialTypeLabel('RAW_MATERIAL')).toBe('原材料');
    expect(materialTypeLabel('FINISHED_GOODS')).toBe('成品');
    expect(materialTypeLabel(undefined)).toBe('未设置');
    expect(materialTypeLabel('OTHER')).toBe('未识别类型');
  });
  it('requires the material type of each supported business', () => {
    expect(getRequiredMaterialType('PURCHASE_RECEIPT')).toBe('RAW_MATERIAL');
    expect(getRequiredMaterialType('PRODUCTION_RECEIPT')).toBe('FINISHED_GOODS');
    expect(getRequiredMaterialType('SALE_SHIPMENT')).toBe('FINISHED_GOODS');
    expect(getRequiredMaterialType('PRODUCTION_SHIPMENT')).toBe('RAW_MATERIAL');
    expect(() => getRequiredMaterialType('UNKNOWN')).toThrow();
  });
  it('keeps actual master type in the selected receipt detail', () => {
    expect(buildReceiptSelection({ id: 11, itemName: '专用商品', materialType: 'RAW_MATERIAL' }, 'PURCHASE_RECEIPT'))
      .toMatchObject({ id: 11, materialType: 'RAW_MATERIAL', quantity: 1 });
  });
  it('rejects finished or unclassified goods before sending a purchase request', () => {
    for (const materialType of ['FINISHED_GOODS', undefined]) {
      expect(() => buildReceiptSelection({ id: 11, materialType }, 'PURCHASE_RECEIPT')).toThrow('原材料');
    }
  });
  it('rejects raw material left over after switching to production receipt', () => {
    expect(() => buildReceiptSelection({ id: 11, materialType: 'RAW_MATERIAL' }, 'PRODUCTION_RECEIPT')).toThrow('成品');
  });
});

describe('WMS simulation entry direction', () => {
  it('offers only incoming business on the receipt page', () => {
    expect(getSimulationBusinessTypes('receipt').map(x => x.value))
      .toEqual(['PURCHASE_RECEIPT', 'PRODUCTION_RECEIPT']);
  });
  it('offers only outgoing business on the shipment page', () => {
    expect(getSimulationBusinessTypes('shipment').map(x => x.value))
      .toEqual(['SALE_SHIPMENT', 'PRODUCTION_SHIPMENT']);
  });
  it('rejects a stale selection from the other direction before sending a request', () => {
    expect(() => buildSimulationRequest('receipt', {
      orderType: 'SALE_SHIPMENT', warehouseId: 107, sourceOrderNo: 'SELF-001',
      details: [{ skuId: 20030, quantity: 3 }],
    })).toThrow('业务类型与当前入口不匹配');
  });
  it('preserves the selected source and quantities in the existing request contract', () => {
    expect(buildSimulationRequest('shipment', {
      orderType: 'PRODUCTION_SHIPMENT', warehouseId: 106, sourceOrderNo: ' SELF-001 ',
      details: [{ skuId: 20029, quantity: 3 }],
    })).toEqual({ orderType: 'PRODUCTION_SHIPMENT', warehouseId: 106,
      sourceOrderNo: 'SELF-001', details: [{ skuId: 20029, quantity: 3 }] });
  });
});

describe('shipment warehouse selection', () => {
  it('explains an incompatible receipt or shipment warehouse in Chinese', () => {
    expect(getWarehouseMismatchMessage('PURCHASE_RECEIPT', 'FINISHED_GOODS')).toContain('原材料仓');
    expect(getWarehouseMismatchMessage('SALE_SHIPMENT', 'RAW_MATERIAL')).toContain('成品仓');
    expect(getWarehouseMismatchMessage('PRODUCTION_RECEIPT', 'FINISHED_GOODS')).toBeUndefined();
  });
  const warehouses = [
    { id: 106, name: '原料仓', warehouseType: 'RAW_MATERIAL' as const },
    { id: 107, name: '成品仓', warehouseType: 'FINISHED_GOODS' as const },
    { id: 108, name: '名称含成品但类型未配置' },
  ];
  it('limits ERP sales to warehouses classified as finished goods', () => {
    expect(getShipmentWarehouses('SALE_SHIPMENT', warehouses).map(w => w.id)).toEqual([107]);
  });
  it('limits MES material issues to raw material warehouses', () => {
    expect(getShipmentWarehouses('PRODUCTION_SHIPMENT', warehouses).map(w => w.id)).toEqual([106]);
  });
  it('does not infer warehouse type from its name or accept an incoming business', () => {
    expect(getShipmentWarehouses('PURCHASE_RECEIPT', warehouses)).toEqual([]);
    expect(getShipmentWarehouses('SALE_SHIPMENT', [warehouses[2]!])).toEqual([]);
  });
  it('uses the stock row SKU ID, preserves warehouse and shows actual ledger quantity', () => {
    const selected = buildShipmentSelection({ id: 940125, skuId: 20030, skuName: '标准',
      skuCode: 'FG-SKU', itemName: '成品', warehouseId: 107, quantity: 9, unit: 'kg' }, 107);
    expect(selected).toMatchObject({ id: 20030, name: '标准', code: 'FG-SKU',
      warehouseId: 107, stockQuantity: 9, quantity: 1, unit: 'kg' });
  });
  it('rejects a stale selection from the previously selected warehouse', () => {
    expect(() => buildShipmentSelection({ skuId: 20029, warehouseId: 106, quantity: 6 }, 107))
      .toThrow('库存不属于当前出库仓库');
  });
  it('does not accept unscoped, missing SKU or nonpositive stock rows', () => {
    for (const row of [
      { skuId: 20030, quantity: 9 },
      { warehouseId: 107, quantity: 9 },
      { warehouseId: 107, skuId: 20030, quantity: 0 },
      { warehouseId: 107, skuId: 20030, quantity: -1 },
    ]) expect(() => buildShipmentSelection(row, 107)).toThrow();
  });
});
