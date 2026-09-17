/** Only an explicit false denotes a new-server unavailable relation; old APIs remain compatible. */
export function collectionUnavailable(item) {
  return item?.saleable === false;
}
export function collectionUnavailableReason(item) {
  if (!collectionUnavailable(item)) return '';
  return typeof item.unavailableReason === 'string' && item.unavailableReason.trim()
    ? item.unavailableReason.trim() : '商品已下架、待审核或不可购买，可编辑删除此记录';
}
export function collectionCanOpen(item) {
  return !collectionUnavailable(item) && item?.spuId != null && String(item.spuId).trim() !== '';
}
