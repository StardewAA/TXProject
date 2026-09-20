import type { InfraWorkLogApi } from '#/api/infra/work-log';

export interface ParsedWorkLogChanges {
  entries: InfraWorkLogApi.ChangeEntry[];
  malformed: boolean;
  warnings: string[];
}

export interface ReadableFieldRow {
  after: string;
  before: string;
  changed: boolean;
  field: string;
}

const REVIEW_TABLES = new Set([
  'wms_item', 'wms_item_sku', 'wms_item_category', 'wms_item_brand', 'wms_warehouse', 'wms_merchant',
  'wms_receipt_order', 'wms_receipt_order_detail', 'wms_shipment_order', 'wms_shipment_order_detail',
  'wms_movement_order', 'wms_movement_order_detail', 'wms_check_order', 'wms_check_order_detail',
  'wms_inventory', 'wms_inventory_history',
]);
const SYSTEM_FIELDS = new Set([
  'id', 'tenantId', 'deleted', 'creator', 'createTime', 'updater', 'updateTime', 'sourceVersion',
  'payloadDigest', 'requestId', 'processInstanceId', 'businessChainNo', 'notificationStatus',
  'notificationError', 'notificationAttempts', 'nextNotificationTime', 'simulation', 'sourceModule',
  'executionError', 'responsePayload', 'aggregateType', 'aggregateNo', 'processedEventVersion',
]);

const FIELD_LABELS: Record<string, string> = {
  amount: '金额', approveStatus: '审批状态', barCode: '条码', batchManage: '批次管理',
  batchNumber: '批次号', batchNumberRule: '批次号规则', boxSpec: '箱规', brandId: '品牌编号', categoryId: '商品分类编号',
  code: '编号', costPrice: '成本价', createTime: '创建时间', creator: '创建人',
  currentQualityReportId: '当前质检报告编号', dangerousGoods: '危险品', expirationDate: '有效期至',
  expirationRule: '有效期计算规则', expiredOutboundBlocked: '过期禁止出库',
  expiryWarningDays: '到期预警天数', grossWeight: '毛重', height: '高度', id: '系统编号',
  itemId: '商品编号', length: '长度', materialType: '商品类型', minPackageUnit: '最小包装单位',
  name: '名称', netWeight: '净重', number: '单号', packageSpec: '包装规格', palletSpec: '托盘规格',
  productionDate: '生产日期', qualityRequired: '需要质检', remark: '备注',
  serialNumberManage: '序列号管理', shelfLifeManage: '保质期管理', shelfLifeMonths: '保质期（月）',
  sellingPrice: '销售价', sourceVersion: '主数据版本', status: '状态', storageCondition: '存储条件',
  temperatureRequirement: '温度要求', unit: '单位', updateTime: '最后修改时间', updater: '最后修改人',
  warehouseId: '仓库编号', width: '宽度', keyword: '关键词', skus: '商品规格',
  no: '单号', bizOrderNo: '业务单号', merchantId: '往来企业编号', orderTime: '单据时间',
  totalPrice: '总金额', totalQuantity: '总数量', type: '业务类型', quantity: '数量',
  price: '单价', actualQuantity: '实际数量', qualifiedQuantity: '合格数量',
  unqualifiedQuantity: '不合格数量', batchNo: '批次号', skuId: '商品规格编号',
  approvalReason: '审批意见', qualityRemark: '质检备注',
  materialNameSnapshot: '商品名称', specificationSnapshot: '规格', unitNameSnapshot: '单位',
  sourceOrderNo: '来源单号', itemName: '商品', skuName: '规格', itemCode: '商品编号', skuCode: '规格编号',
};

const FIELD_ORDER = ['name', 'code', 'number', 'no', 'materialNameSnapshot', 'specificationSnapshot', 'unitNameSnapshot',
  'barCode', 'materialType', 'unit', 'categoryId', 'brandId',
  'length', 'width', 'height', 'grossWeight', 'netWeight', 'costPrice', 'sellingPrice', 'packageSpec',
  'boxSpec', 'palletSpec', 'minPackageUnit', 'quantity', 'price', 'totalPrice', 'status', 'remark', 'id'];

function canonicalKey(key: string) {
  return key.replace(/_([a-z])/g, (_, letter: string) => letter.toUpperCase());
}

function humanizeKey(key: string, table?: string) {
  const canonical = canonicalKey(key);
  if (canonical === 'no' && table === 'wms_receipt_order') return '入库单号';
  if (canonical === 'no' && table === 'wms_shipment_order') return '出库单号';
  if (canonical === 'no' && table === 'wms_movement_order') return '移库单号';
  if (canonical === 'no' && table === 'wms_check_order') return '盘库单号';
  if (table === 'wms_item_sku' && canonical === 'name') return '规格名称';
  if (table === 'wms_item_sku' && canonical === 'code') return '规格编号';
  return FIELD_LABELS[canonical] || key.replaceAll('_', ' ');
}

function displayValue(key: string, value: unknown, table = ''): string {
  key = canonicalKey(key);
  if (value === undefined || value === null || value === '') return '未填写';
  if (['costPrice', 'sellingPrice', 'amount', 'price', 'totalPrice'].includes(key) && !Number.isNaN(Number(value))) {
    return `¥${Number(value).toFixed(2)}`;
  }
  if (typeof value === 'boolean') return value ? '是' : '否';
  if (key === 'status' && table.endsWith('_order')) {
    return ({ 0: '草稿', 4: '已完成', 5: '已作废' } as Record<string, string>)[String(value)] || `状态 ${value}`;
  }
  if (key === 'status') return `${value}` === '0' ? '启用' : `${value}` === '1' ? '停用' : String(value);
  if (key === 'type' && table === 'wms_receipt_order') {
    return ({ 100: '生产入库', 101: '采购入库', 102: '退货入库', 103: '归还入库' } as Record<string, string>)[String(value)] || String(value);
  }
  if (key === 'materialType') return value === 'RAW_MATERIAL' ? '原材料' : value === 'FINISHED_GOODS' ? '成品' : String(value);
  if (['createTime', 'updateTime'].includes(key) && typeof value === 'string') return value.replace('T', ' ');
  if (['length', 'width', 'height'].includes(key)) return `${value} cm`;
  if (['grossWeight', 'netWeight'].includes(key)) return `${value} kg`;
  if (typeof value === 'object') return JSON.stringify(value);
  return String(value);
}

function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {};
}

function sourceDetail(value: Record<string, unknown>) {
  const source = value.source_snapshot_json ?? value.sourceSnapshotJson;
  try {
    const parsed = asRecord(typeof source === 'string' ? JSON.parse(source) : source);
    const detail = { ...value };
    for (const key of ['materialNameSnapshot', 'specificationSnapshot', 'unitNameSnapshot']) {
      if (parsed[key] !== undefined) detail[key] = parsed[key];
    }
    return detail;
  } catch { return value; }
}

export function buildChangeFieldRows(table: string, beforeValue: unknown, afterValue: unknown): ReadableFieldRow[] {
  const before = table.endsWith('_order_detail') ? sourceDetail(asRecord(beforeValue)) : asRecord(beforeValue);
  const after = table.endsWith('_order_detail') ? sourceDetail(asRecord(afterValue)) : asRecord(afterValue);
  const keys = [...new Set([...Object.keys(before), ...Object.keys(after)])]
    .filter((key) => !SYSTEM_FIELDS.has(canonicalKey(key)) && (FIELD_LABELS[canonicalKey(key)] !== undefined))
    .sort((a, b) => {
      const ai = FIELD_ORDER.indexOf(canonicalKey(a)); const bi = FIELD_ORDER.indexOf(canonicalKey(b));
      return (ai < 0 ? 999 : ai) - (bi < 0 ? 999 : bi) || humanizeKey(a, table).localeCompare(humanizeKey(b, table), 'zh-CN');
    });
  return keys.map((key) => ({
    after: displayValue(key, after[key], table),
    before: displayValue(key, before[key], table),
    changed: JSON.stringify(before[key]) !== JSON.stringify(after[key]), field: humanizeKey(key, table),
  }));
}

export function buildChangeRecords(table: string, beforeValue: unknown, afterValue: unknown) {
  const before = Array.isArray(beforeValue) ? beforeValue : beforeValue ? [beforeValue] : [];
  const after = Array.isArray(afterValue) ? afterValue : afterValue ? [afterValue] : [];
  return Array.from({ length: Math.max(before.length, after.length) }, (_, index) => ({
    fields: buildChangeFieldRows(table, before[index], after[index]),
    key: `${table}-${index}`,
  }));
}

export function buildReviewEntries(entries: InfraWorkLogApi.ChangeEntry[]) {
  return entries.filter((entry) => REVIEW_TABLES.has(entry.table)).map((entry) => ({
    ...entry, label: getBusinessObjectLabel(entry.table),
    records: buildChangeRecords(entry.table, entry.before, entry.after).map((record) => ({
      ...record,
      fields: entry.operation === 'UPDATE' ? record.fields : record.fields.filter((field) =>
        (entry.operation === 'DELETE' ? field.before : field.after) !== '未填写'),
    })),
  })).filter((entry) => entry.records.some((record) =>
    entry.operation === 'UPDATE' ? record.fields.some((field) => field.changed) : record.fields.length));
}

export function getBusinessObjectLabel(table?: string) {
  const labels: Record<string, string> = {
    wms_item: '商品', wms_item_sku: '商品规格', wms_item_category: '商品分类', wms_item_brand: '商品品牌',
    wms_warehouse: '仓库', wms_receipt_order: '入库单', wms_receipt_order_detail: '入库明细',
    wms_shipment_order: '出库单', wms_shipment_order_detail: '出库明细', wms_movement_order: '移库单',
    wms_movement_order_detail: '移库明细', wms_check_order: '盘库单', wms_check_order_detail: '盘库明细',
    wms_inventory: '库存', wms_inventory_history: '库存流水',
  };
  return labels[table || ''] || '仓储业务资料';
}

export function getWarehouseArea(url?: string) {
  const path = (url || '').toLowerCase();
  if (path.includes('receipt') || path.includes('inbound')) return '入库管理';
  if (path.includes('shipment') || path.includes('outbound')) return '出库管理';
  if (path.includes('movement') || path.includes('/move')) return '移库管理';
  if (path.includes('check-order') || path.includes('stocktake')) return '盘库管理';
  if (path.includes('inventory') || path.includes('/stock')) return '库存管理';
  if (path.includes('warehouse')) return '仓库资料';
  if (path.includes('item') || path.includes('/crm/products')) return '商品资料';
  return '仓储系统';
}

function getWarehouseObject(url?: string) {
  const path = (url || '').toLowerCase();
  if (path.includes('receipt-order')) return '入库单';
  if (path.includes('shipment-order')) return '出库单';
  if (path.includes('movement-order')) return '移库单';
  if (path.includes('check-order')) return '盘库单';
  if (path.includes('item-category')) return '商品分类';
  if (path.includes('item-brand')) return '商品品牌';
  if (path.includes('item-sku')) return '商品规格';
  if (path.includes('/item') || path.includes('/crm/products')) return '商品';
  if (path.includes('warehouse')) return '仓库';
  if (path.includes('inventory')) return '库存';
  return getWarehouseArea(url);
}

export function humanizeWorkSummary(action: string, summary: string, url?: string) {
  const object = getWarehouseObject(url);
  const verbs: Record<string, string> = {
    APPROVE: '审批了', CREATE: '新增了', DELETE: '删除了', EXPORT: '导出了', IMPORT: '导入了',
    QUERY: '查询了', REJECT: '驳回了', SUBMIT: '提交了', UPDATE: '修改了',
  };
  return verbs[action] ? `${verbs[action]}${object}${action === 'QUERY' && summary.includes('列表') ? '列表' : action === 'QUERY' && summary.includes('分页') ? '资料' : ''}` : summary;
}

export function humanizeBusinessKey(value?: string) {
  if (!value) return '';
  const ignored = new Set(['pageNo', 'pageSize', 'warehouseId', 'skuId', 'itemId', 'categoryId', 'merchantId', 'id']);
  return value.split(' · ').map((part) => {
    const separator = part.indexOf(':');
    if (separator < 0) return part;
    const key = part.slice(0, separator).trim();
    if (ignored.has(key)) return '';
    if (FIELD_LABELS[canonicalKey(key)] === undefined) return '';
    return `${humanizeKey(key)}：${part.slice(separator + 1).trim()}`;
  }).filter(Boolean).slice(0, 3).join(' · ');
}

export function buildReadableConditions(value?: string): Array<{ label: string; value: string }> {
  const parsed = parseJsonValue(value);
  const root = asRecord(parsed);
  const values = { ...asRecord(root.query), ...asRecord(root.body) };
  const ignored = new Set(['pageNo', 'pageSize', 'sortField', 'sortOrder']);
  return Object.entries(values).filter(([key, item]) => !ignored.has(key) && item !== undefined && item !== null && item !== '')
    .map(([key, item]) => {
      if (key === 'skus' && Array.isArray(item)) {
        const names = item.map((sku) => {
          const row = asRecord(sku);
          return [row.name, row.code ? `编号 ${row.code}` : '', row.sellingPrice != null ? `销售价 ¥${Number(row.sellingPrice).toFixed(2)}` : '']
            .filter(Boolean).join('，');
        });
        return { label: humanizeKey(key), value: `${item.length} 个规格：${names.join('；')}` };
      }
      return { label: humanizeKey(key), value: displayValue(key, item) };
    });
}

export function parseJsonValue(value?: string): unknown {
  if (!value?.trim()) {
    return undefined;
  }
  try {
    return JSON.parse(value);
  } catch {
    return value;
  }
}

export function buildWorkLogQuery<T extends InfraWorkLogApi.QueryReq>(
  scope: InfraWorkLogApi.Scope,
  values: T,
): T & { scope: InfraWorkLogApi.Scope } {
  const query = { ...values, module: 'wms', scope };
  if (scope === 'SELF') {
    Reflect.deleteProperty(query, 'userId');
  }
  return query;
}

export function parseWorkLogChanges(value?: string): ParsedWorkLogChanges {
  if (!value?.trim()) {
    return { entries: [], malformed: false, warnings: [] };
  }
  try {
    const parsed = JSON.parse(value) as Partial<InfraWorkLogApi.Changes>;
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      return { entries: [], malformed: true, warnings: [] };
    }
    return {
      entries: Array.isArray(parsed.entries) ? parsed.entries : [],
      malformed: false,
      warnings: Array.isArray(parsed.warnings)
        ? parsed.warnings.filter((warning): warning is string => typeof warning === 'string')
        : [],
    };
  } catch {
    return { entries: [], malformed: true, warnings: [] };
  }
}
