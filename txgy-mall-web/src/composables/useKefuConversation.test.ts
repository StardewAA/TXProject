import { afterEach, beforeEach, expect, it, vi } from 'vitest'
const mocks = vi.hoisted(() => ({ api: { thread: vi.fn(), messages: vi.fn(), read: vi.fn(), send: vi.fn(), human: vi.fn() }, current: true, cleanup: [] as Array<() => void> }))
vi.mock('@/api/kefu-chat', () => ({ kefuChatApi: mocks.api }))
vi.mock('@/utils/tenant-context', () => ({ getTenantContext: () => ({ browseTenantId: '1', accountUserId: '77' }) }))
vi.mock('./useAccountOperation', () => ({ useAccountOperation: () => ({ current: () => mocks.current }) }))
vi.mock('vue', async original => ({ ...await original<typeof import('vue')>(), onBeforeUnmount: (fn: () => void) => mocks.cleanup.push(fn) }))
import { useKefuConversation } from './useKefuConversation'
import type { KefuMessage, KefuThread } from '@/types/kefu-chat'
const id = '12345678-1234-1234-1234-123456789abc'
const initial = (): KefuThread => ({ session: { id, tenantId: 1, scope: 'SHOP', shopId: 7, memberId: 77, state: 'WAITING', agentType: null, agentId: null, version: 0 }, mode: 'SELF_SERVICE', humanRequested: false, onlineAgents: 0, availabilityMessage: '无人在线', lastMessageId: 0, readMessageId: 0, unreadCount: 0, faq: [], attachmentsEnabled: false })
const message = (number = 1): KefuMessage => ({ id: number, sessionId: id, senderRole: 'MEMBER', text: '测试文字', createdAt: Date.now() })
let documentTarget: EventTarget
const visibility = (value: string) => { Object.assign(documentTarget, { visibilityState: value }); documentTarget.dispatchEvent(new Event('visibilitychange')) }
const deferred = <T,>() => { let resolve!: (value: T) => void; const promise = new Promise<T>(done => { resolve = done }); return { resolve, promise } }
beforeEach(() => {
  vi.useFakeTimers(); mocks.cleanup = []; mocks.current = true; Object.values(mocks.api).forEach(fn => fn.mockReset())
  documentTarget = new EventTarget(); Object.assign(documentTarget, { visibilityState: 'visible' }); vi.stubGlobal('document', documentTarget); vi.stubGlobal('window', { setTimeout, clearTimeout })
  let counter = 0; vi.stubGlobal('crypto', { randomUUID: () => `12345678-1234-1234-1234-${String(++counter).padStart(12, '0')}` })
  mocks.api.thread.mockResolvedValue(initial()); mocks.api.messages.mockResolvedValue({ items: [], nextCursor: 0, hasMore: false }); mocks.api.read.mockResolvedValue(initial())
  mocks.api.send.mockResolvedValue({ messages: [message()], thread: initial() }); mocks.api.human.mockResolvedValue({ ...initial(), mode: 'WAITING_HUMAN', humanRequested: true })
})
afterEach(() => { mocks.cleanup.forEach(fn => fn()); vi.useRealTimers(); vi.unstubAllGlobals() })
it('polls ascending cursor, deduplicates overlap and only explicitly acknowledges the loaded cursor', async () => {
  mocks.api.messages.mockResolvedValueOnce({ items: [message()], nextCursor: 1, hasMore: false }).mockResolvedValue({ items: [message(), message(2)], nextCursor: 2, hasMore: false })
  const state = useKefuConversation(initial(), false); await vi.advanceTimersByTimeAsync(5000)
  expect(state.messages.value).toHaveLength(2); expect(mocks.api.messages).toHaveBeenLastCalledWith(id, '1', false); expect(mocks.api.read).not.toHaveBeenCalled()
  await state.acknowledge(); expect(mocks.api.read).toHaveBeenLastCalledWith(id, '2', false)
})
it('unmounted and replaced-account requests cannot repaint or send', async () => {
  const gate = deferred<KefuThread>(); mocks.api.thread.mockReturnValueOnce(gate.promise)
  const state = useKefuConversation(initial(), false); mocks.current = false; gate.resolve(initial()); await vi.advanceTimersByTimeAsync(10000)
  state.input.value = '测试'; await state.send(); expect(state.messages.value).toEqual([]); expect(mocks.api.send).not.toHaveBeenCalled(); expect(mocks.api.thread).toHaveBeenCalledTimes(1)
})
it('unmount stops pending polls and all timers', async () => {
  const gate = deferred<KefuThread>(); mocks.api.thread.mockReturnValueOnce(gate.promise); const state = useKefuConversation(initial(), false)
  mocks.cleanup.forEach(fn => fn()); mocks.cleanup = []; gate.resolve(initial()); await vi.advanceTimersByTimeAsync(10000)
  expect(state.messages.value).toEqual([]); expect(vi.getTimerCount()).toBe(0)
})
it('hidden page does not read or send and resumes only when visible', async () => {
  const state = useKefuConversation(initial(), false); await vi.advanceTimersByTimeAsync(0); visibility('hidden'); state.input.value = '不应发送'
  await state.send(); await vi.advanceTimersByTimeAsync(10000); expect(mocks.api.thread).toHaveBeenCalledTimes(1); expect(mocks.api.send).not.toHaveBeenCalled()
  visibility('visible'); await vi.advanceTimersByTimeAsync(0); expect(mocks.api.thread).toHaveBeenCalledTimes(2)
})
it('failed text send retries the same id and does not duplicate the displayed response', async () => {
  const state = useKefuConversation(initial(), false); await vi.advanceTimersByTimeAsync(0); state.input.value = '测试文字'
  mocks.api.send.mockRejectedValueOnce(new Error('连接中断')); await state.send(); expect(state.input.value).toBe('测试文字'); await state.retryPending()
  expect(mocks.api.send.mock.calls[0]?.[0].clientMessageId).toBe(mocks.api.send.mock.calls[1]?.[0].clientMessageId); expect(state.input.value).toBe(''); expect(state.messages.value).toHaveLength(1)
})
it('uncertain send cannot be replaced by new text, card or human request without explicit discard', async () => {
  const state = useKefuConversation(initial(), false); await vi.advanceTimersByTimeAsync(0); state.input.value = '第一个'; mocks.api.send.mockRejectedValueOnce(new Error('连接中断'))
  await state.send(); state.input.value = '第二个'; await state.send(); await state.sendResource({ resourceType: 'ORDER', resourceId: 10 }); await state.human()
  expect(mocks.api.send).toHaveBeenCalledTimes(1); expect(mocks.api.human).not.toHaveBeenCalled(); expect(state.uncertain.value).toBe(true)
  state.discardPending(); state.input.value = '确认后的新消息'; await state.send(); expect(mocks.api.send.mock.calls[0]?.[0].clientMessageId).not.toBe(mocks.api.send.mock.calls[1]?.[0].clientMessageId)
})
it('cards require explicit send, carry only reference fields and preserve draft text', async () => {
  const state = useKefuConversation(initial(), false); await vi.advanceTimersByTimeAsync(0); expect(mocks.api.send).not.toHaveBeenCalled(); state.input.value = '保留草稿'
  const resource = { resourceType: 'PRODUCT' as const, resourceId: '10' }; mocks.api.send.mockRejectedValueOnce(new Error('连接中断')); await state.sendResource(resource); await state.retryPending()
  expect(mocks.api.send.mock.calls[0]?.[0]).toEqual(mocks.api.send.mock.calls[1]?.[0]); expect(mocks.api.send.mock.calls[0]?.[0].text).toBeUndefined(); expect(state.input.value).toBe('保留草稿')
})
it('human request uses version and shows offline留言 only from confirmed thread', async () => {
  const state = useKefuConversation(initial(), false); await vi.advanceTimersByTimeAsync(0); await state.human(); await state.human()
  expect(mocks.api.human).toHaveBeenCalledTimes(1); expect(mocks.api.human).toHaveBeenCalledWith(id, 0); expect(state.thread.value.mode).toBe('WAITING_HUMAN')
})
it('wrong shop response is rejected and histories are not exposed', async () => {
  mocks.api.thread.mockResolvedValue({ ...initial(), session: { ...initial().session, shopId: 8 } }); mocks.api.messages.mockResolvedValue({ items: [message()], nextCursor: 1, hasMore: false })
  const state = useKefuConversation(initial(), false); await vi.advanceTimersByTimeAsync(0); await state.acknowledge(); await vi.advanceTimersByTimeAsync(10000); expect(state.failed.value).toBe(true); expect(state.messages.value).toEqual([]); expect(mocks.api.thread).toHaveBeenCalledTimes(1)
})
it('revoked read permissions clear history and stop polling until explicit retry', async () => {
  mocks.api.messages.mockResolvedValue({ items: [message()], nextCursor: 1, hasMore: false }); mocks.api.read.mockRejectedValue(Object.assign(new Error('权限已撤销'), { code: 1013016002 }))
  const state = useKefuConversation(initial(), false); await vi.advanceTimersByTimeAsync(0); await state.acknowledge(); await vi.advanceTimersByTimeAsync(10000); expect(state.failed.value).toBe(true); expect(state.messages.value).toEqual([]); expect(mocks.api.thread).toHaveBeenCalledTimes(1)
  mocks.api.read.mockResolvedValue(initial()); await state.refresh(); expect(mocks.api.messages).toHaveBeenLastCalledWith(id, '0', false); expect(state.failed.value).toBe(false)
})
it('transport read receipt failure never resends any chat message', async () => {
  mocks.api.messages.mockResolvedValue({ items: [message()], nextCursor: 1, hasMore: false }); mocks.api.read.mockRejectedValue(new Error('network'))
  const state = useKefuConversation(initial(), false); await vi.advanceTimersByTimeAsync(0); await state.acknowledge(); await vi.advanceTimersByTimeAsync(5000); expect(state.failed.value).toBe(false); expect(state.messages.value).toHaveLength(1); expect(mocks.api.send).not.toHaveBeenCalled()
})
it('unclaimed agents and other actor-type claims cannot send or transfer', async () => {
  const state = useKefuConversation(initial(), true); await vi.advanceTimersByTimeAsync(0); state.input.value = '回复'; await state.send(); await state.human(); await state.sendResource({ resourceType: 'ORDER', resourceId: 9 })
  expect(mocks.api.send).not.toHaveBeenCalled(); expect(mocks.api.human).not.toHaveBeenCalled()
})
it('only the assigned member agent can reply', async () => {
  const claimed: KefuThread = { ...initial(), session: { ...initial().session, state: 'ACTIVE', agentType: 1, agentId: 77 }, mode: 'HUMAN' }; mocks.api.thread.mockResolvedValue(claimed); mocks.api.send.mockResolvedValue({ messages: [message()], thread: claimed })
  const state = useKefuConversation(claimed, true); await vi.advanceTimersByTimeAsync(0); state.input.value = '当前坐席回复'; await state.send(); expect(mocks.api.send).toHaveBeenCalledTimes(1); expect(mocks.api.send.mock.calls[0]?.[1]).toBe(true)
})
it('a card from another shop rejects the whole send response', async () => {
  mocks.api.send.mockResolvedValue({ thread: initial(), messages: [{ ...message(), card: { type: 'PRODUCT', shopId: 9, title: '错误店铺', text: '错误' } }] })
  const state = useKefuConversation(initial(), false); await vi.advanceTimersByTimeAsync(0); await state.sendResource({ resourceType: 'PRODUCT', resourceId: 10 }); expect(state.messages.value).toEqual([]); expect(state.error.value).toContain('店铺归属')
})
it('send during paginated history never acknowledges unloaded intermediate messages', async () => {
  mocks.api.messages.mockResolvedValueOnce({ items: Array.from({ length: 50 }, (_, i) => message(i + 1)), nextCursor: 50, hasMore: true }).mockResolvedValue({ items: Array.from({ length: 50 }, (_, i) => message(i + 51)), nextCursor: 100, hasMore: true })
  mocks.api.send.mockResolvedValue({ messages: [message(151)], thread: initial() }); const state = useKefuConversation(initial(), false)
  await vi.advanceTimersByTimeAsync(0); state.input.value = '插入消息'; await state.send(); await state.acknowledge()
  expect(state.messages.value.at(-1)?.id).toBe(151); expect(mocks.api.read).toHaveBeenLastCalledWith(id, '50', false)
  await vi.advanceTimersByTimeAsync(300); await state.acknowledge(); expect(mocks.api.read).toHaveBeenLastCalledWith(id, '100', false)
})
it('send permission denial hides history and cancels polling immediately', async () => {
  mocks.api.messages.mockResolvedValue({ items: [message()], nextCursor: 1, hasMore: false }); const state = useKefuConversation(initial(), false); await vi.advanceTimersByTimeAsync(0)
  mocks.api.send.mockRejectedValue(Object.assign(new Error('坐席权限已撤销'), { code: 1013016002 })); state.input.value = '不应保留'; await state.send(); await vi.advanceTimersByTimeAsync(10000)
  expect(state.messages.value).toEqual([]); expect(state.failed.value).toBe(true); expect(state.uncertain.value).toBe(false); expect(mocks.api.thread).toHaveBeenCalledTimes(1)
})
