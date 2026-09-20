// Presentation only. The server owns ERP/shipping state, permissions and provider status.
export const logisticsMessages = Object.freeze({
  ERP_PENDING: '发货申请处理中，尚未确认出库',
  ERP_REJECTED: '本次发货未确认，请联系店铺核实',
  NOT_SHIPPED: '订单尚未发货', NO_TRACKING: '暂未登记可查询的运单',
  NOT_CONFIGURED: '物流查询暂未开通', NO_EVENTS: '运单已登记，暂未获取详细物流轨迹',
  AVAILABLE: '以下为物流服务商提供的文字轨迹', PROVIDER_ERROR: '物流查询暂不可用，请稍后重试',
  NOT_APPLICABLE: '本订单无需快递物流查询', ORDER_CANCELED: '订单已关闭',
});
const transportLabels = Object.freeze({
  UNKNOWN: '暂未获取运输状态', WAITING_PICKUP: '待揽收', PICKED_UP: '已揽收',
  IN_TRANSIT: '运输中', ARRIVED: '到达网点', OUT_FOR_DELIVERY: '派送中',
  SIGNED: '已签收', EXCEPTION: '运输异常', RETURNING: '退回中', RETURNED: '已退回', CANCELED: '已取消',
});
const text = (value, limit = 500) => typeof value === 'string' ? value.slice(0, limit) : '';
export function normalizeLogistics(data, orderId) {
  if (!data || typeof data !== 'object' || !Object.hasOwn(logisticsMessages, data.queryStatus)) {
    throw new Error('物流信息暂不可用，请重试');
  }
  if (orderId !== undefined && String(data.orderId) !== String(orderId)) throw new Error('订单信息不匹配');
  const hasTransport = ['AVAILABLE', 'NO_EVENTS'].includes(data.queryStatus) || (data.queryStatus === 'PROVIDER_ERROR' && data.stale === true);
  const hasEvents = data.queryStatus === 'AVAILABLE' || (data.queryStatus === 'PROVIDER_ERROR' && data.stale === true);
  const status = hasTransport && Object.hasOwn(transportLabels, data.transportStatus) ? data.transportStatus : 'UNKNOWN';
  return {
    ...data, message: logisticsMessages[data.queryStatus],
    carrierName: text(data.carrierName, 100), trackingNo: text(data.trackingNo, 100),
    transportStatus: status, transportStatusLabel: transportLabels[status],
    source: text(data.source, 50), stale: data.stale === true, cached: data.cached === true,
    refreshAfterSeconds: Math.min(3600, Math.max(0, Number(data.refreshAfterSeconds) || 0)),
    events: (hasEvents && Array.isArray(data.events) ? data.events : []).filter(row => row && typeof row.content === 'string')
      .slice(0, 200).map(row => ({ ...row, content: text(row.content, 2000),
        statusLabel: Object.hasOwn(transportLabels, row.status) ? transportLabels[row.status] : '物流更新' })),
  };
}
function logisticsEventTime(value) {
  if (typeof value !== 'string' && typeof value !== 'number') return 0;
  const parsed = new Date(typeof value === 'string' ? value.replace(' ', 'T') : value).getTime();
  return Number.isFinite(parsed) ? parsed : 0;
}
export function logisticsMapQuery(content) {
  if (typeof content !== 'string') return '';
  const normalized = content.replace(/\s+/g, ' ').trim().slice(0, 500);
  const bracketed = [...normalized.matchAll(/[【[(（]([^】\])）]{2,40})[】\])）]/g)]
    .map(match => match[1]?.trim() || '')
    .find(value => /(?:省|自治区|特别行政区|市|自治州|地区|盟|区|县|旗)/.test(value));
  if (bracketed) return bracketed.replace(/(?:分拨|转运|营业|配送|快递|服务)?(?:中心|网点|站点|公司).*$/, '').trim();
  const directed = normalized.match(/(?:到达|抵达|离开|发往|转运至|派送至|收寄于|揽收于|当前位置)[:：\s]*([\u4e00-\u9fa5]{2,20}?(?:省|自治区|特别行政区|市|自治州|地区|盟|区|县|旗))/);
  if (directed?.[1]) return directed[1];
  return normalized.match(/([\u4e00-\u9fa5]{2,10}(?:市|自治州|地区|盟))/)?.[1] || '';
}
export function logisticsMapQueries(events, limit = 6) {
  const seen = new Set();
  return [...(Array.isArray(events) ? events : [])].sort((a, b) => logisticsEventTime(a?.time) - logisticsEventTime(b?.time))
    .map(event => ({ event, query: logisticsMapQuery(event?.content) }))
    .filter(({ query }) => Boolean(query) && !seen.has(query) && Boolean(seen.add(query)))
    .slice(-Math.max(1, Math.min(10, Number(limit) || 6)))
    .map(({ event, query }) => ({ label: query, query, time: event.time }));
}
export function logisticsReceiverPoint(order) {
  const longitude = Number(order?.receiverLongitude), latitude = Number(order?.receiverLatitude);
  return order?.receiverCoordSystem === 'GCJ02' && Number.isFinite(longitude) && longitude >= -180 && longitude <= 180
    && Number.isFinite(latitude) && latitude >= -90 && latitude <= 90 ? [longitude, latitude] : null;
}
export function createLatestTask(isCurrent) {
  let revision = 0;
  return {
    begin() { const captured = ++revision; return () => isCurrent() && captured === revision; },
    invalidate() { revision++; },
  };
}
export function fulfillmentTime(value) {
  if ((typeof value !== 'number' && typeof value !== 'string') || value === '') return '时间未提供';
  const parsed = typeof value === 'string' && /^\d{13}$/.test(value) ? Number(value)
    : typeof value === 'string' ? value.replace(' ', 'T') : value;
  const date = new Date(parsed);
  if (!Number.isFinite(date.getTime())) return '时间未提供';
  const pad = value => String(value).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}
export const manualMapMessage = '地图不可用时可继续手工填写省市区和详细地址，不影响保存和下单。';
export function normalizeLocationCapability(data) {
  const ready = data?.status === 'READY' && data?.enabled === true
    && data?.provider === 'AMAP' && data?.coordinateSystem === 'GCJ02';
  return { status: ready ? 'READY' : data?.status === 'NOT_CONFIGURED' ? 'NOT_CONFIGURED' : 'UNAVAILABLE',
    enabled: ready, manualInputEnabled: true,
    message: ready ? (data.message || '高德地图选点已启用。') : manualMapMessage };
}
export function locationSummary(address, prefix = '') {
  const field = name => prefix ? prefix + name[0].toUpperCase() + name.slice(1) : name;
  const longitude = address?.[field('longitude')], latitude = address?.[field('latitude')];
  if (longitude === null || longitude === undefined || latitude === null || latitude === undefined
      || longitude === '' || latitude === '') return '';
  const lng = Number(longitude), lat = Number(latitude);
  if (!Number.isFinite(lng) || !Number.isFinite(lat) || Math.abs(lng) > 180 || Math.abs(lat) > 90
      || address[field('coordSystem')] !== 'GCJ02') return '';
  return `已保存地址点位（GCJ02）：${lng.toFixed(6)}, ${lat.toFixed(6)}；仅为收货地址参考，不是车辆实时位置。`;
}
export function clearAddressLocation(address) {
  return { ...address, longitude: null, latitude: null, coordSystem: null,
    locationSource: null, locationPrecision: null, locationConfirmedAt: null,
    locationConfirmed: false, clearLocation: true };
}
