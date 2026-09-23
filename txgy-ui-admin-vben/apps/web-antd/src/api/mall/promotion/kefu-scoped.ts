import { requestClient } from '#/api/request';

export type KefuScope = 'PLATFORM' | 'SHOP';
export type KefuState = 'WAITING' | 'ACTIVE' | 'CLOSED';
export interface ScopeQuery { scope: KefuScope; shopId: number }
export interface ScopePage extends ScopeQuery { page: number; size: number }
export interface ScopedAgent extends ScopeQuery { actorType: 1 | 2; actorId: number; enabled: boolean; version: number }
export interface AgentGrant extends ScopedAgent { reason: string }
export interface ScopedSession extends ScopeQuery {
  id: string; tenantId: number; memberId: number; state: KefuState;
  agentType?: number | null; agentId?: number | null; version: number;
  createTime: string; updateTime: string;
}
export interface ChatCapability {
  enabled: boolean; version: string; mode: 'FAQ_HUMAN'; attachmentsEnabled: false; message: string;
}
export interface ChatThread {
  session: ScopedSession; mode: 'SELF_SERVICE' | 'WAITING_HUMAN' | 'HUMAN' | 'CLOSED';
  humanRequested: boolean; onlineAgents: number; availabilityMessage: string;
  lastMessageId: number; readMessageId: number; unreadCount: number;
  faq: { id: string; question: string }[]; attachmentsEnabled: false;
}
export interface ChatMessage {
  id: number; sessionId: string; senderRole: 'MEMBER' | 'AGENT' | 'FAQ' | 'AI' | 'SYSTEM';
  text: string; createdAt: number; clientMessageId?: string; faqId?: string; faqVersion?: number;
  card?: ChatCard | null;
}
export interface ChatCard {
  type: 'PRODUCT' | 'ORDER'; resourceId: number; shopId: number; title: string; text: string;
  spuId?: number | null; orderId?: number | null; picUrl?: string | null; amount?: number | null; status?: number | null;
}
export interface ChatPage { items: ChatMessage[]; nextCursor: number; hasMore: boolean }
export interface ChatSend { sessionId: string; version: number; clientMessageId: string; text: string }
export interface ChatSent { messages: ChatMessage[]; thread: ChatThread }
export interface FaqEntry { id: string; question: string; keywords: string[]; answer: string; enabled: boolean }
export interface FaqDocument extends ScopeQuery {
  version: number; publishedVersion: number; entries: FaqEntry[]; publishedEntries: FaqEntry[];
}
const scopeBase = '/promotion/kefu-scope';
const chatBase = '/promotion/kefu-chat';
const options = (signal?: AbortSignal) => ({ signal, headers: { 'X-Silent-Error': 'true' } });

export const getChatCapability = (signal?: AbortSignal) => requestClient.get<ChatCapability>(`${chatBase}/capability`, options(signal));
export const getScopedAgents = (params: ScopePage, signal?: AbortSignal) => requestClient.get<ScopedAgent[]>(`${scopeBase}/agents`, { ...options(signal), params });
export const getMyScopedScopes = (signal?: AbortSignal) => requestClient.get<ScopedAgent[]>(`${scopeBase}/my-scopes`, options(signal));
export const grantScopedAgent = (data: AgentGrant, signal?: AbortSignal) => requestClient.post<boolean>(`${scopeBase}/agent`, data, options(signal));
export const getScopedWorkbench = (params: ScopePage & { state: KefuState }, signal?: AbortSignal) => requestClient.get<ChatThread[]>(`${chatBase}/agent/workbench`, { ...options(signal), params });
export const claimScopedSession = (data: { sessionId: string; version: number }, signal?: AbortSignal) => requestClient.post<ScopedSession>(`${scopeBase}/claim`, data, options(signal));
export const closeScopedSession = (data: { sessionId: string; version: number }, signal?: AbortSignal) => requestClient.post<ScopedSession>(`${scopeBase}/close`, data, options(signal));
export const getAgentThread = (sessionId: string, signal?: AbortSignal) => requestClient.get<ChatThread>(`${chatBase}/agent/thread`, { ...options(signal), params: { sessionId } });
export const getAgentMessages = (params: { sessionId: string; afterId: number; limit: number }, signal?: AbortSignal) => requestClient.get<ChatPage>(`${chatBase}/agent/messages`, { ...options(signal), params });
export const sendAgentMessage = (data: ChatSend, signal?: AbortSignal) => requestClient.post<ChatSent>(`${chatBase}/agent/send`, data, options(signal));
export const readAgentMessages = (data: { sessionId: string; messageId: number }, signal?: AbortSignal) => requestClient.post<ChatThread>(`${chatBase}/agent/read`, data, options(signal));
export const heartbeatAgent = (data: ScopeQuery & { online: boolean }, signal?: AbortSignal) => requestClient.post<{ online: boolean; expiresAt: number }>(`${chatBase}/agent/heartbeat`, data, options(signal));
export const getScopedFaq = (params: ScopeQuery, signal?: AbortSignal) => requestClient.get<FaqDocument>(`${chatBase}/faq`, { ...options(signal), params });
export const saveScopedFaq = (data: ScopeQuery & { version: number; entries: FaqEntry[] }, signal?: AbortSignal) => requestClient.put<FaqDocument>(`${chatBase}/faq`, data, options(signal));
export const publishScopedFaq = (data: ScopeQuery & { version: number; reason: string }, signal?: AbortSignal) => requestClient.post<FaqDocument>(`${chatBase}/faq/publish`, data, options(signal));
