import { describe, expect, it, vi } from 'vitest'
import { createWorkbenchPoller, type WorkbenchPollState } from './order-workbench-poller'

describe('订单待办刷新流', () => {
  it('初载建立基线，不把历史未读提示为新增；之后累计新增且关闭横幅不改待办', async () => {
    const query = vi.fn()
      .mockResolvedValueOnce({ snapshot: { pending: 12, unread: 8 }, cursor: '10', addedCount: 8 })
      .mockResolvedValueOnce({ snapshot: { pending: 14, unread: 10 }, cursor: '12', addedCount: 2 })
    let state: WorkbenchPollState<{ pending: number; unread: number }> | undefined
    const poller = createWorkbenchPoller<{ pending: number; unread: number }>(query, (value) => { state = value })
    await poller.refresh()
    expect(state!.newCount).toBe(0)
    await poller.refresh()
    expect(query.mock.calls[1]).toEqual(['10'])
    expect(state!.newCount).toBe(2)
    poller.dismissBanner()
    expect(state!.newCount).toBe(0)
    expect(state!.snapshot).toEqual({ pending: 14, unread: 10 })
  })

  it('失败保留上次真实计数和时间，不显示假零，恢复后继续从游标补拉', async () => {
    const query = vi.fn().mockResolvedValueOnce({ snapshot: 9, cursor: 'a', addedCount: 0 })
      .mockRejectedValueOnce(new Error('offline'))
      .mockResolvedValueOnce({ snapshot: 10, cursor: 'b', addedCount: 1 })
    let state: WorkbenchPollState<number> | undefined
    const poller = createWorkbenchPoller<number>(query, (value) => { state = value })
    await poller.refresh(); const updated = state!.updatedAt
    await poller.refresh()
    expect(state!.snapshot).toBe(9)
    expect(state!.updatedAt).toBe(updated)
    expect(state!.error).toBe('offline')
    await poller.refresh()
    expect(query.mock.calls[2]).toEqual(['a'])
    expect(state!.error).toBe('')
  })

  it('重复刷新合并串行请求，恢复时在途旧结果不能覆盖新基线', async () => {
    const releases: Array<(value: { snapshot: number; cursor: string; addedCount: number }) => void> = []
    const query = vi.fn(() => new Promise<{ snapshot: number; cursor: string; addedCount: number }>((resolve) => releases.push(resolve)))
    let state: WorkbenchPollState<number> | undefined
    const poller = createWorkbenchPoller(query, (value) => { state = value })
    const first = poller.refresh()
    const second = poller.refresh(true)
    expect(first).toBe(second)
    releases[0]!({ snapshot: 99, cursor: 'old', addedCount: 99 })
    await Promise.resolve(); await Promise.resolve()
    expect(state!.snapshot).toBeUndefined()
    expect(query).toHaveBeenCalledTimes(2)
    releases[1]!({ snapshot: 3, cursor: 'new', addedCount: 3 })
    await first
    expect(state!.snapshot).toBe(3)
    expect(state!.newCount).toBe(0)
  })

  it('销毁范围后晚到的旧账号/旧店铺响应不能写入', async () => {
    let resolve!: (value: { snapshot: number; cursor: string; addedCount: number }) => void
    const publish = vi.fn()
    const poller = createWorkbenchPoller<number>(() => new Promise((done) => { resolve = done }), publish)
    const waiting = poller.refresh()
    poller.dispose()
    resolve({ snapshot: 123, cursor: 'old', addedCount: 1 })
    await waiting
    expect(publish).toHaveBeenCalledTimes(1)
    await poller.refresh()
    expect(publish).toHaveBeenCalledTimes(1)
  })

  it('阅读后服务端未读可减少，真实待办仍保留；刷新间隔有上下限', async () => {
    const query = vi.fn()
      .mockResolvedValueOnce({ snapshot: { pending: 1, unread: 1 }, cursor: '1', addedCount: 1, pollIntervalSeconds: 1 })
      .mockResolvedValueOnce({ snapshot: { pending: 1, unread: 0 }, cursor: '1', addedCount: 0, pollIntervalSeconds: 999 })
    let state: WorkbenchPollState<{ pending: number; unread: number }> | undefined
    const poller = createWorkbenchPoller<{ pending: number; unread: number }>(query, (value) => { state = value })
    await poller.refresh(); expect(state!.interval).toBe(5000)
    await poller.refresh()
    expect(state!.snapshot).toEqual({ pending: 1, unread: 0 })
    expect(state!.interval).toBe(60000)
  })

  it('横幅使用固定基线与真实有效新增数，其他端处理后减少而非逐轮累加', async () => {
    const query = vi.fn()
      .mockResolvedValueOnce({ snapshot: 1, cursor: '10', addedCount: 0 })
      .mockResolvedValueOnce({ snapshot: 3, cursor: '12', addedCount: 2 })
      .mockResolvedValueOnce({ snapshot: 2, cursor: '13', addedCount: 1 })
      .mockResolvedValueOnce({ snapshot: 2, cursor: '13', addedCount: 0 })
    let state: WorkbenchPollState<number> | undefined
    const poller = createWorkbenchPoller<number>(query, (value) => { state=value })
    await poller.refresh(); await poller.refresh(); expect(state!.newCount).toBe(2)
    await poller.refresh(); expect(state!.newCount).toBe(1)
    expect(query.mock.calls[2]).toEqual(['10'])
    poller.dismissBanner(); await poller.refresh()
    expect(query.mock.calls[3]).toEqual(['13'])
    expect(state!.newCount).toBe(0)
  })

  it('服务端回退游标要求重置时建立新基线，之后仍可发现新增', async()=>{
    const query=vi.fn()
      .mockResolvedValueOnce({snapshot:1,cursor:'99',addedCount:0})
      .mockResolvedValueOnce({snapshot:2,cursor:'10',addedCount:0,resetRequired:true})
      .mockResolvedValueOnce({snapshot:3,cursor:'11',addedCount:1})
    let state:WorkbenchPollState<number>|undefined
    const poller=createWorkbenchPoller<number>(query,value=>{state=value})
    await poller.refresh();await poller.refresh();await poller.refresh()
    expect(query.mock.calls[2]).toEqual(['10'])
    expect(state!.newCount).toBe(1)
  })
})
