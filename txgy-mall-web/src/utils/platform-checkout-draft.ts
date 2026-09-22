import type { Identifier } from '@/types/mall'
import { getTenantContext } from './tenant-context'

interface PlatformCheckoutDraft {
  version: 1
  cartItemIds: Identifier[]
  idempotencyKey: string
}

function key() {
  const context = getTenantContext()
  if (!context.accountUserId || !context.accountTenantId) throw new Error('请先核验当前登录账号')
  return `mall-platform-checkout:tenant:${context.accountTenantId}:member:${context.accountUserId}`
}

function normalize(ids: Identifier[]) {
  const values = ids.map(String)
  if (!values.length || values.length > 200 || values.some(id => !/^[1-9]\d*$/.test(id))) {
    throw new Error('结算商品无效，请返回购物车重新选择')
  }
  if (new Set(values).size !== values.length) throw new Error('结算商品重复，请返回购物车重新选择')
  return ids.slice()
}

export const platformCheckoutDraft = {
  save(cartItemIds: Identifier[]) {
    const suffix = typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
      ? crypto.randomUUID() : `${Date.now()}-${Math.random().toString(16).slice(2)}`
    const value: PlatformCheckoutDraft = {
      version: 1,
      cartItemIds: normalize(cartItemIds),
      idempotencyKey: `platform-${suffix}`,
    }
    sessionStorage.setItem(key(), JSON.stringify(value))
  },
  read(): PlatformCheckoutDraft | null {
    try {
      const value = JSON.parse(sessionStorage.getItem(key()) || 'null') as PlatformCheckoutDraft | null
      if (value?.version !== 1 || !Array.isArray(value.cartItemIds)
        || !/^[A-Za-z0-9._:-]{8,64}$/.test(value.idempotencyKey || '')) return null
      return { ...value, cartItemIds: normalize(value.cartItemIds) }
    } catch { return null }
  },
  clear() { sessionStorage.removeItem(key()) },
}
