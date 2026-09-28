import type { Identifier } from '@/types/mall'
import type { KefuCapability, KefuMessage, KefuResource, KefuScope, KefuThread } from '@/types/kefu-chat'

export function scopedChatEnabled(value: KefuCapability | null | undefined) {
  return value?.enabled === true && value.version === 'scoped-chat-v1' && value.mode === 'FAQ_HUMAN' && value.attachmentsEnabled === false
}
export function kefuScope(shopId?: unknown): KefuScope {
  if (shopId === undefined) return { scope: 'PLATFORM', shopId: 0 }
  if (!/^[1-9]\d*$/.test(String(shopId))) throw new Error('店铺客服入口无效')
  return { scope: 'SHOP', shopId: String(shopId) }
}
export function assertKefuThread(value: KefuThread, expected: KefuScope, tenantId: string | undefined, sessionId?: string, memberId?: string | null) {
  const session = value?.session
  if (!session || session.scope !== expected.scope || String(session.shopId) !== String(expected.shopId) || (tenantId !== undefined && String(session.tenantId) !== tenantId)
    || (sessionId && session.id !== sessionId) || !/^[0-9a-f-]{36}$/i.test(session.id)
    || (memberId && String(session.memberId) !== memberId)
    || !Number.isSafeInteger(session.version) || session.version < 0 || !['WAITING', 'ACTIVE', 'CLOSED'].includes(session.state)
    || !['SELF_SERVICE', 'WAITING_HUMAN', 'HUMAN', 'CLOSED'].includes(value.mode)) throw new Error('会话归属或状态不一致，请重新打开客服')
  return value
}
export function messageCursor(value: Identifier) {
  if (typeof value === 'number' && !Number.isSafeInteger(value)) throw new Error('消息编号无效')
  if (!/^(0|[1-9]\d*)$/.test(String(value))) throw new Error('消息编号无效')
  return String(value)
}
export function mergeKefuMessages(existing: KefuMessage[], incoming: KefuMessage[], sessionId: string, shopId?: Identifier) {
  if (!Array.isArray(incoming)) throw new Error('消息格式无效')
  const map = new Map(existing.map(message => [String(message.id), message]))
  for (const message of incoming) {
    const id = messageCursor(message.id)
    if (id === '0' || message.sessionId !== sessionId || (typeof message.text !== 'string' && !message.card)
      || !['MEMBER', 'AGENT', 'FAQ', 'AI', 'SYSTEM'].includes(message.senderRole)) throw new Error('消息归属或格式无效')
    if (message.card && (!['PRODUCT', 'ORDER'].includes(message.card.type) || typeof message.card.title !== 'string' || typeof message.card.text !== 'string')) throw new Error('卡片格式无效')
    if (message.card && shopId !== undefined && String(message.card.shopId) !== String(shopId)) throw new Error('卡片店铺归属不一致')
    map.set(id, message)
  }
  return [...map.values()].sort((a, b) => BigInt(String(a.id)) < BigInt(String(b.id)) ? -1 : 1).slice(-500)
}
export function resourceFromQuery(query: Record<string, unknown>): KefuResource | undefined {
  if (query.resourceType === undefined && query.resourceId === undefined) return undefined
  if (!['PRODUCT', 'ORDER'].includes(String(query.resourceType)) || !/^[1-9]\d*$/.test(String(query.resourceId))) throw new Error('客服资源引用无效')
  return { resourceType: query.resourceType as 'PRODUCT' | 'ORDER', resourceId: String(query.resourceId) }
}
export function kefuModeLabel(thread: KefuThread) {
  if (thread.mode === 'CLOSED') return '本轮咨询已结束'
  if (thread.mode === 'HUMAN') return '人工已接待'
  if (thread.mode === 'WAITING_HUMAN') return thread.onlineAgents > 0 ? '等待人工接待，不保证即时回复' : '暂无在线坐席，可留言等待处理'
  return '在线咨询'
}
export function chatText(value: string) {
  const text = value.trim()
  if (!text || text.length > 2000) throw new Error('请输入 1—2000 字消息')
  return text
}
export function newClientMessageId() {
  if (!globalThis.crypto?.randomUUID) throw new Error('当前浏览器不支持安全消息编号，请使用本机或 HTTPS 入口')
  return crypto.randomUUID()
}
