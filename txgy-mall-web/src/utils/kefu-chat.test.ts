import { expect, it } from 'vitest'
import { assertKefuThread, chatText, kefuModeLabel, kefuScope, mergeKefuMessages, messageCursor, resourceFromQuery, scopedChatEnabled } from './kefu-chat'
import type { KefuCapability, KefuMessage, KefuThread } from '@/types/kefu-chat'
const id = '12345678-1234-1234-1234-123456789abc'
const thread: KefuThread = { session: { id, tenantId: 1, scope: 'SHOP', shopId: 7, memberId: 77, state: 'WAITING', agentType: null, agentId: null, version: 0 }, mode: 'SELF_SERVICE', humanRequested: false, onlineAgents: 0, availabilityMessage: '无人在线', lastMessageId: 0, readMessageId: 0, unreadCount: 0, faq: [], attachmentsEnabled: false }
const message = (messageId: number, extra = {}): KefuMessage => ({ id: messageId, sessionId: id, senderRole: 'MEMBER', text: '测试', createdAt: 1, ...extra })
it('capability must explicitly match the scoped, no-attachments protocol', () => {
  const capability = { enabled: true, version: 'scoped-chat-v1', mode: 'FAQ_HUMAN', attachmentsEnabled: false }
  expect(scopedChatEnabled(capability)).toBe(true)
  for (const value of [null, undefined, {}, { ...capability, enabled: false }, { ...capability, version: 'legacy' }, { ...capability, mode: 'AI' }, { ...capability, attachmentsEnabled: true }]) expect(scopedChatEnabled(value as KefuCapability)).toBe(false)
})
it('explicit platform is distinct from invalid or missing shop scope', () => { expect(kefuScope()).toEqual({ scope: 'PLATFORM', shopId: 0 }); expect(kefuScope('7')).toEqual({ scope: 'SHOP', shopId: '7' }); expect(() => kefuScope('0')).toThrow() })
it.each(['', '0', '-1', '001', 'other', ['1', '2']])('rejects invalid shop identity %j', value => expect(() => kefuScope(value)).toThrow())
it('checks tenant, shop, session and buyer identity before showing a thread', () => {
  expect(assertKefuThread(thread, { scope: 'SHOP', shopId: 7 }, '1', id, '77')).toBe(thread)
  expect(assertKefuThread({ ...thread, session: { ...thread.session, tenantId: 2024 } }, { scope: 'SHOP', shopId: 7 }, undefined, id, '77')).toBeTruthy()
  for (const patch of [{ tenantId: 2 }, { shopId: 8 }, { memberId: 88 }, { scope: 'PLATFORM' }, { id: 'wrong' }, { version: -1 }]) expect(() => assertKefuThread({ ...thread, session: { ...thread.session, ...patch } } as KefuThread, { scope: 'SHOP', shopId: 7 }, '1', id, '77')).toThrow()
})
it('shows true human waiting or closed state instead of a fake online marker', () => {
  expect(kefuModeLabel(thread)).toBe('在线咨询')
  expect(kefuModeLabel({ ...thread, mode: 'WAITING_HUMAN' })).toContain('暂无在线')
  expect(kefuModeLabel({ ...thread, mode: 'WAITING_HUMAN', onlineAgents: 1 })).toContain('不保证')
  expect(kefuModeLabel({ ...thread, mode: 'HUMAN' })).toBe('人工已接待')
  expect(kefuModeLabel({ ...thread, mode: 'CLOSED' })).toContain('结束')
})
it('deduplicates and sorts server message ids while preserving untrusted text as text', () => {
  const content = '<img src=x onerror=alert(1)>'
  const result = mergeKefuMessages([message(2)], [message(1, { text: content }), message(2)], id)
  expect(result).toHaveLength(2); expect(result[0]?.text).toBe(content)
})
it('never merges another session or unrecognized sender', () => {
  expect(mergeKefuMessages([], [message(1, { senderRole: 'AI' })], id)).toHaveLength(1)
  expect(() => mergeKefuMessages([], [message(1, { sessionId: 'other' })], id)).toThrow()
  expect(() => mergeKefuMessages([], [message(1, { senderRole: 'ADMIN_ALL' })], id)).toThrow()
})
it('bounds history and rejects non-safe numeric cursors', () => {
  expect(mergeKefuMessages([], Array.from({ length: 550 }, (_, i) => message(i + 1)), id)).toHaveLength(500)
  expect(() => messageCursor(Number.MAX_SAFE_INTEGER + 1)).toThrow(); expect(messageCursor('9007199254740993')).toBe('9007199254740993')
})
it('resource links carry only explicit ids and never trust query card text or price', () => {
  expect(resourceFromQuery({ resourceType: 'ORDER', resourceId: '42', price: 1, title: 'fake' })).toEqual({ resourceType: 'ORDER', resourceId: '42' })
  expect(resourceFromQuery({})).toBeUndefined(); expect(() => resourceFromQuery({ resourceType: 'REFUND', resourceId: 2 })).toThrow()
})
it('rejects cards from another shop while explicit platform cards can name their real shop', () => {
  const card = { type: 'PRODUCT', resourceId: 3, shopId: 8, title: '商品', text: '说明' }
  expect(() => mergeKefuMessages([], [message(1, { card })], id, 7)).toThrow()
  expect(mergeKefuMessages([], [message(1, { card })], id)).toHaveLength(1)
})
it('validates text without adding business instructions or automatic mutations', () => { expect(chatText(' 咨询配送 ')).toBe('咨询配送'); expect(() => chatText(' ')).toThrow(); expect(() => chatText('字'.repeat(2001))).toThrow() })
