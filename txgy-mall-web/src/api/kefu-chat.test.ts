import { beforeEach, expect, it, vi } from 'vitest'
const request = vi.hoisted(() => vi.fn())
vi.mock('./http', () => ({ request }))
import { kefuChatApi } from './kefu-chat'
beforeEach(() => request.mockReset())
it('only capability uses public browse scope and never silently falls back to legacy chat', async () => {
  await kefuChatApi.capability(); expect(request).toHaveBeenLastCalledWith({ url: '/promotion/kefu-chat/capability', scope: 'browse', headers: { 'x-silent-error': true } })
  await kefuChatApi.open({ scope: 'SHOP', shopId: 7 }); expect(request.mock.calls.at(-1)?.[0]).toMatchObject({ url: '/promotion/kefu-chat/open', method: 'POST', data: { scope: 'SHOP', shopId: 7 } }); expect(request.mock.calls.at(-1)?.[0]).not.toHaveProperty('scope')
  await kefuChatApi.open({ scope: 'SHOP', shopId: 7, productSkuId: 71 }); expect(request.mock.calls.at(-1)?.[0]).toMatchObject({ data: { scope: 'SHOP', shopId: 7, productSkuId: 71 } })
})
it('text sends only current session version and client id to explicit buyer or agent endpoint', async () => {
  const data = { sessionId: 'test', version: 2, clientMessageId: 'uuid', text: '咨询' }
  await kefuChatApi.send(data); expect(request.mock.calls.at(-1)?.[0]).toMatchObject({ url: '/promotion/kefu-chat/send', data })
  await kefuChatApi.send(data, true); expect(request.mock.calls.at(-1)?.[0]).toMatchObject({ url: '/promotion/kefu-chat/agent/send', data })
})
it('polls by cursor and read acknowledgement cannot mutate payment or inventory', async () => {
  await kefuChatApi.messages('session', '9', true); expect(request.mock.calls.at(-1)?.[0]).toMatchObject({ url: '/promotion/kefu-chat/agent/messages', params: { sessionId: 'session', afterId: '9', limit: 50 } })
  await kefuChatApi.read('session', 10); expect(request.mock.calls.at(-1)?.[0]).toMatchObject({ url: '/promotion/kefu-chat/read', data: { sessionId: 'session', messageId: 10 } })
})
it('presence never posts actor identity or grants role rights', async () => {
  await kefuChatApi.heartbeat({ scope: 'SHOP', shopId: 7, online: true }); expect(request.mock.calls.at(-1)?.[0]).toMatchObject({ url: '/promotion/kefu-chat/agent/heartbeat', data: { scope: 'SHOP', shopId: 7, online: true } })
  await kefuChatApi.claim('session', 7); expect(request.mock.calls.at(-1)?.[0]).toMatchObject({ url: '/promotion/kefu-scope/claim', data: { sessionId: 'session', version: 7 } })
})
