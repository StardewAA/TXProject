import { describe, expect, it } from 'vitest'
import { createRefreshQueue } from './refresh-queue'

describe('支付页面刷新队列', () => {
  it('并发刷新合并，旧请求不会覆盖新筛选，且请求不重叠', async () => {
    const releases: Array<() => void> = []
    const applied: number[] = []
    let calls = 0
    let active = 0
    let maxActive = 0
    const queue = createRefreshQueue(async (current) => {
      const call = ++calls
      active += 1
      maxActive = Math.max(maxActive, active)
      await new Promise<void>((resolve) => releases.push(resolve))
      if (current()) applied.push(call)
      active -= 1
    })
    const first = queue.refresh()
    const second = queue.refresh()
    queue.refresh()
    expect(first).toBe(second)
    expect(calls).toBe(1)
    releases[0]!()
    await Promise.resolve()
    await Promise.resolve()
    expect(calls).toBe(2)
    releases[1]!()
    await first
    expect(applied).toEqual([2])
    expect(maxActive).toBe(1)
  })
  it('页面销毁后晚返回的请求不再写入，也不补发排队请求', async () => {
    let release: () => void = () => {}
    let applied = false
    let calls = 0
    const queue = createRefreshQueue(async (current) => {
      calls += 1
      await new Promise<void>((resolve) => { release = resolve })
      applied = current()
    })
    const pending = queue.refresh()
    queue.refresh()
    queue.dispose()
    release()
    await pending
    await queue.refresh()
    expect(applied).toBe(false)
    expect(calls).toBe(1)
  })
})
