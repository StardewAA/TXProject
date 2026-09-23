import { beforeEach, expect, it, vi } from 'vitest'

const owner = vi.hoisted(() => ({ accountTenantId: '1', accountUserId: '316' }))
vi.mock('./tenant-context', () => ({ getTenantContext: () => owner }))
import { platformCheckoutDraft } from './platform-checkout-draft'

beforeEach(() => {
  const values = new Map<string, string>()
  vi.stubGlobal('sessionStorage', {
    getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => values.set(key, value),
    removeItem: (key: string) => values.delete(key),
    clear: () => values.clear(),
  })
  Object.assign(owner, { accountTenantId: '1', accountUserId: '316' })
})

it('isolates a validated draft by source login and preserves one idempotency key', () => {
  platformCheckoutDraft.save([11, 12])
  const first = platformCheckoutDraft.read()
  expect(first?.cartItemIds).toEqual([11, 12])
  expect(first?.idempotencyKey).toMatch(/^[A-Za-z0-9._:-]{8,64}$/)
  expect(platformCheckoutDraft.read()?.idempotencyKey).toBe(first?.idempotencyKey)
  owner.accountUserId = '317'
  expect(platformCheckoutDraft.read()).toBeNull()
})

it('rejects empty, duplicate and malformed cart item selections', () => {
  expect(() => platformCheckoutDraft.save([])).toThrow()
  expect(() => platformCheckoutDraft.save([11, 11])).toThrow()
  expect(() => platformCheckoutDraft.save(['oops'])).toThrow()
})
