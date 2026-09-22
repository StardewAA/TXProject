import type { Identifier } from './mall'
export interface KefuScope { scope: 'PLATFORM' | 'SHOP'; shopId: Identifier }
export interface KefuOpenRequest extends KefuScope { productSkuId?: Identifier }
export interface KefuCapability { enabled: boolean; version: string; mode: string; attachmentsEnabled: boolean; message?: string }
export interface KefuSession extends KefuScope {
  id: string; tenantId: Identifier; memberId: Identifier; state: 'WAITING' | 'ACTIVE' | 'CLOSED'
  agentType: 1 | 2 | null; agentId: Identifier | null; version: number
}
export interface KefuThread {
  session: KefuSession; mode: 'SELF_SERVICE' | 'WAITING_HUMAN' | 'HUMAN' | 'CLOSED'
  humanRequested: boolean; onlineAgents: number; availabilityMessage: string
  lastMessageId: Identifier; readMessageId: Identifier; unreadCount: number
  faq: Array<{ id: Identifier; question: string }>; attachmentsEnabled: false
}
export interface KefuMessage {
  id: Identifier; sessionId: string; senderRole: 'MEMBER' | 'AGENT' | 'FAQ' | 'AI' | 'SYSTEM'
  text: string; createdAt: number; clientMessageId?: string; faqId?: Identifier; faqVersion?: number
  card?: { type: 'PRODUCT' | 'ORDER'; resourceId: Identifier; shopId: Identifier; title: string; text: string; spuId?: Identifier; orderId?: Identifier; picUrl?: string; amount?: number; status?: number }
}
export interface KefuMessages { items: KefuMessage[]; nextCursor: Identifier; hasMore: boolean }
export interface KefuResource { resourceType: 'PRODUCT' | 'ORDER'; resourceId: Identifier }
export interface KefuSend { sessionId: string; version: number; clientMessageId: string; text?: string; resourceType?: 'PRODUCT' | 'ORDER'; resourceId?: Identifier }
export interface KefuSendResult { messages: KefuMessage[]; thread: KefuThread }
export interface KefuHeartbeat { online: boolean; expiresAt: number }
