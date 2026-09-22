const positiveId = (value) => {
  const text = String(value ?? '');
  if (!/^[1-9]\d*$/.test(text) || !Number.isSafeInteger(Number(text))) throw new Error('询价单编号无效');
  return text;
};
const text = (value, max) => typeof value === 'string' ? value.trim().slice(0, max) : '';
const nonNegative = (value) => Number.isSafeInteger(Number(value)) && Number(value) >= 0 ? Number(value) : 0;

// Explicit allow-lists prevent a future backend DTO change from exposing contacts or exact addresses.
export function publicInquirySummary(value) {
  if (!value || !text(value.title, 100)) throw new Error('询价数据无效');
  const status = Number(value.status);
  const deadline = Number(value.deadline);
  if (!Number.isSafeInteger(status) || !Number.isFinite(deadline) || deadline <= 0) throw new Error('询价数据无效');
  return Object.freeze({
    id: positiveId(value.id), no: text(value.no, 40), title: text(value.title, 100),
    enterpriseName: text(value.enterpriseName, 100) || '认证采购企业',
    deliveryArea: text(value.deliveryArea, 100) || '成交后确认详细地址',
    itemSummary: text(value.itemSummary, 300), remark: text(value.remark, 500), deadline, expectedDate: Number(value.expectedDate) || null,
    status, quoteCount: nonNegative(value.quoteCount), itemCount: nonNegative(value.itemCount),
  });
}

export function publicInquiryDetail(value) {
  const rawOrder = value?.order || value;
  const order = Object.freeze({
    ...publicInquirySummary(rawOrder),
    // 详情接口的已清洗采购说明位于包装层。
    remark: text(value?.remark ?? rawOrder?.remark, 500),
  });
  if (!Array.isArray(value?.items)) throw new Error('询价明细无效');
  const items = value.items.map((item) => {
    if (!item || !text(item.name, 200) || !text(item.unit, 20) || !(Number(item.quantity) > 0)) throw new Error('询价明细无效');
    return Object.freeze({
      id: positiveId(item.id), name: text(item.name, 200), code: text(item.code, 100),
      category: text(item.category, 100), specification: text(item.specification, 500),
      quantity: Number(item.quantity), unit: text(item.unit, 20), remark: text(item.remark, 500),
      picUrl: text(item.picUrl, 512),
    });
  });
  return Object.freeze({ order, items: Object.freeze(items) });
}

export function publicInquiryPage(value) {
  if (!value || !Array.isArray(value.list) || !Number.isSafeInteger(Number(value.total)) || Number(value.total) < 0) throw new Error('询价列表无效');
  return { list: value.list.map(publicInquirySummary), total: Number(value.total) };
}

export const inquiryStatusText = (status, deadline, now = Date.now()) => {
  if (status === 4) return '已成交';
  if (status === 3) return '已关闭';
  if (status === 1 && deadline <= now) return '已截止';
  if (status === 1) return '报价中';
  return '未公开';
};

export const canPublicQuote = (order, now = Date.now()) => order?.status === 1 && order.deadline > now;
