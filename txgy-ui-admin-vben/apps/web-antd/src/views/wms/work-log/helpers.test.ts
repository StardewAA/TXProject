import { describe, expect, it } from 'vitest';

import {
  buildChangeFieldRows,
  buildChangeRecords,
  buildReviewEntries,
  buildReadableConditions,
  buildWorkLogQuery,
  getWarehouseArea,
  humanizeBusinessKey,
  humanizeWorkSummary,
  parseJsonValue,
  parseWorkLogChanges,
} from './helpers';

describe('work log display helpers', () => {
  it('drops a stale member filter when querying the personal scope', () => {
    expect(
      buildWorkLogQuery('SELF', {
        action: '审批',
        pageNo: 3,
        pageSize: 20,
        userId: 987,
      }),
    ).toEqual({
      action: '审批',
      module: 'wms',
      pageNo: 3,
      pageSize: 20,
      scope: 'SELF',
    });
  });

  it('keeps the selected member when querying the authorized team scope', () => {
    expect(
      buildWorkLogQuery('ALL', {
        pageNo: 1,
        pageSize: 10,
        userId: 987,
      }),
    ).toEqual({
      module: 'wms',
      pageNo: 1,
      pageSize: 10,
      scope: 'ALL',
      userId: 987,
    });
  });

  it('normalizes recorded changes into entries and collection notes', () => {
    expect(
      parseWorkLogChanges(
        JSON.stringify({
          entries: [
            {
              after: { status: 'APPROVED' },
              before: { status: 'PENDING' },
              operation: 'UPDATE',
              table: 'wms_order',
            },
          ],
          warnings: ['部分关联数据未采集'],
        }),
      ),
    ).toEqual({
      entries: [
        {
          after: { status: 'APPROVED' },
          before: { status: 'PENDING' },
          operation: 'UPDATE',
          table: 'wms_order',
        },
      ],
      malformed: false,
      warnings: ['部分关联数据未采集'],
    });
  });

  it('reports malformed change data without throwing', () => {
    expect(parseWorkLogChanges('{bad json')).toEqual({
      entries: [],
      malformed: true,
      warnings: [],
    });
  });

  it('parses valid request JSON and preserves non-JSON text for display', () => {
    expect(parseJsonValue('{"keyword":"待入库"}')).toEqual({
      keyword: '待入库',
    });
    expect(parseJsonValue('keyword=待入库')).toBe('keyword=待入库');
    expect(parseJsonValue('')).toBeUndefined();
  });

  it('always limits this page to warehouse records', () => {
    expect(buildWorkLogQuery('SELF', { pageNo: 1, pageSize: 20 })).toEqual({
      module: 'wms',
      pageNo: 1,
      pageSize: 20,
      scope: 'SELF',
    });
  });

  it('describes the warehouse area from the business path', () => {
    expect(getWarehouseArea('/admin-api/wms/order/inbound/create')).toBe('入库管理');
    expect(getWarehouseArea('/admin-api/wms/order/shipment/approve')).toBe('出库管理');
    expect(getWarehouseArea('/admin-api/wms/md/item/create')).toBe('商品资料');
    expect(getWarehouseArea('/admin-api/wms/stock/page')).toBe('库存管理');
  });

  it('lists every field and marks only changed values', () => {
    expect(
      buildChangeFieldRows('wms_item_sku', {
        code: 'GG-01',
        costPrice: 20,
        name: 'DN100',
        sellingPrice: 28,
      }, {
        code: 'GG-01',
        costPrice: 22,
        name: 'DN100',
        sellingPrice: 28,
      }),
    ).toEqual([
      { changed: false, field: '规格名称', after: 'DN100', before: 'DN100' },
      { changed: false, field: '规格编号', after: 'GG-01', before: 'GG-01' },
      { changed: true, field: '成本价', after: '¥22.00', before: '¥20.00' },
      { changed: false, field: '销售价', after: '¥28.00', before: '¥28.00' },
    ]);
  });

  it('keeps every affected database row as a readable record', () => {
    expect(buildChangeRecords('wms_item', [], [{ code: 'A', name: '商品A' }, { code: 'B', name: '商品B' }]))
      .toHaveLength(2);
  });

  it('translates database column names and excludes internal tenancy fields', () => {
    expect(buildChangeFieldRows('wms_item_sku', {}, { cost_price: 12, tenant_id: 2005 }))
      .toEqual([{ changed: true, field: '成本价', after: '¥12.00', before: '未填写' }]);
  });

  it('turns query JSON into readable business conditions and hides pagination', () => {
    expect(
      buildReadableConditions(
        JSON.stringify({ body: null, query: { keyword: '岩棉', pageNo: '1', pageSize: '20', status: '0' } }),
      ),
    ).toEqual([
      { label: '关键词', value: '岩棉' },
      { label: '状态', value: '启用' },
    ]);
  });

  it('writes list summaries as normal warehouse language', () => {
    expect(humanizeWorkSummary('QUERY', '获得商品分页', '/admin-api/wms/item/page')).toBe('查询了商品资料');
    expect(humanizeWorkSummary('CREATE', '创建商品', '/admin-api/wms/item/create')).toBe('新增了商品');
    expect(humanizeBusinessKey('pageNo: 1 · name: 岩棉管 · code: CP-01')).toBe('名称：岩棉管 · 编号：CP-01');
  });

  it('excludes integration bookkeeping from leadership review', () => {
    const entries = buildReviewEntries([
      { table: 'wms_integration_request', operation: 'INSERT', before: [], after: [{ aggregate_no: null, payload_digest: 'abc', processing_status: 'PROCESSING', creator: 80168 }] },
      { table: 'wms_receipt_order', operation: 'INSERT', before: [], after: [{ no: 'RK-01', status: 0, creator: 80168, create_time: '2026-09-15T14:45:04' }] },
    ]);
    expect(entries).toHaveLength(1);
    expect(entries[0]?.label).toBe('入库单');
    expect(entries[0]?.records[0]?.fields).toEqual([
      { field: '入库单号', before: '未填写', after: 'RK-01', changed: true },
      { field: '状态', before: '未填写', after: '草稿', changed: true },
    ]);
  });

  it('keeps every business field in edits while removing system metadata', () => {
    expect(buildChangeFieldRows('wms_item', { name: '岩棉', selling_price: 20, creator: 80168 },
      { name: '岩棉', selling_price: 22, creator: 80168 }))
      .toEqual([
        { field: '名称', before: '岩棉', after: '岩棉', changed: false },
        { field: '销售价', before: '¥20.00', after: '¥22.00', changed: true },
      ]);
  });

  it('renders receipt source details as business fields, never raw JSON', () => {
    const fields = buildChangeFieldRows('wms_receipt_order_detail', {}, {
      source_snapshot_json: JSON.stringify({ materialNameSnapshot: '岩棉防尘油', specificationSnapshot: '200kg/桶', unitNameSnapshot: '千克' }),
      quantity: 100, price: 500, total_price: 50000, processing_status: 'PROCESSING',
    });
    expect(fields).toEqual([
      { field: '商品名称', before: '未填写', after: '岩棉防尘油', changed: true },
      { field: '规格', before: '未填写', after: '200kg/桶', changed: true },
      { field: '单位', before: '未填写', after: '千克', changed: true },
      { field: '数量', before: '未填写', after: '100', changed: true },
      { field: '单价', before: '未填写', after: '¥500.00', changed: true },
      { field: '总金额', before: '未填写', after: '¥50000.00', changed: true },
    ]);
  });

  it('keeps the list key concise for leadership review', () => {
    expect(humanizeBusinessKey('warehouseId: 106 · sourceOrderNo: RK-01 · skuId: 20022 · itemName: 岩棉'))
      .toBe('来源单号：RK-01 · 商品：岩棉');
  });

  it('extracts product details when the collector already parsed source JSON', () => {
    const rows = buildChangeFieldRows('wms_receipt_order_detail', {}, {
      source_snapshot_json: { materialNameSnapshot: '岩棉防尘油', specificationSnapshot: '200kg/桶' },
    });
    expect(rows.map((row) => `${row.field}：${row.after}`)).toEqual(['商品名称：岩棉防尘油', '规格：200kg/桶']);
  });

  it('shows only filled business facts for creation but full fields for edits', () => {
    const facts = buildReviewEntries([
      { table: 'wms_receipt_order', operation: 'INSERT', before: [], after: [{ no: 'RK-01', remark: null }] },
      { table: 'wms_receipt_order', operation: 'UPDATE', before: [{ no: 'RK-01', remark: null }], after: [{ no: 'RK-01', remark: '已核对' }] },
    ]);
    expect(facts[0]?.records[0]?.fields.map((field) => field.field)).toEqual(['入库单号']);
    expect(facts[1]?.records[0]?.fields.map((field) => field.field)).toEqual(['入库单号', '备注']);
  });

  it('does not repeat a technical-only update as a business amendment', () => {
    const facts = buildReviewEntries([{
      table: 'wms_receipt_order', operation: 'UPDATE',
      before: [{ no: 'RK-01', processing_status: 'PROCESSING' }],
      after: [{ no: 'RK-01', processing_status: 'SUCCESS' }],
    }]);
    expect(facts).toEqual([]);
  });
});
