import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope, nextTick, reactive, ref, type EffectScope } from 'vue'
import type { TodoEntry, WorkbenchSummary } from '@/api/order-workbench'

const mocks = vi.hoisted(() => {
  const browser = Object.assign(new EventTarget(), {
    setTimeout: (...args: Parameters<typeof setTimeout>) => setTimeout(...args),
    clearTimeout: (...args: Parameters<typeof clearTimeout>) => clearTimeout(...args),
  })
  const page = Object.assign(new EventTarget(), { hidden: false, createElement: () => ({}) })
  vi.stubGlobal('window', browser); vi.stubGlobal('document', page); vi.stubGlobal('navigator', { onLine: true })
  return { browser, page, auth: {} as { isLoggedIn: boolean; profile: { id: number } | null }, query: vi.fn(), read: vi.fn() }
})
vi.mock('@/stores/auth', () => ({ useAuthStore: () => mocks.auth }))
vi.mock('@/api/http', () => ({ tokenStorage: { getAccess: () => 'real-session-context-is-not-stored-here' } }))
vi.mock('@/api/order-workbench', () => ({ orderWorkbenchApi: { summary: mocks.query, read: mocks.read } }))
import { useOrderWorkbench } from './useOrderWorkbench'
import { invalidateAccountContext } from '@/utils/tenant-context'

function snapshot(patch: Partial<WorkbenchSummary> = {}): WorkbenchSummary {
  return { scopeKey:'1:1:316:BUYER:0',serverTime:1,cursor:'1',resetRequired:true,pollIntervalSeconds:20,pendingCount:5,unreadCount:2,newCount:0,todoCounts:{PAYMENT:5},orderStatusCounts:{allCount:5,unpaidCount:5,undeliveredCount:0,deliveredCount:0,completedCount:0,canceledCount:0,uncommentedCount:0},entries:[],rowEntries:[],hasMore:false,...patch }
}
async function flush(){for(let i=0;i<10;i++)await Promise.resolve();await nextTick()}
describe('共享待办生命周期与服务器阅读进度', () => {
  const scopes: EffectScope[] = []
  beforeEach(() => {
    vi.useFakeTimers();mocks.query.mockReset();mocks.read.mockReset()
    mocks.auth=reactive({isLoggedIn:true,profile:{id:316}})
    mocks.page.hidden=false
    mocks.query.mockImplementation(async(params)=>snapshot({scopeKey:`1:1:316:${params.role}:${params.shopId||0}`}));mocks.read.mockResolvedValue(true)
  })
  afterEach(() => {scopes.forEach(scope=>scope.stop());scopes.length=0;mocks.browser.dispatchEvent(new Event('mall:unauthorized'));vi.useRealTimers()})
  function create(role:()=> 'BUYER'|'SUPPLIER'=()=> 'BUYER',shop:()=>number|undefined=()=>undefined){const scope=effectScope();scopes.push(scope);return scope.run(()=>useOrderWorkbench(role,shop))!}

  it('同账号同范围多个导航/页面只使用一个轮询，最后卸载清理',async()=>{
    create();create();await flush()
    expect(mocks.query).toHaveBeenCalledTimes(1)
    expect(vi.getTimerCount()).toBe(1)
    await vi.advanceTimersByTimeAsync(20000)
    expect(mocks.query).toHaveBeenCalledTimes(2)
    scopes[0]!.stop();expect(vi.getTimerCount()).toBe(1)
    scopes[1]!.stop();expect(vi.getTimerCount()).toBe(0)
    mocks.browser.dispatchEvent(new Event('focus'));await flush()
    expect(mocks.query).toHaveBeenCalledTimes(2)
  })

  it('切换店铺立即清空旧数据，晚到旧请求不能泄漏到新范围',async()=>{
    let release!:(value:WorkbenchSummary)=>void
    mocks.query.mockImplementationOnce(()=>new Promise<WorkbenchSummary>(resolve=>{release=resolve}))
    const shop=ref(1),workbench=create(()=> 'SUPPLIER',()=>shop.value)
    shop.value=2;await flush()
    expect(mocks.query.mock.calls[1]![0].shopId).toBe(2)
    release(snapshot({pendingCount:999}));await flush()
    expect(workbench.state.value.snapshot?.pendingCount).toBe(5)
  })

  it('仅把明确已展示未读事件精确提交，不用游标全读也不改待处理数',async()=>{
    const workbench=create();await flush()
    const entry={eventId:'9007199254740995',unread:true} as TodoEntry
    await workbench.read([entry,entry,{eventId:'6',unread:false} as TodoEntry])
    expect(mocks.read).toHaveBeenCalledWith({role:'BUYER',eventIds:['9007199254740995']})
    expect(workbench.state.value.snapshot?.pendingCount).toBe(5)
  })

  it('退出登录取消旧订阅，后续焦点事件不再请求旧账号',async()=>{
    const workbench=create();await flush()
    mocks.auth.isLoggedIn=false;mocks.auth.profile=null
    await flush();expect(workbench.key.value).toBe('')
    expect(workbench.state.value.snapshot).toBeUndefined()
    expect(vi.getTimerCount()).toBe(0)
    mocks.browser.dispatchEvent(new Event('focus'));await flush()
    expect(mocks.query).toHaveBeenCalledTimes(1)
  })

  it('恢复页面重新取摘要基线，不用历史游标轰炸旧消息',async()=>{
    create();await flush()
    mocks.browser.dispatchEvent(new Event('online'));await flush()
    expect(mocks.query.mock.calls.at(-1)![0].afterCursor).toBeUndefined()
  })

  it('标已读后刷新尚未完成时切换账号，返回失败阻止调用方导航旧订单',async()=>{
    const workbench=create();await flush()
    let release!:(value:WorkbenchSummary)=>void
    mocks.query.mockImplementationOnce(()=>new Promise<WorkbenchSummary>(resolve=>{release=resolve}))
    const pending=workbench.read([{eventId:'1',unread:true} as TodoEntry])
    await flush()
    mocks.auth.isLoggedIn=false;mocks.auth.profile=null
    release(snapshot());await flush()
    expect(await pending).toBe(false)
  })
  it('租户身份代次变化停止旧轮询，换账号后只保留新会员scope',async()=>{
    const workbench=create();await flush()
    let release!:(value:WorkbenchSummary)=>void
    mocks.query.mockImplementationOnce(()=>new Promise<WorkbenchSummary>(resolve=>{release=resolve}))
    const old=workbench.refresh();await flush()
    mocks.auth.profile={id:417}
    mocks.query.mockImplementation(async params=>snapshot({scopeKey:`1:1:417:${params.role}:${params.shopId||0}`,pendingCount:2}))
    invalidateAccountContext();await flush()
    release(snapshot({pendingCount:999}));await old;await flush()
    expect(workbench.key.value).toContain(':1:417:BUYER:0')
    expect(workbench.state.value.snapshot?.pendingCount).toBe(2)
    expect(vi.getTimerCount()).toBe(1)
  })
})
