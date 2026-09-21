import { afterEach, beforeEach, expect, it, vi } from 'vitest'
const mocks = vi.hoisted(() => ({ request: vi.fn(), unmount: [] as Array<() => void> }))
vi.mock('@/api/mall-auth', () => ({ challengeRequest: mocks.request }))
vi.mock('vue', async original => ({ ...await original<typeof import('vue')>(), onBeforeUnmount: (cleanup: () => void) => mocks.unmount.push(cleanup) }))
import { ref } from 'vue'
import { useImageRegistration } from './useImageRegistration'

const response = { tenantId: '1', challengeId: 'a'.repeat(32), imageDataUrl: 'data:image/png;base64,aGVsbG8=', expiresIn: 300, retryAfter: 2 }
const deferred = () => {
  let resolve!: (value: typeof response) => void
  const promise = new Promise<typeof response>(yes => { resolve = yes })
  return { promise, resolve }
}
const setup = () => {
  const loginName = ref(''), tenant = ref<string | undefined>('1'), enabled = ref(true), clearCode = vi.fn()
  return { loginName, tenant, enabled, clearCode, form: useImageRegistration(loginName, tenant, enabled, clearCode) }
}
beforeEach(() => {
  vi.useFakeTimers(); mocks.request.mockReset(); mocks.unmount = []
  vi.stubGlobal('window', { setInterval, clearInterval })
  mocks.request.mockResolvedValue(response)
})
afterEach(() => {
  mocks.unmount.forEach(cleanup => cleanup()); vi.useRealTimers(); vi.unstubAllGlobals()
})

it('requests an image only after a valid login name and sends a stable private form session', async () => {
  const page = setup(); page.loginName.value = 'short'
  await vi.advanceTimersByTimeAsync(500); expect(mocks.request).not.toHaveBeenCalled()
  page.loginName.value = ' Buyer_1 '; await vi.advanceTimersByTimeAsync(350)
  expect(page.form.image.value).toBe(response.imageDataUrl)
  const first = mocks.request.mock.calls[0]![1]
  expect(first).toMatchObject({ tenantId: '1', loginName: 'buyer_1', purpose: 'REGISTER' })
  expect(first.captchaSession).toMatch(/^[a-f0-9]{32}$/)
  await vi.advanceTimersByTimeAsync(2000); await page.form.refresh()
  const next = mocks.request.mock.calls[1]![1]
  expect(next.captchaSession).toBe(first.captchaSession); expect(next.previousChallengeId).toBe(response.challengeId)
})
it('clears the old image and answer immediately when the tenant changes', async () => {
  const page = setup(); page.loginName.value = 'buyer_1'; await vi.advanceTimersByTimeAsync(350)
  page.clearCode.mockClear(); page.tenant.value = '2'
  expect(page.form.image.value).toBe(''); expect(page.clearCode).toHaveBeenCalled()
  expect(() => page.form.payload('ab2cd')).toThrow()
  mocks.request.mockResolvedValue({ ...response, tenantId: '2', challengeId: 'c'.repeat(32) })
  await vi.advanceTimersByTimeAsync(2000)
  expect(page.form.payload('ab2cd').tenantId).toBe('2')
})
it('rejects a late image response after the login name changes', async () => {
  const gate = deferred(); mocks.request.mockReturnValueOnce(gate.promise)
  const page = setup(); page.loginName.value = 'buyer_1'; await vi.advanceTimersByTimeAsync(350)
  const oldCurrent = mocks.request.mock.calls[0]![2] as () => boolean
  page.loginName.value = 'buyer_2'; expect(oldCurrent()).toBe(false)
  gate.resolve(response); await vi.advanceTimersByTimeAsync(0)
  expect(page.form.image.value).toBe(''); expect(page.form.error.value).toBe('')
  await vi.advanceTimersByTimeAsync(350)
  expect(page.form.payload('ab2cd').loginName).toBe('buyer_2')
})
it('capability closure invalidates the image and cannot invoke another registration protocol', async () => {
  const page = setup(); page.loginName.value = 'buyer_1'; await vi.advanceTimersByTimeAsync(350)
  page.enabled.value = false
  expect(page.form.image.value).toBe(''); expect(() => page.form.payload('ab2cd')).toThrow()
  await page.form.refresh(); expect(mocks.request).toHaveBeenCalledTimes(1)
  expect(page.form.error.value).toContain('暂未开放')
})
it('request failure is visible and never retries another endpoint', async () => {
  mocks.request.mockRejectedValue(new Error('图片服务暂不可用'))
  const page = setup(); page.loginName.value = 'buyer_1'; await vi.advanceTimersByTimeAsync(350)
  expect(page.form.error.value).toBe('图片服务暂不可用'); expect(page.form.loading.value).toBe(false)
  expect(page.form.image.value).toBe(''); expect(mocks.request).toHaveBeenCalledTimes(1)
})
it('unmount invalidates pending requests and removes timers', async () => {
  const gate = deferred(); mocks.request.mockReturnValue(gate.promise)
  const page = setup(); page.loginName.value = 'buyer_1'; await vi.advanceTimersByTimeAsync(350)
  mocks.unmount.forEach(cleanup => cleanup()); mocks.unmount = []
  gate.resolve(response); await vi.advanceTimersByTimeAsync(0)
  expect(page.form.image.value).toBe(''); expect(page.form.loading.value).toBe(false)
  expect(vi.getTimerCount()).toBe(0)
})
