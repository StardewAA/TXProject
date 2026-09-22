import { beforeEach, expect, it, vi } from 'vitest'
const mocks = vi.hoisted(() => ({ download: vi.fn(), click: vi.fn(), create: vi.fn(), revoke: vi.fn() }))
vi.mock('./http', () => ({ request: vi.fn(), requestBlob: mocks.download }))
import { inquiryApi } from './inquiry'
beforeEach(() => {
  vi.useRealTimers(); mocks.download.mockReset(); mocks.click.mockReset(); mocks.create.mockReset(); mocks.revoke.mockReset()
  vi.stubGlobal('document', { createElement: () => ({ click: mocks.click }) })
  vi.stubGlobal('URL', { createObjectURL: mocks.create.mockReturnValue('blob:test'), revokeObjectURL: mocks.revoke })
})
it('does not start a download from an expired page', async () => {
  await inquiryApi.template(() => false); expect(mocks.download).not.toHaveBeenCalled()
})
it('suppresses file creation after leaving the page or switching accounts', async () => {
  let current = true, resolve!: (value: Blob) => void
  mocks.download.mockReturnValue(new Promise<Blob>(r => { resolve = r }))
  const pending = inquiryApi.template(() => current)
  current = false; resolve(new Blob(['xlsx'])); await pending
  expect(mocks.create).not.toHaveBeenCalled(); expect(mocks.click).not.toHaveBeenCalled()
})
it('downloads only while owned and revokes the temporary URL', async () => {
  vi.useFakeTimers(); mocks.download.mockResolvedValue(new Blob(['xlsx']))
  await inquiryApi.template(() => true); expect(mocks.click).toHaveBeenCalledOnce()
  await vi.runAllTimersAsync(); expect(mocks.revoke).toHaveBeenCalledWith('blob:test'); vi.useRealTimers()
})
it('does not download an error envelope as an Excel file', async () => {
  mocks.download.mockResolvedValue({ code: 401 })
  await expect(inquiryApi.template(() => true)).rejects.toThrow('模板下载失败')
  expect(mocks.create).not.toHaveBeenCalled()
})
