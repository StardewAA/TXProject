import { beforeEach, expect, it, vi } from 'vitest'
import { effectScope } from 'vue'
const mocks = vi.hoisted(() => ({ epoch: 0, confirm: vi.fn() }))
vi.mock('@/utils/tenant-context', () => ({ getTenantContext: () => ({ epoch: mocks.epoch }), isCurrentTenantEpoch: (epoch: number) => epoch === mocks.epoch }))
vi.mock('@/api/http', () => ({ StaleSessionError: class extends Error {} }))
vi.mock('element-plus', () => ({ ElMessageBox: { confirm: mocks.confirm } }))
import { createAccountOperation, useAccountMessageBox, useAccountOperation } from './useAccountOperation'
beforeEach(() => { mocks.epoch = 0; mocks.confirm.mockReset() })
it('login generation changes permanently invalidate old work', () => {
  const op = createAccountOperation()
  expect(op.current()).toBe(true)
  mocks.epoch++
  expect(op.current()).toBe(false); expect(op.assert).toThrow()
  expect(createAccountOperation().current()).toBe(true)
})
it('same session confirmation and token rotation keep the operation usable', () => {
  const op = createAccountOperation()
  op.assert(); op.assert(); expect(op.current()).toBe(true)
})
it('disposing a page blocks continuations without a login change', () => {
  const scope = effectScope(), op = scope.run(useAccountOperation)!
  scope.stop(); expect(op.current()).toBe(false); expect(op.assert).toThrow()
})
it.each(['account', 'unmount'])('confirm cannot continue after %s changes', async kind => {
  let resolve!: (value: string) => void
  mocks.confirm.mockReturnValue(new Promise<string>(r => { resolve = r }))
  const scope = effectScope(), box = scope.run(useAccountMessageBox)!
  const pending = box.confirm('test', 'test').catch(error => error)
  if (kind === 'account') mocks.epoch++; else scope.stop()
  resolve('confirm'); expect(await pending).toBeInstanceOf(Error); scope.stop()
})
it('valid confirmation returns its result and cancellation remains a rejection', async () => {
  const scope = effectScope(), box = scope.run(useAccountMessageBox)!
  mocks.confirm.mockResolvedValueOnce('confirm').mockRejectedValueOnce('cancel')
  expect(await box.confirm('test', 'test')).toBe('confirm')
  await expect(box.confirm('test', 'test')).rejects.toBe('cancel'); scope.stop()
})
it('an expired page cannot even open a new confirmation', async () => {
  const scope = effectScope(), box = scope.run(useAccountMessageBox)!
  mocks.epoch++; await expect(box.confirm('test', 'test')).rejects.toThrow()
  expect(mocks.confirm).not.toHaveBeenCalled(); scope.stop()
})
