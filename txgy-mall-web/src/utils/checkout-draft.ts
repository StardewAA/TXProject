import { getTenantContext } from './tenant-context'
import type { SettlementItem } from '@/api/mall'
import { checkoutIntentId, validCheckoutIntent } from './checkout-intent.mjs'
function key() {
  const context = getTenantContext()
  if (!context.accountUserId || context.accountTenantId !== context.browseTenantId) throw new Error('请先核验当前商城账号')
  return `mall-checkout-items:tenant:${context.accountTenantId}:member:${context.accountUserId}`
}
function persist(value: unknown) {
  const storageKey=key(), encoded=JSON.stringify(value)
  sessionStorage.setItem(storageKey,encoded)
  if(sessionStorage.getItem(storageKey)!==encoded) throw new Error('无法安全保存本次结算，请检查浏览器存储后重试')
}
export const checkoutDraft = {
  save: (items: SettlementItem[]) => persist({version:1,intentId:checkoutIntentId(),items}),
  snapshot: (): { intentId: string; items: SettlementItem[] } | null => {
    try {
      const draft = JSON.parse(sessionStorage.getItem(key()) || 'null')
      const items = Array.isArray(draft) ? draft : draft?.items
      if (!Array.isArray(items) || !items.length || items.length > 1000) return null
      if (items.some(item => !item || !/^[1-9]\d*$/.test(String(item.skuId)) || !Number.isSafeInteger(item.count) || item.count <= 0
        || (item.cartId !== undefined && !/^[1-9]\d*$/.test(String(item.cartId))))) return null
      const cleanItems = items.map(({skuId,count,cartId}) => ({skuId,count,...(cartId === undefined ? {} : {cartId})}))
      if (Array.isArray(draft)) {
        const migrated={version:1,intentId:checkoutIntentId(),items:cleanItems}
        persist(migrated)
        return migrated
      }
      if(draft.version!==1 || !validCheckoutIntent(draft.intentId)) return null
      return {intentId:draft.intentId,items:cleanItems}
    } catch { return null }
  },
  read: (): SettlementItem[] => checkoutDraft.snapshot()?.items || [],
  clear: () => sessionStorage.removeItem(key()),
}
