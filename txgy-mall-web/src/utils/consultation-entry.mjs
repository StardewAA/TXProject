// Identical on PC/H5. Only explicit clicks resolve context; no message is sent here.
export function consultationId(value) {
  if (typeof value === 'number' && !Number.isSafeInteger(value)) return null;
  const text = String(value ?? '');
  return /^[1-9]\d*$/.test(text) && Number.isSafeInteger(Number(text)) ? text : null;
}
export function createConsultationAction({ resource, readProduct, readOrder, current, confirmPlatform, navigate, update }) {
  let busy = false;
  const key = value => `${value.type}:${value.id ?? ''}`;
  return async function open() {
    if (busy || !current()) return false;
    const initial = resource(), initialKey = key(initial);
    const valid = () => current() && key(resource()) === initialKey;
    busy = true; update({ busy: true, error: '' });
    try {
      let target = { scope: 'PLATFORM', shopId: 0 };
      if (initial.type !== 'PLATFORM') {
        const id = consultationId(initial.id);
        if (!['PRODUCT', 'ORDER'].includes(initial.type) || !id) throw new Error('咨询资源编号不完整，请从平台咨询入口进入');
        const value = await (initial.type === 'PRODUCT' ? readProduct(id) : readOrder(id));
        if (!valid()) return false;
        if (!value || consultationId(value.id) !== id) throw new Error('商品或本人订单无法核验，请刷新后重试；不会自动转到其他店铺');
        const shopId = consultationId(value.shopId);
        if (shopId) target = { scope: 'SHOP', shopId: Number(shopId), resourceType: initial.type, resourceId: Number(id) };
        else {
          const accepted = await confirmPlatform();
          if (!valid() || !accepted) return false;
        }
      }
      if (!valid()) return false;
      await navigate(target);
      return true;
    } catch (error) {
      if (valid()) update({ error: error?.message || '客服入口核验失败，请稍后重试' });
      return false;
    } finally { busy = false; update({ busy: false }); }
  };
}
