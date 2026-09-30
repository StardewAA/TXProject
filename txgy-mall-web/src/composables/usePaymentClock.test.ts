import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createRenderer, h, type App } from 'vue'
import { usePaymentClock } from './usePaymentClock'

const renderer = createRenderer<Record<string, unknown>, Record<string, unknown>>({
  createElement: () => ({}), createText: () => ({}), createComment: () => ({}),
  setText() {}, setElementText() {}, parentNode: () => null, nextSibling: () => null,
  insert() {}, remove() {}, patchProp() {},
})

describe('支付时钟页面生命周期', () => {
  let app: App | undefined
  let browser: EventTarget
  let page: EventTarget & { hidden: boolean }
  let clock: ReturnType<typeof usePaymentClock>
  const server = 1790000000000

  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval', 'Date', 'performance'] })
    browser = Object.assign(new EventTarget(), { setInterval, clearInterval })
    page = Object.assign(new EventTarget(), { hidden: false })
    vi.stubGlobal('window', browser)
    vi.stubGlobal('document', page)
  })
  afterEach(() => { app?.unmount(); app = undefined; vi.unstubAllGlobals(); vi.useRealTimers() })

  function mount(refresh: () => Promise<unknown>) {
    app = renderer.createApp({ setup() { clock = usePaymentClock(refresh); return () => h('div') } })
    app.mount({})
    clock.sync(server, clock.capture())
  }

  it('同页仅一个时钟，卸载清理全部定时器和恢复监听', async () => {
    const refresh = vi.fn(async () => {})
    mount(refresh)
    expect(vi.getTimerCount()).toBe(1)
    await vi.advanceTimersByTimeAsync(3000)
    expect(clock.now.value).toBe(server + 3000)
    app!.unmount(); app = undefined
    expect(vi.getTimerCount()).toBe(0)
    browser.dispatchEvent(new Event('focus'))
    page.dispatchEvent(new Event('visibilitychange'))
    expect(refresh).not.toHaveBeenCalled()
  })

  it('切到后台立即失去付款计时依据，恢复后重校准，重复恢复不并发刷新', async () => {
    const releases: Array<() => void> = []
    const refresh = vi.fn(() => {
      const generation = clock.capture()
      return new Promise<void>((resolve) => { releases.push(() => { clock.sync(server + 60000, generation); resolve() }) })
    })
    mount(refresh)
    page.hidden = true
    page.dispatchEvent(new Event('visibilitychange'))
    expect(clock.now.value).toBeUndefined()
    page.hidden = false
    page.dispatchEvent(new Event('visibilitychange'))
    browser.dispatchEvent(new Event('focus'))
    browser.dispatchEvent(new Event('pageshow'))
    expect(refresh).toHaveBeenCalledTimes(1)
    expect(clock.now.value).toBeUndefined()
    releases[0]!()
    await Promise.resolve()
    expect(clock.now.value).toBeUndefined()
    expect(refresh).toHaveBeenCalledTimes(2)
    releases[1]!()
    await Promise.resolve()
    expect(clock.now.value).toBe(server + 60000)
  })

  it('隐藏前启动的在途响应不能在后台重新校准，即使随后已经恢复可见', async () => {
    mount(async () => {})
    const oldGeneration = clock.capture()
    page.hidden = true
    page.dispatchEvent(new Event('visibilitychange'))
    expect(clock.sync(server + 60000, oldGeneration)).toBe(false)
    expect(clock.now.value).toBeUndefined()
    page.hidden = false
    page.dispatchEvent(new Event('visibilitychange'))
    expect(clock.sync(server + 60000, oldGeneration)).toBe(false)
    await Promise.resolve()
    expect(clock.now.value).toBeUndefined()
  })

  it('正在恢复刷新时再次隐藏/显示，旧响应不能开放支付且必须排队一次新请求', async () => {
    const releases: Array<() => void> = []
    const accepted: boolean[] = []
    const refresh = vi.fn(() => {
      const generation = clock.capture()
      return new Promise<void>((resolve) => { releases.push(() => { accepted.push(clock.sync(server + 60000, generation)); resolve() }) })
    })
    mount(refresh)
    browser.dispatchEvent(new Event('focus'))
    expect(refresh).toHaveBeenCalledTimes(1)
    page.hidden = true
    page.dispatchEvent(new Event('visibilitychange'))
    page.hidden = false
    page.dispatchEvent(new Event('visibilitychange'))
    expect(refresh).toHaveBeenCalledTimes(1)
    releases[0]!()
    await Promise.resolve()
    expect(accepted).toEqual([false])
    expect(clock.now.value).toBeUndefined()
    expect(refresh).toHaveBeenCalledTimes(2)
    releases[1]!()
    await Promise.resolve()
    expect(accepted).toEqual([false, true])
    expect(clock.now.value).toBe(server + 60000)
  })

  it('销毁后任何在途校准结果均无效', () => {
    mount(async () => {})
    const generation = clock.capture()
    app!.unmount(); app = undefined
    expect(clock.sync(server + 60000, generation)).toBe(false)
    expect(clock.now.value).toBeUndefined()
  })

  it('恢复时接口失败保持待核实，不退回电脑时间准许付款', async () => {
    mount(async () => { throw new Error('offline') })
    browser.dispatchEvent(new Event('focus'))
    await Promise.resolve()
    await vi.advanceTimersByTimeAsync(2000)
    expect(clock.now.value).toBeUndefined()
  })

  it('电脑时钟突变只触发重核实，不用新电脑时间重置期限', async () => {
    const refresh = vi.fn(async () => {})
    mount(refresh)
    vi.setSystemTime(Date.now() - 3600000)
    await vi.advanceTimersByTimeAsync(1000)
    expect(refresh).toHaveBeenCalledTimes(1)
    expect(clock.now.value).toBeUndefined()
  })
})
