const MAX_CART_ITEMS = 200;
const CHECKOUT_NO_PATTERN = /^[A-Za-z0-9._:-]{1,40}$/;
const IDEMPOTENCY_KEY_PATTERN = /^[A-Za-z0-9._:-]{8,64}$/;

const positiveId = (value) => {
  const text = String(value ?? '').trim();
  return /^[1-9]\d*$/.test(text) && Number.isSafeInteger(Number(text)) ? Number(text) : null;
};

const nonNegativeMoney = (value) => {
  const amount = Number(value);
  return Number.isSafeInteger(amount) && amount >= 0 ? amount : null;
};

const text = (value, fallback = '') => String(value ?? '').trim() || fallback;

const nonNegativeInteger = (value) => {
  const number = Number(value);
  return Number.isSafeInteger(number) && number >= 0 ? number : null;
};

const normalizeCheckoutNo = (value) => {
  const checkoutNo = text(value);
  return CHECKOUT_NO_PATTERN.test(checkoutNo) ? checkoutNo : null;
};

const platformCheckoutIntentOwner = (value) => {
  const carrierTenantId = positiveId(value?.carrierTenantId);
  const memberId = positiveId(value?.memberId);
  if (!carrierTenantId || !memberId) throw new Error('平台账号身份尚未核验，请重新登录');
  return { carrierTenantId, memberId };
};

const intentHash = (value) => {
  let first = 0x811c9dc5;
  let second = 0x9e3779b9;
  for (let index = 0; index < value.length; index++) {
    const code = value.charCodeAt(index);
    first = Math.imul(first ^ code, 0x01000193);
    second = Math.imul(second ^ code, 0x85ebca6b);
  }
  return `${(first >>> 0).toString(16).padStart(8, '0')}${(second >>> 0).toString(16).padStart(8, '0')}`;
};

const defaultIntentSuffix = () => {
  const random = typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID() : `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 14)}`;
  return String(random).replace(/[^A-Za-z0-9._:-]/g, '').slice(0, 44);
};

const checkoutAmounts = (value) => {
  const amounts = {
    goodsAmount: nonNegativeMoney(value?.goodsAmount),
    freightAmount: nonNegativeMoney(value?.freightAmount),
    discountAmount: nonNegativeMoney(value?.discountAmount),
    payAmount: nonNegativeMoney(value?.payAmount),
    paidAmount: nonNegativeMoney(value?.paidAmount),
    refundedAmount: nonNegativeMoney(value?.refundedAmount),
  };
  return Object.values(amounts).includes(null) ? null : amounts;
};

const normalizeCheckoutSummary = (value) => {
  const checkoutNo = normalizeCheckoutNo(value?.checkoutNo);
  const amounts = checkoutAmounts(value);
  if (!checkoutNo || !amounts) return null;
  const aggregateStatus = text(value?.aggregateStatus, 'UNKNOWN');
  const payStatus = text(value?.payStatus, 'UNKNOWN');
  const planningOnly = value?.planningOnly === true || aggregateStatus === 'DRAFT_ONLY';
  return Object.freeze({
    checkoutNo,
    currency: text(value?.currency, 'CNY'),
    ...amounts,
    aggregateStatus,
    payStatus,
    failureReason: text(value?.failureReason),
    createTime: text(value?.createTime),
    planningOnly,
    platformPayOrderId: positiveId(value?.platformPayOrderId),
    // A planning snapshot must stay non-payable even if a malformed payload says otherwise.
    payable: !planningOnly && payStatus !== 'NOT_PAYABLE' && value?.payable === true,
  });
};

const normalizePlanItem = (value) => {
  const cartItemId = positiveId(value?.cartItemId);
  const spuId = positiveId(value?.spuId);
  const skuId = positiveId(value?.skuId);
  const quantity = positiveId(value?.quantity);
  const unitPrice = nonNegativeMoney(value?.unitPrice);
  const goodsAmount = nonNegativeMoney(value?.goodsAmount);
  if (!cartItemId || !spuId || !skuId || !quantity || unitPrice === null || goodsAmount === null) return null;
  return Object.freeze({
    cartItemId, spuId, skuId, quantity, unitPrice, goodsAmount,
    tenantId: positiveId(value?.tenantId),
    enterpriseId: positiveId(value?.enterpriseId),
    shopId: positiveId(value?.shopId),
    spuName: text(value?.spuName, '商品'),
    picUrl: text(value?.picUrl),
    properties: Object.freeze(Array.isArray(value?.properties)
      ? value.properties.filter((property) => property && typeof property === 'object') : []),
  });
};

const normalizeChildPlan = (value) => {
  const planNo = text(value?.planNo);
  const tenantId = positiveId(value?.tenantId);
  const enterpriseId = positiveId(value?.sellerEnterpriseId);
  const shopId = positiveId(value?.shopId);
  const itemCount = nonNegativeInteger(value?.itemCount);
  const totalQuantity = nonNegativeInteger(value?.totalQuantity);
  const amounts = checkoutAmounts({ ...value, paidAmount: 0, refundedAmount: 0 });
  if (!planNo || !tenantId || !enterpriseId || !shopId || itemCount === null
      || totalQuantity === null || !amounts) return null;
  const sourceItems = Array.isArray(value?.items) ? value.items : [];
  const items = sourceItems.map(normalizePlanItem);
  if (items.some((item) => !item) || items.length !== itemCount
      || items.reduce((sum, item) => sum + item.quantity, 0) !== totalQuantity) return null;
  return Object.freeze({
    planNo, tenantId, sellerEnterpriseId: enterpriseId, shopId, itemCount, totalQuantity,
    goodsAmount: amounts.goodsAmount,
    freightAmount: amounts.freightAmount,
    discountAmount: amounts.discountAmount,
    payAmount: amounts.payAmount,
    tenantName: text(value?.tenantName, `租户 ${tenantId}`),
    enterpriseName: text(value?.enterpriseName, `企业 ${enterpriseId}`),
    shopName: text(value?.shopName, `店铺 ${shopId}`),
    planStatus: text(value?.planStatus, 'DRAFT'),
    blockReason: text(value?.blockReason),
    items: Object.freeze(items),
  });
};

export function normalizePlatformCart(value) {
  const tenantGroups = [];
  for (const tenant of Array.isArray(value?.tenantGroups) ? value.tenantGroups : []) {
    const tenantId = positiveId(tenant?.tenantId);
    const tenantName = text(tenant?.tenantName);
    if (!tenantId || !tenantName || tenantId === 3000) continue;
    const shops = [];
    for (const shop of Array.isArray(tenant?.shops) ? tenant.shops : []) {
      const enterpriseId = positiveId(shop?.enterpriseId);
      const shopId = positiveId(shop?.shopId);
      if (!enterpriseId || !shopId) continue;
      const items = [];
      for (const item of Array.isArray(shop?.items) ? shop.items : []) {
        const id = positiveId(item?.id);
        const spuId = positiveId(item?.spuId);
        const skuId = positiveId(item?.skuId);
        const quantity = positiveId(item?.quantity);
        const price = nonNegativeMoney(item?.price);
        if (!id || !spuId || !skuId || !quantity || price === null
            || positiveId(item?.tenantId) !== tenantId
            || positiveId(item?.enterpriseId) !== enterpriseId
            || positiveId(item?.shopId) !== shopId) continue;
        items.push(Object.freeze({
          id, tenantId, enterpriseId, shopId, spuId, skuId, quantity, price,
          spuName: text(item?.spuName, '商品'),
          picUrl: text(item?.picUrl),
          selected: item?.selected === true,
          valid: item?.valid === true,
          invalidReason: text(item?.invalidReason, item?.valid === true ? '' : '商品当前不可结算'),
          properties: Object.freeze(Array.isArray(item?.properties)
            ? item.properties.filter((property) => property && typeof property === 'object') : []),
        }));
      }
      if (items.length) shops.push(Object.freeze({
        enterpriseId, shopId,
        enterpriseName: text(shop?.enterpriseName, '企业'),
        shopName: text(shop?.shopName, '店铺'),
        items: Object.freeze(items),
      }));
    }
    if (shops.length) tenantGroups.push(Object.freeze({ tenantId, tenantName, shops: Object.freeze(shops) }));
  }
  const rows = tenantGroups.flatMap((tenant) => tenant.shops.flatMap((shop) => shop.items));
  return Object.freeze({ tenantGroups: Object.freeze(tenantGroups), rows: Object.freeze(rows) });
}

export function platformSelectedIds(cart) {
  return Object.freeze((Array.isArray(cart?.rows) ? cart.rows : [])
    .filter((item) => item.valid && item.selected)
    .map((item) => item.id));
}

export function normalizeCartItemIds(value) {
  const source = Array.isArray(value) ? value : String(value ?? '').split(',');
  const ids = [...new Set(source.map(positiveId).filter(Boolean))];
  if (!ids.length || ids.length > MAX_CART_ITEMS) throw new Error('结算商品清单无效');
  return Object.freeze(ids);
}

export function formatPlatformProperties(properties) {
  const values = (Array.isArray(properties) ? properties : [])
    .map((item) => text(item?.valueName || item?.name))
    .filter(Boolean);
  return values.join(' / ') || '默认规格';
}

export function normalizePlatformPreview(value, expectedIds = []) {
  const validItemCount = Number(value?.validItemCount);
  const totalQuantity = Number(value?.totalQuantity);
  const goodsAmount = nonNegativeMoney(value?.goodsAmount);
  const freightAmount = nonNegativeMoney(value?.freightAmount);
  const discountAmount = nonNegativeMoney(value?.discountAmount);
  const payAmount = nonNegativeMoney(value?.payAmount);
  const platformAccountId = positiveId(value?.platformAccountId);
  const addressId = positiveId(value?.address?.addressId);
  const wanted = normalizeCartItemIds(expectedIds);
  if (!platformAccountId || !addressId || !Number.isSafeInteger(validItemCount) || validItemCount < 0
      || !Number.isSafeInteger(totalQuantity) || totalQuantity < 0
      || [goodsAmount, freightAmount, discountAmount, payAmount].includes(null)) {
    throw new Error('平台结算预览数据异常');
  }
  const returnedIds = [];
  const tenantGroups = (Array.isArray(value?.tenantGroups) ? value.tenantGroups : []).map((tenant) => ({
    ...tenant,
    tenantName: text(tenant?.tenantName, '租户商城'),
    shops: (Array.isArray(tenant?.shops) ? tenant.shops : []).map((shop) => ({
      ...shop,
      shopName: text(shop?.shopName, '店铺'),
      enterpriseName: text(shop?.enterpriseName, '企业'),
      items: (Array.isArray(shop?.items) ? shop.items : []).map((item) => {
        const cartItemId = positiveId(item?.cartItemId);
        if (cartItemId) returnedIds.push(cartItemId);
        return { ...item, cartItemId };
      }).filter((item) => item.cartItemId),
    })),
  }));
  const failedItems = (Array.isArray(value?.failedItems) ? value.failedItems : [])
    .map((item) => ({ cartItemId: positiveId(item?.cartItemId), reason: text(item?.reason, '商品未通过校验') }))
    .filter((item) => item.cartItemId);
  const accounted = new Set([...returnedIds, ...failedItems.map((item) => item.cartItemId)]);
  if (wanted.some((id) => !accounted.has(id))) throw new Error('平台结算预览缺少商品校验结果');
  return Object.freeze({
    ...value, platformAccountId, validItemCount, totalQuantity, goodsAmount, freightAmount,
    discountAmount, payAmount, tenantGroups: Object.freeze(tenantGroups),
    failedItems: Object.freeze(failedItems),
    blockingReasons: Object.freeze((Array.isArray(value?.blockingReasons) ? value.blockingReasons : [])
      .map((reason) => text(reason)).filter(Boolean)),
    address: Object.freeze({ ...value.address, addressId }),
    validationPassed: value?.validationPassed === true,
    submittable: value?.submittable === true && value?.validationPassed === true && failedItems.length === 0,
  });
}

export function normalizePlatformCheckoutPage(value) {
  const total = nonNegativeInteger(value?.total);
  if (total === null || !Array.isArray(value?.list)) throw new Error('平台结算记录数据异常');
  const list = value.list.map(normalizeCheckoutSummary);
  if (list.some((item) => !item)) throw new Error('平台结算记录数据异常');
  return Object.freeze({ list: Object.freeze(list), total });
}

export function normalizePlatformCheckoutDetail(value, expectedCheckoutNo) {
  const summary = normalizeCheckoutSummary(value);
  const expected = normalizeCheckoutNo(expectedCheckoutNo);
  if (!summary || !expected || summary.checkoutNo !== expected) throw new Error('平台结算详情数据异常');
  const childPlans = (Array.isArray(value?.childPlans) ? value.childPlans : []).map(normalizeChildPlan);
  if (childPlans.some((item) => !item)) throw new Error('平台结算详情数据异常');
  const childOrders = (Array.isArray(value?.childOrders) ? value.childOrders : []).map((order) => ({
    orderId: positiveId(order?.orderId), orderNo: text(order?.orderNo),
    tenantId: positiveId(order?.tenantId), payAmount: nonNegativeMoney(order?.payAmount),
  }));
  if (childOrders.some((order) => !order.orderId || !order.tenantId || order.payAmount === null)) {
    throw new Error('平台子订单数据异常');
  }
  return Object.freeze({
    ...summary,
    childOrders: Object.freeze(childOrders),
    childPlans: Object.freeze(childPlans),
  });
}

export function platformCheckoutAggregateLabel(value) {
  return ({
    DRAFT_ONLY: '拆单规划',
    CREATED: '已创建子订单',
    PROCESSING: '处理中',
    ALL_UNPAID: '全部待支付',
    PARTIALLY_PAID: '部分已支付',
    ALL_UNDELIVERED: '全部待发货',
    PARTIALLY_DELIVERED: '部分已发货',
    ALL_DELIVERED: '全部已发货',
    PARTIALLY_COMPLETED: '部分完成',
    ALL_COMPLETED: '全部完成',
    PARTIALLY_CANCELED: '部分取消',
    ALL_CANCELED: '全部取消',
    PARTIALLY_REFUNDED: '部分退款',
    ALL_REFUNDED: '全部退款',
    PART_COMPLETED: '部分完成',
    COMPLETED: '全部完成',
    PART_REFUNDED: '部分退款',
    REFUNDED: '全部退款',
    FAILED: '处理失败',
  })[text(value)] || text(value, '未知状态');
}

export function platformCheckoutPayLabel(value) {
  return ({
    NOT_PAYABLE: '不可支付',
    WAITING: '待支付',
    UNPAID: '待支付',
    PAID: '已支付',
    PART_PAID: '部分支付',
    REFUNDED: '已退款',
    PART_REFUNDED: '部分退款',
    CLOSED: '已关闭',
  })[text(value)] || text(value, '未知状态');
}

export function openPlatformCheckoutIntent(storage, ownerValue, cartItemIds, addressValue,
                                           suffixFactory = defaultIntentSuffix) {
  if (!storage || typeof storage.getItem !== 'function' || typeof storage.setItem !== 'function'
      || typeof storage.removeItem !== 'function') throw new Error('无法保存结算重试凭据');
  const owner = platformCheckoutIntentOwner(ownerValue);
  const addressId = positiveId(addressValue);
  if (!addressId) throw new Error('请选择有效的收货地址');
  const ids = [...normalizeCartItemIds(cartItemIds)].sort((left, right) => left - right);
  const identity = `${owner.carrierTenantId}:${owner.memberId}:${addressId}:${ids.join(',')}`;
  const storageKey = `mall-platform-checkout:v1:${owner.carrierTenantId}:${owner.memberId}:${intentHash(identity)}`;
  let raw;
  try { raw = storage.getItem(storageKey); } catch { throw new Error('无法读取结算重试凭据'); }
  if (raw) {
    try {
      const saved = JSON.parse(raw);
      if (saved?.version !== 1 || saved.identity !== identity
          || !IDEMPOTENCY_KEY_PATTERN.test(saved.idempotencyKey || '')) {
        throw new Error('结算重试凭据校验失败，请返回购物车重新进入');
      }
      return Object.freeze({
        idempotencyKey: saved.idempotencyKey,
        clear() {
          try {
            const latest = JSON.parse(storage.getItem(storageKey) || 'null');
            if (latest?.identity === identity && latest?.idempotencyKey === saved.idempotencyKey) {
              storage.removeItem(storageKey);
            }
          } catch { /* Confirmed server success does not become a client failure because cleanup failed. */ }
        },
      });
    } catch (error) {
      if (error instanceof Error && error.message.startsWith('结算重试凭据')) throw error;
      throw new Error('结算重试凭据校验失败，请返回购物车重新进入');
    }
  }
  const idempotencyKey = `h5-platform-${text(suffixFactory())}`;
  if (!IDEMPOTENCY_KEY_PATTERN.test(idempotencyKey)) throw new Error('无法生成有效的结算重试凭据');
  const saved = JSON.stringify({ version: 1, identity, idempotencyKey });
  try {
    storage.setItem(storageKey, saved);
    if (storage.getItem(storageKey) !== saved) throw new Error();
  } catch { throw new Error('无法保存结算重试凭据'); }
  return Object.freeze({
    idempotencyKey,
    clear() {
      try {
        const latest = JSON.parse(storage.getItem(storageKey) || 'null');
        if (latest?.identity === identity && latest?.idempotencyKey === idempotencyKey) {
          storage.removeItem(storageKey);
        }
      } catch { /* Confirmed server success does not become a client failure because cleanup failed. */ }
    },
  });
}

export const platformCartPath = () => '/pages/platform/cart';
export const platformCheckoutHistoryPath = (checkoutNo) => {
  const value = checkoutNo == null || checkoutNo === '' ? null : normalizeCheckoutNo(checkoutNo);
  if (checkoutNo != null && checkoutNo !== '' && !value) throw new Error('平台结算单号无效');
  return value ? `/pages/platform/history?checkoutNo=${encodeURIComponent(value)}` : '/pages/platform/history';
};
export const platformCheckoutPath = (ids) =>
  `/pages/platform/checkout?cartItemIds=${encodeURIComponent(normalizeCartItemIds(ids).join(','))}`;
